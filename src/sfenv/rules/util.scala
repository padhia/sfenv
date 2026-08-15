package sfenv
package rules

import scala.collection.immutable.SortedMap
import scala.compiletime.{constValue, constValueTuple, erasedValue, summonInline}
import scala.deriving.Mirror

import fabric.*
import fabric.define.{Definition, DefType}
import fabric.rw.{Asable, RW, RWException}

private def asProp(key: String, x: Json): (Ident, PropVal) = Ident(key) -> (x match
  case Bool(v, _)   => PropVal(v)
  case Str(v, _)    => PropVal(v)
  case NumInt(v, _) => PropVal(BigDecimal(v))
  case NumDec(v, _) => PropVal(v)
  case _            => throw RWException(s"$key is a pass-through element; quotes are required for the value"))

extension (json: Json) inline def attr(lookup: String) = json.getOrElse(lookup, Null)

// Recursively walk Labels and Types in lockstep, building a Tuple of decoded values.
// The "props" field collects all JSON keys not matched by any other field label.
private inline def readElems[Labels <: Tuple, Types <: Tuple](
    json: Json,
    allLabels: List[String]
): Tuple =
  inline erasedValue[(Labels, Types)] match
    case _: (EmptyTuple, EmptyTuple) =>
      EmptyTuple
    case _: ((label *: restLabels), (tpe *: restTypes)) =>
      (inline erasedValue[label] match
        case _: "props" =>
          (if json.isNull then Props.empty
           else
             SortedMap.from(
               json.asObj.value.iterator
                 .filterNot { case (k, _) => allLabels.filterNot(_ == "props").contains(k) }
                 .map(asProp)
             )
          ).asInstanceOf[tpe]
        case _: String =>
          json.attr(constValue[label & String]).as[tpe](using summonInline[RW[tpe]])
      ) *: readElems[restLabels, restTypes](json, allLabels)

// Produce a Json => A function for a Product type A.
// Named fields are read via their RW instances; the "props" field absorbs leftover keys.
inline def fromJson[A](using m: Mirror.ProductOf[A]): Json => A =
  json =>
    val allLabels = constValueTuple[m.MirroredElemLabels].toList.asInstanceOf[List[String]]
    m.fromProduct(readElems[m.MirroredElemLabels, m.MirroredElemTypes](json, allLabels))

// Derive a write-only RW[A] for any Product with a props: Props field.
inline def propsRW[A](using Mirror.ProductOf[A]): RW[A] =
  RW.from(
    r = _ => throw UnsupportedOperationException("Serialization not supported"),
    w = fromJson[A],
    d = Definition(DefType.Json)
  )
