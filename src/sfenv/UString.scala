package sfenv

import fabric.*
import fabric.define.DefType
import fabric.rw.RW

import cats.Show

opaque type UString = String
object UString:
  def apply(s: String): UString = s.toUpperCase

  given Show[UString] = Show.show(t => t)
  given RW[UString]   = RW.from(r = us => str(us), w = j => UString(j.asString), d = DefType.Str)
