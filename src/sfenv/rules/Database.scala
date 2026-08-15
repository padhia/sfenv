package sfenv
package rules

import scala.collection.immutable.SortedMap

import fabric.rw.*
import envr.ObjMeta

case class Database(
    transient: Option[Boolean],
    schemas: SortedMap[String, Schema],
    tags: Option[Tags],
    comment: Option[SqlLiteral],
    props: Props,
)

object Database:
  given RW[Database] = propsRW

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
