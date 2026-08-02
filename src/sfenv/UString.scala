package sfenv

import cats.Show

import fabric.*
import fabric.define.DefType
import fabric.rw.RW

opaque type UString = String
object UString:
  def apply(s: String): UString = s.toUpperCase

  extension (u: UString) inline def value: String = u

  given Show[UString]     = Show.show(t => t)
  given RW[UString]       = RW.from(r = us => str(apply(us)), w = j => apply(j.asString), d = DefType.Str)
  given Ordering[UString] = Ordering.by(s => s)
