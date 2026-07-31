package sfenv

import cats.syntax.all.*

import munit.FunSuite

class PropValSuite extends FunSuite:
  test("PropVal - Bool"):
    assertEquals(PropVal(true).show, "TRUE")
    assertEquals(PropVal(false).show, "FALSE")

  test("PropVal - Num"):
    assertEquals(PropVal(1).show, "1")
    assertEquals(PropVal(BigDecimal(2048)).show, "2048")

  test("PropVal - Str"):
    assertEquals(PropVal("'Literal String'").show, "'Literal String'")
    assertEquals(PropVal("(VAL1, VAL2)").show, "(VAL1, VAL2)")
    assertEquals(PropVal("identifier_string").show, "IDENTIFIER_STRING")
    assertEquals(PropVal("4XL").show, "'4XL'")
    assertEquals(PropVal(Ident("ident")).show, "IDENT")

  test("PropVal - Sch"):
    assertEquals(PropVal((Ident("db"), Ident("sch"))).show, "DB.SCH")
