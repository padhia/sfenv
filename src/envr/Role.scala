package sfenv
package envr

import cats.data.Chain

case class Role(name: RoleName, accRoles: List[RoleName], meta: ObjMeta, createObj: Boolean):
  def rolePairs = accRoles.map((_, name))

object Role:
  extension (r: (RoleName, RoleName))
    def grant  = SqlStmt(Admin.Sec, s"GRANT ${r._1} TO ${r._2}")
    def revoke = SqlStmt(Admin.Sec, s"REVOKE ${r._1} FROM ${r._2}")

  given SfObj[Role] with
    extension (obj: Role) def id: SfObjId = SfObjId(obj.name.roleName)
    def genSql(obj: SqlOp[Role]): Chain[SqlStmt] = obj match
      case SqlOp.Create(r) =>
        SqlStmt(Admin.Sec, s"CREATE ${r.name}${r.meta}") +: Chain.fromSeq(r.rolePairs).map(_.grant)

      case SqlOp.Drop(r) =>
        Chain.fromSeq(r.rolePairs).map(_.revoke) :+ SqlStmt(Admin.Sec, s"DROP ${r.name}")

      case SqlOp.Alter(newRole, oldRole) =>
        Chain.fromSeq(newRole.rolePairs.filterNot(x => oldRole.rolePairs.exists(_ == x)).map(_.grant))
          ++ Chain.fromSeq(oldRole.rolePairs.filterNot(x => newRole.rolePairs.exists(_ == x)).map(_.revoke))

  given SqlObj[Role] with
    override type Key = RoleName

    extension (role: Role)
      override def id = role.name

      override def create: Chain[Sql] =
        import role.*
        val acc = Chain.fromSeq(accRoles).map(ar => Sql.RoleGrant(ar, name))
        if createObj then Sql.CreateRole(name, meta) +: acc else acc

      override def unCreate =
        import role.*
        val acc = Chain.fromSeq(accRoles).map(ar => Sql.RoleGrant(ar, name, true))
        if createObj then acc :+ Sql.DropRole(name) else acc

      override def alter(old: Role): Chain[Sql] =
        Chain.fromSeq(role.accRoles).regrant(Chain.fromSeq(old.accRoles), role.name) ++
          (if role.createObj then role.meta.alter(role.name.kind, role.name.roleName, old.meta) else Chain.empty)
