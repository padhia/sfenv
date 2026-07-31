package sfenv
package rules

import scala.collection.immutable.ListMap

import fabric.*
import fabric.define.DefType
import fabric.rw.*
import envr.ObjMeta

case class Database(
    transient: Option[Boolean],
    schemas: ListMap[String, Schema],
    tags: Option[Tags],
    comment: Option[SqlLiteral],
    props: Props,
)

object Database:
  given RW[Database] = RW.from(
    r = _ => throw UnsupportedOperationException("Database serialization not supported"),
    w = json =>
      Database(
        transient = json.attr("transient").as[Option[Boolean]],
        schemas = json("schemas").as[ListMap[String, Schema]],
        tags = json.attr("tags").as[Option[Tags]],
        comment = json.attr("comment").as[Option[SqlLiteral]],
        props = json.props[Database],
      ),
    d = DefType.Json
  )

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
