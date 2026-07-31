package sfenv

import fabric.*
import fabric.define.DefType
import fabric.rw.{RW, RWException}

import cats.syntax.show.*

import com.monovore.decline.Argument
import cats.data.Validated

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

object ProcessDrops:
  def apply(dropOpt: String): Option[ProcessDrops] =
    dropOpt match
      case "all"       => Some(All)
      case "non-local" => Some(NonLocal)
      case "none"      => Some(Never)
      case _           => None

  given Argument[ProcessDrops] = Argument.from("all|non-local|none")(x =>
    Validated.fromOption(ProcessDrops(x), "invalid drop option; choose from: 'all', 'non-local', 'none'").toValidatedNel
  )

  given RW[ProcessDrops] = RW.from(
    r = pd => str(pd match
      case All      => "all"
      case NonLocal => "non-local"
      case Never    => "none"
    ),
    w = j => {
      val s = j.asString
      apply(s).getOrElse(throw RWException(show"invalid drop option '$s'; choose from: 'all', 'non-local', 'none'"))
    },
    d = DefType.Str
  )
