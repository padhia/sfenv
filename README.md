# sfenv

`sfenv` helps manage top-level Snowflake objects and their permissions in multiple _environments_.

An environment refers to an isolated set of Snowflake objects. Typical environments are DEV, QA, and PROD. Each environment, ideally, has the same set of objects, except that they may differ due to their current stage in their lifecycle. For example, objects in the DEV environment may have changes that are not yet migrated to higher environments.

Snowflake objects and permissions are declaratively defined in a YAML, JSON, or Pkl file (called _Rules File_). See the [examples/](https://github.com/padhia/sfenv/tree/main/examples) folder for samples and [RULES.md](./RULES.md) for a detailed description of rules specification.

## Installation

Download the latest binary for your platform from the [releases page](https://github.com/padhia/sfenv/releases) on GitHub and place it on your `PATH`.

## How It Works

`sfenv` implements Snowflake's [recommended RBAC pattern](https://docs.snowflake.com/en/user-guide/security-access-control-considerations): fine-grained _Access Roles_ (database roles scoped to a schema or warehouse) are granted to coarse-grained _Functional Roles_ (account-level roles), which are then assigned to users. This separates _what_ a role can access from _who_ holds that role.

A key feature is environment-specific permissions: a single rules file can grant developers read-only access in PROD while granting them full read-write access in DEV or QA — no duplication required.

`sfenv` reads a rules file and writes SQL statements to stdout. Pipe the output directly to your Snowflake client to apply it:

```sh
sfenv -e PROD my-rules.yaml | snow sql -c <connection-name> -i
```

## Features

- One rules file manages all environments (DEV, QA, PROD, …)
- Databases, Schemas, Warehouses, Shares, Users, Application IDs, and RBAC all in one place
- Access Roles and Functional Roles follow Snowflake best practices
- Environment-specific permissions within a single file (e.g. `RWC` in DEV, `R` in PROD)
- Incremental SQL generation via `--diff`: only changes since a previous version are emitted
- Rules files can be written in YAML, JSON, or [Pkl](https://pkl-lang.org)

## Usage

```sh
sfenv [-e <env>] [--admin-roles] [<rules-file>]
sfenv [-e <env>] [-d <path>] [--drop <choice>] [-F] [<rules-file>]
```

Where:

- `<rules-file>`: A YAML, JSON, or Pkl file containing object and privileges definitions. Reads from stdin when omitted.
- `-e, --env ENV`: An _environment_ name, to derive object and role names (default: `$SFENV` or `DEV`)
- `--admin-roles`: Generate SQL to create the environment's `secadm` and `dbadm` admin roles. Run this once before applying the main environment SQL for the first time.
- `-d, --diff <rules>`: When specified, SQL statements are generated only for the differences between the given rules file and the main rules file
- `--gen-drop <all|local|none>`: Determine what `DROP` SQL statements are generated without being commented out
    - `all`: all `DROP` statements are generated without comments
    - `local`: only `DROP` statements that may lead to data loss (includes local databases and schemas, but not _shared_ databases) are commented out
    - `none`: all `DROP` statements are commented out
- `-F, --only-future`: When generating permissions for objects at the schema level, generate only `FUTURE` grants, and skip `ALL` grants, which can be expensive to run for large schemas

`--gen-drop` and `--only-future` can also be set as `drop` and `only-future` respectively in the `options` section of the rules file; CLI flags take precedence over file-level settings.

## Choosing a Rules File Format

YAML and JSON are supported natively. `.pkl` files are supported if [Pkl cli](https://github.com/apple/pkl/releases) is available in the `PATH`. Pkl offers additional benefits for larger configurations:

- **Imports and reusable defaults** — define schema or warehouse templates once in a shared file and extend them per object
- **Inheritance** — `(defaults.sch) { transient = true }` extends a base template with one override
- **Expression reuse** — `QA = DEV` copies an environment's permission block without repetition

See [`examples/defaults.pkl`](https://github.com/padhia/sfenv/tree/main/examples/defaults.pkl) and [`examples/example.pkl`](https://github.com/padhia/sfenv/tree/main/examples/example.pkl) for a working Pkl example. `example.pkl` imports `defaults.pkl` via `modulepath:` (see [Integrating with Git](#integrating-with-git) for why), which `sfenv` resolves relative to its working directory — run it from within `examples/` (e.g. `cd examples && sfenv example.pkl`), not from the repo root.

## Maintaining State

In a limited capacity, `sfenv` supports generating SQL statements relative to a previous _state_. This functionality is enabled by the `--diff` option that accepts a second rules file, generally an older version of the rule file. `sfenv` can then generate SQL statements for only the differences between the two rule files. The easiest way to maintain versions of rule files is to use a version control system such as `git`.

### Integrating with Git

If you use `git` to manage rules files, you can define a custom [`difftool`](https://git-scm.com/docs/git-difftool) to generate incremental (delta) SQL statements only for the differences between two git versions.

**git configuration**

Run the following command to register `sfenv` as a `difftool`.

```sh
git config difftool.sfenv.cmd 'sfenv $REMOTE --diff $LOCAL'
```

Example: to generate SQL statements for changes made, but not yet committed, to a rules file:

```sh
git difftool -yt sfenv my-rules.yaml
```

Typical use cases include:

- Generating SQL statements for changes made, but not yet committed, to a rules file.
- Generating SQL statements for changes made between two environments when a separate branch tracks each environment.
- Generating a rollback script by switching current and old versions in the sfenv command invocation.
    ```sh
    sfenv previous-good.yaml --diff current-bad.yaml
    ```

> [!WARNING]
> **Pkl rules files with relative imports do not work with `git difftool`.** `git difftool` copies only the diffed file itself into an isolated temporary directory — files it `import`s or `amends` via a relative path (e.g. `import "defaults.pkl"`) will not be present alongside it, causing Pkl to fail with a "Cannot find module" error. This limitation does not affect normal (non-`difftool`) invocations of `sfenv`, since the working directory still has all sibling files available.
>
> **Workaround:** use Pkl's [`modulepath:`](https://pkl-lang.org/main/current/language-reference/index.html#modulepath-uris) scheme instead of a plain relative path for any import shared across files that may be diffed via `git difftool`:
> ```pkl
> import "modulepath:/defaults.pkl"   // instead of: import "defaults.pkl"
> ```
> `sfenv` always runs `pkl eval --module-path <current-working-directory>`, so `modulepath:` imports resolve against the directory `sfenv`/`git difftool` was invoked from (typically the repo root) rather than the location of the file being evaluated — which still works correctly even when that file is an isolated temp copy. This has no effect on files that don't use `modulepath:` imports; those must still avoid relative `import`/`amends`, or use YAML/JSON instead, to work with `git difftool`.

## Current Limitations

1. `sfenv` is a pure SQL generator — it does not connect to Snowflake, does not inspect any existing account state, and does not validate objects or privileges against a live environment. All SQL is produced solely from the rules file.
1. Not all Snowflake object types are supported. Managing Databases, Schemas, Warehouses, Roles, Users, and permissions (RBAC) is fully supported.
1. There is no strict validation of object parameters or privileges. Any unrecognized object parameters or privileges are reproduced verbatim in the generated SQL, and errors will only surface when the SQL is executed against Snowflake.
1. Pkl rules files that use relative `import`/`amends` (e.g. `import "defaults.pkl"`) cannot be used with `git difftool`, since only the diffed file is copied into an isolated temp directory and sibling files it depends on are unavailable — use `modulepath:` imports instead (see [Integrating with Git](#integrating-with-git)).
