package sfenv

import cats.Show

import fabric.*
import fabric.define.{Definition, DefType}
import fabric.rw.RW

opaque type UString = String
object UString:
  def apply(s: String): UString = s.toUpperCase

  extension (u: UString) inline def value: String = u

  given Show[UString]     = Show.show(t => t)
  given RW[UString]       = RW.from(r = us => str(apply(us)), w = j => apply(j.asString), d = Definition(DefType.Str))
  given Ordering[UString] = Ordering.by(s => s)
