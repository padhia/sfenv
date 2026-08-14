package sfenv
package rules

import scala.collection.immutable.SortedMap
import scala.jdk.CollectionConverters.*

import org.snakeyaml.engine.v2.api.{Load, LoadSettings}
import fabric.{Arr, Bool, Json, Null, NumDec, NumInt, Obj, Str}

object YamlParser:
  private lazy val load = Load(LoadSettings.builder().setMaxAliasesForCollections(1000).build())

  def apply(content: String): Json = toJson(load.loadFromString(content))

  private def toJson(value: Any): Json = value match
    case null                    => Null
    case v: java.lang.Boolean    => Bool(v.booleanValue)
    case v: java.lang.Integer    => NumInt(v.longValue)
    case v: java.lang.Long       => NumInt(v.longValue)
    case v: java.lang.Double     => NumDec(BigDecimal(v.doubleValue))
    case v: java.math.BigInteger => NumDec(BigDecimal(new java.math.BigDecimal(v)))
    case v: String               => Str(v)
    case v: java.util.List[?]    => Arr(v.asScala.map(toJson).toVector)
    case v: java.util.Map[?, ?]  =>
      val (merges, explicit) = v.asScala.toSeq.partition(_._1 == "<<") // merge-key operations
      val anchorPairs        = merges.flatMap:
        case (_, anchor: java.util.Map[?, ?]) => anchor.asScala.toSeq.map((k, v2) => k.toString -> toJson(v2))
        case _                                => Nil
      val explicitPairs = explicit.map((k, v2) => k.toString -> toJson(v2))
      val explicitKeys  = explicitPairs.map(_._1).toSet
      Obj(SortedMap.from(anchorPairs.filterNot((k, _) => explicitKeys(k)) ++ explicitPairs))
    case v => Str(v.toString)
