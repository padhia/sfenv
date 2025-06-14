package sfenv

import io.circe.*

import cats.data.Validated
import cats.syntax.show.*

import com.monovore.decline.Argument

/** Processing option for DROP SQL statments.
  *   - All: retain all DROP SQLs
  *   - NonLocal: comment out only DROP SQLs that may lead to data loss, i.e. databases (except Shares) and schemas
  *   - Never: comment out all DROP SQLs
  */
enum ProcessDrops:
  case All, NonLocal, Never

  /** Returns a comment prefix string to be used before SQL statement */
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

  given Decoder[ProcessDrops] =
    summon[Decoder[String]].emap(x => apply(x).toRight(show"invalid drop option '$x'; choose from: 'all', 'non-local', 'none'"))
