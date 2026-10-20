# sfenv

`sfenv` is a management utility designed to oversee top-level Snowflake objects and their corresponding access permissions across multiple _environments_.

An environment represents a designated collection of Snowflake objects generated from a single baseline configuration template, isolated from one another. Standard environments include Development (DEV), Quality Assurance (QA), and Production (PROD). Ideally, each environment mirrors the same target object definitions, varying only by their specific stage within the deployment lifecycle. For instance, modifications residing in DEV may not yet be promoted to upstream environments.

Snowflake objects and role-based privilege assignments are declaratively specified within a structured YAML, JSON, or Pkl configuration file (referred to as the _Rules File_). Refer to the [examples/](https://github.com/padhia/sfenv/tree/main/examples) directory for sample implementations and [RULES.md](./RULES.md) for comprehensive specifications regarding rule definitions.

**Notes**:

`sfenv` operates strictly as a declarative SQL generator, synthesizing DDL and DCL statements exclusively from the provided input rules file. Key operational boundaries include:

- No active database connections are established to inspect live object states or privileges.
- No automatic synchronization is performed between existing Snowflake deployments and local configurations.
- Unrecognized or custom properties (particularly for databases, schemas, and warehouses) are passed through into the generated SQL output verbatim.
- Comprehensive support is provided for managing Databases, Schemas, Warehouses, Roles, Users, and Role-Based Access Control (RBAC) privileges; support for other top-level objects maybe missing

## Installation

Download the latest release binary for your target architecture from the official [GitHub Releases](https://github.com/padhia/sfenv/releases) repository and ensure executable availability within system `PATH`.

## How It Works

`sfenv` aligns with Snowflake's [recommended RBAC architecture](https://docs.snowflake.com/en/user-guide/security-access-control-considerations): granular _Access Roles_ (database-level roles scoped to specific schemas or warehouses) are granted to broader _Functional Roles_ (account-level roles), which are subsequently assigned to individual user accounts. This architectural separation enforces proper decoupling between privilege scope and user identity.

A central capability is environment-aware authorization modeling: a consolidated rules file can grant read-only privileges to developers in Production while provisioning read-write privileges within Development or QA environments without requiring configuration redundancy.

`sfenv` parses the target rules file and outputs synthesized DDL/DCL statements to standard output. The resulting stream can be piped directly into the Snowflake CLI for immediate execution:

```sh
sfenv -e PROD my-rules.yaml | snow sql -c <connection-name> -i
```

It is strongly recommended that the generated output be verified before submitting it to Snowflake for execution.

## Features

- Consolidated management of all deployment environments (DEV, QA, PROD, etc.) within a single configuration file
- Unified definitions for Databases, Schemas, Warehouses, Shares, Users, Application IDs, and RBAC policies
- Adherence to Snowflake best practices via dual-layer Access and Functional Role abstractions
- Environment-specific access controls declared concisely (e.g., `RWC` in DEV versus [`R`](https://pkl-lang.org) in PROD)
- Incremental deployment support via the `diff` command, emitting SQL strictly for net-new or modified declarations
- Multi-format support for rule definitions, including YAML, JSON, and [Pkl](https://pkl-lang.org)

## Usage

```sh
sfenv [-e <env>] [--gen-drop <choice>] [-F] [<rules-file>]
sfenv diff [-e <env>] [--gen-drop <choice>] [-F] [<commit> [<commit>]] <rules-file>
sfenv admin [-e <env>] [<rules-file>]
```

Command Parameters:

- `<rules-file>`: Specifies the input YAML, JSON, or Pkl configuration file containing object and privilege specifications. Reads from standard input if omitted.
- `-e, --env ENV`: The targeted _environment_ identifier used to contextualize object and role naming conventions (default: `$SFENV` or `DEV`).
- `admin`: Command that generates foundational SQL to initialize administrative roles (`secadm` and `dbadm`) for the environment. Should be executed prior to applying primary environment configurations.
- `diff`: Command that generates SQL only for differences between two git states of the rules file (see [Maintaining State](#maintaining-state)).
- `--gen-drop <all|local|none>`: Controls execution behavior for destructive `DROP` SQL directives.
    - `all`: Emits all `DROP` statements active for direct execution.
    - `local`: Comments out potentially destructive `DROP` statements affecting local databases and schemas while leaving non-destructive statements active.
    - `none`: Comments out all `DROP` statements across the generated script.
- `-F, --only-future`: Restricts schema-level grant generation exclusively to `FUTURE` privileges, bypassing explicit `ALL` grants to optimize execution performance on expansive schemas.

The `--gen-drop` and `--only-future` parameters may also be declared persistent under the `options` block within the rules file using `drop` and `only-future` properties, respectively. Command-line flags explicitly override configuration file settings.

## Choosing a Rules File Format

YAML and JSON formats are natively supported. Support for `.pkl` configurations is enabled when the [Pkl CLI](https://github.com/apple/pkl/releases) utility is present within the system `PATH`. Utilizing Pkl introduces key modularity features for large-scale infrastructure specifications:

- **Modular Imports & Templates** — Define standardized schema or warehouse templates in shared modules and extend them per object declaration.
- **Object Inheritance** — Parameterize base configurations (e.g., `(defaults.sch) { transient = true }`) to inherit default settings while applying targeted property overrides.
- **Expression Reuse** — Reference existing environment privilege definitions directly (e.g., `QA = DEV`) to reduce duplicate declarations.

Reference implementations are provided in [`examples/defaults.pkl`](https://github.com/padhia/sfenv/tree/main/examples/defaults.pkl) and [`examples/example.pkl`](https://github.com/padhia/sfenv/tree/main/examples/example.pkl). `example.pkl` imports `defaults.pkl` using a relative path, which Pkl resolves against the importing file's directory, so it can be evaluated from any directory (e.g., `sfenv examples/example.pkl`).

### Recommended: KCL with typed schemas

[KCL](https://www.kcl-lang.io) (`.k`) rules files are supported when the [KCL CLI](https://www.kcl-lang.io/docs/user_docs/getting-started/install) is on the system `PATH`. KCL offers the same modularity as Pkl, and with the typed schemas in the [`kcl`](https://github.com/padhia/sfenv/tree/main/kcl) package ([`core.k`](https://github.com/padhia/sfenv/tree/main/kcl/core.k) matches the sfenv rules model, [`rules.k`](https://github.com/padhia/sfenv/tree/main/kcl/rules.k) adds commonly used properties) it also checks the structure before any SQL is generated:

- Misspelled `config` or `options` attributes, wrong value types (e.g., `managed = "yes"`), and invalid choices (e.g., an unknown `drop` value) are rejected when the file is evaluated, instead of producing SQL that fails in Snowflake.
- Frequently used attributes are typed; databases, schemas, warehouses, roles and users still accept extra pass-through properties.
- Your own defaults can be layered on top by extending the schemas, as [`examples/defaults.k`](https://github.com/padhia/sfenv/tree/main/examples/defaults.k) and [`examples/example.k`](https://github.com/padhia/sfenv/tree/main/examples/example.k) do.

Using KCL with these schemas is recommended to minimize SQL errors at runtime.

The KCL package (named `sfenv`) is versioned independently of the `sfenv` application and released with `kcl/vX.Y.Z` tags. When an `sfenv` release requires a newer package version, its release notes state the minimum version.

## Maintaining State

`sfenv` generates SQL relative to a prior state of the **same** rules file tracked in `git`, following `git diff` conventions:

| Command               | Source (previous)  | Target (current)             |
| --------------------- | ------------------ | ---------------------------- |
| `sfenv FILE`          | empty              | working directory (or stdin) |
| `sfenv diff FILE`     | git index (staged) | working directory            |
| `sfenv diff A FILE`   | commit `A`         | working directory            |
| `sfenv diff A B FILE` | commit `A`         | commit `B`                   |

`A` and `B` accept any commit specification understood by git (e.g. `HEAD~1`, `main`, a tag or a SHA). Examples:

```sh
sfenv diff my-rules.yaml                   # unstaged changes
sfenv diff HEAD my-rules.yaml              # all changes since last commit
sfenv diff -e PROD prod main my-rules.yaml # align prod with main
sfenv diff main prod my-rules.yaml         # rollback script
```

Notes:

- `sfenv` must be run from within the git repository; the rules file may be given as a relative or an absolute path and must be inside the repository. Reading rules from standard input is not supported by `diff`. Options such as `-e` must follow the command name (`sfenv diff -e PROD ...`, not `sfenv -e PROD diff ...`).
- If the rules file does not exist at the source, it is treated as empty (all objects are generated as new). It must exist at the target.
- `git` must be available in `PATH`. YAML/JSON files are read directly from git. For Pkl files, the repository tree at the requested state is extracted to a temporary directory (using `git read-tree` and `git checkout-index`, leaving the repository untouched) so that relative imports resolve at the same state. The temporary directory is removed on exit.
