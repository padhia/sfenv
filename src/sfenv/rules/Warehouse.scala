package sfenv
package rules

import io.circe.*

import scala.collection.immutable.ListMap

import envr.ObjMeta

case class Warehouse(x: Warehouse.Aux, props: Props):
  export x.*

object Warehouse:
  case class Aux(acc_roles: Option[AccRoles], tags: Option[Tags], comment: Option[SqlLiteral]) derives Decoder

  given Decoder[Warehouse] with
    def apply(c: HCursor) = summon[Decoder[Aux]](c).map(Warehouse(_, Util.fromCursor[Aux](c)))

  given ObjMap[Warehouse]:
    type Key   = Ident
    type Value = envr.Warehouse.Value

    extension (r: Warehouse)
      def keyVal(k: String)(using n: NameResolver) =
        (
          n.wh(k),
          envr.Warehouse.Value(
            meta = ObjMeta(r.props, r.tags, r.comment),
            accRoleMap = r.acc_roles.map(_.resolve(k)).getOrElse(ListMap.empty)
          )
        )
