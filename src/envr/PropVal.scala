package sfenv
package envr

import fabric.{Bool => JBool, Json, NumDec => JNum, Str => JStr}
import fabric.define.DefType
import fabric.rw.RW

enum PropVal:
  case Num(value: BigDecimal)
  case Str(value: String)
  case Bool(value: Boolean)

  override def toString(): String =
    this match
      case Num(x)  => x.toString()
      case Str(x)  => if x.startsWith("'") || "[A-Za-z_][A-Za-z_0-9$]*".r.matches(x) then x else x.asSqlLiteral
      case Bool(x) => if x then "TRUE" else "FALSE"

  def toJson: Json =
    this match
      case Num(x)  => JNum(x)
      case Str(x)  => JStr(x)
      case Bool(x) => JBool(x)

object PropVal:
  def fromJson(x: Json) =
    x match
      case JStr(x, _)  => Str(x)
      case JNum(x, _)  => Num(x)
      case JBool(x, _) => Bool(x)
      case _           => throw RuntimeException(s"$x is not a valid PropVal")

  given RW[PropVal] = RW.from(_.toJson, PropVal.fromJson, DefType.Json)
