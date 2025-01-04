package sfenv

import cats.data.Chain

opaque type SfObjId = String
object SfObjId:
  def apply(x: String): SfObjId = x

trait SfObj[T]:
  extension (obj: T) def id: SfObjId
  def genSql(obj: SqlOp[T]): Chain[SqlStmt]
