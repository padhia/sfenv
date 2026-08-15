# Snowflake Environment Manager

`sfenv` tool helps generate Snowflake DDL and DCL statements to manage a set of Snowflake account level objects, such as Databases, Schemas, Warehouses etc and their permissions

## Technical Stack

All artifacts listed are at their latest versions

- Scala 3
- mill, build tool
- cats-effect and cats lirbaries
- fabric, for parsing JSON AST
- decline, for command-line parsing
- snakeyaml-engine, for parsing YAML to AST

The project uses `flake.nix` to create the development environment

## High-level project architecture

- The input source, YAML or Pkl files, are parsed and converted to internal form
- If an another source, referred to as base, is given by `--diff` option, it too, is converted to an internal form
    - the main internal form is then diff'ed with the base
    - generate the SQL statements depending if an existing object has been modified, is created new, or has been dropped
- If there is no other source to diff against, generate SQL statements for the objects as being created new
