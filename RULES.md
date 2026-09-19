# Rules File

Rules file is a `yaml` or a `json` file that is used by `sfenv` utility to generate Snowflake DDLs and DCLs. A _rules-file_ consists of a configuration to help derive names, object definitions for one more _object types_ (called _sections_), roles and permissions.

All available _sections_ are documented below. Any section can be omitted when not required.

- `config`
- `options`
- `account`
- `imports`
- `databases`
- `warehouses`
- `compute_pools`
- `roles`
- `users`
- `apps`

Although object properties listed below don't explicitly include them, but all object and role definitions accept `tags` _mapping object_ consisting of _tag-name_ and a _tag-value_

## Attribute Names

Always prefer lowercase attribute keys in YAML, JSON, and Pkl object definitions, including nested properties, for example `default_namespace`, `acc_roles`, and `warehouse_size`. Any field-names (**not values**) that are in different cases may be ignored or may generate incorrect output.

## Nested Properties

Pass-through properties support strings, numbers, booleans, nested objects, and arrays. Objects render as `(NAME = value, ...)` with sorted keys; arrays render as `(value, ...)` in input order. Both may be nested. Empty objects and arrays render as `()`. Null pass-through values are not supported. Existing string quoting rules apply recursively. Whether a particular nested value is accepted by Snowflake depends on the target property's syntax.

An object containing `db` and `sch` is an ordinary nested property, not a schema reference. For user and application namespaces, use the named `default_namespace` string attribute instead.

## `config`

A YAML/JSON object that controls naming of database objects.

**Example:**

```yaml
config:
  secadm: "RL_{env}_SECADMIN"
  dbadm: "RL_{env}_SYSADMIN"
  database: "{db}_{env}"
  schema: "{sch}"
  warehouse: "WH_{env}_{wh}"
  acc_role: "{sch}_{acc}"
  wacc_role: "_WH_{env}_{wh}_{acc}"
  fn_role: "RL_{env}_{role}"
  app_id: "APP_{env}_{app}"
  cpool: "CP_{env}_{cp}"
```

| template    | Allowed Variables | default       |
| ----------- | ----------------- | ------------- |
| `secadm`    |                   | `USERADMIN`   |
| `dbadm`     |                   | `SYSADMIN`    |
| `database`  | env, db           | `{db}`        |
| `schema`    | env, db, sch      | `{sch}`       |
| `warehouse` | env, wh           | `{wh}`        |
| `acc_role`  | env, db, sch, acc | `{sch}_{acc}` |
| `wacc_role` | env, wh, acc      | `_{wh}_{acc}` |
| `fn_role`   | env, role         | `{role}`      |
| `app_id`    | env, app          | `{app}`       |
| `cpool`     | env, cp           | `{cp}`        |

Notes:

- All attributes in `config` section are _templates_ to derive corresponding object names.
    - Names are derived by substituting _variables_ (placeholders enclosed in `{}`).
- `env` is a special variable that is supplied at run-time. This enables generating SQL statements that are similar but have slightly different names depending on the _environment_.
- Except for `env`, all other variables are derived from the context within the rules file.
- Generated DDLs and DCLs will include appropriate `use role <secadm>|<dbadm>` statements.
    - `<secadm>` is security administrator ID for an environment and controls permissions
    - `<dbadm>` is resource owner and owns the created objects

## `options`

A YAML/JSON object containing _options_ that control SQL code generation.

**Example:**

```yaml
options:
    only_futures: true
    drops: non-local
```

Notes:

- `create_users` controls whether DDLs for managing users are generated or not.
    - this option affects both, `users` and `apps`, sections
    - By default Snowflake User IDs only serve as anchors for assigning roles and are not created
    - Recommendation: enable this option if users are not externally managed
- `create_roles` controls whether DDLs for managing _account-level roles_ are generated or not.
    - Note that database-level roles are always generated when required
