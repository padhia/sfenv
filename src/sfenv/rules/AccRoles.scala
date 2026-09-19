package sfenv
package rules

import scala.collection.immutable.{SortedMap, SortedSet}

import envr.{AccRole, RoleName}

type AccGroup   = UString
type AccObjType = UString
type AccPrivs   = List[UString]
type AccRoles   = SortedMap[AccGroup, SortedMap[AccObjType, AccPrivs]]

extension (ar: AccRoles)
  def resolve(mkRole: UString => RoleName): SortedSet[AccRole] =
    def resolvePriv(name: UString, ops: SortedMap[AccObjType, AccPrivs]): AccRole =
      val privileges = ops.map((kind, values) => (Ident(kind.value), values))
      AccRole(
        mkRole(name),
        privileges.get(Ident("ROLE")).map(roles => SortedSet.from(roles.map(mkRole))).getOrElse(SortedSet.empty),
        privileges.filter(_._1 != Ident("ROLE")).map((kind, values) => (kind, SortedSet.from(values)))
      )

    SortedSet.from(ar.toList.map((name, privileges) => resolvePriv(name, privileges)))

  def resolve(db: String, sch: String)(using n: NameResolver): SortedSet[AccRole] =
    resolve(x => RoleName.Access(n.db(db), n.sch(db, sch), n.acc(db, sch, x.value)))

  def resolve(wh: String)(using n: NameResolver): SortedSet[AccRole] =
    resolve(x => RoleName.Account(n.wacc(wh, x.value)))
