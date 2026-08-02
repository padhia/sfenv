package sfenv
package envr

import scala.collection.immutable.SortedMap

import munit.FunSuite

class SchemaSuite extends FunSuite:
  def testSch =
    val sch = Schema(
      db = "DEV_DB",
      name = "SCH",
      transient = true,
      managed = true,
      meta = ObjMeta(Props("data_retention_time_in_days" -> 10)),
      accRoles = SortedMap(
        "R" -> SortedMap(
          "database" -> List("usage"),
          "schema"   -> List("usage"),
          "table"    -> List("select")
        ),
        "RW" -> SortedMap(
          "role"  -> List("R"),
          "table" -> List("insert", "update", "truncate", "delete")
        )
      )
    )
    sch match
      case Right(x) => x
      case Left(_)  => fail("testSch creation failed")

  test("create - basic"):
    val actual = Schema("DEV_DB", "SCH").getOrElse(fail("schema construction failed")).create.sqls
    assertEquals(actual, List("CREATE SCHEMA IF NOT EXISTS DEV_DB.SCH"))

  test("create - acc-roles"):
    val expected = List(
      """|CREATE TRANSIENT SCHEMA IF NOT EXISTS DEV_DB.SCH WITH MANAGED ACCESS
         |    DATA_RETENTION_TIME_IN_DAYS = 10""".stripMargin,
      "CREATE DATABASE ROLE IF NOT EXISTS DEV_DB.SCH_R",
      "GRANT DATABASE ROLE DEV_DB.SCH_R TO ROLE DEV_SYSADM",
      "GRANT USAGE ON DATABASE DEV_DB TO DATABASE ROLE DEV_DB.SCH_R",
      "GRANT USAGE ON SCHEMA DEV_DB.SCH TO DATABASE ROLE DEV_DB.SCH_R",
      "GRANT SELECT ON FUTURE TABLES IN SCHEMA DEV_DB.SCH TO DATABASE ROLE DEV_DB.SCH_R",
      "GRANT SELECT ON ALL TABLES IN SCHEMA DEV_DB.SCH TO DATABASE ROLE DEV_DB.SCH_R",
      "CREATE DATABASE ROLE IF NOT EXISTS DEV_DB.SCH_RW",
      "GRANT DATABASE ROLE DEV_DB.SCH_RW TO ROLE DEV_SYSADM",
      "GRANT DATABASE ROLE DEV_DB.SCH_R TO DATABASE ROLE DEV_DB.SCH_RW",
      "GRANT DELETE, INSERT, TRUNCATE, UPDATE ON FUTURE TABLES IN SCHEMA DEV_DB.SCH TO DATABASE ROLE DEV_DB.SCH_RW",
      "GRANT DELETE, INSERT, TRUNCATE, UPDATE ON ALL TABLES IN SCHEMA DEV_DB.SCH TO DATABASE ROLE DEV_DB.SCH_RW"
    )
    assertEquals(testSch.create.sqls, expected)

  test("drop"):
    val expected = List(
      "REVOKE DELETE, INSERT, TRUNCATE, UPDATE ON ALL TABLES IN SCHEMA DEV_DB.SCH FROM DATABASE ROLE DEV_DB.SCH_RW",
      "REVOKE DELETE, INSERT, TRUNCATE, UPDATE ON FUTURE TABLES IN SCHEMA DEV_DB.SCH FROM DATABASE ROLE DEV_DB.SCH_RW",
      "REVOKE DATABASE ROLE DEV_DB.SCH_R FROM DATABASE ROLE DEV_DB.SCH_RW",
      "REVOKE DATABASE ROLE DEV_DB.SCH_RW FROM ROLE DEV_SYSADM",
      "DROP DATABASE ROLE IF EXISTS DEV_DB.SCH_RW",
      "REVOKE SELECT ON ALL TABLES IN SCHEMA DEV_DB.SCH FROM DATABASE ROLE DEV_DB.SCH_R",
      "REVOKE SELECT ON FUTURE TABLES IN SCHEMA DEV_DB.SCH FROM DATABASE ROLE DEV_DB.SCH_R",
      "REVOKE USAGE ON SCHEMA DEV_DB.SCH FROM DATABASE ROLE DEV_DB.SCH_R",
      "REVOKE USAGE ON DATABASE DEV_DB FROM DATABASE ROLE DEV_DB.SCH_R",
      "REVOKE DATABASE ROLE DEV_DB.SCH_R FROM ROLE DEV_SYSADM",
      "DROP DATABASE ROLE IF EXISTS DEV_DB.SCH_R",
      "DROP SCHEMA IF EXISTS DEV_DB.SCH"
    )
    assertEquals(testSch.drop.sqls, expected)

  test("alter"):
    val testSch2 =
      testSch.copy(value = testSch.value.copy(managed = false, meta = ObjMeta(Props("data_retention_time_in_days" -> 20))))
    val expected = List(
      "ALTER SCHEMA IF EXISTS DEV_DB.SCH DISABLE MANAGED ACCESS",
      "ALTER SCHEMA IF EXISTS DEV_DB.SCH SET DATA_RETENTION_TIME_IN_DAYS = 20"
    )
    assertEquals(testSch2.update(testSch).sqls, expected)
