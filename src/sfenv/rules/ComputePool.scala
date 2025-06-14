package sfenv
package rules

import io.circe.*

import scala.collection.immutable.ListMap

import envr.ObjMeta

case class ComputePool(x: ComputePool.Aux, props: Props):
  export x.*

object ComputePool:
  case class Aux(
      minNodes: Option[Int],
      maxNodes: Option[Int],
      instanceFamily: Option[String],
      tags: Option[Tags],
      comment: Option[SqlLiteral]
  ) derives Decoder:
    def minNodes_       = minNodes.getOrElse(1)
    def maxNodes_       = maxNodes.getOrElse(minNodes_)
    def instanceFamily_ = instanceFamily.getOrElse("CPU_X64_XS")
    def objMeta         =
      val props = ListMap(
        "MIN_NODES"       -> PropVal(minNodes_),
        "MAX_NODES"       -> PropVal(maxNodes_),
        "INSTANCE_FAMILY" -> PropVal(instanceFamily_)
      ).map((k, v) => (Ident(k), v))
      ObjMeta(props, tags, comment)

  given Decoder[ComputePool] with
    def apply(c: HCursor) = summon[Decoder[Aux]](c).map(ComputePool(_, Util.fromCursor[Aux](c)))

  given ObjMap[ComputePool]:
    type Key   = Ident
    type Value = ObjMeta

    extension (r: ComputePool)
      def keyVal(k: String)(using n: NameResolver) =
        (
          n.cp(k),
          r.objMeta
        )
