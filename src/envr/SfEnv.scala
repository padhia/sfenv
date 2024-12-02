package sfenv
package envr

import fs2.Stream

import Sql.usingRole
import SqlOperable.given

type ObjType = String

case class SfEnv(
    secAdm: RoleName,
    sysAdm: RoleName,
    imports: List[Import],
    databases: List[Database],
    warehouses: List[Warehouse],
    roles: List[Role],
    users: List[UserId],
    createUsers: Boolean,
    createRoles: Boolean,
    drops: ProcessDrops,
    onlyFutures: Boolean
):
  /** Generate SQL statements from the RBAC configuration
    *
    * @param curr
    *   current RBAC configuration
    * @param prev
    *   previous RBAC configuration, optional
    * @param onlyFuture
    *   only generate FUTURE GRANTS (no GRANT TO ALL)
    * @return
    *   Stream of DDL texts
    */
  def genSqls[F[_]](prev: Option[SfEnv]): Stream[F, String] =
    def formatSql(s: String) =
      (if "^(CREATE|USE) ".r.findPrefixOf(s).isDefined then Stream.emit("") else Stream.empty) ++ Stream.emit(s + ";")

    val stmts = prev.map(p => this.alter(p)).getOrElse(this.create) // generate a stream of Sqls from Rbac
    def isUserSql(sql: Sql) =
      import Sql.*
      sql match
        case CreateObj(k, _, _) => k == "USER"
        case DropObj(k, _, _)   => k == "USER"
        case AlterObj(k, _, _)  => k == "USER"
        case _                  => false

    def isRoleSql(sql: Sql) =
      import Sql.*

      extension (r: RoleName)
        def isAccRole: Boolean =
          r match
            case _: RoleName.Account => true
            case _                   => false

      sql match
        case CreateRole(r, _) => r.isAccRole
        case DropRole(r)      => r.isAccRole
        case AlterRole(r, _)  => r.isAccRole
        case _                => false

    stmts
      .through(s => if createUsers then s else s.filter(!isUserSql(_)))
      .through(s => if createRoles then s else s.filter(!isRoleSql(_)))
      .through(usingRole)
      .flatMap((adm, sql) =>
        Stream.emits(adm.toList).map(_.toSql(secAdm, sysAdm)) ++ Stream.emits(sql.texts(sysAdm, onlyFutures, drops))
      )
      .flatMap(formatSql) // add delimiter and optionally, a blank line for formatting
      .dropWhile(_ == "") // skip the initial empty line

  def adminRoleSqls =
    s"""|USE ROLE USERADMIN;
        |
        |CREATE ROLE IF NOT EXISTS ${secAdm.roleName};
        |GRANT ${secAdm.role} TO ROLE USERADMIN;
        |GRANT CREATE ROLE ON ACCOUNT TO ${secAdm.role};
        |
        |CREATE ROLE IF NOT EXISTS ${sysAdm.roleName};
        |GRANT ${sysAdm.role} TO ROLE SYSADMIN;
        |
        |USE ROLE SYSADMIN;
        |GRANT CREATE DATABASE ON ACCOUNT TO ${sysAdm.role};""".stripMargin

object SfEnv:
  given SqlOperable[SfEnv] with
    extension (x: SfEnv)
      override def create[F[_]]: Stream[F, Sql] =
        given SqlObj[Database] = Database.sqlObj(x.secAdm)

        x.imports.create ++
          x.databases.create ++
          x.warehouses.create ++
          x.roles.create ++
          x.users.create

      override def unCreate[F[_]]: Stream[F, Sql] =
        given SqlObj[Database] = Database.sqlObj(x.secAdm)

        x.imports.unCreate ++
          x.databases.unCreate ++
          x.warehouses.unCreate ++
          x.roles.unCreate ++
          x.users.unCreate

      override def alter[F[_]](old: SfEnv): Stream[F, Sql] =
        given SqlObj[Database] = Database.sqlObj(x.secAdm)

        x.imports.alter(old.imports) ++
          x.databases.alter(old.databases) ++
          x.warehouses.alter(old.warehouses) ++
          x.roles.alter(old.roles) ++
          x.users.alter(old.users)
