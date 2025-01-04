package sfenv
package envr

import cats.data.Chain

type Props = Map[String, PropVal]

object Props:
  def empty = Map.empty[String, PropVal]

  extension (xs: Props)
    def propsToStrSeq = Chain.fromSeq(xs.toSeq).map((k, v) => s"${k.toUpperCase()} = $v")
    def toChain       = Chain.fromSeq(xs.toSeq).map((k, v) => s"${k.toUpperCase()} = $v")
