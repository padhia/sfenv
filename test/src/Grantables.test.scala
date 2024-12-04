package sfenv
package envr

import cats.data.Chain

import Grantables.*
import munit.FunSuite

class GrantablesTests extends FunSuite:
  val of      = "TABLE"
  val to      = "DB1.SCH1"
  val grantee = RoleName.Database("DB1", "SCH1_RW")
  val ps      = Privileges(List("SELECT", "INSERT"))
  val rs      = Roles(List(RoleName.Database("DB1", "SCH1_R")))
  val envAdm  = RoleName.Account("ENVADMIN")

  def sqls(xs: Chain[Sql]) = xs.flatMap(_.texts(envAdm, true)).toList

  test("grant - privileges"):
    val expected = List("GRANT SELECT, INSERT ON FUTURE TABLES IN SCHEMA DB1.SCH1 TO DATABASE ROLE DB1.SCH1_RW")
    assert(clue(sqls(ps.grant(of, to, grantee))) == expected)

  test("revoke - privileges"):
    val expected = List("REVOKE SELECT, INSERT ON FUTURE TABLES IN SCHEMA DB1.SCH1 FROM DATABASE ROLE DB1.SCH1_RW")
    assert(clue(sqls(ps.revoke(of, to, grantee))) == expected)

  test("grant - roles"):
    val expected = List("GRANT DATABASE ROLE DB1.SCH1_R TO DATABASE ROLE DB1.SCH1_RW")
    assert(clue(sqls(rs.grant(of, to, grantee))) == expected)

  test("alter - privileges"):
    val ps2 = Privileges(List("SELECT", "UPDATE"))
    val expected = List(
      "GRANT UPDATE ON FUTURE TABLES IN SCHEMA DB1.SCH1 TO DATABASE ROLE DB1.SCH1_RW",
      "REVOKE INSERT ON FUTURE TABLES IN SCHEMA DB1.SCH1 FROM DATABASE ROLE DB1.SCH1_RW"
    )

    assert(clue(sqls(ps2.alter(ps, of, to, grantee))) == expected)

  test("nodiff - privileges"):
    assert(clue(sqls(ps.alter(ps, of, to, grantee))) == List.empty[String])

  test("nodiff - roles"):
    assert(clue(sqls(rs.alter(rs, of, to, grantee))) == List.empty[String])