- `create_warehouse` controls whether DDLs for managing _account-level roles_ are generated or not.
- `only_futures`: generate `ALL` in addition to `FUTURE` grants
- `drops`: controls generation of `DROP` statements
    - `non-local`: generate `DROP` for objects that are not local (for example shares)
    - `all`: generate `DROP` statements
    - `none`: do not generate `DROP` statements
- command-line options have higher priority over options specified in rules file

## `account`

An optional object describing account-level settings. Currently, only account-level parameters are supported

### `params`

A mapping of account parameter names to values. Put parameters inside `params`, not directly under `account`. Both an omitted `account` section and an omitted `params` field default to an empty mapping.

**Example:**

```yaml
account:
  params:
    statement_timeout_in_seconds: 3600
    timezone: America/New_York
```

- Parameter names are emitted as uppercase SQL identifiers and are not expanded using environment naming templates.
- String parameter values are automatically single-quoted; supply plain strings without adding SQL quotes. Numbers and booleans use their normal property rendering.
- Without `--diff`, each parameter generates an `ALTER ACCOUNT SET` statement.
- With `--diff`, new or changed parameters generate `ALTER ACCOUNT SET`; parameters present in the previous rules file but absent from the current one generate `ALTER ACCOUNT UNSET`. Unchanged parameters generate no statements. Omitting the entire section therefore removes all previously declared parameters when diffing.
- These statements use the `ACCOUNTADMIN` role. Only specify parameters and values supported by Snowflake at account scope; the rules parser does not validate parameter names against Snowflake.
- `params` uses the same property value types as other properties, including nested objects and arrays, but null values are not supported. Structured values are only useful where the target parameter accepts the generated syntax.

## `imports`

A YAML/JSON object containing imported share names and their definitions.

**Example:**

```yaml
imports:
  CUST:
    provider: CUSTP
    share: DATA_SHR
    roles:
      - DBA
      - DEVLOPER
```

An imported share is a YAML/JSON object that has following attributes:

- **`provider`**: provider account name
- **`share`**: name of the share
- `roles`: A list of functional roles that will be granted `imported privileges`JSON objects

## `databases`

A YAML/JSON object containing database names and their definitions.

**Example:**

```yaml
databases:
  edw:
    data_retention_time_in_days: 10
    comment: EDW core database
    schemas:
      customer: &sch_defaults
        managed: true
        data_retention_time_in_days: 10
        acc_roles:
          r:
            database: [usage, monitor]
            schema: [usage, monitor]
            table: [select, references]
            view: [select]
          rw:
            role: [R]
            table: [insert, update, truncate, delete]
          rwc:
            role: [RW]
            schema: ["create table", "create view", "create procedure"]

  bi:
    transient: true
    data_retention_time_in_days: 10
    comment: Analytics database
    schemas:
      customer:
      	<<: *sch_defaults
        transient: true
```

### database

A database is a YAML/JSON object that has following attributes:

- `transient`: `true` if this is a transient database
- `comment`: comment that'll be part of the generated DDL
- `schemas`: A YAML/JSON list containing one or more schema YAML/JSON objects
- `...`: Other attributes are reproduced as defined in the generated DDL

### schema

A schema is a YAML/JSON object that defines a database schema and has following attributes:

- `transient`: `true` if this is a transient schema
- `managed`: `true` if this is a managed schema
- `comment`: comment that'll be part of the generated DDL
- `acc_roles`: A YAML/JSON object containing access role definitions to create
    - Note: granting an access-role to a functional-role will automatically include USAGE privilage on the database and schema
- `...`: Other attributes are reproduced as defined in the generated DDL

## `warehouses`

`warehouses` is a YAML/JSON object containing one or more warehouse names and definitions.

**Example**

```yaml
warehouses:
  LOAD: &wh_defaults
    warehouse_size: SMALL
    initially_suspended: true
    auto_suspend: 300
    auto_resume: true
    acc_roles:
      R:
        warehouse: [usage]
      RW:
        role: [R]
        warehouse: [operate]
      RWC:
        role: [RW]
        warehouse: [monitor, modify]
  ETL:
    <<: *wh_defaults
    warehouse_size: X-LARGE
```

