package sfenv
package envr
package test

import munit.FunSuite
import sfenv.Main.toRbac

class UserTests extends FunSuite:
  val users =
    """|users:
       |  JDOE:
       |    default_role: DBA
       |    default_warehouse: LOAD
       |    default_namespace: EDW.CUSTOMER
       |    default_secondary_roles: ('ALL')
       |    comment: John Doe
       |""".stripMargin

  test("create user"):
    val actual =
      s"""|$config
          |$users
          |""".stripMargin
        .toRbac("DEV")
        .map(rbac => rbac.users(0).create.flatMap(_.texts(rbac.sysAdm)).toList.mkString(""))

    val expected =
      """|CREATE USER IF NOT EXISTS JDOE
         |    DEFAULT_WAREHOUSE = WH_DEV_LOAD
         |    DEFAULT_NAMESPACE = EDW_DEV.CUSTOMER
         |    DEFAULT_ROLE = RL_DEV_DBA
         |    DEFAULT_SECONDARY_ROLES = ('ALL')
         |    COMMENT = 'John Doe'""".stripMargin

    assert(clue(actual) == clue(Right(expected)))

  test("skip creating users"):
    val actual =
      s"""|$config
          |$users
          |    create: false
          |""".stripMargin
        .toRbac("DEV")
        .map(_.genSqls(None).toList.mkString)

    assert(clue(actual) == clue(Right("")))
