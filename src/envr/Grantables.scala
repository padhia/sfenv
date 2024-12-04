package sfenv
package envr

import cats.data.Chain

enum Grantables:
  case Roles(rs: List[RoleName])
  case Privileges(ps: List[String])

  def permit[F[_]](revoke: Boolean)(of: ObjType, to: String, grantee: RoleName): Chain[Sql] =
    this match
      case Roles(rs)      => Chain.fromSeq(rs).map(r => Sql.RoleGrant(r, grantee, revoke))
      case Privileges(ps) => Chain(Sql.ObjGrant(of, to, grantee, ps, revoke))

  def grant[F[_]]  = permit[F](false)
  def revoke[F[_]] = permit[F](true)

  def alter[F[_]](other: Grantables, of: ObjType, to: String, grantee: RoleName): Chain[Sql] =
    (this, other) match
      case (Roles(rsN), Roles(rsO)) =>
        Roles(rsN.filter(!rsO.contains(_))).grant(of, to, grantee) ++
          Roles(rsO.filter(!rsN.contains(_))).revoke(of, to, grantee)
      case (Privileges(psN), Privileges(psO)) =>
        Privileges(psN.filter(!psO.contains(_))).grant(of, to, grantee) ++
          Privileges(psO.filter(!psN.contains(_))).revoke(of, to, grantee)
      case _ => Chain.empty
