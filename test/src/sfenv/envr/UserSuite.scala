package sfenv
package envr

import scala.collection.immutable.SortedSet

import munit.FunSuite

class UserSuite extends FunSuite:
  val user = User(
    Ident("jdoe"),
    meta = ObjMeta(
      Props(
        "default_role"            -> "RL_DEV_DBA",
        "default_warehouse"       -> "WH_DEV_LOAD",
        "default_secondary_roles" -> "('ALL')",
        "comment"                 -> "John Doe"
      )
    ),
    defaultNamespace = Some("EDW_DEV.CUSTOMER")
  )

  test("create"):
    val expected = List(
      """|CREATE USER IF NOT EXISTS JDOE
         |    DEFAULT_NAMESPACE = EDW_DEV.CUSTOMER
         |    COMMENT = 'John Doe'
         |    DEFAULT_ROLE = RL_DEV_DBA
         |    DEFAULT_SECONDARY_ROLES = ('ALL')
         |    DEFAULT_WAREHOUSE = WH_DEV_LOAD""".stripMargin
    )
    assertEquals(user.create.sqls, expected)

  test("skip create"):
    val existing = user.copy(createObj = false)
    assertEquals(existing.create.sqls, Nil)
    assertEquals(existing.drop.sqls, Nil)

  test("drop removes the user without a namespace cleanup statement"):
    assertEquals(user.drop.sqls, List("DROP USER IF EXISTS JDOE"))

  test("alter unrelated properties leaves namespace unchanged"):
    val changed = user.copy(meta =
      ObjMeta(
        Props(
          "default_role"            -> "RL_DEV_DBA",
          "default_warehouse"       -> "WH_DEV_LOAD",
          "default_secondary_roles" -> "()"
        )
      )
    )
    assertEquals(
      changed.update(user).sqls,
      List(
        "ALTER USER IF EXISTS JDOE SET DEFAULT_SECONDARY_ROLES = ()",
        "ALTER USER IF EXISTS JDOE UNSET COMMENT"
      )
    )

  test("namespace addition change removal and no change"):
    val absent  = user.copy(defaultNamespace = None)
    val changed = user.copy(defaultNamespace = Some("OTHER.PUBLIC"))
    assertEquals(user.update(absent).sqls, List("ALTER USER IF EXISTS JDOE SET DEFAULT_NAMESPACE = EDW_DEV.CUSTOMER"))
    assertEquals(changed.update(user).sqls, List("ALTER USER IF EXISTS JDOE SET DEFAULT_NAMESPACE = OTHER.PUBLIC"))
    assertEquals(absent.update(user).sqls, List("ALTER USER IF EXISTS JDOE UNSET DEFAULT_NAMESPACE"))
    assertEquals(user.update(user).sqls, Nil)
    assertEquals(absent.update(absent).sqls, Nil)

  test("sorted-set diff detects namespace-only changes"):
    val changed = user.copy(defaultNamespace = Some("OTHER"))
    assertEquals(
      summon[CDA[SortedSet[User]]].update(SortedSet(changed))(SortedSet(user)).sqls,
      List("ALTER USER IF EXISTS JDOE SET DEFAULT_NAMESPACE = OTHER")
    )

  test("namespace updates apply to externally managed users"):
    val existing = user.copy(createObj = false)
    val removed  = existing.copy(defaultNamespace = None)
    assertEquals(removed.update(existing).sqls, List("ALTER USER IF EXISTS JDOE UNSET DEFAULT_NAMESPACE"))

  test("create without namespace and with database-only namespace"):
    val plain = User(Ident("plain"))
    assertEquals(plain.create.sqls, List("CREATE USER IF NOT EXISTS PLAIN"))
    val databaseOnly = plain.copy(defaultNamespace = Some("EDW_DEV"))
    assertEquals(databaseOnly.create.sqls, List("CREATE USER IF NOT EXISTS PLAIN DEFAULT_NAMESPACE = EDW_DEV"))
