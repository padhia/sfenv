package sfenv
package envr

import cats.data.Chain

import Sql.*

case class UserId(name: String, roles: List[RoleName], meta: ObjMeta)

object UserId:
  given SqlObj[UserId] with
    type Key = String

    extension (user: UserId)
      override def id = user.name

      override def create =
        import user.*
        Chain(Sql.CreateObj("USER", name, meta.toString())) ++
          Chain.fromSeq(roles).map(r => Sql.RoleGrant(r, name))

      override def unCreate = Chain(Sql.DropObj("USER", user.name))

      override def alter(old: UserId) =
        Chain.fromSeq(user.roles).regrant(Chain.fromSeq(old.roles), user.name) ++
          user.meta.alter("USER", user.name, old.meta)
