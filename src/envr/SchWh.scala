package sfenv
package envr

import fabric.*
import fabric.rw.*
import rules.Util.*

enum SchWh:
  case Schema(db: String, sch: String)
  case Warehouse(wh: String)

  override def toString(): String = this match
    case Schema(db, sch) => s"$db.$sch"
    case Warehouse(wh)   => wh

object SchWh:
  def apply(x: String): Either[String, SchWh] =
    x.split("\\.") match
      case Array(db, sch) => Right(Schema(db, sch))
      case Array(wh)      => Right(Warehouse(wh))
      case _              => Left(s"$x is not valid Schema or Wahrehouse name")

  given RW[SchWh] = RW.string(_.toString(), SchWh(_).value)
