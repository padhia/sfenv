package sfenv
package rules

import fabric.*
import fabric.define.DefType
import fabric.rw.*

import scala.collection.immutable.ListMap

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
  def objMeta =
    val p = ListMap(
      "MIN_NODES"       -> PropVal(minNodes_),
      "MAX_NODES"       -> PropVal(maxNodes_),
      "INSTANCE_FAMILY" -> PropVal(instanceFamily_)
    ).map((k, v) => (Ident(k), v))
    ObjMeta(p, tags, comment)

object ComputePool:
  given RW[ComputePool] = RW.from(
    r = _ => throw UnsupportedOperationException("ComputePool serialization not supported"),
    w = json => ComputePool(
      minNodes       = json.attr("minNodes").as[Option[Int]],
      maxNodes       = json.attr("maxNodes").as[Option[Int]],
      instanceFamily = json.attr("instanceFamily").as[Option[String]],
      tags           = json.attr("tags").as[Option[Tags]],
      comment        = json.attr("comment").as[Option[SqlLiteral]],
      props          = json.props[ComputePool],
    ),
    d = DefType.Json
  )

  given ObjMap[ComputePool]:
    type Key   = Ident
    type Value = ObjMeta

    extension (r: ComputePool)
      def keyVal(k: String)(using n: NameResolver) =
        (n.cp(k), r.objMeta)
