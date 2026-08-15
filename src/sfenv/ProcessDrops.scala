package sfenv

import cats.syntax.all.*

import com.monovore.decline.Argument

import fabric.*
import fabric.define.{Definition, DefType}
import fabric.rw.{RW, RWException}

/** Processing option for DROP SQL statments.
  *   - All: retain all DROP SQLs
  *   - NonLocal: comment out only DROP SQLs that may lead to data loss
  *   - Never: comment out all DROP SQLs
  */
enum ProcessDrops:
  case All, NonLocal, Never

  def useMask(isForeign: => Boolean) =
    this match
      case All      => false
      case Never    => true
      case NonLocal => if isForeign then false else true

  override def toString(): String = this match
    case All      => "all"
    case NonLocal => "non-local"
    case Never    => "none"

object ProcessDrops:
  def apply(dropOpt: String): Either[String, ProcessDrops] =
    dropOpt match
      case "all"       => Right(All)
      case "non-local" => Right(NonLocal)
      case "none"      => Right(Never)
      case _           => Left(show"invalid drop option $dropOpt; choose from: 'all', 'non-local', 'none'")

  given Argument[ProcessDrops] = Argument.from("all|non-local|none")(apply(_).toValidatedNel)

  given RW[ProcessDrops] = RW.from(
    r = x => str(x.toString()),
    w = j => apply(j.asString).fold(e => throw RWException(e), x => x),
    d = Definition(DefType.Str)
  )
