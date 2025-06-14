package sfenv
package envr
package test

import munit.FunSuite

class UserTests extends FunSuite:
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
         |    DEFAULT_ROLE = RL_DEV_DBA
         |    DEFAULT_WAREHOUSE = WH_DEV_LOAD
         |    DEFAULT_NAMESPACE = EDW_DEV.CUSTOMER
         |    DEFAULT_SECONDARY_ROLES = ('ALL')
         |    COMMENT = 'John Doe'""".stripMargin,
    )

    assert(clue(user.create.sqls.length) == clue(1))
    assert(clue(user.create.sqls) == clue(expected))

  test("skip create"):
    val user2 = user.copy(value = user.value.copy(createObj = false))

    assert(clue(user2.create.sqls.length) == clue(0))

  test("drop"):
    val expected = List("DROP USER IF EXISTS JDOE")
    assert(clue(user.drop.sqls.length) == clue(1))
    assert(clue(user.drop.sqls) == clue(expected))

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
    val actual = user2.update(user).sqls
    assert(clue(actual) == clue(expected))
