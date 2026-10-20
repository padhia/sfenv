# Layer 1: KCL schemas matching the sfenv rules model (src/sfenv/rules/*.scala).
#
# Keep in sync with the Scala case classes. Schemas whose Scala class has a `props` field
# are relaxed (`[...str]: any`): extra attributes are passed through to the generated SQL.
# See rules.k for commonly used pass-through properties as typed, optional attributes.

type Privs = {str:[str]}      # object type (or "role") -> privileges / inherited access roles
type AccRoles = {str:Privs}   # access role name -> privileges
type AccRefs = {str:str}      # "DB.SCHEMA" or "WAREHOUSE" -> access role name

schema Meta:
    tags?: {str:str}
    comment?: str

schema Config:
    secadm: str = "USERADMIN"
    dbadm: str = "SYSADMIN"
    database: str = "{db}"
    schema: str = "{sch}"
    warehouse: str = "{wh}"
    acc_role: str = "{sch}_{acc}"
    wacc_role: str = "_{wh}_{acc}"
    fn_role: str = "{role}"
    app_id: str = "{app}"
    cpool: str = "{cp}"

schema Options:
    drop: "all" | "local" | "none" = "local"
    only_futures: bool = False

schema Account:
    params: {str:any} = {}

schema Import:
    provider: str
    share: str
    roles: [str] = []

schema Schema(Meta):
    [...str]: any
    transient: bool = False
    managed: bool = False
    acc_roles: AccRoles = {}

schema Database(Meta):
    [...str]: any
    transient: bool = False
    schemas: {str:Schema}

schema Warehouse(Meta):
    [...str]: any
    acc_roles: AccRoles = {}

schema ComputePool(Meta):
    [...str]: any
    min_nodes: int = 1
    max_nodes?: int
    instance_family: str = "CPU_X64_XS"
    application?: str

    check:
        max_nodes >= min_nodes if typeof(max_nodes) == "int", "max_nodes must be >= min_nodes"

schema Role(Meta):
    [...str]: any
    create: bool = True
    users: [str] = []
    apps: [str] = []
    sys_roles: [str] = []
    acc_roles: AccRefs = {}
    env_acc_roles: {str:AccRefs} = {}

schema User(Meta):
    [...str]: any
    create: bool = True
    roles: [str] = []
    $type?: str             # `type` is a KCL keyword
    default_role?: str
    default_warehouse?: str
    default_namespace?: str

schema Rules:
    config: Config = Config()
    options: Options = Options()
    account: Account = Account()
    imports: {str:Import} = {}
    databases: {str:Database} = {}
    warehouses: {str:Warehouse} = {}
    compute_pools: {str:ComputePool} = {}
    roles: {str:Role} = {}
    users: {str:User} = {}
    apps: {str:User} = {}
