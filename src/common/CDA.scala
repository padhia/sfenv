package sfenv

import cats.data.Chain

trait CDA[T]:
  extension (obj: T)
    def create: Chain[SqlStmt]
    def drop: Chain[SqlStmt]
    def update(old: T): Chain[SqlStmt]
    def sameId(other: T): Boolean
    def updatable(old: T): Boolean
