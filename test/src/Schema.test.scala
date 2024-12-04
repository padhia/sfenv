package sfenv
package envr
package test

import munit.FunSuite
import sfenv.Main.toRbac

class SchemaTests extends FunSuite:
  import SchemaTests.*

  test("create"):
    val ddl = rule1
      .toRbac("DEV")
      .map: rbac =>
        val db               = rbac.databases(0)
        given SqlObj[Schema] = Schema.sqlObj(db.name)
        db.schemas(0).create.flatMap(_.texts(rbac.sysAdm)).toList

    val expected = List(
      "CREATE SCHEMA IF NOT EXISTS EDW_DEV.CUSTOMER WITH MANAGED ACCESS DATA_RETENTION_TIME_IN_DAYS = 10",
      "CREATE DATABASE ROLE IF NOT EXISTS EDW_DEV.CUSTOMER_R",
      "GRANT DATABASE ROLE EDW_DEV.CUSTOMER_R TO ROLE RL_DEV_SYSADMIN",
      "GRANT USAGE ON DATABASE EDW_DEV TO DATABASE ROLE EDW_DEV.CUSTOMER_R",
      "GRANT USAGE ON SCHEMA EDW_DEV.CUSTOMER TO DATABASE ROLE EDW_DEV.CUSTOMER_R",
      "GRANT SELECT ON FUTURE TABLES IN SCHEMA EDW_DEV.CUSTOMER TO DATABASE ROLE EDW_DEV.CUSTOMER_R",
      "GRANT SELECT ON ALL TABLES IN SCHEMA EDW_DEV.CUSTOMER TO DATABASE ROLE EDW_DEV.CUSTOMER_R",
      "CREATE DATABASE ROLE IF NOT EXISTS EDW_DEV.CUSTOMER_RW",
      "GRANT DATABASE ROLE EDW_DEV.CUSTOMER_RW TO ROLE RL_DEV_SYSADMIN",
      "GRANT DATABASE ROLE EDW_DEV.CUSTOMER_R TO DATABASE ROLE EDW_DEV.CUSTOMER_RW",
      "GRANT INSERT, UPDATE, TRUNCATE, DELETE ON FUTURE TABLES IN SCHEMA EDW_DEV.CUSTOMER TO DATABASE ROLE EDW_DEV.CUSTOMER_RW",
      "GRANT INSERT, UPDATE, TRUNCATE, DELETE ON ALL TABLES IN SCHEMA EDW_DEV.CUSTOMER TO DATABASE ROLE EDW_DEV.CUSTOMER_RW"
    )

    assert(clue(ddl) == Right(expected))

  test("drop"):
    val ddl = rule1
      .toRbac("DEV")
      .map: rbac =>
        val db               = rbac.databases(0)
        given SqlObj[Schema] = Schema.sqlObj(db.name)
        db.schemas(0).unCreate.flatMap(_.texts(rbac.sysAdm)).toList

    val expected = List(
      "DROP DATABASE ROLE IF EXISTS EDW_DEV.CUSTOMER_R",
      "DROP DATABASE ROLE IF EXISTS EDW_DEV.CUSTOMER_RW",
      "--DROP SCHEMA IF EXISTS EDW_DEV.CUSTOMER"
    )

    assert(clue(ddl) == Right(expected))

  test("alter"):
    val ddl =
      for
        curr <- rule2.toRbac("DEV")
        prev <- rule1.toRbac("DEV")
      yield {
        val db               = curr.databases(0)
        val currSch          = curr.databases(0).schemas(0)
        val prevSch          = prev.databases(0).schemas(0)
        given SqlObj[Schema] = Schema.sqlObj(db.name)
        currSch.alter(prevSch).flatMap(_.texts(curr.sysAdm)).toList
      }
    val expected = List(
      "ALTER SCHEMA IF EXISTS EDW_DEV.CUSTOMER DISABLE MANAGED ACCESS",
      "ALTER SCHEMA IF EXISTS EDW_DEV.CUSTOMER SET DATA_RETENTION_TIME_IN_DAYS = 20"
    )
    assert(clue(ddl) == Right(expected))

object SchemaTests:
  val rule1 =
    s"""|$config
        |databases:
        |  EDW:
        |    data_retention_time_in_days: 10
        |    comment: EDW core database
        |    tags:
        |      DEPT: payroll
        |      SHIFT: day
        |    schemas:
        |      CUSTOMER:
        |        managed: true
        |        data_retention_time_in_days: 10
        |        acc_roles:
        |          R:
        |            database: [usage]
        |            schema: [usage]
        |            table: [select]
        |          RW:
        |            role: [R]
        |            table: [insert, update, truncate, delete]
        |""".stripMargin

  val rule2 =
    s"""|$config
        |databases:
        |  EDW:
        |    data_retention_time_in_days: 20
        |    comment: EDW core database
        |    tags:
        |      DEPT: payroll
        |      SHIFT: day
        |    schemas:
        |      CUSTOMER:
        |        managed: false
        |        data_retention_time_in_days: 20
        |        acc_roles:
        |          R:
        |            database: [usage]
        |            schema: [usage]
        |            table: [select]
        |          RW:
        |            role: [R]
        |            table: [insert, update, truncate, delete]
        |""".stripMargin
