# Layer 2: core.k plus commonly used pass-through properties as typed, optional attributes.
#
# These attributes are not interpreted by sfenv; they are passed through to the generated SQL.
#
# Schemas here are strict: KCL does not inherit index signatures, so by not restating
# `[...str]: any` only the attributes declared here or in core.k are accepted. Use core.k
# directly to pass through properties not listed here. The exception is User, which stays
# relaxed so session parameters (not typed here) can still be set per user.

import .core

type Privs = core.Privs
type AccRoles = core.AccRoles
type AccRefs = core.AccRefs

schema Config(core.Config):
    """See core.Config."""

schema Options(core.Options):
    """See core.Options."""

schema Account(core.Account):
    """See core.Account."""

schema Import(core.Import):
    """See core.Import."""

schema Schema(core.Schema):
    data_retention_time_in_days?: int
    max_data_extension_time_in_days?: int
    stage_undrop_retention_time_in_days?: int
    default_ddl_collation?: str
    log_level?: "trace" | "debug" | "info" | "warn" | "error" | "fatal" | "off"
    trace_level?: "always" | "on_event" | "propagate" | "off"
    storage_serialization_policy?: "compatible" | "optimized"
    classification_profile?: str
    object_visibility?: "privileged"
    enable_data_compaction?: bool
    replace_invalid_characters?: bool
    # Iceberg / external catalog defaults
    external_volume?: str
    catalog?: str
    catalog_sync?: str
    iceberg_default_ddl_collation?: str
    iceberg_version_default?: int
    iceberg_merge_on_read_behavior?: "auto" | "enabled" | "disabled"
    enable_iceberg_merge_on_read?: bool
    # OAuth (catalog / Horizon)
    oauth_authorization_server?: str
    oauth_scopes_supported?: str

schema Database(core.Database):
    schemas: {str:Schema}
    data_retention_time_in_days?: int
    max_data_extension_time_in_days?: int
    stage_undrop_retention_time_in_days?: int
    default_ddl_collation?: str
    storage_serialization_policy?: "compatible" | "optimized"
    object_visibility?: str  # "privileged" or an object visibility spec
    enable_data_compaction?: bool
    replace_invalid_characters?: bool
    # Iceberg / external catalog defaults
    external_volume?: str
    catalog?: str
    catalog_sync?: str
    catalog_sync_namespace_mode?: "nest" | "flatten"
    catalog_sync_namespace_flatten_delimiter?: str
    iceberg_default_ddl_collation?: str
    iceberg_version_default?: int
    iceberg_merge_on_read_behavior?: "auto" | "enabled" | "disabled"
    enable_iceberg_merge_on_read?: bool
    # OAuth (catalog / Horizon)
    oauth_authorization_server?: str
    oauth_scopes_supported?: str

schema Warehouse(core.Warehouse):
    warehouse_type?: "standard" | "snowpark-optimized" | "adaptive"
    warehouse_size?: str  # also accepts aliases such as "x-large"
    generation?: "1" | "2"
    resource_constraint?: "standard_gen_1" | "standard_gen_2" | "memory_1x" | "memory_1x_x86" | "memory_16x" | "memory_16x_x86" | "memory_64x" | "memory_64x_x86"
    initially_suspended?: bool
    auto_suspend?: int
    auto_resume?: bool
    min_cluster_count?: int
    max_cluster_count?: int
    scaling_policy?: "standard" | "economy"
    resource_monitor?: str
    enable_query_acceleration?: bool
    query_acceleration_max_scale_factor?: int
    max_concurrency_level?: int
    statement_timeout_in_seconds?: int
    statement_queued_timeout_in_seconds?: int

schema ComputePool(core.ComputePool):
    auto_resume?: bool
    initially_suspended?: bool
    auto_suspend_secs?: int
    placement_group?: str
    backup_instance_families?: [str]

schema Role(core.Role):
    """See core.Role."""

schema User(core.User):
    [...str]: any  # relaxed: session parameters and other properties pass through
    $type?: "person" | "service" | "service_agent" | "legacy_service"
    default_secondary_roles?: "('ALL')" | "()"
    login_name?: str
    display_name?: str
    first_name?: str
    middle_name?: str
    last_name?: str
    email?: str
    must_change_password?: bool
    disabled?: bool
    allowed_interfaces?: [str]
    days_to_expiry?: int
    mins_to_unlock?: int
    mins_to_bypass_mfa?: int
    rsa_public_key?: str
    rsa_public_key_fp?: str
    rsa_public_key_2?: str
    rsa_public_key_2_fp?: str
    workload_identity?: {str:any}
    # Object parameters
    enable_unredacted_query_syntax_error?: bool
    enable_unredacted_secure_object_error?: bool
    network_policy?: str

schema Rules(core.Rules):
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
