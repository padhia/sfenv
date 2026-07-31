package sfenv
package rules

import munit.FunSuite

class NameResolverSuite extends FunSuite:
  val nr = Config.Resolver(
    env       = "DEV",
    secadm    = "RL_{env}_SECADMIN",
    dbadm     = "RL_{env}_SYSADMIN",
    database  = "{db}_{env}",
    schema    = "{sch}",
    warehouse = "WH_{env}_{wh}",
    acc_role  = "{sch}_{acc}",
    wacc_role = "_WH_{env}_{wh}_{acc}",
    fn_role   = "RL_{env}_{role}",
    app_id    = "APP_{env}_{app}",
    cpool     = "CP_{cp}",
  )

  test("NameResolver - adm"):
    assertEquals(nr.secAdmin, Ident("RL_DEV_SECADMIN"))
    assertEquals(nr.dbAdmin, Ident("RL_DEV_SYSADMIN"))

  test("NameResolver - other"):
    assertEquals(nr.db("ETL"),            Ident("ETL_DEV"))
    assertEquals(nr.sch("ETL", "CUST"),   Ident("CUST"))
    assertEquals(nr.acc("ETL", "CUST", "R"), Ident("CUST_R"))
    assertEquals(nr.wacc("LOAD", "RW"),   Ident("_WH_DEV_LOAD_RW"))
    assertEquals(nr.fn("QA"),             Ident("RL_DEV_QA"))
    assertEquals(nr.app("ETL"),           Ident("APP_DEV_ETL"))
    assertEquals(nr.cp("ETL"),            Ident("CP_ETL"))
