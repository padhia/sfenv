package sfenv
package envr

import io.circe.*

import cats.Show
import cats.syntax.all.*

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

  given Show[SchWh]:
    def show(x: SchWh): String = x match
      case Schema(db, sch) => show"$db.$sch"
      case Warehouse(wh)   => wh.show

  given KeyDecoder[SchWh]:
    def apply(x: String) = SchWh.apply(x)
