package sfenv

import cats.syntax.all.*

import com.monovore.decline.Argument

import fabric.*
import fabric.define.DefType
import fabric.rw.{RW, RWException}

/** Which DROP SQL statments should be commented out for safety
  *   - All: all DROP SQLs
  *   - Local: comment out only DROP SQLs that may lead to data loss
  *   - Never: comment out all DROP SQLs
  */
enum GenDrop:
  case All, Local, Never

  def useMask(isForeign: => Boolean) =
    this match
      case All   => false
      case Local => if isForeign then false else true
      case Never => true

  override def toString(): String = this match
    case All   => "all"
    case Local => "local"
    case Never => "none"

object GenDrop:
  def apply(dropOpt: String): Either[String, GenDrop] =
    GenDrop.values
      .find(_.toString().toLowerCase() == dropOpt.toLowerCase())
      .toRight(show"Invalid drop option $dropOpt; choose from: ${GenDrop.values.map(x => s"'$x'").mkString(", ")}")

  given Argument[GenDrop] = Argument.from(GenDrop.values.map(_.toString()).mkString("|"))(apply(_).toValidatedNel)

  given RW[GenDrop] = RW.from(
    r = x => str(x.toString()),
    w = j => apply(j.asString).fold(e => throw RWException(e), x => x),
    d = DefType.Str
  )
