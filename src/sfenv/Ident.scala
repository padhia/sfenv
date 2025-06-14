package sfenv

import io.circe.*

import cats.Show

case class Ident(value: String):
  val canonical = value.toUpperCase()

  override def equals(that: Any): Boolean = that match
    case x: String => Ident(x).canonical == canonical
    case x: Ident  => x.canonical == canonical
    case _         => false

object Ident:
  given Show[Ident]    = Show.show(_.canonical)
  given Decoder[Ident] = summon[Decoder[String]].map(Ident.apply)
  given KeyDecoder[Ident]:
    override def apply(key: String): Option[Ident] = Some(Ident(key))
