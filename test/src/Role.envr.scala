package sfenv
package envr
package test

import munit.FunSuite

class RoleTests extends FunSuite:
  val role = Role("RL_DEVELOPER_DEV", List("DB_DEV.SCH_R", "_WH_DEV"))

  test("create"):
    val expected = List(
      "CREATE ROLE IF NOT EXISTS RL_DEVELOPER_DEV",
      "GRANT ROLE RL_DEVELOPER_DEV TO ROLE DEV_SYSADM",
      "GRANT DATABASE ROLE DB_DEV.SCH_R TO ROLE RL_DEVELOPER_DEV",
      "GRANT ROLE _WH_DEV TO ROLE RL_DEVELOPER_DEV",
    )

    assert(role.isRight)
    role.map: r =>
      assert(clue(r.create.sqls) == clue(expected))

  test("drop"):
    val expected = List(
      "REVOKE DATABASE ROLE DB_DEV.SCH_R FROM ROLE RL_DEVELOPER_DEV",
      "REVOKE ROLE _WH_DEV FROM ROLE RL_DEVELOPER_DEV",
      "REVOKE ROLE RL_DEVELOPER_DEV FROM ROLE DEV_SYSADM",
      "DROP ROLE IF EXISTS RL_DEVELOPER_DEV",
    )

    assert(role.isRight)
    role.map: r =>
      assert(clue(r.drop.sqls) == clue(expected))
