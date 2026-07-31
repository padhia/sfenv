package sfenv
package envr

import cats.data.Chain

import munit.FunSuite

class ObjMetaSuite extends FunSuite:
  val testProps = Props("STR_PROP" -> "STR_VAL", "NUM_PROP" -> 2, "BOOL_PROP" -> true)
  val comment   = Some(SqlLiteral("A sample comment"))
  val tags      = Tags("tag1" -> "tag value 1", "tag2" -> "tag value 2")

  test("toString - props"):
    assertEquals(ObjMeta(props = testProps).sql(""), " STR_PROP = STR_VAL NUM_PROP = 2 BOOL_PROP = TRUE")

  test("toString - long"):
    val expected = """|
                      |    STR_PROP = STR_VAL
                      |    NUM_PROP = 2
                      |    BOOL_PROP = TRUE
                      |    COMMENT = 'A sample comment'
                      |    WITH TAG TAG1 = 'tag value 1', TAG2 = 'tag value 2'""".stripMargin
    assertEquals(ObjMeta(testProps, Some(tags), comment).sql(""), expected)

  test("alter - comment"):
    val om1 = ObjMeta(comment = Some(SqlLiteral("An old comment")))
    val om2 = ObjMeta(comment = Some(SqlLiteral("A new comment")))
    val om3 = ObjMeta(comment = None)
    assertEquals(om2.sql("", om1), Chain(" SET COMMENT = 'A new comment'"))
    assertEquals(om3.sql("", om1), Chain(" UNSET COMMENT"))

  test("alter - props"):
    val oldOM = ObjMeta(Props("STR_PROP" -> "STR_VAL", "NUM_PROP" -> 3, "BOOL_PROP" -> true))
    val newOM = ObjMeta(Props("STR_PROP" -> "STR_VAL2", "NUM_PROP" -> 2))
    assertEquals(newOM.sql("", oldOM), Chain(" SET STR_PROP = STR_VAL2 NUM_PROP = 2", " UNSET BOOL_PROP"))

  test("alter - tags added, changed, and removed"):
    val om1 = ObjMeta(tags = Some(Tags("old_tag" -> "old value", "shared_tag" -> "original")))
    val om2 = ObjMeta(tags = Some(Tags("shared_tag" -> "updated", "new_tag" -> "added")))
    assertEquals(
      om2.sql("ALTER SCHEMA", om1),
      Chain("ALTER SCHEMA SET TAG SHARED_TAG = 'updated' NEW_TAG = 'added'", "ALTER SCHEMA UNSET TAG OLD_TAG")
    )
