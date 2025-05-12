package sfenv

import fs2.*

import cats.data.Chain

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
  ): Chain[String] = this match
    case CreateObj(k, n, o) => Chain(s"CREATE $k IF NOT EXISTS $n$o")
    case AlterObj(k, n, o)  => Chain(s"ALTER $k IF EXISTS $n$o")
    case AlterRole(r, m)    => Chain(s"ALTER ${r.kind} IF NOT EXIST ${r.roleName}$m")
    case DropRole(r)        => Chain(s"${drops.sqlPfx(false)}DROP ${r.kind} IF EXISTS ${r.roleName}")
    case DropObj(k, n, s)   => Chain(s"${drops.sqlPfx(k == "SCHEMA" || (k == "DATABASE" && !s))}DROP $k IF EXISTS $n")

    case CreateRole(r, m) =>
      Chain(s"CREATE ${r.kind} IF NOT EXISTS ${r.roleName}$m", s"GRANT ${r.role} TO ${sysAdm.role}")

    case RoleGrant(n, g, r) =>
      val grantee = g match
        case x: RoleName => x.role
        case x: UserName => s"USER $x"
      Chain(if r then s"REVOKE ${n.role} FROM $grantee" else s"GRANT ${n.role} TO $grantee")

    case ObjGrant(t, n, g, p, r) =>
      if p.isEmpty then Chain.empty
      else
        val (grant, to)  = if r then ("REVOKE", "FROM") else ("GRANT", "TO")
        val ts = if t.endsWith("Y") then t.stripSuffix("Y") + "IES" else t + "S"

        t match
          case "DATABASE" | "SCHEMA" | "WAREHOUSE" => Chain(s"$grant ${p.mkString(", ")} ON $t $n $to ${g.role}")
          case _ =>
            def objGrant(scope: String) = s"$grant ${p.mkString(", ")} ON $scope $ts IN SCHEMA $n $to ${g.role}"
            Chain(objGrant("FUTURE")) ++ (if onlyFuture then Chain.empty else Chain(objGrant("ALL")))

object Sql:
  /** Inject appropriate USE ROLE ... statements before DDL and DCL statements
    *   - Any role creation or granting roles shall be done using security admin ID
    *   - Any object creation or granting access to object shall be done using sysadmin ID
    *   - if more than one consecutive statements use the same role, remove redundant USE ROLE stateents
    *
    * @return
    *   Chain of tuple containing Optional Admin-role and the original Sql
    */
  def usingRole[F[_]]: Pipe[F, Sql, (Option[SqlAdmin], Sql)] = xs =>
    def setRole(p: Option[Sql], c: Sql) =
      val prevRole = p.map(_.useRole)
      val currRole = Some(c.useRole)
      (if currRole == prevRole then None else currRole, c)

    xs.zipWithPrevious.map(setRole)
