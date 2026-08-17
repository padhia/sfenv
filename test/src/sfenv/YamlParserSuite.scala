package sfenv

import cats.effect.unsafe.implicits.global

import munit.FunSuite

class YamlParserSuite extends FunSuite:

  private def parse(yaml: String) = YamlParser(yaml.stripMargin).unsafeRunSync()

  test("merge key - anchor fields are inlined into child map"):
    val child = parse("""
      |base: &anchor
      |  x: 1
      |  y: hello
      |child:
      |  <<: *anchor
      |  z: world
      """)("child")
    assertEquals(child("x").asNum.asLong, 1L)
    assertEquals(child("y").asStr.value, "hello")
    assertEquals(child("z").asStr.value, "world")

  test("merge key - explicit field overrides anchor field"):
    val child = parse("""
      |base: &anchor
      |  x: 1
      |  y: original
      |child:
      |  <<: *anchor
      |  y: overridden
      """)("child")
    assertEquals(child("x").asNum.asLong, 1L)
    assertEquals(child("y").asStr.value, "overridden")

  test("merge key - << key does not appear in the result"):
    val child = parse("""
      |base: &anchor
      |  x: 1
      |child:
      |  <<: *anchor
      """)("child")
    assert(child.get("<<").isEmpty)

  test("merge key - explicit wins even when it precedes << in the document"):
    val child = parse("""
      |base: &anchor
      |  x: anchor-x
      |  y: anchor-y
      |child:
      |  x: explicit-x   # override appears BEFORE <<
      |  <<: *anchor
      """)("child")
    assertEquals(child("x").asStr.value, "explicit-x") // explicit wins
    assertEquals(child("y").asStr.value, "anchor-y")   // anchor-only field present

  test("plain alias - anchor map is the value, entries are not inlined"):
    val json = parse("""
      |base: &anchor
      |  x: 1
      |child: *anchor
      """)
    assertEquals(json("child")("x").asNum.asLong, 1L) // x is inside child
    assert(json.get("x").isEmpty)                     // x is NOT inlined into root

  test("scalar types - boolean"):
    val json = parse("t: true\nf: false")
    assertEquals(json("t").asBool.value, true)
    assertEquals(json("f").asBool.value, false)

  test("scalar types - integer"):
    assertEquals(parse("n: 42")("n").asNum.asLong, 42L)

  test("scalar types - null"):
    assert(parse("value: null")("value").isNull)

  test("scalar types - sequence"):
    val actual = parse("items:\n  - a\n  - b\n  - c")("items").asVector.map(_.asStr.value).toList
    assertEquals(actual, List("a", "b", "c"))
