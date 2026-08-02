package sfenv
package envr

import munit.FunSuite

class DatabaseSuite extends FunSuite:
  test("create - basic"):
    assertEquals(Database("EDW_DEV").create.sqls(0), "CREATE DATABASE IF NOT EXISTS EDW_DEV")

  test("create - grant"):
    val sqls = Database("EDW_DEV").create.sqls
    assertEquals(sqls.length, 2)
    assertEquals(sqls(1), "GRANT USAGE, CREATE DATABASE ROLE ON DATABASE EDW_DEV TO ROLE DEV_SECADM")

  test("create - options"):
    val actual = Database(
      "EDW_DEV",
      transient = true,
      tags      = Tags("TAG1" -> "TAG1 VALUE", "TAG2" -> "TAG2 VALUE"),
      comment   = Some("EDW Core database"),
      props     = Props("data_retention_time_in_days" -> 10)
    ).create.sqls(0)
    val expected = """|CREATE TRANSIENT DATABASE IF NOT EXISTS EDW_DEV
                      |    COMMENT = 'EDW Core database'
                      |    DATA_RETENTION_TIME_IN_DAYS = 10
                      |    WITH TAG TAG1 = 'TAG1 VALUE', TAG2 = 'TAG2 VALUE'""".stripMargin
    assertEquals(actual, expected)

  test("drop"):
    val expected = List(
      "REVOKE USAGE, CREATE DATABASE ROLE ON DATABASE EDW_DEV FROM ROLE DEV_SECADM",
      "DROP DATABASE IF EXISTS EDW_DEV"
    )
    assertEquals(Database("EDW_DEV").drop.sqls, expected)

  test("alter"):
    val db1    = Database("EDW_DEV", comment = Some("EDW core database"), props = Props("data_retention_time_in_days" -> 10))
    val db2    = Database("EDW_DEV", comment = Some("EDW core database2"))
    val expected = List(
      "ALTER DATABASE IF EXISTS EDW_DEV SET COMMENT = 'EDW core database2'",
      "ALTER DATABASE IF EXISTS EDW_DEV UNSET DATA_RETENTION_TIME_IN_DAYS"
    )
    assertEquals(db2.update(db1).sqls, expected)
