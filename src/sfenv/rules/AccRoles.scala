package sfenv
package rules

import scala.collection.immutable.SortedMap

import collection.immutable.SortedSet
import envr.{AccRole, RoleName}

type AccGroup   = UString
type AccObjType = UString
type AccPrivs   = List[UString]
type AccRoles   = SortedMap[AccGroup, SortedMap[AccObjType, AccPrivs]]

extension (ar: AccRoles)
  def resolve(mkRole: UString => RoleName): SortedMap[RoleName, AccRole.Value] =
    def resolvePriv(name: UString, ops: SortedMap[AccObjType, AccPrivs]): (RoleName, AccRole.Value) =
      val _ops = ops.map((k, v) => (Ident(k.value), v))

      (
        mkRole(name),
        AccRole.Value(
          _ops.get(Ident("ROLE")).map(xs => SortedSet.from(xs.map(x => mkRole(x)))).getOrElse(SortedSet.empty),
          _ops.filter(_._1 != Ident("ROLE")).map((t, ps) => (t, SortedSet.from(ps)))
        )
      )

    ar.map((k, v) => resolvePriv(k, v))

  def resolve(db: String, sch: String)(using n: NameResolver): SortedMap[RoleName, AccRole.Value] =
    resolve(x => RoleName.Database(n.db(db), n.acc(db, sch, x.value)))

  def resolve(wh: String)(using n: NameResolver): SortedMap[RoleName, AccRole.Value] =
    resolve(x => RoleName.Account(n.wacc(wh, x.value)))
