# Rules File

Rules file is a `yaml` or a `json` file that is used by `sfenv` utility to generate Snowflake DDLs and DCLs. A _rules-file_ consists of a configuration to help derive names, object definitions for one more _object types_ (called _sections_), roles and permissions.

All available _sections_ are documented below. Any section can be omitted when not required.

- `config`
- `options`
- `imports`
- `databases`
- `warehouses`
- `roles`
- `users`
- `apps`

Although object properties listed below don't explicitly include them, but all object and role definitions accept `tags` _mapping object_ consisting of _tag-name_ and a _tag-value_

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

| template    | Allowed Variables | default         |
| ----------- | ----------------- | --------------- |
| `secadm`    |                   | `USERADMIN` |
| `dbadm`     |                   | `SYSADMIN`      |
| `database`  | env, db           | `{db}`          |
| `schema`    | env, db, sch      | `{sch}`         |
| `warehouse` | env, wh           | `{wh}`          |
| `acc_role`  | env, db, sch, acc | `{sch}_{acc}`   |
| `wacc_role` | env, wh, acc      | `_{wh}_{acc}`   |
| `fn_role`   | env, role         | `{role}`        |
| `app_id`    | env, app          | `{app}`         |
| `cpool`     | env, cp           | `{cp}`          |

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
  EDW:
    data_retention_time_in_days: 10
    comment: EDW core database
    schemas:
      CUSTOMER: &sch_defaults
        managed: true
        data_retention_time_in_days: 10
        acc_roles:
          R:
            database: [usage, monitor]
            schema: [usage, monitor]
            table: [select, references]
            view: [select]
          RW:
            role: [R]
            table: [insert, update, truncate, delete]
          RWC:
            role: [RW]
            schema: ["create table", "create view", "create procedure"]

  BI:
    transient: true
    data_retention_time_in_days: 10
    comment: Analytics database
    schemas:
      CUSTOMER:
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

**Example**

```yaml
roles:
  DEVELOPER:
    users:
      - JDOE
    apps:
      - ETL
    comment: Developers
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

## `apps`

Similar to `users` above except application IDs are created and can be specific to an environment.
