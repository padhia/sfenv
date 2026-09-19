package sfenv
package rules

import java.util.Locale

import scala.collection.immutable.SortedSet
import scala.compiletime.constValueTuple
import scala.deriving.Mirror

import fabric.*
import fabric.define.{Definition, DefType}
import fabric.rw.*

def keyedSortedSetRW[A: {RW, Ordering}](nameOf: A => String): RW[SortedSet[A]] =
  RW.from(
    r = values =>
      val entries = values.toList.map(value => nameOf(value) -> Obj(value.json.asObj.value - "name"))
      if entries.map(_._1).distinct.size != entries.size then throw RWException("Duplicate object names")
      Obj(entries.toMap)
    ,
    w = json =>
      json.asObj.value.toList.sortBy(_._1).foldLeft(SortedSet.empty[A]): (values, entry) =>
        val (name, body) = entry
        val fields = if body.isNull then obj().asObj.value else body.asObj.value
        if fields.keysIterator.exists(_.equalsIgnoreCase("name")) then
          throw RWException(s"Object '$name' must not specify name; use the object key")
        val value = Obj(fields + ("name" -> str(name))).as[A]
        if values.contains(value) then throw RWException(s"Duplicate object name under ordering: '$name'")
        values + value
    ,
    d = Definition(DefType.Obj("[key]" -> Definition(DefType.Json)))
  )

inline def propsRW[A](using mirror: Mirror.ProductOf[A]): RW[A] =
  val writer = RW.genW[A]
  val labels = constValueTuple[mirror.MirroredElemLabels].toList.asInstanceOf[List[String]].filterNot(_ == "props").toSet
  RW.from(
    r = _ => throw UnsupportedOperationException("Serialization not supported"),
    w = json =>
      val fields     = if json.isNull then obj().asObj.value else json.asObj.value
      val collisions = fields.keysIterator.toList
        .groupBy(_.toLowerCase(Locale.ROOT))
        .toList
        .sortBy(_._1)
        .collect { case (_, keys) if keys.size > 1 => keys.sorted.mkString("[", ", ", "]") }
      if collisions.nonEmpty then throw RWException(s"Duplicate object keys ignoring case: ${collisions.mkString(", ")}")
      val normalized     = fields.map((key, value) => key.toLowerCase(Locale.ROOT) -> value)
      val (named, extra) = normalized.partition((key, _) => labels.contains(key))
      writer.write(Obj(named + ("props" -> Obj(extra))))
    ,
    d = Definition(DefType.Json)
  )
