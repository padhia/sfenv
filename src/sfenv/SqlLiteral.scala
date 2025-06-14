package sfenv

import io.circe.*

import cats.Show
import cats.syntax.show.*

opaque type SqlLiteral = String

object SqlLiteral:
  def apply(value: String): SqlLiteral = value

  given Show[SqlLiteral]    = Show.show(x => show"'${x.replace("'", "''")}'")
  given Decoder[SqlLiteral] = Decoder.decodeString.map(SqlLiteral(_))
