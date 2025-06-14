package sfenv
package envr
package test

import munit.FunSuite

class DatabaseTests extends FunSuite:
  test("create - basic"):
    val actual   = Database("EDW_DEV").create.sqls(0)
    val expected = "CREATE DATABASE IF NOT EXISTS EDW_DEV"
    assert(clue(actual) == clue(expected))

  test("create - grant"):
    val sqls = Database("EDW_DEV").create.sqls
    assert(sqls.length == 2)
    assert(clue(sqls(1)) == clue("GRANT USAGE, CREATE DATABASE ROLE ON DATABASE EDW_DEV TO ROLE DEV_SECADM"))

  test("create - options"):
    val actual = Database(
      "EDW_DEV",
      transient = true,
      tags = Tags("TAG1" -> "TAG1 VALUE", "TAG2" -> "TAG2 VALUE"),
      comment = Some("EDW Core database"),
      props = Props("data_retention_time_in_days" -> 10)
    ).create.sqls(0)
    val expected = """|CREATE TRANSIENT DATABASE IF NOT EXISTS EDW_DEV
                      |    DATA_RETENTION_TIME_IN_DAYS = 10
                      |    COMMENT = 'EDW Core database'
                      |    WITH TAG TAG1 = 'TAG1 VALUE', TAG2 = 'TAG2 VALUE'""".stripMargin
    assert(clue(actual) == clue(expected))

  test("drop"):
    val actual   = Database("EDW_DEV").drop.sqls
    val expected =
      List("REVOKE USAGE, CREATE DATABASE ROLE ON DATABASE EDW_DEV FROM ROLE DEV_SECADM", "DROP DATABASE IF EXISTS EDW_DEV")
    assert(clue(actual) == clue(expected))

  test("alter"):
    val db1 = Database("EDW_DEV", comment = Some("EDW core database"), props = Props("data_retention_time_in_days" -> 10))
    val db2 = Database("EDW_DEV", comment = Some("EDW core database2"))

    val actual   = db2.update(db1).sqls
    val expected = List(
      "ALTER DATABASE IF EXISTS EDW_DEV SET COMMENT = 'EDW core database2'",
      "ALTER DATABASE IF EXISTS EDW_DEV UNSET DATA_RETENTION_TIME_IN_DAYS"
    )
    assert(clue(actual) == clue(expected))
