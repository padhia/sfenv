package sfenv

import cats.Show
import cats.syntax.show.*

import fabric.*
import fabric.define.{Definition, DefType}
import fabric.rw.RW

opaque type SqlLiteral = String

object SqlLiteral:
  def apply(value: String): SqlLiteral = value

  given Show[SqlLiteral] = Show.show(x => show"'${x.replace("'", "''")}'")
  given RW[SqlLiteral]   = RW.from(r = sl => str(sl), w = j => SqlLiteral(j.asString), d = Definition(DefType.Str))
