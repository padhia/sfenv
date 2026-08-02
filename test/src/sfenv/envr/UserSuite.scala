package sfenv
package envr

import munit.FunSuite

class UserSuite extends FunSuite:
  val user = User(
    Ident("jdoe"),
    User.Value(meta =
      ObjMeta(
        Props(
          "default_role"            -> "RL_DEV_DBA",
          "default_warehouse"       -> "WH_DEV_LOAD",
          "default_namespace"       -> (Ident("EDW_DEV"), Ident("CUSTOMER")),
          "default_secondary_roles" -> "('ALL')",
          "comment"                 -> "John Doe"
        )
      )
    )
  )

  test("create"):
    val expected = List(
      """|CREATE USER IF NOT EXISTS JDOE
         |    COMMENT = 'John Doe'
         |    DEFAULT_NAMESPACE = EDW_DEV.CUSTOMER
         |    DEFAULT_ROLE = RL_DEV_DBA
         |    DEFAULT_SECONDARY_ROLES = ('ALL')
         |    DEFAULT_WAREHOUSE = WH_DEV_LOAD""".stripMargin,
    )
    assertEquals(user.create.sqls, expected)

  test("skip create"):
    val user2 = user.copy(value = user.value.copy(createObj = false))
    assertEquals(user2.create.sqls.length, 0)

  test("drop"):
    assertEquals(user.drop.sqls, List("DROP USER IF EXISTS JDOE"))

  test("alter"):
    val user2 = user.copy(value =
      user.value.copy(meta =
        ObjMeta(
          Props(
            "default_role"            -> "RL_DEV_DBA",
            "default_warehouse"       -> "WH_DEV_LOAD",
            "default_namespace"       -> (Ident("EDW_DEV"), Ident("CUSTOMER")),
            "default_secondary_roles" -> "()",
          )
        )
      )
    )
    val expected = List(
      "ALTER USER IF EXISTS JDOE SET DEFAULT_SECONDARY_ROLES = ()",
      "ALTER USER IF EXISTS JDOE UNSET COMMENT"
    )
    assertEquals(user2.update(user).sqls, expected)
