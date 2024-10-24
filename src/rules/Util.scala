package sfenv
package rules

import scala.compiletime.constValueTuple
import scala.deriving.Mirror

import envr.{Props, PropVal}
import fabric.*
import fabric.rw.*

object Util:
  extension [T](x: Either[String, T])
    def value: T =
      x match
        case Right(y) => y
        case Left(y)  => throw new RuntimeException(y)

  private def toProps(x: Map[String, Json], omit: List[String]): Props =
    summon[RW[Map[String, PropVal]]].write(x.filter((k, _) => omit.forall(_ != k)))

  inline def extraElems[A <: Product](o: Json)(using m: Mirror.ProductOf[A]): Props =
    o match
      case Obj(x) => toProps(x, constValueTuple[m.MirroredElemLabels].toList.asInstanceOf[List[String]])
      case _      => throw RuntimeException(s"$o must be an Obj to parse as Props")
