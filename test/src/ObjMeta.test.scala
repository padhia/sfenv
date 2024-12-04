package sfenv
package envr

import cats.data.Chain

import PropVal.*
import munit.FunSuite

class ObjMetaTests extends FunSuite:
  val testProps = Map("STR_PROP" -> Str("STR_VAL"), "NUM_PROP" -> Num(2), "BOOL_PROP" -> Bool(true))
  val comment   = Some("A sample comment")
  val tags      = Map("tag1" -> "tag value 1", "tag2" -> "tag value 2")
  val sysAdm    = RoleName.Account("ENVADMIN")

  def sqls(xs: Chain[Sql]) = xs.flatMap(_.texts(sysAdm, true)).toList

  test("toString - props"):
    val testOM   = ObjMeta(testProps)
    val expected = " STR_PROP = STR_VAL NUM_PROP = 2 BOOL_PROP = TRUE"

    assert(clue(testOM.toString()) == expected)

  test("toString - long"):
    val testOM = ObjMeta(testProps, Some(tags), comment)
    val expected = """|
                      |    STR_PROP = STR_VAL
                      |    NUM_PROP = 2
                      |    BOOL_PROP = TRUE
                      |    WITH TAG TAG1 = 'tag value 1', TAG2 = 'tag value 2'
                      |    COMMENT = 'A sample comment'""".stripMargin

    assert(clue(testOM.toString()) == expected)

  test("alter - comment"):
    val oldComment  = ObjMeta(comment = Some("An old comment"))
    val newComment  = ObjMeta(comment = Some("A new comment"))
    val newComment2 = ObjMeta(comment = None)

    assert(
      clue(sqls(newComment.alter("SCHEMA", "DB1.SCH1", oldComment))) == List(
        "ALTER SCHEMA IF EXISTS DB1.SCH1 SET COMMENT = 'A new comment'"
      )
    )
    assert(
      clue(sqls(newComment2.alter("SCHEMA", "DB1.SCH1", oldComment))) == List("ALTER SCHEMA IF EXISTS DB1.SCH1 UNSET COMMENT")
    )

  test("alter - props"):
    val oldOM = ObjMeta(Map("STR_PROP" -> Str("STR_VAL"), "NUM_PROP" -> Num(3), "BOOL_PROP" -> Bool(true)))
    val newOM = ObjMeta(Map("STR_PROP" -> Str("STR_VAL2"), "NUM_PROP" -> Num(2)))
    val expected = List(
      "ALTER SCHEMA IF EXISTS DB1.SCH1 SET STR_PROP = STR_VAL2, NUM_PROP = 2",
      "ALTER SCHEMA IF EXISTS DB1.SCH1 UNSET BOOL_PROP"
    )

    assert(clue(sqls(newOM.alter("SCHEMA", "DB1.SCH1", oldOM))) == expected)

  test("alter - props"):
    val oldOM = ObjMeta(Map("STR_PROP" -> Str("STR_VAL"), "NUM_PROP" -> Num(3), "BOOL_PROP" -> Bool(true)))
    val newOM = ObjMeta(Map("STR_PROP" -> Str("STR_VAL2"), "NUM_PROP" -> Num(2)))
    val expected = List(
      "ALTER SCHEMA IF EXISTS DB1.SCH1 SET STR_PROP = STR_VAL2, NUM_PROP = 2",
      "ALTER SCHEMA IF EXISTS DB1.SCH1 UNSET BOOL_PROP"
    )

    assert(clue(sqls(newOM.alter("SCHEMA", "DB1.SCH1", oldOM))) == expected)
