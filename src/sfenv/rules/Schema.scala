package sfenv
package rules

import scala.collection.immutable.SortedMap

import fabric.rw.*
import envr.ObjMeta

case class Schema(
    transient: Option[Boolean],
    managed: Option[Boolean],
    acc_roles: Option[AccRoles],
    tags: Option[Tags],
    comment: Option[SqlLiteral],
    props: Props,
):
  def resolve(dbName: String, schName: String)(using n: NameResolver) =
    (
      n.sch(dbName, schName),
      envr.Schema.Value(
        transient = transient.getOrElse(false),
        managed = managed.getOrElse(false),
        meta = ObjMeta(props, tags, comment),
        accRoleMap = acc_roles.map(_.resolve(dbName, schName)).getOrElse(SortedMap.empty)
      )
    )

object Schema:
  given RW[Schema] = propsRW
