package sfenv
package test

import cats.syntax.all.*

import munit.FunSuite

class PropValTests extends FunSuite:
  test("PropVal - Bool"):
    assert(clue(PropVal(true).show) == clue("TRUE"))
    assert(clue(PropVal(false).show) == clue("FALSE"))

  test("PropVal - Num"):
    assert(clue(PropVal(1).show) == clue("1"))
    assert(clue(PropVal(BigDecimal(2048)).show) == clue("2048"))

  test("PropVal - Str"):
    assert(clue(PropVal("'Literal String'").show) == clue("'Literal String'"))
    assert(clue(PropVal("(VAL1, VAL2)").show) == clue("(VAL1, VAL2)"))
    assert(clue(PropVal("identifier_string").show) == clue("IDENTIFIER_STRING"))
    assert(clue(PropVal("4XL").show) == clue("'4XL'"))
    assert(clue(PropVal(Ident("ident")).show) == clue("IDENT"))

  test("PropVal - Sch"):
    val value = PropVal((Ident("db"), Ident("sch"))).show
    assert(clue(value) == clue("DB.SCH"))
