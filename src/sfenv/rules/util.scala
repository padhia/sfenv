package sfenv
package rules

import scala.collection.immutable.SortedMap
import scala.compiletime.constValueTuple
import scala.deriving.Mirror

import fabric.*
import fabric.rw.RWException

private def asProp(key: String, x: Json): (Ident, PropVal) = Ident(key) -> (x match
  case Bool(v, _)   => PropVal(v)
  case Str(v, _)    => PropVal(v)
  case NumInt(v, _) => PropVal(BigDecimal(v))
  case NumDec(v, _) => PropVal(v)
  case _            => throw RWException(s"$key is a pass-through element; quotes are required for the value"))

extension (json: Json)
  inline def attr(lookup: String) = json.getOrElse(lookup, Null)

  inline def props[A <: Product](using m: Mirror.ProductOf[A]): Props =
    if json.isNull then Props.empty
    else
      val exclude = constValueTuple[m.MirroredElemLabels].toList.asInstanceOf[List[String]].filterNot(_ == "props")
      SortedMap.from(
        json.asObj.value.iterator
          .filterNot { case (k, _) => exclude.contains(k) }
          .map(asProp)
      )
