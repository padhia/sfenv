package sfenv
package envr

import cats.data.Chain
import cats.syntax.all.*

import SqlStmt.*

case class ComputePool(name: Ident, meta: ObjMeta)

object ComputePool:
  val kind = "COMPUTE POOL"

  given Ordering[ComputePool] = Ordering.by(_.name)

  given CDA[ComputePool]:
    extension (obj: ComputePool)
      def create: Chain[SqlStmt]                   = Chain(obj.meta.ddl(kind.cr))
      def drop: Chain[SqlStmt]                     = Chain(show"${kind.dr} ${obj.name}".ddl)
      def update(old: ComputePool): Chain[SqlStmt] = obj.meta.ddl(show"${kind.alt} ${obj.name}", old.meta)
      def sameId(other: ComputePool): Boolean      = obj.name == other.name
      def updatable(old: ComputePool): Boolean     = true
