package sfenv
package envr

import scala.collection.immutable.SortedMap

import munit.FunSuite

class WarehouseSuite extends FunSuite:
  val wh = Warehouse(
    Ident("WH_DEV_LOAD"),
    Warehouse.Value(
      meta = ObjMeta(Props("warehouse_size" -> "SMALL", "auto_suspend" -> 300)),
      accRoleMap = SortedMap.empty
    )
  )

  test("create"):
    val expected = List(
      """|CREATE WAREHOUSE IF NOT EXISTS WH_DEV_LOAD
         |    AUTO_SUSPEND = 300
         |    WAREHOUSE_SIZE = SMALL""".stripMargin
    )
    assertEquals(wh.create.sqls, expected)

  test("drop"):
    assertEquals(wh.drop.sqls, List("DROP WAREHOUSE IF EXISTS WH_DEV_LOAD"))

  test("alter - prop changed and removed"):
    val wh2 = Warehouse(
      Ident("WH_DEV_LOAD"),
      Warehouse.Value(
        meta = ObjMeta(Props("warehouse_size" -> "MEDIUM")),
        accRoleMap = SortedMap.empty
      )
    )
    val expected = List(
      "ALTER WAREHOUSE IF EXISTS WH_DEV_LOAD SET WAREHOUSE_SIZE = MEDIUM",
      "ALTER WAREHOUSE IF EXISTS WH_DEV_LOAD UNSET AUTO_SUSPEND"
    )
    assertEquals(wh2.update(wh).sqls, expected)
