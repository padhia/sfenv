package sfenv
package envr

import munit.FunSuite

class ImportSuite extends FunSuite:
  val imp = Import(
    Ident("CUST_DEV"),
    Import.Value(Ident("CUSTP"), Ident("DATA_SHR"), List(Ident("DBA"), Ident("DEVELOPER")))
  )

  test("create"):
    val expected = List(
      "CREATE DATABASE IF NOT EXISTS CUST_DEV FROM SHARE CUSTP.DATA_SHR",
      "GRANT IMPORTED PRIVILEGES ON DATABASE CUST_DEV TO ROLE DBA",
      "GRANT IMPORTED PRIVILEGES ON DATABASE CUST_DEV TO ROLE DEVELOPER"
    )
    assertEquals(imp.create.sqls, expected)

  test("drop"):
    val expected = List(
      "REVOKE IMPORTED PRIVILEGES ON DATABASE CUST_DEV FROM ROLE DEVELOPER",
      "REVOKE IMPORTED PRIVILEGES ON DATABASE CUST_DEV FROM ROLE DBA",
      "DROP DATABASE IF EXISTS CUST_DEV"
    )
    assertEquals(imp.drop.sqls, expected)

  test("update - role added and removed"):
    val imp2     = Import(Ident("CUST_DEV"), Import.Value(Ident("CUSTP"), Ident("DATA_SHR"), List(Ident("DBA"), Ident("ETL"))))
    val expected = List(
      "REVOKE IMPORTED PRIVILEGES ON DATABASE CUST_DEV FROM ROLE DEVELOPER",
      "GRANT IMPORTED PRIVILEGES ON DATABASE CUST_DEV TO ROLE ETL"
    )
    assertEquals(imp2.update(imp).sqls, expected)
