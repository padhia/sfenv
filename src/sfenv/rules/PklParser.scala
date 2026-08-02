package sfenv
package rules

import java.nio.file.Path

import scala.collection.immutable.SortedMap
import scala.jdk.CollectionConverters.*

import org.pkl.config.java.ConfigEvaluator
import org.pkl.core.{ModuleSource, PNull, PObject}
import fabric.{Arr, Bool, Json, Null, NumDec, NumInt, Obj, Str}

object PklParser:
  def apply(path: Path): Json =
    val evaluator = ConfigEvaluator.preconfigured()
    try toJson(evaluator.evaluate(ModuleSource.path(path)).getRawValue)
    finally evaluator.close()

  private def toJson(value: Any): Json = value match
    case null                    => Null
    case _: PNull                => Null
    case v: java.lang.Boolean    => Bool(v.booleanValue)
    case v: java.lang.Integer    => NumInt(v.longValue)
    case v: java.lang.Long       => NumInt(v.longValue)
    case v: java.lang.Double     => NumDec(BigDecimal(v.doubleValue))
    case v: java.math.BigDecimal => NumDec(BigDecimal(v))
    case v: String               => Str(v)
    case v: PObject              =>
      Obj(SortedMap.from(v.getProperties.asScala.map((k, v2) => k -> toJson(v2))))
    case v: java.util.List[?] => Arr(v.asScala.map(toJson).toVector)
    case v                    => Str(v.toString)
