package sfenv
package rules

import io.circe.*

import envr.{ObjMeta, Props, PropVal}

case class ComputePool(x: ComputePool.Aux, props: Props):
  export x.*

  def resolve(cpName: String)(using n: NameResolver) =
    val defaults = Map(
      "MIN_NODES"       -> PropVal.Num(x.minNodes_),
      "MAX_NODES"       -> PropVal.Num(x.maxNodes_),
      "INSTANCE_FAMILY" -> PropVal.Str(x.instanceFamily_)
    )
    envr.ComputePool(name = n.cp(cpName), meta = ObjMeta(defaults ++ props, tags, comment))

object ComputePool:
  case class Aux(minNodes: Option[Int], maxNodes: Option[Int], instanceFamily: Option[String], tags: Tags, comment: Comment)
      derives Decoder:
    def minNodes_       = minNodes.getOrElse(1)
    def maxNodes_       = maxNodes.getOrElse(minNodes_)
    def instanceFamily_ = instanceFamily.getOrElse("CPU_X64_XS")

  given Decoder[ComputePool] with
    def apply(c: HCursor) = summon[Decoder[Aux]](c).map(ComputePool(_, Util.fromCursor[Aux](c)))
