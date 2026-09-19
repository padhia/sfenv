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
- Incremental deployment support via the `--diff` option, emitting SQL strictly for net-new or modified declarations
- Multi-format support for rule definitions, including YAML, JSON, and [Pkl](https://pkl-lang.org)

## Usage

```sh
sfenv [-e <env>] [--admin-roles] [<rules-file>]
sfenv [-e <env>] [-d <path>] [--drop <choice>] [-F] [<rules-file>]
```

Command Parameters:

- `<rules-file>`: Specifies the input YAML, JSON, or Pkl configuration file containing object and privilege specifications. Reads from standard input if omitted.
- `-e, --env ENV`: The targeted _environment_ identifier used to contextualize object and role naming conventions (default: `$SFENV` or `DEV`).
- `--admin-roles`: Generates foundational SQL to initialize administrative roles (`secadm` and `dbadm`) for the environment. Should be executed prior to applying primary environment configurations.
- `-d, --diff <rules>`: Enables differential mode, generating SQL statements strictly for modifications detected between the baseline rules file and the current rules specification.
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

Reference implementations are provided in [`examples/defaults.pkl`](https://github.com/padhia/sfenv/tree/main/examples/defaults.pkl) and [`examples/example.pkl`](https://github.com/padhia/sfenv/tree/main/examples/example.pkl). Note that `example.pkl` references `defaults.pkl` via the `modulepath:` scheme (detailed under [Integrating with Git](#integrating-with-git)). Because `sfenv` resolves module paths relative to the working directory, commands must be executed directly from within the `examples/` directory (e.g., `cd examples && sfenv example.pkl`).

## Maintaining State

`sfenv` supports differential state generation to synthesize SQL updates relative to a prior baseline configuration state. This functionality is driven by the `--diff` option, which accepts a secondary rules file representing the target baseline state. Combining this capability with a version control system like `git` facilitates automated migration generation.

### Integrating with Git

When managing rules files within a `git` repository, configuring a dedicated [`difftool`](https://git-scm.com/docs/git-difftool) enables seamless generation of delta SQL scripts between arbitrary commits or branches.

**git configuration**

Execute the following command to register `sfenv` as a custom Git `difftool`:

```sh
git config difftool.sfenv.cmd 'sfenv $REMOTE --diff $LOCAL'
```

To generate differential SQL statements reflecting uncommitted working tree changes:

```sh
git difftool -yt sfenv my-rules.yaml
```

Primary integration workflows include:

- Generating migration SQL for uncommitted local rule modifications.
- Generating environment alignment scripts across environment-specific Git branches.
- Synthesizing rollback DDL/DCL scripts by reversing target and baseline files during invocation.

```sh
sfenv previous-good.yaml --diff current-bad.yaml
```

> \[\!WARNING\] **Pkl rules configurations utilizing relative imports are incompatible with `git difftool`.** `git difftool` copies only the target diff file into an isolated temporary directory. Sibling files referenced via relative import paths (e.g., `import "defaults.pkl"`) will be missing, causing evaluation errors. Standard execution modes of `sfenv` are unaffected by this limitation, as local sibling files remain available in the active working directory.
>
> **Recommended Workaround:** Utilize Pkl's [`modulepath:`](https://pkl-lang.org/main/current/language-reference/index.html#modulepath-uris) resolution protocol instead of explicit relative file paths for any shared modules evaluated under `git difftool`:

```
import "modulepath:/defaults.pkl"   // instead of: import "defaults.pkl"
```

> `sfenv` executes `pkl eval --module-path <current-working-directory>` internally, ensuring that `modulepath:` references resolve against the command invocation directory (typically the repository root) rather than the isolated temporary file path. Configurations omitting `modulepath:` imports must avoid relative module imports or utilize standard YAML/JSON formats for compatibility with `git difftool`.
