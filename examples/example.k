import .defaults
import sfenv.rules as t

config = defaults.Config()

imports = {
    CUST = {
        provider = "CUSTP"
        share = "DATA_SHR"
        roles = ["DBA", "DEVELOPER"]
    }
    PARTNER = {
        provider = "PARTNER_ACCT"
        share = "PARTNER_DB"
        roles = ["DBA", "DEVELOPER", "ETL"]
    }
}

databases = {
    EDW = defaults.Database() {
        data_retention_time_in_days = 10
        comment = "EDW core database"
        tags = {DEPT = "payroll", SHIFT = "day"}
        schemas = {
            CUSTOMER = defaults.Schema()
        }
    }
    BI = {
        data_retention_time_in_days = 10
        comment = "Analytics database"
        schemas = {
            CUSTOMER = defaults.Schema() {transient = True}
        }
    }
}

warehouses = {
    LOAD = defaults.Warehouse()
    ETL = defaults.Warehouse() { warehouse_size = "X-LARGE", max_cluster_count = 2 }
    BI_S = defaults.Warehouse() {warehouse_size = "MEDIUM", max_cluster_count = 5}
    BI_L = defaults.Warehouse() {warehouse_size = "2X-LARGE", max_cluster_count = 2}
}

_dev_qa_acc = {
    "edw.customer" = "RWC"
    "bi.customer" = "RWC"
    load = "RW"
}

roles = {
    dba = defaults.Role("RWC") { comment = "Database Administrators" }
    developer = defaults.Role("R") {
        comment = "Developers"
        env_acc_roles = {
            DEV = _dev_qa_acc
            QA = _dev_qa_acc
        }
    }
    etl = defaults.Role("R") {
        comment = "ETL Application"
        acc_roles = {
          "edw.customer" = "RW"
          load = "R"
          etl = "R"
        }
    }
}

_user_roles = ["DBA", "SYSINFO"]
users = {
    jdoe = t.User() {
        default_role = "DBA"
        default_warehouse = "LOAD"
        default_namespace = "EDW.CUSTOMER"
        default_secondary_roles = "('ALL')"
        comment = "John Doe"
        roles = _user_roles
    }
    moe = {
        create = False # // do not create the user, except only generate permissions
        roles = _user_roles
    }
}

apps = {
    etl = {
        comment = "ETL application ID"
        roles = ["ETL"]
    }
}
