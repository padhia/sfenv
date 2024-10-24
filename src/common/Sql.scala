package sfenv

import fs2.*

import envr.{RoleName, UserName}

enum Sql:
  case Use(role: RoleName)
  case CreateObj(kind: String, name: String, other: String)
  case AlterObj(kind: String, name: String, other: String)
  case DropObj(kind: String, name: String, isObjShr: Boolean = false)
  case CreateRole(role: RoleName, meta: envr.ObjMeta)
  case AlterRole(role: RoleName, meta: envr.ObjMeta)
  case DropRole(role: RoleName)
  case RoleGrant(name: RoleName, grantee: RoleName | UserName, revoke: Boolean = false)
  case ObjGrant(objType: String, objName: String, grantee: RoleName, privileges: List[String], revoke: Boolean = false)

  def useRole(secAdm: RoleName, sysAdm: RoleName): Option[Sql.Use] =
    this match
      case _: CreateRole | _: DropRole | _: AlterRole | _: RoleGrant => Some(Use(secAdm))
      case _: CreateObj | _: DropObj | _: AlterObj | _: ObjGrant     => Some(Use(sysAdm))
      case _: Use                                                    => None

  /** Convert Sql to one or more DDL texts
    *
    * @param onlyFuture
    *   generate only FUTURE for GRANT statements (no ALL)
    * @return
    *   Stream of DDL Texts encoded as Option[String]; None encode empty lines for formatting
    */
  def stream[F[_]](
      sysAdm: RoleName,
      onlyFuture: Boolean = false,
      drops: ProcessDrops = ProcessDrops.NonLocal
  ): Stream[F, String] = this match
    case Use(role)          => Stream.emit(s"USE ${role.role}")
    case CreateObj(k, n, o) => Stream.emit(s"CREATE $k IF NOT EXISTS $n$o")
    case AlterObj(k, n, o)  => Stream.emit(s"ALTER $k IF EXISTS $n$o")
    case AlterRole(r, m)    => Stream.emit(s"ALTER ${r.kind} IF NOT EXIST ${r.roleName}$m")
    case DropRole(r)        => Stream.emit(s"${drops.sqlPfx(false)}DROP ${r.kind} IF EXISTS ${r.roleName}")
    case DropObj(k, n, s)   => Stream.emit(s"${drops.sqlPfx(k == "SCHEMA" || (k == "DATABASE" && !s))}DROP $k IF EXISTS $n")

    case CreateRole(r, m) =>
      Stream.emit(s"CREATE ${r.kind} IF NOT EXISTS ${r.roleName}$m") ++
        Stream.emit(s"GRANT ${r.role} TO ${sysAdm.role}")

    case RoleGrant(n, g, r) =>
      val grantee = g match
        case x: RoleName => x.role
        case x: UserName => s"USER $x"
      Stream.emit(if r then s"REVOKE ${n.role} FROM $grantee" else s"GRANT ${n.role} TO $grantee")

    case ObjGrant(t, n, g, p, r) =>
      if p.isEmpty then Stream.empty
      else
        val grant = if r then "REVOKE" else "GRANT"
        val to    = if r then "FROM" else "TO"

        t match
          case "DATABASE" | "SCHEMA" | "WAREHOUSE" => Stream.emit(s"$grant ${p.mkString(", ")} ON $t $n $to ${g.role}")
          case _ =>
            def objGrant(scope: String) = s"$grant ${p.mkString(", ")} ON $scope ${t}S IN SCHEMA $n $to ${g.role}"
            Stream.emit(objGrant("FUTURE")) ++ (if onlyFuture then Stream.empty else Stream.emit(objGrant("ALL")))

object Sql:
  /** Inject appropriate USE ROLE ... statements before DDL and DCL statements
    *   - Any role creation or granting roles shall be done using security admin ID
    *   - Any object creation or granting access to object shall be done using sysadmin ID
    *   - if more than one consecutive statements use the same role, remove redundant USE ROLE stateents
    *
    * @return
    *   Pipe that returns original Stream of Sql statements with USE ROLE inserted
    */
  def usingRole[F[_]](secAdm: RoleName, sysAdm: RoleName): Pipe[F, Sql, Sql] = s =>
    /** set current role if it is different from previous role
      *
      * @param prev
      *   role for the previous statement
      * @param curr
      *   role for the current statement
      * @return
      *   None if curr role is same as prev, otherwise curr role
      */
    def setRole(prev: Option[Option[Sql]], curr: Option[Sql]) =
      prev match
        case None       => curr
        case Some(prev) => if prev == curr then None else curr

    val roles =
      s
        .map(_.useRole(secAdm, sysAdm)) // determine the rule to use
        .zipWithPrevious                // pair it with the previous role
        .map(setRole(_, _))             // change the Role if it is same as the previous one

    roles.interleave(s.map(Some(_))).unNone // Remove statements that are NOOP for changing role
