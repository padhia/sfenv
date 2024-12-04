package sfenv
import cats.data.Chain

/** A SqlOperable can be created, un-created (dropped) and altered using one or more Sql
  */
trait SqlOperable[T]:
  extension (x: T)
    def create: Chain[Sql]
    def unCreate: Chain[Sql]
    def alter(y: T): Chain[Sql]

object SqlOperable:
  given [T](using SqlObj[T]): SqlOperable[Chain[T]] with
    extension (xs: Chain[T])
      override def create: Chain[Sql]   = xs.flatMap(_.create)
      override def unCreate: Chain[Sql] = xs.flatMap(_.unCreate)
      override def alter(ys: Chain[T]): Chain[Sql] =
        val creates = xs.filterNot(x => ys.exists(_.id == x.id))
        val drops   = ys.filterNot(y => xs.exists(_.id == y.id))
        val alters  = xs.flatMap(x => ys.map((x, _)).filter(_.id == _.id))

        creates.flatMap(_.create) ++
          drops.flatMap(_.unCreate) ++
          alters.flatMap((c, p) => c.alter(p))
