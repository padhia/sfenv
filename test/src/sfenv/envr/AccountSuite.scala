package sfenv
package envr

import fabric.*
import munit.FunSuite

class AccountSuite extends FunSuite:
  test("account creation preserves string quoting and nested rendering"):
    val account = Account(Props("text" -> "identifier", "flag" -> true, "nested" -> obj("count" -> num(2))))
    assertEquals(
      account.create.sqls,
      List(
        "ALTER ACCOUNT SET FLAG = TRUE",
        "ALTER ACCOUNT SET NESTED = (COUNT = 2)",
        "ALTER ACCOUNT SET TEXT = 'identifier'"
      )
    )

  test("account update and removal retain deterministic ordering"):
    val old     = Account(Props("keep" -> 1, "removed" -> false, "changed" -> "old"))
    val current = Account(Props("keep" -> 1, "added" -> true, "changed" -> "new"))
    assertEquals(
      current.update(old).sqls,
      List(
        "ALTER ACCOUNT UNSET REMOVED",
        "ALTER ACCOUNT SET ADDED = TRUE",
        "ALTER ACCOUNT SET CHANGED = 'new'"
      )
    )
    assertEquals(current.update(current).sqls, Nil)
    assertEquals(current.drop.sqls, List("ALTER ACCOUNT UNSET KEEP", "ALTER ACCOUNT UNSET CHANGED", "ALTER ACCOUNT UNSET ADDED"))
