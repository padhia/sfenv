package sfenv

import scala.collection.immutable.SortedMap

extension [T](xs: List[T]) def --(ys: List[T]) = xs.filterNot(ys.contains(_))

enum Admin:
  case Sec, Sys

type EnvName = String

type Props = SortedMap[Ident, PropVal]
object Props:
  def apply(values: (String, PropVal.PropType)*): Props = SortedMap.from(values.map((x, y) => (Ident(x), PropVal(y))))
  def empty                                             = SortedMap.empty[Ident, PropVal]

type Tags = SortedMap[Ident, SqlLiteral]
object Tags:
  def apply(values: (String, String)*): Tags = SortedMap.from(values.map((x, y) => (Ident(x), SqlLiteral(y))))
  def empty: Tags                            = SortedMap.empty

enum GenGrant:
  case All, Future

/** Inserts ANSI color codes into a CLI help text block.
  *
  * Recognized elements:
  *   - Section headers: lines ending with `:` → colored `h`
  *   - Long options: `--word` (alphanumeric + hyphens) → colored `o`
  *   - Short options: `-X` (single letter, not `--`) → colored `o`
  *   - Meta variables: `<word>` → colored `m`
  */
def colorizeHelp(
    text: String,
    h: String = "\u001b[33m", // gold       — section headers
    o: String = "\u001b[94m", // light blue — options
    m: String = "\u001b[37m", // light gray — meta variables
): String =
  val reset  = "\u001b[0m"
  val header = """(.+):""".r
  val inline = raw"""(--[a-zA-Z][a-zA-Z0-9-]*)|((?<!-)-[a-zA-Z](?![a-zA-Z0-9-]))|(<[a-zA-Z][a-zA-Z0-9-]*>)""".r

  def colorLine(line: String): String =
    if header.matches(line) then s"$h$line$reset"
    else
      inline.replaceAllIn(
        line,
        _.matched match
          case s if s.startsWith("<") => s"$m$s$reset"
          case s                      => s"$o$s$reset"
      )

  text.split("\n", -1).map(colorLine).mkString("\n")
