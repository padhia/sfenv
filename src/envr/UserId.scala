package sfenv
package envr

import cats.data.Chain

case class UserId(name: String, roles: List[RoleName], meta: ObjMeta, createObj: Boolean)

object UserId:
  given SqlObj[UserId] with
    type Key = String

    extension (user: UserId)
      override def id = user.name

      override def create =
        import user.*
        (if createObj then Chain(Sql.CreateObj("USER", name, meta.toString())) else Chain.empty) ++
          Chain.fromSeq(roles).map(r => Sql.RoleGrant(r, name))

      override def unCreate = if user.createObj then Chain(Sql.DropObj("USER", user.name)) else Chain.empty

      override def alter(old: UserId) =
        Chain.fromSeq(user.roles).regrant(Chain.fromSeq(old.roles), user.name) ++
          (if user.createObj then user.meta.alter("USER", user.name, old.meta) else Chain.empty)
