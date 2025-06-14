package sfenv
package envr

import cats.data.Chain
import cats.syntax.all.*

import SqlStmt.*

case class Role(name: Ident, value: Role.Value):
  export value.*

object Role:
  val kind = "ROLE"

  case class Value(accRoles: List[RoleName], meta: ObjMeta, createObj: Boolean)

  def apply(
      name: String,
      accRoles: List[String] = List.empty,
      meta: ObjMeta = ObjMeta.empty,
      createObj: Boolean = true
  ): Either[String, Role] =
    accRoles
      .traverse(RoleName.apply)
      .map: ar =>
        apply(Ident(name), Value(ar, meta, createObj))

  given CDA[Role]:
    extension (role: Role)
      private def permit(accRoles: Seq[RoleName], f: Permit[RoleName] => SqlStmt) =
        Chain.fromSeq(accRoles.map(ar => Permit(ar, RoleName.Account(role.name), grantor = Admin.Sec))).map(f)

      private def permit = Permit(RoleName.Account(role.name), Grantee.SysAdm, grantor = Admin.Sec)

      def create: Chain[SqlStmt] =
        val ddl =
          import role.*
          if createObj
          then Chain(meta.dcl(show"${Role.kind.cr} ${name}"), role.permit.grant)
          else Chain.empty

        ddl ++ role.permit(role.accRoles, _.grant)

      def drop: Chain[SqlStmt] =
        val ddl =
          import role.*
          if createObj
          then Chain(role.permit.revoke, show"${Role.kind.dr} ${name}".dcl)
          else Chain.empty
        role.permit(role.accRoles, _.revoke) ++ ddl

      def update(old: Role): Chain[SqlStmt] =
        role.meta.dcl(show"${kind.alt} ${role.name}", old.meta)
          ++ role.permit(old.accRoles -- role.accRoles, _.revoke)
          ++ role.permit(role.accRoles -- old.accRoles, _.grant)

      def sameId(other: Role): Boolean  = role.name == other.name
      def updatable(old: Role): Boolean = true
