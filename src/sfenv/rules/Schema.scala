package sfenv
package rules

import scala.collection.immutable.SortedMap

import fabric.rw.*
import envr.ObjMeta

case class Schema(
    transient: Boolean = false,
    managed: Boolean = false,
    acc_roles: AccRoles = SortedMap.empty,
    tags: Tags = SortedMap.empty,
    comment: Option[SqlLiteral] = None,
    props: Props = Props.empty,
):
  def asEnvr(dbName: String, schName: String)(using resolver: NameResolver): envr.Schema =
    envr.Schema(
      (resolver.db(dbName), resolver.sch(dbName, schName)),
      transient = transient,
      managed = managed,
      meta = ObjMeta(props, tags, comment),
      accRoles = acc_roles.resolve(dbName, schName)
    )

object Schema:
  given RW[Schema] = propsRW
