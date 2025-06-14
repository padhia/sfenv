package sfenv
package envr

import cats.data.Chain

type ObjGrants = Map[ObjType, Grantables]
type AccRoles  = Map[RoleName, ObjGrants]

object AccRoles:
  def cda(objName: String, db: String = "") =
    new CDA[AccRoles]:
      extension (obj: AccRoles)
        override def create: Chain[SqlStmt] =
          obj.toList.flatMap: (r, ogm) =>
            ogm.toList.flatMap: (ot, gr) =>
              gr match
                case Grantables.Roles(rs) => rs
                case Grantables.Privileges(ps) =>


          SqlStmt(Admin.Sec, s"CREATE DATABASE ROLE IF NOT EXISTS $objName") +:
            Chain.fromIterableOnce(opm).flatMap((ot, gs) => gs.grant(ot, if ot == "DATABASE" then dbName else objName, role))

        override def drop: Chain[SqlStmt] = ???
        override def sameId(other: AccRoles): Boolean = ???
        override def updatable(old: AccRoles): Boolean = ???
        override def update(old: AccRoles): Chain[SqlStmt] = ???

  def sqlOperable(objName: String, dbName: String = "") =
    new SqlOperable[AccRoles]:
      private def genRole(role: RoleName, opm: ObjGrants) =
        Sql.CreateRole(role, ObjMeta()) +:
          Chain.fromIterableOnce(opm).flatMap((ot, gs) => gs.grant(ot, if ot == "DATABASE" then dbName else objName, role))

      private def ungenRole(role: RoleName, opm: ObjGrants) =
        Chain.fromIterableOnce(opm).flatMap((ot, gs) => gs.revoke(ot, if ot == "DATABASE" then dbName else objName, role)) :+ Sql
          .DropRole(role)

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
        override def create: Chain[Sql] = Chain.fromIterableOnce(ar).flatMap(genRole(_, _))

        override def unCreate: Chain[Sql] = Chain.fromIterableOnce(ar).flatMap(ungenRole(_, _))

        override def alter(old: AccRoles): Chain[Sql] =
          ar.merge(old)
            .flatMap((grantee, newOgm, oldOgm) =>
              (newOgm, oldOgm) match
                case (Some(ogm), None)  => genRole(grantee, ogm)
                case (None, Some(_))    => Chain(Sql.DropRole(grantee))
                case (Some(n), Some(o)) => alterGrants(grantee, n, o)
                case _                  => Chain.empty
            )
