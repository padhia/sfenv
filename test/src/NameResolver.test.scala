package sfenv.rules

import munit.FunSuite

class NameResolverTest extends FunSuite:
  val cfg = Config(
    secadm = "RL_{env}_SECADMIN",
    dbadm = "RL_{env}_SYSADMIN",
    database = "{db}_{env}",
    schema = "{sch}",
    warehouse = "WH_{env}_{wh}",
    acc_role = "{sch}_{acc}",
    wacc_role = "_WH_{env}_{wh}_{acc}",
    fn_role = "RL_{env}_{role}",
    app_id = "APP_{env}_{app}",
    cpool = Some("CP_{cp}"),
  )

  val nr = NameResolver.makeUsing(cfg, "DEV")

  test("NameResolver - adm") {
    assert(nr.secAdmin == "RL_DEV_SECADMIN")
    assert(nr.dbAdmin == "RL_DEV_SYSADMIN")
  }

  test("NameResolver - other") {
    assert(clue(nr.db("ETL")) == "ETL_DEV")
    assert(clue(nr.sch("ETL", "CUST")) == "CUST")
    assert(clue(nr.acc("ETL", "CUST", "R")) == "CUST_R")
    assert(clue(nr.wacc("LOAD", "RW")) == "_WH_DEV_LOAD_RW")
    assert(clue(nr.fn("QA")) == "RL_DEV_QA")
    assert(clue(nr.app("ETL")) == "APP_DEV_ETL")
  }
