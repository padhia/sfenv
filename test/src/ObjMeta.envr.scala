package sfenv
package envr
package test

import cats.data.Chain

import munit.FunSuite

class ObjMetaTests extends FunSuite:
  val testProps = Props("STR_PROP" -> "STR_VAL", "NUM_PROP" -> 2, "BOOL_PROP" -> true)
  val comment   = Some(SqlLiteral("A sample comment"))
  val tags      = Tags("tag1" -> "tag value 1", "tag2" -> "tag value 2")
  val sysAdm    = RoleName("ENVADMIN")

  test("toString - props"):
    val actual   = ObjMeta(props = testProps).sql("")
    val expected = " STR_PROP = STR_VAL NUM_PROP = 2 BOOL_PROP = TRUE"

    assert(clue(actual) == clue(expected))

  test("toString - long"):
    val actual   = ObjMeta(testProps, Some(tags), comment).sql("")
    val expected = """|
                      |    STR_PROP = STR_VAL
                      |    NUM_PROP = 2
                      |    BOOL_PROP = TRUE
                      |    COMMENT = 'A sample comment'
                      |    WITH TAG TAG1 = 'tag value 1', TAG2 = 'tag value 2'""".stripMargin

    assert(clue(actual) == clue(expected))

  test("alter - comment"):
    val om1 = ObjMeta(comment = Some(SqlLiteral("An old comment")))
    val om2 = ObjMeta(comment = Some(SqlLiteral("A new comment")))
    val om3 = ObjMeta(comment = None)

    assert(clue(om2.sql("", om1)) == clue(Chain(" SET COMMENT = 'A new comment'")))
    assert(clue(om3.sql("", om1)) == clue(Chain(" UNSET COMMENT")))

  test("alter - props"):
    val oldOM    = ObjMeta(Props("STR_PROP" -> "STR_VAL", "NUM_PROP" -> 3, "BOOL_PROP" -> true))
    val newOM    = ObjMeta(Props("STR_PROP" -> "STR_VAL2", "NUM_PROP" -> 2))
    val expected = Chain(
      " SET STR_PROP = STR_VAL2 NUM_PROP = 2",
      " UNSET BOOL_PROP"
    )
    val actual = newOM.sql("", oldOM)
    assert(clue(actual) == clue(expected))
