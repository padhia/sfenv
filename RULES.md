# Rules File

Rules file is a `yaml` or a `json` file that is used by `sfenv` utility to generate Snowflake DDLs and DCLs. A *rules-file* consists of a configuration to help derive names, object definitions for one more *object types* (called *sections*), roles and permissions.

All available *sections* are documented below. Only the `config` section is required, all other sections may be omitted when not required.

- **`config`**
- `options`
- `imports`
- `databases`
- `warehouses`
- `roles`
- `users`
- `apps`

Although object properties listed below don't explicitly include them, but all object and role definitions accept `tags` *mapping object* consisting of *tag-name* and a *tag-value*

## `config`

A YAML/JSON object that controls naming of database objects. This section must list all attributes shown in the following example:

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
```

Notes:

- All attributes in `config` section are *templates* to derive corresponding object names.
  - Names are derived by substituting *placeholders* (variables enclosed in `{}`).
- `env` is a special variable that is supplied at run-time. This enables generating SQL statements that are similar but have slightly different names depending on the *environment*.
- Value for `env` is supplied at runtime whereas values for all other placeholder names are taken from other sections within the rules file.
- Access role names are derived using the placeholders of the resource type they control.
- Generated DDLs and DCLs will include appropriate `use role <secadm>|<dbadm>` statements.
  - `<secadm>` is security administrator ID for an environment and controls permissions
  - `<dbadm>` is resource owner and owns the created objects

## `options`

A YAML/JSON object containing *options* that control SQL code generation.

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
- `create_roles` controls whether DDLs for managing *account-level roles* are generated or not.
  - Note that database-level roles are always generated when required
- `create_warehouse` controls whether DDLs for managing *account-level roles* are generated or not.
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
- `...`: Any other attributes are passed through and become part of the DDL

### schema
A schema is a YAML/JSON object that defines a database schema and has following attributes:

- `transient`: `true` if this is a transient schema
- `managed`: `true` if this is a managed schema
- `comment`: comment that'll be part of the generated DDL
- `acc_roles`: A YAML/JSON object containing access role definitions to create
- `...`: Any other attributes are passed through and become part of the DDL

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
- `...`: Any other attributes are passed through and become part of the DDL

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
A YAML/JSON object containing one or more *functional role* names and their properties. A role definition can specify an optional attribute named `create`, which if set to `false` (default `true`), causes no DDL statements to be generated for the `ROLE`, but does generate DCL for any associated roles.

### functional roles

A functional role has following attributes:
- `comment`: comment that'll be part of the generated DDL
- `acc_roles`: A YAML/JSON list containing *references* to schema and/or warehouse access roles
- `env_acc_roles`: Allows overriding access roles for specific environments

**Example**

```yaml
roles:
  DEVELOPER:
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
A *user* is an object mapping of name and its properties. Valid properties
- `comment`: comment that'll be part of the generated DDL
- `roles`: A YAML/JSON list containing names of the functional roles to be assigned to this user ID
- references specified in the `default*` keys are expanded per regular patterns specified in `config` section

## `apps`
Similar to `users` above except application IDs are created and can be specific to an environment.
