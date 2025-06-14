package sfenv
package rules

import io.circe.*

import scala.collection.immutable.ListMap

import envr.ObjMeta

case class Schema(x: Schema.Aux, props: Props):
  export x.*
  def resolve(dbName: String, schName: String)(using n: NameResolver) =
    (
      n.sch(dbName, schName),
      envr.Schema.Value(
        transient = transient.getOrElse(false),
        managed = managed.getOrElse(false),
        meta = ObjMeta(props, tags, comment),
        accRoleMap = acc_roles.map(_.resolve(dbName, schName)).getOrElse(ListMap.empty)
      )
    )

object Schema:
  case class Aux(
      transient: Option[Boolean],
      managed: Option[Boolean],
      acc_roles: Option[AccRoles],
      tags: Option[Tags],
      comment: Option[SqlLiteral]
  ) derives Decoder

  given Decoder[Schema] with
    def apply(c: HCursor) = summon[Decoder[Aux]](c).map(Schema(_, Util.fromCursor[Aux](c)))
