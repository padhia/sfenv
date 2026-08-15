package sfenv
package rules

import scala.collection.immutable.SortedMap

import fabric.rw.*
import envr.ObjMeta

case class ComputePool(
    minNodes: Option[Int],
    maxNodes: Option[Int],
    instanceFamily: Option[String],
    tags: Option[Tags],
    comment: Option[SqlLiteral],
    props: Props,
):
  def minNodes_       = minNodes.getOrElse(1)
  def maxNodes_       = maxNodes.getOrElse(minNodes_)
  def instanceFamily_ = instanceFamily.getOrElse("CPU_X64_XS")
  def objMeta         =
    val p = SortedMap(
      "MIN_NODES"       -> PropVal(minNodes_),
      "MAX_NODES"       -> PropVal(maxNodes_),
      "INSTANCE_FAMILY" -> PropVal(instanceFamily_)
    ).map((k, v) => (Ident(k), v))
    ObjMeta(p, tags, comment)

object ComputePool:
  given RW[ComputePool] = propsRW

  given ObjMap[ComputePool]:
    type Key   = Ident
    type Value = ObjMeta

    extension (r: ComputePool)
      def keyVal(k: String)(using n: NameResolver) =
        (n.cp(k), r.objMeta)
