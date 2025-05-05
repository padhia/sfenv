package sfenv
package envr

import fs2.Stream

type ObjType = String

extension [T: SqlObj](xs: List[T])
  def create[F[_]]: Stream[F, Sql]   = Stream.emits(xs).flatMap(x => Stream.emits(x.create.toList))
  def unCreate[F[_]]: Stream[F, Sql] = Stream.emits(xs).flatMap(x => Stream.emits(x.unCreate.toList))
  def alter[F[_]](ys: List[T]): Stream[F, Sql] =
    val creates = Stream.emits(xs).filterNot(x => ys.exists(_.id == x.id))
    val drops   = Stream.emits(ys).filterNot(y => xs.exists(_.id == y.id))
    val alters  = Stream.emits(xs).flatMap(x => Stream.emits(ys).map((x, _))).filter(_.id == _.id)

    creates.flatMap(x => Stream.emits(x.create.toList)) ++
      drops.flatMap(x => Stream.emits(x.unCreate.toList)) ++
      alters.flatMap((c, p) => Stream.emits(c.alter(p).toList))

case class SfEnv(
    secAdm: RoleName,
    sysAdm: RoleName,
    imports: List[Import],
    databases: List[Database],
    warehouses: List[Warehouse],
    computePools: List[ComputePool],
    roles: List[Role],
    users: List[UserId],
    createUsers: Boolean,
    createRoles: Boolean,
    drops: ProcessDrops,
    onlyFutures: Boolean
):
  private def create =
    given SqlObj[Database] = Database.sqlObj(secAdm)

    imports.create ++
      computePools.create ++
      warehouses.create ++
      databases.create ++
      roles.create ++
      users.create

  private def alter(old: SfEnv) =
    given SqlObj[Database] = Database.sqlObj(secAdm)

    imports.alter(old.imports) ++
      computePools.alter(old.computePools) ++
      warehouses.alter(old.warehouses) ++
      databases.alter(old.databases) ++
      roles.alter(old.roles) ++
      users.alter(old.users)

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
  def genSqls[F[_]]: Stream[F, String] = genSqls(None)
  def genSqls[F[_]](prev: Option[SfEnv]): Stream[F, String] =
    def formatSql(s: String) =
      if "^(CREATE|USE) ".r.findPrefixOf(s).isDefined then List("", s + ";") else List(s + ";")

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
        def isAccountRole: Boolean =
          r match
            case _: RoleName.Account => true
            case _                   => false

      sql match
        case CreateRole(r, _) => r.isAccountRole
        case DropRole(r)      => r.isAccountRole
        case AlterRole(r, _)  => r.isAccountRole
        case _                => false

    val stmts =
      prev
        .map(p => this.alter(p))
        .getOrElse(this.create) // generate a stream of Sqls from Rbac
        .through(s => if createUsers then s else s.filter(!isUserSql(_)))
        .through(s => if createRoles then s else s.filter(!isRoleSql(_)))

    def isDrop(x: Sql) =
      x match
        case Sql.DropObj(_, _, _) => true
        case Sql.DropRole(_)      => true
        case _                    => false

    val notDropStmts = stmts.filterNot(isDrop)
    val dropStmts    = stmts.filter(isDrop)

    (notDropStmts ++ dropStmts)
      .through(Sql.usingRole)
      .flatMap: (role, sql) =>
        Stream.emits(role.toList).map(_.toSql(secAdm, sysAdm)) ++ Stream.emits(sql.texts(sysAdm, onlyFutures, drops).toList)
      .flatMap(x => Stream.emits(formatSql(x))) // add delimiter and optionally, a blank line for formatting
      .dropWhile(_ == "")                       // skip the initial empty line

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
