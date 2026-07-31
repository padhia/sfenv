package sfenv
package rules

import fabric.*
import fabric.define.DefType
import fabric.rw.*

import scala.collection.immutable.ListMap

import envr.ObjMeta

case class Warehouse(
    acc_roles: Option[AccRoles],
    tags: Option[Tags],
    comment: Option[SqlLiteral],
    props: Props,
)

object Warehouse:
  given RW[Warehouse] = RW.from(
    r = _ => throw UnsupportedOperationException("Warehouse serialization not supported"),
    w = json => Warehouse(
      acc_roles = json.attr("acc_roles").as[Option[AccRoles]],
      tags      = json.attr("tags").as[Option[Tags]],
      comment   = json.attr("comment").as[Option[SqlLiteral]],
      props     = json.props[Warehouse],
    ),
    d = DefType.Json
  )

  given ObjMap[Warehouse]:
    type Key   = Ident
    type Value = envr.Warehouse.Value

    extension (r: Warehouse)
      def keyVal(k: String)(using n: NameResolver) =
        (
          n.wh(k),
          envr.Warehouse.Value(
            meta       = ObjMeta(r.props, r.tags, r.comment),
            accRoleMap = r.acc_roles.map(_.resolve(k)).getOrElse(ListMap.empty)
          )
        )
