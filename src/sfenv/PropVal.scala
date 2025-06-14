package sfenv

import cats.Show
import cats.syntax.all.*

enum PropVal:
  case Num(value: BigDecimal)
  case Str(value: String)
  case Sch(db: Ident, sch: Ident)
  case Bool(value: Boolean)

object PropVal:
  type PropType = BigDecimal | Int | String | Ident | Boolean | (Ident, Ident)

  given Show[PropVal] = Show.show: x =>
    x match
      case Num(x) => x.show
      case Str(x) =>
        if x.startsWith("'") || x.startsWith("(") then x
        else if "[A-Za-z_][A-Za-z_0-9$]*".r.matches(x) then x.toUpperCase
        else SqlLiteral(x).show
      case Sch(d, s) => show"$d.$s"
      case Bool(x)   => if x then "TRUE" else "FALSE"

  def apply(value: PropType): PropVal =
    value match
      case x: BigDecimal        => Num(x)
      case x: Int               => Num(x)
      case x: String            => Str(x)
      case x: Ident             => Str(x.show)
      case (x: Ident, y: Ident) => Sch(x, y)
      case x: Boolean           => Bool(x)
