package sfenv

import cats.syntax.show.*

import munit.FunSuite

class IdentSuite extends FunSuite:
  test("equality is case-insensitive"):
    assertEquals(Ident("SNOWFLAKE"), Ident("snowflake"))
    assertEquals(Ident("My_Table"), Ident("MY_TABLE"))

  test("equals String case-insensitively"):
    // Ident.equals is overridden to accept String; use .equals() since == needs CanEqual
    assert(Ident("SNOWFLAKE").equals("snowflake"))
    assert(Ident("snowflake").equals("SNOWFLAKE"))

  test("show produces canonical uppercase form"):
    assertEquals(Ident("my_schema").show, "MY_SCHEMA")
    assertEquals(Ident("ALREADY_UPPER").show, "ALREADY_UPPER")

  test("different identifiers are not equal"):
    assertNotEquals(Ident("FOO"), Ident("BAR"))
    assert(!Ident("FOO").equals("BAR"))
