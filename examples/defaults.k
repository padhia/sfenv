import sfenv.rules as r

type ObjPrivs = r.Privs
type AccRoles = r.AccRoles

schema Config(r.Config):
    secadm: str = "RL_{env}_SECADMIN"
    dbadm: str = "RL_{env}_SYSADMIN"
    database: str = "{db}_{env}"
    schema: str = "{sch}"
    warehouse: str = "WH_{env}_{wh}"
    acc_role: str = "{sch}_{acc}"
    wacc_role: str = "_WH_{env}_{wh}_{acc}"
    fn_role: str = "RL_{env}_{role}"
    app_id: str = "APP_{env}_{app}"
    cpool: str = "CP_{cp}"

schema Schema(r.Schema):
    managed: bool = True
    data_retention_time_in_days?: int = 10
    acc_roles: AccRoles = { # assigning Access-role to a Functional-role will automatically include USAGE on the database and schema
        R = {
            table = ["select", "references"]
            view = ["select"]
        }
        RW = {
            role = ["R"]
            table = ["insert", "update", "truncate", "delete"]
        }
        RWC = {
            role = ["RW"]
            schema = ["create table", "create view", "create procedure"]
        }
    }

schema Database(r.Database):
    data_retention_time_in_days: int = 10

schema Warehouse(r.Warehouse):
    warehouse_size: str = "SMALL"
    initially_suspended: bool = True
    auto_suspend: int = 300
    auto_resume: bool = True
    min_cluster_count: int = 1
    max_cluster_count: int = 1
    scaling_policy: str = "STANDARD"
    statement_timeout_in_seconds: int = 3600
    statement_queued_timeout_in_seconds: int = 300
    acc_roles: AccRoles = {
        R = {warehouse = ["usage"]}
        RW = {
            role = ["R"]
            warehouse = ["operate"]
        }
        RWC = {
            role = ["RW"]
            warehouse = ["monitor", "modify"]
        }
    }

schema Role[priv: str](r.Role):
    acc_roles: r.AccRefs = {
        "edw.customer" = priv
        "bi.customer" = priv
        load = priv
        etl = priv
        bi_s = priv
        bi_l = priv
    }
