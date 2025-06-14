package sfenv
package rules
package test

import munit.FunSuite

class NameResolverTests extends FunSuite:
  val nr = Config.Resolver(
    env = "DEV",
    secadm = "RL_{env}_SECADMIN",
    dbadm = "RL_{env}_SYSADMIN",
    database = "{db}_{env}",
    schema = "{sch}",
    warehouse = "WH_{env}_{wh}",
    acc_role = "{sch}_{acc}",
    wacc_role = "_WH_{env}_{wh}_{acc}",
    fn_role = "RL_{env}_{role}",
    app_id = "APP_{env}_{app}",
    cpool = "CP_{cp}",
  )

  test("NameResolver - adm"):
    assert(nr.secAdmin == Ident("RL_DEV_SECADMIN"))
    assert(nr.dbAdmin == Ident("RL_DEV_SYSADMIN"))

  test("NameResolver - other"):
    assert(clue(nr.db("ETL")) == Ident("ETL_DEV"))
    assert(clue(nr.sch("ETL", "CUST")) == Ident("CUST"))
    assert(clue(nr.acc("ETL", "CUST", "R")) == Ident("CUST_R"))
    assert(clue(nr.wacc("LOAD", "RW")) == Ident("_WH_DEV_LOAD_RW"))
    assert(clue(nr.fn("QA")) == Ident("RL_DEV_QA"))
    assert(clue(nr.app("ETL")) == Ident("APP_DEV_ETL"))
