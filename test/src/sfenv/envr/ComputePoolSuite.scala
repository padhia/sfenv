package sfenv
package envr

import munit.FunSuite

class ComputePoolSuite extends FunSuite:
  def pool(application: Option[String] = None, props: Props = Props("min_nodes" -> 1, "max_nodes" -> 2)) =
    ComputePool(Ident("CP_DEV_ETL"), application, ObjMeta(props))

  test("create"):
    val expected = List("CREATE COMPUTE POOL IF NOT EXISTS CP_DEV_ETL MAX_NODES = 2 MIN_NODES = 1")
    assertEquals(pool().create.sqls, expected)

  test("create for application"):
    val expected = List(
      """|CREATE COMPUTE POOL IF NOT EXISTS CP_DEV_ETL FOR APPLICATION MY_APP
         |    MAX_NODES = 2
         |    MIN_NODES = 1""".stripMargin
    )
    assertEquals(pool(Some("MY_APP")).create.sqls, expected)

  test("drop"):
    assertEquals(pool().drop.sqls, List("DROP COMPUTE POOL IF EXISTS CP_DEV_ETL"))

  test("alter - prop changed and removed"):
    val old = pool(props = Props("min_nodes" -> 1, "max_nodes" -> 2, "auto_suspend_secs" -> 600))
    val cur = pool(props = Props("min_nodes" -> 1, "max_nodes" -> 3))
    val expected = List(
      "ALTER COMPUTE POOL IF EXISTS CP_DEV_ETL SET MAX_NODES = 3",
      "ALTER COMPUTE POOL IF EXISTS CP_DEV_ETL UNSET AUTO_SUSPEND_SECS"
    )
    assertEquals(cur.update(old).sqls, expected)

  test("alter - same application is updatable"):
    val app = Some("MY_APP")
    assert(pool(app).updatable(pool(app)))
    assertEquals(
      pool(app, Props("min_nodes" -> 1, "max_nodes" -> 3)).update(pool(app)).sqls,
      List("ALTER COMPUTE POOL IF EXISTS CP_DEV_ETL SET MAX_NODES = 3")
    )

  test("alter - application change is not updatable"):
    assert(!pool(Some("MY_APP")).updatable(pool()))
    assert(!pool().updatable(pool(Some("MY_APP"))))
    assert(!pool(Some("APP_B")).updatable(pool(Some("APP_A"))))
