package sfenv
package envr

import scala.collection.immutable.SortedSet

import munit.FunSuite

class RoleSuite extends FunSuite:
  val role = Role("RL_DEVELOPER_DEV", List("DB_DEV.SCH_R", "_WH_DEV"))

  private def withSysRoles(createObj: Boolean): Role =
    val base = role.getOrElse(fail("role construction failed"))
    base.copy(
      sysRoles =
        SortedSet[SysRole](SysRole.Account(Ident("SHARED_READER")), SysRole.Database(Ident("SHARED_DB"), Ident("READER"))),
      createObj = createObj
    )

  List(false, true).foreach: createObj =>
    test(s"sys_roles grants and revokes are independent of create=$createObj"):
      val configured = withSysRoles(createObj)
      val created    = configured.create.sqls
      val dropped    = configured.drop.sqls
      val grants     = List(
        "GRANT DATABASE ROLE SHARED_DB.READER TO ROLE RL_DEVELOPER_DEV",
        "GRANT ROLE SHARED_READER TO ROLE RL_DEVELOPER_DEV"
      )
      val revokes = List(
        "REVOKE ROLE SHARED_READER FROM ROLE RL_DEVELOPER_DEV",
        "REVOKE DATABASE ROLE SHARED_DB.READER FROM ROLE RL_DEVELOPER_DEV"
      )
      val base        = configured.copy(sysRoles = SortedSet.empty)
      val baseCreated = base.create.sqls
      val baseDropped = base.drop.sqls
      val prefixSize  = if createObj then 2 else 0
      assertEquals(created, baseCreated.take(prefixSize) ++ grants ++ baseCreated.drop(prefixSize))
      val suffixSize = if createObj then 2 else 0
      assertEquals(dropped, baseDropped.dropRight(suffixSize) ++ revokes ++ baseDropped.takeRight(suffixSize))
      assertEquals(created.exists(_.startsWith("CREATE ROLE")), createObj)
      assertEquals(dropped.exists(_.startsWith("DROP ROLE")), createObj)

    test(s"sys_roles additions and removals work with create=$createObj"):
      val previous = withSysRoles(createObj)
      val current  = previous.copy(sysRoles = SortedSet[SysRole](SysRole.Account(Ident("NEW_READER"))))
      assertEquals(
        current.update(previous).sqls,
        List(
          "REVOKE ROLE SHARED_READER FROM ROLE RL_DEVELOPER_DEV",
          "REVOKE DATABASE ROLE SHARED_DB.READER FROM ROLE RL_DEVELOPER_DEV",
          "GRANT ROLE NEW_READER TO ROLE RL_DEVELOPER_DEV"
        )
      )
      assertEquals(current.update(current).sqls, Nil)

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
