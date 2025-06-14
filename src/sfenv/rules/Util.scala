package sfenv
package rules

import io.circe.*

import scala.collection.immutable.ListMap
import scala.compiletime.constValueTuple
import scala.deriving.Mirror

object Util:
  def toProp(x: Json): Option[PropVal] =
    x.asBoolean
      .map(PropVal.apply)
      .orElse(x.asString.map(PropVal.apply))
      .orElse(x.asNumber.flatMap(_.toBigDecimal.map(PropVal.apply)))

  def fromJsonObject(x: JsonObject): Props =
    ListMap.from(x.toList.map((k, v) => (Ident(k), toProp(v))).collect { case (k, Some(v)) => (k, v) })

  def fromCursor(c: HCursor, excl: List[String]): Props =
    c.value.asObject
      .map(_.filterKeys(k => !excl.contains(k)))
      .map(fromJsonObject)
      .getOrElse(Props.empty)

  inline def fromCursor[A <: Product](c: HCursor)(using m: Mirror.ProductOf[A]): Props =
    fromCursor(c, constValueTuple[m.MirroredElemLabels].toList.asInstanceOf[List[String]])
