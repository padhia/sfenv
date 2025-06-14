package sfenv
package rules

import io.circe.*

import envr.{ObjMeta, Props}

case class Schema(x: Schema.Aux, props: Props):
  export x.*
  def resolve(dbName: String, schName: String)(using n: NameResolver) =
    envr.Schema(
      db = n.db(dbName),
      name = n.sch(dbName, schName),
      transient = transient.getOrElse(false),
      managed = managed.getOrElse(false),
      meta = ObjMeta(props, tags, comment),
      accRoles = acc_roles.map(_.resolve(dbName, schName)).getOrElse(Map.empty)
    )

object Schema:
  case class Aux(
      transient: Option[Boolean],
      managed: Option[Boolean],
      acc_roles: Option[AccRoles],
      tags: Tags,
      comment: Comment
  ) derives Decoder

  given Decoder[Schema] with
    def apply(c: HCursor) = summon[Decoder[Aux]].apply(c).map(Schema(_, Util.fromCursor[Aux](c)))
