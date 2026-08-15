package sfenv
package envr

import cats.Show
import cats.syntax.all.*

import fabric.*
import fabric.define.{Definition, DefType}
import fabric.rw.{RW, RWException}

enum SchWh:
  case Schema(db: Ident, sch: Ident)
  case Warehouse(wh: Ident)

  def typeName: String = this match
    case Schema(_, _) => "SCHEMA"
    case Warehouse(_) => "WAREHOUSE"

object SchWh:
  def apply(x: String) =
    x.split("\\.") match
      case Array(db, sch) => Some(Schema(Ident(db), Ident(sch)))
      case Array(wh)      => Some(Warehouse(Ident(wh)))
      case _              => None

  given Ordering[SchWh] = Ordering.by(_.show)

  given Show[SchWh]:
    def show(x: SchWh): String = x match
      case Schema(db, sch) => show"$db.$sch"
      case Warehouse(wh)   => wh.show

  given RW[SchWh] = RW.from(
    r = sw => str(sw.show),
    w = j => {
      val s = j.asString
      apply(s).getOrElse(throw RWException(s"Invalid SchWh: '$s'"))
    },
    d = Definition(DefType.Str)
  )
