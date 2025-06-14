package sfenv
package envr

import cats.data.Chain

import scala.collection.immutable.ListMap

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

  given [K, V](using T: CDA[(K, V)]): CDA[ListMap[K, V]] with
    extension (objs: ListMap[K, V])
      def sameId(other: ListMap[K, V]): Boolean       = true
      def updatable(other: ListMap[K, V]): Boolean    = true
      def create: Chain[SqlStmt]                      = Chain.fromSeq(objs.toList).flatMap(_.create)
      def drop: Chain[SqlStmt]                        = Chain.fromSeq(objs.toList).reverse.flatMap(T.drop)
      def update(olds: ListMap[K, V]): Chain[SqlStmt] =
        Chain.fromSeq(olds.toList).filterNot(x => objs.exists(_.sameId(x))).reverse.flatMap(T.drop)
          ++ Chain.fromSeq(objs.toList).filterNot(x => olds.exists(_.sameId(x))).flatMap(_.create)
          ++ Chain
            .fromSeq(objs.toList)
            .map(x => (x, olds.find(_.sameId(x))))
            .collect { case (x, Some(y)) if x != y => (x, y) }
            .flatMap((x, y) => if x.updatable(y) then x.update(y) else T.drop(y) ++ x.create)
