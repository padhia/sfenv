package sfenv

import io.circe.*

import cats.data.Chain
import cats.data.Validated
import cats.kernel.Eq

import com.monovore.decline.Argument

/** Processing option for DROP SQL statments.
  *   - All: retain all DROP SQLs
  *   - NonLocal: comment out only DROP SQLs that may lead to data loss, i.e. databases (except Shares) and schemas
  *   - Never: comment out all DROP SQLs
  */
enum ProcessDrops:
  case All, NonLocal, Never

  /** Returns a comment prefix string to be used before SQL statement */
  def sqlPfx(isLocal: => Boolean) =
    this match
      case All      => ""
      case Never    => "--"
      case NonLocal => if isLocal then "--" else ""

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
    summon[Decoder[String]].emap(x => apply(x).toRight(s"invalid drop option '$x'; choose from: 'all', 'non-local', 'none'"))

extension (x: String) def asSqlLiteral = s"'${x.replace("'", "''")}'"

extension [K, V](m1: Map[K, V])
  def merge(m2: Map[K, V]): Chain[(K, Option[V], Option[V])] =
    val keys = m1.keySet ++ m2.keySet
    Chain.fromIterableOnce(keys).map(k => (k, m1.get(k), m2.get(k)))

extension [A: Eq](x: Chain[A])
  def merge(y: Chain[A]): Chain[(Option[A], Option[A])] =
    x.filterNot(y.contains(_)).map(x => (Some(x), None)) ++
      x.filter(y.contains(_)).map(x => (Some(x), Some(x))) ++
      y.filterNot(x.contains(_)).map(x => (None, Some(x)))

extension [A: Eq](x: List[A]) def merge(y: List[A]): Chain[(Option[A], Option[A])] = Chain.fromSeq(x).merge(Chain.fromSeq(y))
