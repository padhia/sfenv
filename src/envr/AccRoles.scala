package sfenv
package envr

import cats.data.Chain

type ObjGrants = Map[ObjType, Grantables]
type AccRoles  = Map[RoleName, ObjGrants]

object AccRoles:
  def sqlOperable(objName: String, dbName: String = "") =
    new SqlOperable[AccRoles]:
      private def genRole(role: RoleName, opm: ObjGrants) =
        Sql.CreateRole(role, ObjMeta.empty) +:
          Chain.fromIterableOnce(opm).flatMap((ot, gs) => gs.grant(ot, if ot == "DATABASE" then dbName else objName, role))

      private def alterGrants(grantee: RoleName, opm: ObjGrants, old: ObjGrants): Chain[Sql] =
        opm
          .merge(old)
          .flatMap((ot, gsN, gsO) =>
            (gsN, gsO) match
              case (Some(gs), None)   => gs.grant(ot, objName, grantee)
              case (None, Some(gs))   => gs.revoke(ot, objName, grantee)
              case (Some(n), Some(o)) => n.alter(o, ot, objName, grantee)
              case _                  => Chain.empty
          )

      extension (ar: AccRoles)
        override def create: Chain[Sql] =
          Chain.fromIterableOnce(ar).flatMap(genRole(_, _))

        override def unCreate: Chain[Sql] =
          Chain.fromIterableOnce(ar.keys).map(r => Sql.DropRole(r))

        override def alter(old: AccRoles): Chain[Sql] =
          ar.merge(old)
            .flatMap((grantee, newOgm, oldOgm) =>
              (newOgm, oldOgm) match
                case (Some(ogm), None)  => genRole(grantee, ogm)
                case (None, Some(_))    => Chain(Sql.DropRole(grantee))
                case (Some(n), Some(o)) => alterGrants(grantee, n, o)
                case _                  => Chain.empty
            )
