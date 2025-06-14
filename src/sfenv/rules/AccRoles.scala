package sfenv
package rules

import scala.collection.immutable.ListMap

import collection.immutable.ListSet
import envr.{AccRole, RoleName}

type AccRoles = ListMap[String, ListMap[String, List[String]]]

extension (ar: AccRoles)
  def resolve(mkRole: String => RoleName): ListMap[RoleName, AccRole.Value] =
    def resolvePriv(name: String, ops: ListMap[String, List[String]]): (RoleName, AccRole.Value) =
      val _ops = ops.map((k, v) => (Ident(k), v.map(_.toUpperCase())))

      (
        mkRole(name),
        AccRole.Value(
          _ops.get(Ident("ROLE")).map(xs => ListSet.from(xs.map(mkRole))).getOrElse(ListSet.empty),
          _ops.filter(_._1 != Ident("ROLE")).map((t, ps) => (t, ListSet.from(ps.map(UString.apply))))
        )
      )

    ar.map((k, v) => resolvePriv(k, v))

  def resolve(db: String, sch: String)(using n: NameResolver): ListMap[RoleName, AccRole.Value] =
    resolve(x => RoleName.Database(n.db(db), n.acc(db, sch, x)))

  def resolve(wh: String)(using n: NameResolver): ListMap[RoleName, AccRole.Value] =
    resolve(x => RoleName.Account(n.wacc(wh, x)))
