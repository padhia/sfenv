package sfenv
package envr
package test

import munit.FunSuite
import sfenv.Main.toRbac

class DatabaseTests extends FunSuite:
  import DatabaseTests.*

  test("create"):
    val ddl = rule1
      .toRbac("DEV")
      .map: rbac =>
        given SqlObj[Database] = Database.sqlObj(rbac.secAdm)
        rbac.databases(0).create.flatMap(_.texts(rbac.sysAdm)).toList

    val expected = List(
      "CREATE DATABASE IF NOT EXISTS EDW_DEV DATA_RETENTION_TIME_IN_DAYS = 10 COMMENT = 'EDW core database'",
      "GRANT CREATE DATABASE ROLE, USAGE ON DATABASE EDW_DEV TO ROLE RL_DEV_SECADMIN"
    )
    assert(clue(ddl) == Right(expected))

  test("drop"):
    val ddl = rule1
      .toRbac("DEV")
      .map: rbac =>
        given SqlObj[Database] = Database.sqlObj(rbac.secAdm)
        rbac.databases(0).unCreate.flatMap(_.texts(rbac.sysAdm)).toList

    val expected = List("--DROP DATABASE IF EXISTS EDW_DEV")
    assert(clue(ddl) == Right(expected))

  test("alter"):
    val ddl =
      for
        curr <- rule2.toRbac("DEV")
        prev <- rule1.toRbac("DEV")
      yield {
        val currDb             = curr.databases(0)
        val prevDb             = prev.databases(0)
        given SqlObj[Database] = Database.sqlObj(curr.secAdm)
        currDb.alter(prevDb).flatMap(_.texts(curr.sysAdm)).toList
      }
    val expected = List(
      "ALTER DATABASE IF EXISTS EDW_DEV SET COMMENT = 'EDW core database2'",
      "ALTER DATABASE IF EXISTS EDW_DEV UNSET DATA_RETENTION_TIME_IN_DAYS"
    )
    assert(clue(ddl) == Right(expected))

object DatabaseTests:
  val rule1 =
    s"""|$config
        |databases:
        |  EDW:
        |    data_retention_time_in_days: 10
        |    comment: EDW core database
        |""".stripMargin

  val rule2 =
    s"""|$config
        |databases:
        |  EDW:
        |    comment: EDW core database2
        |""".stripMargin
