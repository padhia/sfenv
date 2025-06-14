package sfenv
package envr

import cats.data.Chain

case class Import(name: String, provider: String, share: String, roles: List[RoleName])

object Import:
  private val priv = List("IMPORTED PRIVILEGES")

  given SqlObj[Import] with
    type Key = String

    extension (shr: Import)
      override def id = shr.name

      override def create =
        import shr.*
        Sql.CreateObj("DATABASE", name, s" FROM SHARE $provider.$share") +:
          Chain.fromSeq(roles).map(r => Sql.ObjGrant("DATABASE", name, r, priv))

      override def unCreate = Chain(Sql.DropObj("DATABASE", shr.name, true))

      override def alter(old: Import) =
        if shr.provider == old.provider && shr.share == old.share then
          shr.roles
            .merge(old.roles)
            .collect:
              case (Some(n), None) => Sql.ObjGrant("DATABASE", shr.name, n, priv)
              case (None, Some(o)) => Sql.ObjGrant("DATABASE", shr.name, o, priv, revoke = true)
        else old.unCreate ++ create
