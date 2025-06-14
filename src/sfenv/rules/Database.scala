package sfenv
package rules

import io.circe.*

import scala.collection.immutable.ListMap

import envr.ObjMeta

case class Database(x: Database.Aux, props: Props):
  export x.*

object Database:
  case class Aux(
      transient: Option[Boolean],
      schemas: ListMap[String, Schema],
      tags: Option[Tags],
      comment: Option[SqlLiteral]
  ) derives Decoder

  given Decoder[Database] with
    def apply(c: HCursor) = summon[Decoder[Aux]](c).map(Database(_, Util.fromCursor[Aux](c)))

  given ObjMap[Database]:
    type Key   = Ident
    type Value = envr.Database.Value

    extension (r: Database)
      def keyVal(k: String)(using n: NameResolver) =
        (
          n.db(k),
          envr.Database.Value(
            transient = r.transient.getOrElse(false),
            meta = ObjMeta(r.props, r.tags, r.comment),
            schemas = r.schemas.map((schName, x) => x.resolve(k, schName))
          )
        )
