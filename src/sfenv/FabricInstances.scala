package sfenv

import scala.collection.immutable.{SortedMap, SortedSet}

import fabric.*
import fabric.define.{Definition, DefType}
import fabric.rw.*

given SortedMapRW[K: RW: Ordering, V: RW]: RW[SortedMap[K, V]] =
  if summon[RW[K]].definition.defType == DefType.Str then
    RW.from(
      r = m => Obj(SortedMap.from(m.map { case (k, v) => k.json.asString -> v.json })),
      w = j => SortedMap.from(j.asObj.value.map { case (k, v) => str(k).as[K] -> v.as[V] }),
      d = Definition(DefType.Obj("[key]" -> summon[RW[V]].definition))
    )
  else
    RW.from(
      r = m => Arr(m.toVector.map { case (k, v) => obj("key" -> k.json, "value" -> v.json) }),
      w = j => SortedMap.from(j.asVector.map { jj => jj("key").as[K] -> jj("value").as[V] }),
      d = Definition(DefType.Arr(Definition(DefType.Obj("key" -> summon[RW[K]].definition, "value" -> summon[RW[V]].definition))))
    )

given SortedSetRW[V: RW: Ordering]: RW[SortedSet[V]] =
  RW.from(
    r = s => Arr(s.map(_.json).toVector),
    w = j => SortedSet.from(j.asVector.map(_.as[V])),
    d = Definition(DefType.Arr(summon[RW[V]].definition))
  )
