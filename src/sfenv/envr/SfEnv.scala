package sfenv
package envr

import cats.data.Chain
import cats.syntax.all.*

import scala.collection.immutable.ListMap

import CDA.given
import UserGrants.*

extension [K, V](xm: ListMap[K, V])
  private def make[T](f: (K, V) => T): List[T]                  = xm.toList.map(f(_, _))
  private def create[T: CDA](f: (K, V) => T)                    = xm.make(f).create
  private def update[T: CDA](f: (K, V) => T, ym: ListMap[K, V]) = xm.make(f).update(ym.make(f))

case class SfEnv(
    secAdm: Ident,
    sysAdm: Ident,
    imports: ListMap[Ident, Import.Value],
    databases: ListMap[Ident, Database.Value],
    warehouses: ListMap[Ident, Warehouse.Value],
    computePools: ListMap[Ident, ComputePool.Value],
    roles: ListMap[Ident, Role.Value],
    users: ListMap[Ident, User.Value],
    userGrants: UserGrants,
):

  private def create =
    imports.create(Import.apply)
      ++ computePools.create(ComputePool.apply)
      ++ warehouses.create(Warehouse.apply)
      ++ databases.create(Database.apply)
      ++ roles.create(Role.apply)
      ++ users.create(User.apply)
      ++ userGrants.grant

  private def alter(old: SfEnv) =
    imports.update(Import.apply, old.imports)
      ++ computePools.update(ComputePool.apply, old.computePools)
      ++ warehouses.update(Warehouse.apply, old.warehouses)
      ++ databases.update(Database.apply, old.databases)
      ++ (old.userGrants -- userGrants).revoke
      ++ roles.update(Role.apply, old.roles)
      ++ users.update(User.apply, old.users)
      ++ (userGrants -- old.userGrants).grant

  def genSqls[F[_]](prev: Option[SfEnv])(using genOpts: SqlGenOptions) =
    def maskDropStmt(stmt: SqlStmt): SqlStmt =
      if genOpts.drop.useMask(stmt.isForeign) then
        stmt.text match
          case Sql.Txt(sql) => if sql.startsWith("DROP") then stmt.copy(text = Sql.Txt("-- " + sql)) else stmt
          case _            => stmt
      else stmt

    def prettify(xs: Chain[String]): Chain[String] =
      xs.foldLeft((Chain.empty[String], Option.empty[String])): (acc, x) =>
        val curr = x.split(' ').headOption
        (acc._1 ++ (if curr == acc._2 || acc._2.isEmpty then Chain(x) else Chain("", x)), curr)
      ._1

    prev
      .map(p => alter(p))
      .getOrElse(create)
      .through(if genOpts.onlyFutures then _.filter(!_.forAll) else identity)
      .map(maskDropStmt)
      .through(SqlStmt.sqlStream(sysAdm, secAdm))
      .through(prettify)

  def adminRoleSqls =
    show"""|USE ROLE USERADMIN;
           |
           |CREATE ROLE IF NOT EXISTS ${secAdm};
           |GRANT ${secAdm} TO ROLE USERADMIN;
           |GRANT CREATE ROLE ON ACCOUNT TO ${secAdm};
           |
           |CREATE ROLE IF NOT EXISTS ${sysAdm};
           |GRANT ${sysAdm} TO ROLE SYSADMIN;
           |
           |USE ROLE SYSADMIN;
           |GRANT CREATE DATABASE ON ACCOUNT TO ${sysAdm};""".stripMargin
