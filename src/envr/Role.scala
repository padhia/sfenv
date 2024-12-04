package sfenv
package envr

import cats.data.Chain

import Sql.*

case class Role(name: RoleName, accRoles: List[RoleName], meta: ObjMeta)

object Role:
  given SqlObj[Role] with
    override type Key = RoleName

    extension (role: Role)
      override def id = role.name

      override def create: Chain[Sql] =
        import role.*
        Sql.CreateRole(name, meta) +: Chain.fromSeq(accRoles).map(ar => Sql.RoleGrant(ar, name))

      override def unCreate = Chain(Sql.DropObj("ROLE", role.name.roleName))

      override def alter(old: Role): Chain[Sql] =
        Chain.fromSeq(role.accRoles).regrant(Chain.fromSeq(old.accRoles), role.name) ++
          role.meta.alter(role.name.kind, role.name.roleName, old.meta)
