package sfenv
package envr

import cats.data.Chain
import cats.syntax.all.*

import collection.immutable.{SortedMap, SortedSet}

case class AccRole(name: RoleName, value: AccRole.Value):
  export value.*

object AccRole:
  case class Value(roles: SortedSet[RoleName], privs: SortedMap[Ident, SortedSet[UString]])

  def apply(name: String, privs: SortedMap[String, List[String]]): Either[String, (RoleName, AccRole.Value)] =
    for
      n  <- RoleName(name)
      rs <- privs.get("role").getOrElse(List.empty).traverse(RoleName.apply).map(SortedSet.from)
      ps = privs.filter(_._1 != "role").map((k, v) => (Ident(k), SortedSet.from(v.map(UString.apply))))
    yield (n, Value(rs, ps))

  def apply(
      accRoles: SortedMap[String, SortedMap[String, List[String]]],
      pfx: String
  ): Either[String, SortedMap[RoleName, AccRole.Value]] =
    def attachDbSch(k: String, v: List[String]) = (k, if k == "role" then v.map(x => show"${pfx}_$x") else v)
    accRoles.toList.traverse((k, v) => apply(show"${pfx}_$k", v.map(attachDbSch))).map(SortedMap.from(_))

  def cda(grantOn: SchWh) =
    new CDA[AccRole]:
      extension (ar: AccRole)
        private def permit(objType: Ident, privileges: SortedSet[UString]): Chain[Permit[String]] =
          if privileges.isEmpty then Chain.empty
          else
            val privs = privileges.map(_.show).mkString(", ")

            ar.name match
              case RoleName.Access(db, _, _) if objType == Ident("DATABASE") =>
                Chain(Permit(show"$privs ON DATABASE $db", ar.name))
              case RoleName.Access(_, _, _) if objType != Ident("SCHEMA") =>
                Chain("FUTURE", "ALL").map(x =>
                  Permit(
                    show"$privs ON $x ${objType.show.plural} IN SCHEMA $grantOn",
                    Grantee.Role(ar.name),
                    Admin.Sys,
                    x == "ALL"
                  )
                )
              case _ => Chain(Permit(show"$privs ON ${objType} $grantOn", ar.name))

        private def permit(roles: SortedSet[RoleName]): Chain[Permit[String]] =
          Chain.fromSeq(roles.toSeq).map(r => Permit(r.show, ar.name, grantor = Admin.Sec))

        private def permit(privs: SortedMap[Ident, SortedSet[UString]]): Chain[Permit[String]] =
          Chain.fromSeq(privs.toSeq).flatMap(ar.permit(_, _))

        def permit: Chain[Permit[String]] =
          Chain(Permit(ar.name.show, Grantee.SysAdm, grantor = Admin.Sec))
            ++ permit(ar.roles)
            ++ permit(ar.privs)

        override def sameId(other: AccRole): Boolean  = ar.name == other.name
        override def updatable(old: AccRole): Boolean = true

        override def create: Chain[SqlStmt]               = ar.name.create +: ar.permit.map(_.grant)
        override def drop: Chain[SqlStmt]                 = ar.permit.reverse.map(_.revoke) :+ ar.name.drop
        override def update(old: AccRole): Chain[SqlStmt] =
          val privs = ar.privs.map((k, v) => (k, (v, old.privs.getOrElse(k, SortedSet.empty[UString]))))
            ++ (old.privs -- ar.privs.keys).map((k, v) => (k, (SortedSet.empty[UString], v)))

          ar.permit(privs.map((k, v) => (k, v._2 -- v._1))).map(_.revoke)
            ++ ar.permit(old.roles -- ar.roles).map(_.revoke)
            ++ ar.permit(ar.roles -- old.roles).map(_.grant)
            ++ ar.permit(privs.map((k, v) => (k, v._1 -- v._2))).map(_.grant)
