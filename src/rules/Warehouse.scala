package sfenv
package rules

import io.circe.*

import envr.{ObjMeta, Props}

case class Warehouse(x: Warehouse.Aux, props: Props):
  export x.*
  def resolve(whName: String)(using n: NameResolver) =
    envr.Warehouse(
      name = n.wh(whName),
      meta = ObjMeta(props, tags, comment),
      accRoles = acc_roles.map(_.resolve(whName)).getOrElse(Map.empty),
    )

object Warehouse:
  case class Aux(acc_roles: Option[AccRoles], tags: Tags, comment: Comment) derives Decoder

  given Decoder[Warehouse] with
    def apply(c: HCursor) = summon[Decoder[Aux]](c).map(Warehouse(_, Util.fromCursor[Aux](c)))
