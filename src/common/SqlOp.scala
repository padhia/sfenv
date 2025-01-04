package sfenv

enum SqlOp[T](val obj: T):
  case Create(override val obj: T) extends SqlOp(obj)
  case Drop(override val obj: T) extends SqlOp(obj)
  case Alter(override val obj: T, val other: T) extends SqlOp(obj)
