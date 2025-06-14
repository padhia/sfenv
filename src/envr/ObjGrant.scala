package sfenv
package envr

import cats.data.Chain

case class ObjGrant(objType: String, objName: String, grantee: RoleName, privileges: List[String]):
  def grant: Chain[SqlStmt]  = toSql("GRANT", "TO")
  def revoke: Chain[SqlStmt] = toSql("REVOKE", "FROM")

  private def toSql(grant: String, to: String): Chain[SqlStmt] = toSql(grant, to, privileges)

  private def toSql(grant: String, to: String, privileges: List[String]): Chain[SqlStmt] =
    if privileges.isEmpty then Chain.empty
    else
      val objTypes = if objType.endsWith("Y") then objType.stripSuffix("Y") + "IES" else objName + "S"
      val priv     = privileges.mkString(", ")

      if List("DATABASE", "SCHEMA", "WAREHOUSE", "COMPUTE POOL").exists(_ == objType) then
        Chain(SqlStmt(Admin.Sys, s"$grant $priv ON $objType $objName $to ${grantee.role}"))
      else
        Chain(
          SqlStmt(Admin.Sys, s"$grant $priv ON FUTURE $objTypes IN SCHEMA $objName $to ${grantee.role}"),
          SqlStmt(Admin.Sys, s"$grant $priv ON ALL $objTypes IN SCHEMA $objName $to ${grantee.role}", hasPast = true),
        )

object ObjGrant:
  given CDA[ObjGrant]:
    extension (obj: ObjGrant)
      override def create: Chain[SqlStmt]           = obj.grant
      override def drop: Chain[SqlStmt]             = obj.revoke
      override def sameId(other: ObjGrant): Boolean =
        obj.objType == other.objType && obj.objName == other.objName && obj.grantee == other.grantee
      override def updatable(old: ObjGrant): Boolean     = true
      override def update(old: ObjGrant): Chain[SqlStmt] =
        if obj.privileges == old.privileges then Chain.empty
        else
          obj.toSql("GRANT", "TO", obj.privileges.filterNot(p => old.privileges.contains(p))) ++
            obj.toSql("REVOKE", "FROM", old.privileges.filterNot(p => obj.privileges.contains(p)))
