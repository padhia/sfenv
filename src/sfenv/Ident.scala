package sfenv

import fabric.*
import fabric.define.DefType
import fabric.rw.RW

import cats.Show

case class Ident(value: String):
  val canonical = value.toUpperCase()

  override def equals(that: Any): Boolean = that match
    case x: String => Ident(x).canonical == canonical
    case x: Ident  => x.canonical == canonical
    case _         => false

object Ident:
  given Show[Ident] = Show.show(_.canonical)
  given RW[Ident]   = RW.from(r = id => str(id.value), w = j => Ident(j.asString), d = DefType.Str)
