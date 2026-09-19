package sfenv
package envr

import cats.data.Chain

import scala.collection.immutable.{SortedMap, SortedSet}

trait CDA[T]:
  extension (obj: T)
    def sameId(other: T): Boolean
    def updatable(old: T): Boolean
    def create: Chain[SqlStmt]
    def drop: Chain[SqlStmt]
    def update(old: T): Chain[SqlStmt]

object CDA:
  given [T: CDA]: CDA[List[T]] with
    extension (objs: List[T])
      def sameId(other: List[T]): Boolean       = true
      def updatable(other: List[T]): Boolean    = true
      def create: Chain[SqlStmt]                = Chain.fromSeq(objs).flatMap(_.create)
      def drop: Chain[SqlStmt]                  = Chain.fromSeq(objs).reverse.flatMap(_.drop)
      def update(olds: List[T]): Chain[SqlStmt] =
        Chain.fromSeq(olds).filterNot(x => objs.exists(_.sameId(x))).reverse.flatMap(_.drop)
          ++ Chain.fromSeq(objs).filterNot(x => olds.exists(_.sameId(x))).flatMap(_.create)
          ++ Chain
            .fromSeq(objs)
            .map(x => (x, olds.find(_.sameId(x))))
            .collect { case (x, Some(y)) if x != y => (x, y) }
            .flatMap((x, y) => if x.updatable(y) then x.update(y) else y.drop ++ x.create)

  given sortedMap[K, V](using T: CDA[(K, V)]): CDA[SortedMap[K, V]] with
    extension (objs: SortedMap[K, V])
      def sameId(other: SortedMap[K, V]): Boolean       = true
      def updatable(other: SortedMap[K, V]): Boolean    = true
      def create: Chain[SqlStmt]                        = Chain.fromSeq(objs.toList).flatMap(_.create)
      def drop: Chain[SqlStmt]                          = Chain.fromSeq(objs.toList).reverse.flatMap(T.drop)
      def update(olds: SortedMap[K, V]): Chain[SqlStmt] =
        Chain.fromSeq(olds.toList).filterNot(x => objs.exists(_.sameId(x))).reverse.flatMap(T.drop)
          ++ Chain.fromSeq(objs.toList).filterNot(x => olds.exists(_.sameId(x))).flatMap(_.create)
          ++ Chain
            .fromSeq(objs.toList)
            .map(x => (x, olds.find(_.sameId(x))))
            .collect { case (x, Some(y)) if x != y => (x, y) }
            .flatMap((x, y) => if x.updatable(y) then x.update(y) else T.drop(y) ++ x.create)

  given sortedSet[T](using CDA[T]): CDA[SortedSet[T]] with
    extension (xs: SortedSet[T])
      def sameId(ys: SortedSet[T]): Boolean    = true
      def updatable(ys: SortedSet[T]): Boolean = true
      def create: Chain[SqlStmt]               = Chain.fromSeq(xs.toList).flatMap(_.create)
      def drop: Chain[SqlStmt]                 = Chain.fromSeq(xs.toList).reverse.flatMap(summon[CDA[T]].drop)

      def update(ys: SortedSet[T]): Chain[SqlStmt] =
        Chain.fromSeq(ys.toList).filterNot(x => xs.exists(_.sameId(x))).reverse.flatMap(summon[CDA[T]].drop)
          ++ Chain.fromSeq(xs.toList).filterNot(x => ys.exists(_.sameId(x))).flatMap(_.create)
          ++ Chain
            .fromSeq(xs.toList)
            .map(x => (x, ys.find(_.sameId(x))))
            .collect { case (current, Some(previous)) => (current, previous) }
            .flatMap: (current, previous) =>
              if current.updatable(previous) then current.update(previous)
              else if current != previous then summon[CDA[T]].drop(previous) ++ current.create
              else Chain.empty
