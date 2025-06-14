package sfenv

import io.circe.Decoder

import cats.Show

opaque type UString = String
object UString:
  def apply(s: String): UString = s.toUpperCase

  given Decoder[UString] = Decoder[String].map(UString(_))
  given Show[UString]    = Show.show(t => t)
