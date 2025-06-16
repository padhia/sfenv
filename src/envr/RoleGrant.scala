package sfenv
package envr

import cats.data.Chain

case class RoleGrant(name: RoleName, grantee: RoleName | UserName):
  def grant: Chain[SqlStmt]  = toSql("GRANT", "TO")
  def revoke: Chain[SqlStmt] = toSql("REVOKE", "FROM")

  private def toSql(grant: String, to: String): Chain[SqlStmt] =
    val granteeName = grantee match
      case x: RoleName => x.role
      case x: UserName => x

    Chain(SqlStmt(Admin.Sec, s"$grant USAGE ON $name $to $granteeName"))

object RoleGrant:
  given CDA[RoleGrant]:
    extension (obj: RoleGrant)
      override def create: Chain[SqlStmt]                 = obj.grant
      override def drop: Chain[SqlStmt]                   = obj.revoke
      override def sameId(other: RoleGrant): Boolean      = obj.name == other.name && obj.grantee == other.grantee
      override def updatable(old: RoleGrant): Boolean     = false
      override def update(old: RoleGrant): Chain[SqlStmt] = old.revoke ++ obj.grant
