package sfenv
package envr

import cats.data.{Chain, State}
import cats.syntax.all.*

import scala.collection.immutable.SortedSet

import SqlStmt.*

case class Role(
    name: Ident,
    accRoles: List[RoleName],
    sysRoles: SortedSet[SysRole],
    meta: ObjMeta,
    createObj: Boolean
):
  val roleName = RoleName.Account(name)

object Role:
  val kind = "ROLE"

  type DbSch = (Ident, Ident)

  given Ordering[Role] = Ordering.by(_.name)

  def apply(
      name: String,
      accRoles: List[String] = List.empty,
      meta: ObjMeta = ObjMeta.empty,
      createObj: Boolean = true
  ): Either[String, Role] =
    accRoles
      .traverse(RoleName.apply)
      .map: ar =>
        apply(Ident(name), ar, SortedSet.empty, meta, createObj)

  given CDA[Role]:
    extension (role: Role)
      private def permit(ar: RoleName): State[Set[DbSch], Chain[Permit[String]]] = State: seen =>
        ar match
          case RoleName.Access(db, sch, _) if !seen.contains((db, sch)) =>
            val useDb =
              if seen.exists(_._1 == db) then Chain.empty
              else Chain(Permit(show"USAGE ON DATABASE $db", role.roleName, grantor = Admin.Sys))
            val useSch = Permit(show"USAGE ON SCHEMA $db.$sch", role.roleName, grantor = Admin.Sys)
            (seen + ((db, sch)), useDb ++ Chain(useSch, Permit(ar.show, role.roleName, grantor = Admin.Sec)))
          case _ => (seen, Chain(Permit(ar.show, role.roleName, grantor = Admin.Sec)))

      private def permit(accRoles: Seq[RoleName], f: Permit[String] => SqlStmt): Chain[SqlStmt] =
        Chain
          .fromSeq(accRoles)
          .flatTraverse(permit)
          .run(Set.empty[DbSch])
          .value
          ._2
          .map(f)

      private def sysRoleTC: CDA[SortedSet[SysRole]] =
        given CDA[SysRole] = SysRole.cda(show"ROLE ${role.name}")
        CDA.sortedSet[SysRole]

      private def permit = Permit(RoleName.Account(role.name), Grantee.SysAdm, grantor = Admin.Sec)

      def create: Chain[SqlStmt] =
        given CDA[SortedSet[SysRole]] = sysRoleTC

        val ddl =
          import role.*
          if createObj
          then Chain(meta.dcl(show"${Role.kind.cr} ${name}"), role.permit.grant)
          else Chain.empty

        ddl ++ role.sysRoles.create ++ role.permit(role.accRoles, _.grant)

      def drop: Chain[SqlStmt] =
        given sr: CDA[SortedSet[SysRole]] = sysRoleTC

        val ddl =
          import role.*
          if createObj
          then Chain(role.permit.revoke, show"${Role.kind.dr} ${name}".dcl)
          else Chain.empty

        role.permit(role.accRoles, _.revoke) ++ sr.drop(role.sysRoles) ++ ddl

      def update(old: Role): Chain[SqlStmt] =
        given sr: CDA[SortedSet[SysRole]] = sysRoleTC

        role.meta.dcl(show"${kind.alt} ${role.name}", old.meta)
          ++ role.permit(old.accRoles -- role.accRoles, _.revoke)
          ++ role.permit(role.accRoles -- old.accRoles, _.grant)
          ++ role.sysRoles.update(old.sysRoles)

      def sameId(other: Role): Boolean  = role.name == other.name
      def updatable(old: Role): Boolean = true
