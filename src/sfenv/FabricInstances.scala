package sfenv

import scala.collection.immutable.{ListMap, ListSet, VectorMap}

import fabric.*
import fabric.define.DefType
import fabric.rw.*

given listMapRW[K: RW, V: RW]: RW[ListMap[K, V]] =
  if summon[RW[K]].definition == DefType.Str then
    RW.from(
      r = m => Obj(VectorMap.from(m.map { case (k, v) => k.json.asString -> v.json })),
      w = j => ListMap.from(j.asObj.value.map { case (k, v) => str(k).as[K] -> v.as[V] }),
      d = DefType.Obj(None, "[key]" -> summon[RW[V]].definition)
    )
  else
    RW.from(
      r = m => Arr(m.toVector.map { case (k, v) => obj("key" -> k.json, "value" -> v.json) }),
      w = j => ListMap.from(j.asVector.map { jj => jj("key").as[K] -> jj("value").as[V] }),
      d = DefType.Arr(DefType.Obj(None, "key" -> summon[RW[K]].definition, "value" -> summon[RW[V]].definition))
    )

given listSetRW[V: RW]: RW[ListSet[V]] =
  RW.from(
    r = s => Arr(s.map(_.json).toVector),
    w = j => ListSet.from(j.asVector.map(_.as[V])),
    d = DefType.Arr(summon[RW[V]].definition)
  )
