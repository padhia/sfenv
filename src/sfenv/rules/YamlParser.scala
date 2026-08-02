package sfenv
package rules

import scala.collection.immutable.SortedMap
import scala.compiletime.constValueTuple
import scala.deriving.Mirror
import scala.jdk.CollectionConverters.*

import org.snakeyaml.engine.v2.api.{Load, LoadSettings}
import fabric.{Arr, Bool, Json, Null, NumDec, NumInt, Obj, Str}
import fabric.rw.RWException

object YamlParser:
  private lazy val load = Load(LoadSettings.builder().setMaxAliasesForCollections(1000).build())

  def apply(content: String): Json = toJson(load.loadFromString(content))

  private def toJson(value: Any): Json = value match
    case null                    => Null
    case v: java.lang.Boolean    => Bool(v.booleanValue)
    case v: java.lang.Integer    => NumInt(v.longValue)
    case v: java.lang.Long       => NumInt(v.longValue)
    case v: java.lang.Double     => NumDec(BigDecimal(v.doubleValue))
    case v: java.math.BigDecimal => NumDec(BigDecimal(v))
    case v: String               => Str(v)
    case v: java.util.Map[?, ?]  =>
      // Merge-key (<<) handling: separate anchor entries from explicit entries so that
      // explicit entries always win regardless of their position in the document.
      // (The sequence-order approach — anchor first, explicit last — would fail for the
      // valid but uncommon case where explicit entries precede << in the YAML.)
      val (merges, explicit) = v.asScala.toSeq.partition(_._1 == "<<")
      val anchorPairs        = merges.flatMap:
        case (_, anchor: java.util.Map[?, ?]) => anchor.asScala.toSeq.map((k, v2) => k.toString -> toJson(v2))
        case _                                => Nil
      val explicitPairs = explicit.map((k, v2) => k.toString -> toJson(v2))
      val explicitKeys  = explicitPairs.map(_._1).toSet
      Obj(SortedMap.from(anchorPairs.filterNot((k, _) => explicitKeys(k)) ++ explicitPairs))
    case v: java.util.List[?] => Arr(v.asScala.map(toJson).toVector)
    case v                    => Str(v.toString)

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

export YamlParser.{attr, props}
