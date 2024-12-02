package sfenv

import fs2.*

import envr.{RoleName, UserName}

enum SqlAdmin:
  case Sec, Sys

  def toSql(secAdm: RoleName, sysAdm: RoleName): String =
    this match
      case Sec => s"USE ${secAdm.role}"
      case Sys => s"USE ${sysAdm.role}"

enum Sql:
  case CreateObj(kind: String, name: String, other: String)
  case AlterObj(kind: String, name: String, other: String)
  case DropObj(kind: String, name: String, isObjShr: Boolean = false)
  case CreateRole(role: RoleName, meta: envr.ObjMeta)
  case AlterRole(role: RoleName, meta: envr.ObjMeta)
  case DropRole(role: RoleName)
  case RoleGrant(name: RoleName, grantee: RoleName | UserName, revoke: Boolean = false)
  case ObjGrant(objType: String, objName: String, grantee: RoleName, privileges: List[String], revoke: Boolean = false)

  def useRole: SqlAdmin =
    this match
      case CreateObj(k, _, _) if k == "USER"                         => SqlAdmin.Sec
      case AlterObj(k, _, _) if k == "USER"                          => SqlAdmin.Sec
      case DropObj(k, _, _) if k == "USER"                           => SqlAdmin.Sec
      case _: CreateRole | _: DropRole | _: AlterRole | _: RoleGrant => SqlAdmin.Sec
      case _: CreateObj | _: DropObj | _: AlterObj | _: ObjGrant     => SqlAdmin.Sys

  /** Convert Sql to one or more DDL texts
    *
    * @param onlyFuture
    *   generate only FUTURE for GRANT statements (no ALL)
    * @return
    *   Stream of DDL Texts encoded as Option[String]; None encode empty lines for formatting
    */
  def texts(
      sysAdm: RoleName,
      onlyFuture: Boolean = false,
      drops: ProcessDrops = ProcessDrops.NonLocal
  ): List[String] = this match
    case CreateObj(k, n, o) => List(s"CREATE $k IF NOT EXISTS $n$o")
    case AlterObj(k, n, o)  => List(s"ALTER $k IF EXISTS $n$o")
    case AlterRole(r, m)    => List(s"ALTER ${r.kind} IF NOT EXIST ${r.roleName}$m")
    case DropRole(r)        => List(s"${drops.sqlPfx(false)}DROP ${r.kind} IF EXISTS ${r.roleName}")
    case DropObj(k, n, s)   => List(s"${drops.sqlPfx(k == "SCHEMA" || (k == "DATABASE" && !s))}DROP $k IF EXISTS $n")

    case CreateRole(r, m) =>
      List(s"CREATE ${r.kind} IF NOT EXISTS ${r.roleName}$m", s"GRANT ${r.role} TO ${sysAdm.role}")

    case RoleGrant(n, g, r) =>
      val grantee = g match
        case x: RoleName => x.role
        case x: UserName => s"USER $x"
      List(if r then s"REVOKE ${n.role} FROM $grantee" else s"GRANT ${n.role} TO $grantee")

    case ObjGrant(t, n, g, p, r) =>
      if p.isEmpty then List.empty
      else
        val grant = if r then "REVOKE" else "GRANT"
        val to    = if r then "FROM" else "TO"

        t match
          case "DATABASE" | "SCHEMA" | "WAREHOUSE" => List(s"$grant ${p.mkString(", ")} ON $t $n $to ${g.role}")
          case _ =>
            def objGrant(scope: String) = s"$grant ${p.mkString(", ")} ON $scope ${t}S IN SCHEMA $n $to ${g.role}"
            List(objGrant("FUTURE")) ++ (if onlyFuture then List.empty else List(objGrant("ALL")))

object Sql:
  /** Inject appropriate USE ROLE ... statements before DDL and DCL statements
    *   - Any role creation or granting roles shall be done using security admin ID
    *   - Any object creation or granting access to object shall be done using sysadmin ID
    *   - if more than one consecutive statements use the same role, remove redundant USE ROLE stateents
    *
    * @return
    *   Pipe that returns original Stream of Sql statements with USE ROLE inserted
    */
  def usingRole[F[_]]: Pipe[F, Sql, (Option[SqlAdmin], Sql)] = s =>
    /** set current role if it is different from previous role
      *
      * @param prev
      *   role for the previous statement
      * @param curr
      *   role for the current statement
      * @return
      *   None if curr role is same as prev, otherwise curr role
      */
    def setRole(prev: Option[SqlAdmin], curr: SqlAdmin) =
      prev match
        case None       => Some(curr)
        case Some(prev) => if prev == curr then None else Some(curr)

    s
      .map(x => (x.useRole, x))                          // determine the rule to use
      .zipWithPrevious                                   // pair it with the previous role
      .map((p, c) => (setRole(p.map(_._1), c._1), c._2)) // change the Role if it is same as the previous one
