package sfenv
package rules

import scala.collection.immutable.SortedMap

import fabric.rw.*
import envr.ObjMeta

case class ComputePool(
    name: String,
    min_nodes: Int = 1,
    max_nodes: Option[Int] = None,
    instance_family: String = "CPU_X64_XS",
    tags: Tags = SortedMap.empty,
    comment: Option[SqlLiteral] = None,
    props: Props,
):
  def objMeta =
    val properties = Props(
      "MIN_NODES"       -> min_nodes,
      "MAX_NODES"       -> max_nodes.getOrElse(min_nodes),
      "INSTANCE_FAMILY" -> instance_family
    )
    ObjMeta(properties, tags, comment)

  def asEnvr(using resolver: NameResolver): envr.ComputePool =
    envr.ComputePool(resolver.cp(name), objMeta)

object ComputePool:
  given Ordering[ComputePool] = Ordering.by(_.name)

  given RW[ComputePool] = propsRW
