package sfenv
package envr

import cats.data.Chain

case class ObjGrant(objType: String, objName: String, privileges: List[String]):
  def grant(grantee: RoleName): Chain[SqlStmt]  = toSql("GRANT", "TO", grantee)
  def revoke(grantee: RoleName): Chain[SqlStmt] = toSql("REVOKE", "FROM", grantee)

  private def toSql(grant: String, to: String, grantee: RoleName): Chain[SqlStmt] = toSql(grant, to, grantee, privileges)

  private def toSql(grant: String, to: String, grantee: RoleName, privileges: List[String]): Chain[SqlStmt] =
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
  def make(grantee: RoleName) =
    new CDA[ObjGrant]:
      extension (obj: ObjGrant)
        override def create: Chain[SqlStmt]            = obj.grant(grantee)
        override def drop: Chain[SqlStmt]              = obj.revoke(grantee)
        override def sameId(other: ObjGrant): Boolean  = obj.objType == other.objType && obj.objName == other.objName
        override def updatable(old: ObjGrant): Boolean = true

        override def update(old: ObjGrant): Chain[SqlStmt] =
          if obj.privileges == old.privileges then Chain.empty
          else
            obj.toSql("GRANT", "TO", grantee, obj.privileges -- old.privileges) ++
              obj.toSql("REVOKE", "FROM", grantee, old.privileges -- obj.privileges)
