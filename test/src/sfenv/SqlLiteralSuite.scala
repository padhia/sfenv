package sfenv

import cats.syntax.show.*

import munit.FunSuite

class SqlLiteralSuite extends FunSuite:
  test("show wraps value in single quotes"):
    assertEquals(SqlLiteral("hello world").show, "'hello world'")

  test("show escapes embedded single quotes by doubling them"):
    assertEquals(SqlLiteral("it's alive").show, "'it''s alive'")

  test("show escapes multiple embedded single quotes"):
    assertEquals(SqlLiteral("o'clock it's").show, "'o''clock it''s'")

  test("show handles value with no special characters"):
    assertEquals(SqlLiteral("plain string").show, "'plain string'")
