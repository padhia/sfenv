package sfenv
package rules

import scala.collection.immutable.{SortedMap, SortedSet}

import fabric.rw.*
import envr.ObjMeta

case class Database(
    name: String,
    schemas: SortedMap[String, Schema],
    transient: Boolean = false,
    tags: Tags = SortedMap.empty,
    comment: Option[SqlLiteral] = None,
    props: Props,
):
  def asEnvr(using resolver: NameResolver): envr.Database =
    envr.Database(
      resolver.db(name),
      transient = transient,
      meta = ObjMeta(props, tags, comment),
      schemas = SortedSet.from(schemas.toList.map((schemaName, schema) => schema.asEnvr(name, schemaName)))
    )

object Database:
  given Ordering[Database] = Ordering.by(_.name)

  given RW[Database] = propsRW
