package sfenv
package envr

import cats.data.Chain

type Props = Map[String, PropVal]

object Props:
  def empty = Map.empty[String, PropVal]

  extension (xs: Props)
    def propsToStrSeq = Chain.fromSeq(xs.toSeq).map((k, v) => s"${k.toUpperCase()} = $v")
    def toChain       = Chain.fromSeq(xs.toSeq).map((k, v) => s"${k.toUpperCase()} = $v")
    def diff(ys: Props): (Chain[String], Chain[String]) =
      val set   = (xs.filterNot((k, v) => ys.get(k) == Some(v))).propsToStrSeq
      val unset = Chain.fromSeq((ys -- xs.keys).keys.toSeq)
      (set, unset)