A warehouse is a YAML/JSON object with following attributes:

- `comment`: comment that'll be part of the generated DDL
- `acc_roles`: A YAML/JSON object containing access role definitions to create
- `...`: Other attributes are reproduced as defined in the generated DDL

## `compute_pools`

Compute-pool definitions use lowercase snake_case attributes:

- `min_nodes`: minimum nodes, default `1`.
- `max_nodes`: maximum nodes, defaults to the selected `min_nodes`.
- `instance_family`: instance family, default `CPU_X64_XS`.
- `tags` and `comment`: metadata for the compute pool.

The former camelCase spellings `minNodes`, `maxNodes`, and `instanceFamily` are no longer recognized as these attributes. Update existing rules files to use snake_case.

```yaml
compute_pools:
  ETL:
    min_nodes: 1
    max_nodes: 3
    instance_family: CPU_X64_XS
```

## access roles

An access role is created for each schema or a warehouse. An access role is specified as a YAML/JSON object with access role name as key and attribute being another YAML/JSON object that encodes a list of permissions for each database object type.

**Example**

```yaml
R:
  database: [usage, monitor]
  schema: [usage, monitor]
  table: [select, references]
  view: [select]
RW:
  role: [R]
  table: [insert, update, truncate, delete]
```

The above example when specified for a schema will

- create two database roles, corresponding to `R` and `RW`
- each access role specifies object type and permissions mapping
- permissions are list of SQL privileges

## `roles`

A YAML/JSON object containing one or more _functional role_ names and their properties. A role definition can specify an optional attribute named `create`, which if set to `false` (default `true`), causes no DDL statements to be generated for the `ROLE`, but does generate DCL for any associated roles.

### functional roles

A functional role has following attributes:

- `comment`: comment that'll be part of the generated DDL
- `acc_roles`: A YAML/JSON list containing _references_ to schema and/or warehouse access roles
- `env_acc_roles`: Allows overriding access roles for specific environments
- `sys_roles`: An optional list of existing account roles or database roles to grant to this functional role. Unlike access roles, these roles are used verbatim without any template expansion

**Example**

```yaml
roles:
  DEVELOPER:
    users:
      - JDOE
    apps:
      - ETL
    comment: Developers
    sys_roles:
      - SHARED_READER
      - SHARED_DB.READER
    acc_roles:
      EDW.CUSTOMER: R
      BI.CUSTOMER: R
      LOAD: R
      ETL: R
    env_acc_roles:
      DEV: &dev_permissions
        EDW.CUSTOMER: RWC
        BI.CUSTOMER: RWC
        LOAD: RW
      QA: *dev_permissions
```

Note: `users` and `apps` are User IDs to which the role must be granted. Alternatively, User IDs from `users` and `apps` sections can also specify roles to be granted.

## `users`

A YAML/JSON object that describes Snowflake user IDs. A user definition can specify an optional attribute named `create`, which if set to `false` (default `true`), causes no DDL statements to be generated for the `USER`, but does generate DCL for any associated roles.

**Example**

```yaml
users:
  JDOE:
    default_role: DBA
    default_warehouse: ETL
    default_namespace: EDW.CUSTOMER
    comment: John Doe
    roles:
      - DBA
      - SYSINFO
```

A _user_ is an object mapping of name and its properties. Valid properties

- `comment`: comment that'll be part of the generated DDL
- `roles`: A YAML/JSON list containing names of the functional roles to be assigned to this user ID
- references specified in the `default*` keys are expanded per regular patterns specified in `config` section
- `default_namespace`: a string in the form `database` or `database.schema`. Names are resolved using the configured templates. It is managed separately from pass-through properties: changes generate `SET DEFAULT_NAMESPACE`, removal generates `UNSET DEFAULT_NAMESPACE`, and dropping a user requires no separate namespace cleanup.

## `apps`

Similar to `users` above except application IDs are created and can be specific to an environment.
