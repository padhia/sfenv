package sfenv

import cats.Show
import cats.data.Chain
import cats.syntax.all.*

import fabric.*
import fabric.define.{Definition, DefType}
import fabric.rw.*

opaque type Props = Obj

object Props:
  type Input = Json | String | Int | BigDecimal | Boolean | Ident

  private def validate(json: Json): Unit = json match
    case Null       => throw RWException("Null is not a supported property value")
    case value: Obj => value.value.valuesIterator.foreach(validate)
    case value: Arr => value.value.foreach(validate)
    case _          => ()

  def fromJson(json: Json): Props =
    if !json.isObj then throw RWException("Properties must be a JSON object")
    validate(json)
    json.asObj

  def apply(values: (String, Input)*): Props =
    fromJson(
      Obj(
        values.iterator
          .map: (key, value) =>
            val json = value match
              case json: Json         => json
              case text: String       => str(text)
              case number: Int        => num(number)
              case number: BigDecimal => num(number)
              case flag: Boolean      => bool(flag)
              case identifier: Ident  => str(identifier.show)
            key -> json
          .toMap
      )
    )

  def empty: Props = Obj.empty

  trait RenderObj:
    def render(key: String, value: Json): String

  object RenderObj extends RenderObj:
    def render(key: String, json: Json): String = json match
      case Str(value, _) =>
        if value.startsWith("'") || value.startsWith("(") then value
        else if "[A-Za-z_][A-Za-z_0-9$]*".r.matches(value) then value.toUpperCase
        else SqlLiteral(value).show
      case Bool(value, _) => if value then "TRUE" else "FALSE"
      case number: Num    => number.asBigDecimal.show
      case value: Obj     =>
        value.value.toList.sortBy(_._1).map((key, child) => show"${Ident(key)} = ${renderValue(child)}").mkString("(", ", ", ")")
      case Arr(values, _) => values.map(renderValue).mkString("(", ", ", ")")
      case Null           => throw RWException("Null is not a supported property value")

  def renderValue(json: Json): String = json match
    case Str(value, _) =>
      if value.startsWith("'") || value.startsWith("(") then value
      else if "[A-Za-z_][A-Za-z_0-9$]*".r.matches(value) then value.toUpperCase
      else SqlLiteral(value).show
    case Bool(value, _) => if value then "TRUE" else "FALSE"
    case number: Num    => number.asBigDecimal.show
    case value: Obj     =>
      value.value.toList.sortBy(_._1).map((key, child) => show"${Ident(key)} = ${renderValue(child)}").mkString("(", ", ", ")")
    case Arr(values, _) => values.map(renderValue).mkString("(", ", ", ")")
    case Null           => throw RWException("Null is not a supported property value")

  given Show[Props] = Show.show(renderValue)

  given RW[Props] = RW.from(
    r = props => props,
    w = fromJson,
    d = Definition(DefType.Json)
  )

  extension (props: Props)
    def toJson: Obj                    = props
    def entries: List[(String, Json)]  = props.value.toList.sortBy(_._1)
    def apply(key: String): Json       = props.value(key)
    def get(key: String): Option[Json] = props.value.get(key)
    def words: Chain[String] = Chain.fromSeq(props.entries.map((key, value) => show"${Ident(key)} = ${renderValue(value)}"))

    def updated(key: String, value: Json): Props =
      validate(value)
      Obj(props.value.updated(key, value))

    def ++(other: Props): Props = Obj(props.value ++ other.value)

    def changes(old: Props): (Chain[String], Chain[String]) =
      val changed = Obj(props.value.filter((key, value) => !old.value.get(key).contains(value)))
      val removed = old.value.keySet.diff(props.value.keySet).toList.sorted.map(key => Ident(key).show)
      (changed.words, Chain.fromSeq(removed))
