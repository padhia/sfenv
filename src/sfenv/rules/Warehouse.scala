package sfenv
package rules

import scala.collection.immutable.SortedSet

import fabric.rw.*
import envr.ObjMeta

case class Warehouse(
    name: String,
    acc_roles: Option[AccRoles],
    tags: Option[Tags],
    comment: Option[SqlLiteral],
    props: Props,
):
  def asEnvr(using resolver: NameResolver): envr.Warehouse =
    envr.Warehouse(
      resolver.wh(name),
      meta = ObjMeta(props, tags, comment),
      accRoles = acc_roles.map(_.resolve(name)).getOrElse(SortedSet.empty)
    )

object Warehouse:
  given Ordering[Warehouse] = Ordering.by(_.name)

  given RW[Warehouse] = propsRW
