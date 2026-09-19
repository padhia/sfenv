package sfenv

import cats.syntax.all.*

import scala.compiletime.testing.typeCheckErrors

import fabric.*
import fabric.rw.*
import munit.FunSuite

class PropsSuite extends FunSuite:
  test("scalar SQL rendering preserves existing conventions"):
    val cases = List(
      bool(true)               -> "TRUE",
      bool(false)              -> "FALSE",
      num(1)                   -> "1",
      num(BigDecimal("1.25"))  -> "1.25",
      str("identifier_string") -> "IDENTIFIER_STRING",
      str("4XL")               -> "'4XL'",
      str("two words")         -> "'two words'",
      str("O'Brien")           -> "'O''Brien'",
      str("'Literal String'")  -> "'Literal String'",
      str("(VAL1, VAL2)")      -> "(VAL1, VAL2)"
    )
    cases.foreach((json, expected) => assertEquals(Props.renderValue(json), expected))

  test("objects sort keys while arrays preserve order"):
    val json = obj(
      "settings" -> obj("retries" -> num(3), "enabled" -> bool(true)),
      "items"    -> arr(str("two words"), num(2), bool(false))
    )
    val props = Props.fromJson(json)
    assertEquals(props.show, "(ITEMS = ('two words', 2, FALSE), SETTINGS = (ENABLED = TRUE, RETRIES = 3))")
    assertEquals(props.json, json)
    assertEquals(props.json.as[Props], props)
    assertEquals(props.toJson, json)

  test("empty collections render as parentheses"):
    assertEquals(Props.empty.show, "()")
    assertEquals(Props.renderValue(arr()), "()")
    assertEquals(Props.empty.json, obj())

  test("db and sch objects no longer have special meaning"):
    val props = Props.fromJson(obj("namespace" -> obj("db" -> str("source"), "sch" -> str("public"))))
    assertEquals(props.show, "(NAMESPACE = (DB = SOURCE, SCH = PUBLIC))")
    assertEquals(props.json.as[Props], props)

  test("root must be an object and nested null is rejected"):
    List(Null, arr(), str("value"), num(1)).foreach: json =>
      intercept[RWException](json.as[Props])
    List(obj("key" -> Null), obj("key" -> arr(Null)), obj("key" -> obj("nested" -> Null))).foreach: json =>
      intercept[RWException](json.as[Props])
    intercept[RWException](Props("key" -> Null))
    intercept[RWException](Props.empty.updated("key", Null))

  test("convenience construction accepts scalars and AST values"):
    val props = Props("enabled" -> true, "count" -> 2, "name" -> Ident("some_name"), "items" -> arr(num(1)))
    assertEquals(props("count"), num(2))
    assertEquals(props.get("missing"), None)
    assertEquals(props("name"), str("SOME_NAME"))
    assertEquals(props.updated("count", num(3))("count"), num(3))

  test("merge is shallow and right biased"):
    val original    = Props("nested" -> obj("old" -> num(1)), "retained" -> true)
    val replacement = Props("nested" -> obj("new" -> num(2)))
    assertEquals((original ++ replacement).toJson, obj("nested" -> obj("new" -> num(2)), "retained" -> bool(true)))

  test("diff replaces changed nested properties and unsets removed keys"):
    val old                = Props("nested" -> obj("value" -> num(1)), "gone" -> true, "unchanged" -> 1)
    val current            = Props("nested" -> obj("value" -> num(2)), "added" -> false, "unchanged" -> BigDecimal("1.0"))
    val (changed, removed) = current.changes(old)
    assertEquals(changed.toList, List("ADDED = FALSE", "NESTED = (VALUE = 2)"))
    assertEquals(removed.toList, List("GONE"))
    assertEquals(current.changes(current)._1.toList, Nil)
    assertEquals(current.changes(current)._2.toList, Nil)

  test("opaque properties cannot be replaced with unchecked JSON"):
    assert(typeCheckErrors("""val properties: sfenv.Props = fabric.obj()""").nonEmpty)
