package sfenv
package envr

import munit.FunSuite

class RoleSuite extends FunSuite:
  val role = Role("RL_DEVELOPER_DEV", List("DB_DEV.SCH_R", "_WH_DEV"))

  test("create"):
    val r        = role.getOrElse(fail("role construction failed"))
    val expected = List(
      "CREATE ROLE IF NOT EXISTS RL_DEVELOPER_DEV",
      "GRANT ROLE RL_DEVELOPER_DEV TO ROLE DEV_SYSADM",
      "GRANT USAGE ON DATABASE DB_DEV TO ROLE RL_DEVELOPER_DEV",
      "GRANT USAGE ON SCHEMA DB_DEV.SCH TO ROLE RL_DEVELOPER_DEV",
      "GRANT DATABASE ROLE DB_DEV.SCH_R TO ROLE RL_DEVELOPER_DEV",
      "GRANT ROLE _WH_DEV TO ROLE RL_DEVELOPER_DEV",
    )
    assertEquals(r.create.sqls, expected)

  test("drop"):
    val r        = role.getOrElse(fail("role construction failed"))
    val expected = List(
      "REVOKE USAGE ON DATABASE DB_DEV FROM ROLE RL_DEVELOPER_DEV",
      "REVOKE USAGE ON SCHEMA DB_DEV.SCH FROM ROLE RL_DEVELOPER_DEV",
      "REVOKE DATABASE ROLE DB_DEV.SCH_R FROM ROLE RL_DEVELOPER_DEV",
      "REVOKE ROLE _WH_DEV FROM ROLE RL_DEVELOPER_DEV",
      "REVOKE ROLE RL_DEVELOPER_DEV FROM ROLE DEV_SYSADM",
      "DROP ROLE IF EXISTS RL_DEVELOPER_DEV",
    )
    assertEquals(r.drop.sqls, expected)

  test("update - revoke removed roles and grant added roles"):
    val r1       = role.getOrElse(fail("role1 construction failed"))
    val r2       = Role("RL_DEVELOPER_DEV", List("DB_DEV.SCH_R", "DB_DEV.SCH_RW")).getOrElse(fail("role2 construction failed"))
    val expected = List(
      "REVOKE ROLE _WH_DEV FROM ROLE RL_DEVELOPER_DEV",
      "GRANT USAGE ON DATABASE DB_DEV TO ROLE RL_DEVELOPER_DEV",
      "GRANT USAGE ON SCHEMA DB_DEV.SCH TO ROLE RL_DEVELOPER_DEV",
      "GRANT DATABASE ROLE DB_DEV.SCH_RW TO ROLE RL_DEVELOPER_DEV",
    )
    assertEquals(r2.update(r1).sqls, expected)
