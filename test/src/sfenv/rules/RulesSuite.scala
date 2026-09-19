package sfenv
package rules

import cats.effect.unsafe.implicits.global

import java.nio.file.Path

import scala.collection.immutable.{SortedMap, SortedSet}

import fabric.*
import fabric.rw.*
import munit.FunSuite

class RulesSuite extends FunSuite:
  extension (json: Json)
    private def namedAs[A: RW]: A = Obj(json.asObj.value + ("name" -> str("EXAMPLE"))).as[A]

  test("named attributes accept mixed case and do not leak into extras"):
    val user = obj("DEFAULT_NAMESPACE" -> str("EDW.CUSTOMER"), "CrEaTe" -> bool(false)).namedAs[User]
    assertEquals(user.default_namespace, Some(Namespace.Schema("EDW", "CUSTOMER")))
    assertEquals(user.create, false)
    assertEquals(user.props, Props.empty)
    val pool = obj("MIN_NODES" -> num(3), "INSTANCE_FAMILY" -> str("CPU_X64_S")).namedAs[ComputePool]
    assertEquals(pool.min_nodes, 3)
    assertEquals(pool.instance_family, "CPU_X64_S")
    assertEquals(pool.props, Props.empty)

  test("first-level extras normalize without changing nested keys or values"):
    val nested = obj("Name" -> str("KeepCase"), "name" -> str("different"))
    val user   = obj("CuStOm" -> nested, "ITEMS" -> arr(nested), "LOGIN_NAME" -> str("MixedCase")).namedAs[User]
    assertEquals(user.props, Props("custom" -> nested, "items" -> arr(nested), "login_name" -> "MixedCase"))

  test("case-insensitive duplicate named attributes are rejected"):
    val error = intercept[RWException]:
      obj("default_namespace" -> str("EDW.CUSTOMER"), "DEFAULT_NAMESPACE" -> str("OTHER.PUBLIC")).namedAs[User]
    assertEquals(error.getMessage, "Duplicate object keys ignoring case: [DEFAULT_NAMESPACE, default_namespace]")

  test("case-insensitive extra collisions have deterministic diagnostics"):
    val error = intercept[RWException]:
      obj("timeout" -> num(1), "TIMEOUT" -> num(2), "Other" -> str("a"), "other" -> str("b")).namedAs[User]
    assertEquals(error.getMessage, "Duplicate object keys ignoring case: [Other, other], [TIMEOUT, timeout]")

  test("mixed-case named duplicates are rejected even without canonical spelling"):
    intercept[RWException]:
      obj("CREATE" -> bool(true), "Create" -> bool(false)).namedAs[User]

  test("duplicate attributes surface as rules parsing errors"):
    val error = intercept[AppError.RulesParsingError]:
      Rules("users:\n  PERSON:\n    create: true\n    CREATE: false\n").unsafeRunSync()
    assert(error.getMessage.contains("Duplicate object keys ignoring case: [CREATE, create]"))

  test("identifier maps retain case while nested rules objects check their own attributes"):
    val database =
      obj("SCHEMAS" -> obj("MixedSchema" -> obj("MANAGED" -> bool(true))), "TAGS" -> obj("TagName" -> str("KeepCase")))
        .namedAs[Database]
    assertEquals(database.schemas.keySet, Set("MixedSchema"))
    assertEquals(database.schemas("MixedSchema").managed, true)
    assertEquals(database.tags, Tags("TagName" -> "KeepCase"))
    val error = intercept[RWException]:
      obj("schemas" -> obj("MixedSchema" -> obj("managed" -> bool(true), "MANAGED" -> bool(false)))).namedAs[Database]
    assert(error.getMessage.contains("Duplicate object keys ignoring case: [MANAGED, managed]"))

  test("database uses constructor defaults for omitted fields"):
    val database = obj("schemas" -> obj()).namedAs[Database]
    assertEquals(database, Database(name = "EXAMPLE", schemas = SortedMap.empty, props = Props.empty))

  test("schema uses constructor defaults for omitted fields"):
    assertEquals(obj().as[Schema], Schema())

  test("user uses constructor defaults for omitted fields"):
    assertEquals(obj().namedAs[User], User(name = "EXAMPLE", props = Props.empty))

  test("compute pool uses constructor defaults for omitted fields"):
    val pool = obj().namedAs[ComputePool]
    assertEquals(pool, ComputePool(name = "EXAMPLE", props = Props.empty))
    assertEquals(pool.objMeta.props("MAX_NODES"), num(1))

  test("Fabric-derived models retain their defaults"):
    assertEquals(obj().as[Config], Config())
    assertEquals(obj().as[Account], Account())
    assertEquals(obj().namedAs[Role], Role(name = "EXAMPLE", comment = None))
    assertEquals(obj("name" -> str("CUST"), "provider" -> str("PROVIDER"), "share" -> str("SHARE")).as[Import].roles, Nil)
    assertEquals(Rules("{}").unsafeRunSync().account, Account())

  test("explicit values override defaults and known fields stay out of props"):
    val user = obj(
      "create"         -> bool(false),
      "roles"          -> arr(str("DBA")),
      "tags"           -> obj("DEPT" -> str("finance")),
      "comment"        -> str("Test user"),
      "default_role"   -> str("DBA"),
      "disabled"       -> bool(true),
      "login_name"     -> str("someone"),
      "days_to_expiry" -> num(7)
    ).namedAs[User]
    assertEquals(user.create, false)
    assertEquals(user.roles, SortedSet("DBA"))
    assertEquals(user.tags, Tags("DEPT" -> "finance"))
    assertEquals(user.comment, Some(SqlLiteral("Test user")))
    assertEquals(user.default_role, Some("DBA"))
    assertEquals(user.props, Props("disabled" -> true, "login_name" -> "someone", "days_to_expiry" -> 7))

  test("explicit schema flags and access roles are preserved"):
    val schema = obj(
      "transient"                   -> bool(true),
      "managed"                     -> bool(true),
      "acc_roles"                   -> obj("R" -> obj("table" -> arr(str("select")))),
      "data_retention_time_in_days" -> num(10)
    ).as[Schema]
    assertEquals(schema.transient, true)
    assertEquals(schema.managed, true)
    assertEquals(schema.acc_roles.size, 1)
    assertEquals(schema.props, Props("data_retention_time_in_days" -> 10))

  test("compute pool preserves snake_case settings and dependent max_nodes fallback"):
    val pool = obj("min_nodes" -> num(3), "instance_family" -> str("CPU_X64_S")).namedAs[ComputePool]
    assertEquals(pool.min_nodes, 3)
    assertEquals(pool.instance_family, "CPU_X64_S")
    assertEquals(pool.objMeta.props("MAX_NODES"), num(3))
    assertEquals(obj("max_nodes" -> num(5)).namedAs[ComputePool].max_nodes, Some(5))
    assertEquals(pool.props, Props.empty)

  test("warehouse retains optional field and pass-through behavior"):
    val warehouse = obj("warehouse_size" -> str("SMALL")).namedAs[Warehouse]
    assertEquals(warehouse.acc_roles, None)
    assertEquals(warehouse.tags, None)
    assertEquals(warehouse.comment, None)
    assertEquals(warehouse.props, Props("warehouse_size" -> "SMALL"))

  test("null defaulted fields follow Fabric default semantics"):
    assertEquals(obj("transient" -> Null, "tags" -> Null).as[Schema], Schema())

  test("empty YAML object definitions use defaults"):
    val rules = Rules("users:\n  EMPTY:\nwarehouses:\n  EMPTY:\n").unsafeRunSync()
    assertEquals(rules.users.find(_.name == "EMPTY").getOrElse(fail("Missing EMPTY")), User(name = "EMPTY", props = Props.empty))
    assertEquals(rules.warehouses.find(_.name == "EMPTY").getOrElse(fail("Missing EMPTY")).props, Props.empty)

  test("required fields without defaults remain required"):
    val error = intercept[RWException](obj().namedAs[Database])
    assert(error.getMessage.contains("schemas"))

  test("invalid explicit values are not replaced by defaults"):
    intercept[RWException](obj("transient" -> obj()).as[Schema])
    intercept[RWException](obj("roles" -> bool(false)).namedAs[User])

  test("pass-through values reject null at every nesting level"):
    List(Null, obj("nested" -> Null), arr(Null)).foreach: invalid =>
      val error = intercept[RWException](obj("custom" -> invalid).as[Schema])
      assert(error.getMessage.contains("Null is not a supported property value"))

  test("props input key is an ordinary nested property, not a namespace"):
    val schema = obj("props" -> obj("custom" -> str("value"))).as[Schema]
    assertEquals(schema.props, Props("props" -> obj("custom" -> str("value"))))

  test("nested YAML pass-through properties decode and render recursively"):
    val rules = Rules("""users:
                        |  EXAMPLE:
                        |    create: false
                        |    settings:
                        |      enabled: true
                        |      items:
                        |        - 2
                        |        - nested: [false, "two words"]
                        |    empty_object: {}
                        |    empty_array: []
                        |    namespace: {db: source, sch: public}
                        |""".stripMargin).unsafeRunSync()
    val user = rules.users.find(_.name == "EXAMPLE").getOrElse(fail("Missing EXAMPLE"))
    assertEquals(user.create, false)
    assertEquals(Props.renderValue(user.props("settings")), "(ENABLED = TRUE, ITEMS = (2, (NESTED = (FALSE, 'two words'))))")
    assertEquals(user.props("empty_object"), obj())
    assertEquals(user.props("empty_array"), arr())
    assertEquals(user.props("namespace"), obj("db" -> str("source"), "sch" -> str("public")))
    val resolvedUser = rules.resolve("DEV").users.find(_.name == Ident("EXAMPLE")).getOrElse(fail("Missing EXAMPLE user"))
    val rendered     = resolvedUser.meta.sql("USER EXAMPLE")
    assert(rendered.contains("SETTINGS = (ENABLED = TRUE, ITEMS = (2, (NESTED = (FALSE, 'two words'))))"))

  test("props models still reject serialization"):
    intercept[UnsupportedOperationException](Schema().json)

  test("example YAML parses and resolves without adding omitted fields"):
    val rules    = Rules(Path.of(sys.env("MILL_WORKSPACE_ROOT"), "examples/example.yaml")).unsafeRunSync()
    val database = rules.databases.find(_.name == "EDW").getOrElse(fail("Missing EDW"))
    assertEquals(database.transient, false)
    assertEquals(database.props("data_retention_time_in_days"), num(10))
    assertEquals(database.schemas("CUSTOMER").managed, true)
    assertEquals(rules.databases.find(_.name == "BI").getOrElse(fail("Missing BI")).schemas("CUSTOMER").transient, true)
    assertEquals(rules.users.find(_.name == "MOE").getOrElse(fail("Missing MOE")).create, false)
    assertEquals(rules.users.find(_.name == "JDOE").getOrElse(fail("Missing JDOE")).create, true)
    val resolved = rules.resolve("DEV")
    assertEquals(resolved.databases.size, 2)
    assertEquals(resolved.warehouses.size, 4)
    assertEquals(resolved.users.size, 3)
    val user = resolved.users.find(_.name == Ident("JDOE")).getOrElse(fail("Missing JDOE user"))
    assertEquals(user.defaultNamespace, Some("EDW_DEV.CUSTOMER"))
    assertEquals(user.meta.props.get("DEFAULT_NAMESPACE"), None)

  test("user and application namespaces resolve separately from properties"):
    val rules = Rules("""config:
                        |  database: "{db}_{env}"
                        |  schema: "{sch}_{env}"
                        |  app_id: "APP_{app}"
                        |users:
                        |  PERSON:
                        |    default_namespace: EDW.CUSTOMER
                        |apps:
                        |  SERVICE:
                        |    default_namespace: EDW
                        |""".stripMargin).unsafeRunSync()
    assertEquals(rules.users.find(_.name == "PERSON").getOrElse(fail("Missing PERSON")).props, Props.empty)
    val resolved = rules.resolve("QA")
    val person   = resolved.users.find(_.name == Ident("PERSON")).getOrElse(fail("Missing PERSON user"))
    val service  = resolved.users.find(_.name == Ident("APP_SERVICE")).getOrElse(fail("Missing APP_SERVICE user"))
    assertEquals(person.defaultNamespace, Some("EDW_QA.CUSTOMER_QA"))
    assertEquals(service.defaultNamespace, Some("EDW_QA"))
    assertEquals(person.meta.props("TYPE"), str("PERSON"))
    assertEquals(service.meta.props("TYPE"), str("SERVICE"))
    assertEquals(person.meta.props.get("DEFAULT_NAMESPACE"), None)
    assertEquals(service.meta.props.get("DEFAULT_NAMESPACE"), None)

  test("resolved object sets are ordered by name and schemas retain qualified names"):
    val rules = Rules("""config:
                        |  database: "{db}_{env}"
                        |  schema: "{sch}_{env}"
                        |  warehouse: "WH_{env}_{wh}"
                        |  cpool: "CP_{env}_{cp}"
                        |  fn_role: "RL_{env}_{role}"
                        |databases:
                        |  ZETA:
                        |    schemas: {ZETA: {}, ALPHA: {}}
                        |  ALPHA:
                        |    schemas: {ZETA: {}, ALPHA: {}}
                        |warehouses: {ZETA: {}, ALPHA: {}}
                        |compute_pools: {ZETA: {}, ALPHA: {}}
                        |roles: {ZETA: {}, ALPHA: {}}
                        |""".stripMargin).unsafeRunSync()
    val resolved = rules.resolve("DEV")
    assertEquals(resolved.databases.toList.map(_.name), List(Ident("ALPHA_DEV"), Ident("ZETA_DEV")))
    assertEquals(resolved.warehouses.toList.map(_.name), List(Ident("WH_DEV_ALPHA"), Ident("WH_DEV_ZETA")))
    assertEquals(resolved.computePools.toList.map(_.name), List(Ident("CP_DEV_ALPHA"), Ident("CP_DEV_ZETA")))
    assertEquals(resolved.roles.toList.map(_.name), List(Ident("RL_DEV_ALPHA"), Ident("RL_DEV_ZETA")))
    val schemas = SortedSet.from(resolved.databases.toList.flatMap(_.schemas))
    assertEquals(
      schemas.toList.map(_.name),
      List(
        (Ident("ALPHA_DEV"), Ident("ALPHA_DEV")),
        (Ident("ALPHA_DEV"), Ident("ZETA_DEV")),
        (Ident("ZETA_DEV"), Ident("ALPHA_DEV")),
        (Ident("ZETA_DEV"), Ident("ZETA_DEV"))
      )
    )

  test("resolved sets detect same-name property changes for every migrated object"):
    val previous = Rules("""databases:
                           |  EDW:
                           |    schemas: {CUSTOMER: {}}
                           |warehouses: {LOAD: {}}
                           |compute_pools: {BATCH: {}}
                           |roles: {READER: {}}
                           |""".stripMargin).unsafeRunSync().resolve("DEV")
    val current = Rules("""databases:
                          |  EDW:
                          |    comment: updated
                          |    schemas:
                          |      CUSTOMER: {managed: true}
                          |warehouses:
                          |  LOAD: {comment: updated}
                          |compute_pools:
                          |  BATCH: {comment: updated}
                          |roles:
                          |  READER: {comment: updated}
                          |""".stripMargin).unsafeRunSync().resolve("DEV")
    val statements = current.genSqls[cats.effect.IO](Some(previous))(using GenDrop.All, GenGrant.All).toList
    List(
      "ALTER DATABASE IF EXISTS EDW SET COMMENT = 'updated'",
      "ALTER SCHEMA IF EXISTS EDW.CUSTOMER ENABLE MANAGED ACCESS",
      "ALTER WAREHOUSE IF EXISTS LOAD SET COMMENT = 'updated'",
      "ALTER COMPUTE POOL IF EXISTS BATCH SET COMMENT = 'updated'",
      "ALTER ROLE IF EXISTS READER SET COMMENT = 'updated'"
    ).foreach: statement =>
      assert(statements.exists(_.contains(statement)), statement)
    assertEquals(current.genSqls[cats.effect.IO](Some(current))(using GenDrop.All, GenGrant.All).toList, Nil)

  test("access-role-only changes propagate through schema database and warehouse sets"):
    def resolve(tablePrivilege: String, warehousePrivilege: String) =
      Rules(s"""databases:
               |  EDW:
               |    schemas:
               |      CUSTOMER:
               |        acc_roles:
               |          R: {table: [$tablePrivilege]}
               |warehouses:
               |  LOAD:
               |    acc_roles:
               |      R: {warehouse: [$warehousePrivilege]}
               |""".stripMargin).unsafeRunSync().resolve("DEV")
    val previous = resolve("select", "usage")
    val current = resolve("insert", "operate")
    val statements = current.genSqls[cats.effect.IO](Some(previous))(using GenDrop.All, GenGrant.All).toList
      .filter(statement => statement.startsWith("GRANT ") || statement.startsWith("REVOKE "))
    assertEquals(
      statements,
      List(
        "REVOKE USAGE ON WAREHOUSE LOAD FROM ROLE _LOAD_R;",
        "GRANT OPERATE ON WAREHOUSE LOAD TO ROLE _LOAD_R;",
        "REVOKE SELECT ON FUTURE TABLES IN SCHEMA EDW.CUSTOMER FROM DATABASE ROLE EDW.CUSTOMER_R;",
        "REVOKE SELECT ON ALL TABLES IN SCHEMA EDW.CUSTOMER FROM DATABASE ROLE EDW.CUSTOMER_R;",
        "GRANT INSERT ON FUTURE TABLES IN SCHEMA EDW.CUSTOMER TO DATABASE ROLE EDW.CUSTOMER_R;",
        "GRANT INSERT ON ALL TABLES IN SCHEMA EDW.CUSTOMER TO DATABASE ROLE EDW.CUSTOMER_R;"
      )
    )
    assertEquals(current.genSqls[cats.effect.IO](Some(current))(using GenDrop.All, GenGrant.All).toList, Nil)

  test("keyed imports decode to named sorted objects and resolve templates"):
    val rules = Rules("""config:
                        |  database: "{db}_{env}"
                        |  fn_role: "RL_{env}_{role}"
                        |imports:
                        |  Zeta: {provider: SOURCE, share: DATA}
                        |  Alpha: {provider: SOURCE, share: DATA, roles: [READER]}
                        |""".stripMargin).unsafeRunSync()
    assertEquals(rules.imports.toList.map(_.name), List("Alpha", "Zeta"))
    assertEquals(rules.imports.head.roles, List(Ident("READER")))
    assertEquals(rules.imports.last.roles, Nil)
    assertEquals(
      rules.resolve("DEV").imports.toList,
      List(
        envr.Import(Ident("ALPHA_DEV"), Ident("SOURCE"), Ident("DATA"), List(Ident("RL_DEV_READER"))),
        envr.Import(Ident("ZETA_DEV"), Ident("SOURCE"), Ident("DATA"), Nil)
      )
    )

  test("import collection codec round-trips keyed JSON without nested names"):
    val imports = SortedSet(Import("MixedCase", Ident("SOURCE"), Ident("DATA"), List(Ident("READER"))))
    val json = imports.json(using Rules.importsRW)
    assertEquals(
      json,
      obj("MixedCase" -> obj("provider" -> str("SOURCE"), "share" -> str("DATA"), "roles" -> arr(str("READER"))))
    )
    assertEquals(json.as[SortedSet[Import]](using Rules.importsRW).toList, imports.toList)
    assertEquals(obj("imports" -> json).as[Rules].imports.toList, imports.toList)

  test("keyed imports reject explicit names including case variants"):
    List("name", "NAME", "NaMe").foreach: field =>
      val error = intercept[AppError.RulesParsingError]:
        Rules(s"imports:\n  SOURCE: {provider: PROVIDER, share: DATA, $field: OVERRIDE}\n").unsafeRunSync()
      assert(error.getMessage.contains("must not specify name"))

  test("empty import bodies still require provider and share"):
    List("imports:\n  SOURCE:\n", "imports:\n  SOURCE: {}\n", "imports:\n  SOURCE: {provider: PROVIDER}\n").foreach: document =>
      val error = intercept[AppError.RulesParsingError](Rules(document).unsafeRunSync())
      assert(error.getMessage.contains("provider") || error.getMessage.contains("share"))

  test("keyed import codec rejects arrays and scalar bodies"):
    List("imports: []", "imports: {SOURCE: 1}", "imports: {SOURCE: []}").foreach: document =>
      intercept[AppError.RulesParsingError](Rules(document).unsafeRunSync())

  test("keyed collection codec rejects ordering collisions instead of losing objects"):
    val codec = keyedSortedSetRW[Import](_.name)(using summon[RW[Import]], Ordering.by[Import, Int](_.name.length))
    val body = obj("provider" -> str("SOURCE"), "share" -> str("DATA"))
    val error = intercept[RWException]:
      obj("A" -> body, "B" -> body).as[SortedSet[Import]](using codec)
    assert(error.getMessage.contains("Duplicate object name under ordering"))

  test("ordinary sorted sets retain array encoding"):
    val values = SortedSet("B", "A")
    assertEquals(values.json, arr(str("A"), str("B")))
    assertEquals(arr(str("B"), str("A")).as[SortedSet[String]], values)

  test("all keyed rule collections preserve String names and sort by name"):
    val empty = obj("Zeta" -> obj(), "Alpha" -> obj())
    val databases = obj("Zeta" -> obj("schemas" -> obj()), "Alpha" -> obj("schemas" -> obj()))
    val rules = obj(
      "databases" -> databases,
      "warehouses" -> empty,
      "roles" -> empty,
      "users" -> empty,
      "apps" -> empty,
      "compute_pools" -> empty
    ).as[Rules]
    val expected = List("Alpha", "Zeta")
    assertEquals(rules.databases.toList.map(_.name), expected)
    assertEquals(rules.warehouses.toList.map(_.name), expected)
    assertEquals(rules.roles.toList.map(_.name), expected)
    assertEquals(rules.users.toList.map(_.name), expected)
    assertEquals(rules.apps.toList.map(_.name), expected)
    assertEquals(rules.compute_pools.toList.map(_.name), expected)
    assertEquals(rules.databases.head.props, Props.empty)
    assertEquals(rules.warehouses.head.props, Props.empty)
    assertEquals(rules.users.head.props, Props.empty)
    assertEquals(rules.apps.head.props, Props.empty)
    assertEquals(rules.compute_pools.head.props, Props.empty)

  List("databases", "warehouses", "roles", "users", "apps", "compute_pools").foreach: section =>
    test(s"$section rejects explicit nested names and non-object collections"):
      List("name", "NAME", "NaMe").foreach: field =>
        val error = intercept[RWException]:
          obj(section -> obj("Example" -> obj(field -> str("Override")))).as[Rules]
        assert(error.getMessage.contains("must not specify name"))
      intercept[RWException](obj(section -> arr()).as[Rules])
      intercept[RWException](obj(section -> obj("Example" -> num(1))).as[Rules])

  test("empty named definitions retain defaults and database schemas remain required"):
    val rules = Rules("warehouses: {Empty:}\nroles: {Empty:}\nusers: {Empty:}\napps: {Empty:}\ncompute_pools: {Empty:}\n")
      .unsafeRunSync()
    assertEquals(rules.warehouses.head.props, Props.empty)
    assertEquals(rules.roles.head.name, "Empty")
    assertEquals(rules.users.head, User("Empty", props = Props.empty))
    assertEquals(rules.apps.head, User("Empty", props = Props.empty))
    assertEquals(rules.compute_pools.head.min_nodes, 1)
    val error = intercept[AppError.RulesParsingError](Rules("databases: {Empty:}\n").unsafeRunSync())
    assert(error.getMessage.contains("schemas"))

  test("named users apps and roles preserve grants from both declaration directions"):
    val rules = Rules("""config:
                        |  fn_role: "RL_{env}_{role}"
                        |  app_id: "APP_{env}_{app}"
                        |roles:
                        |  Reader:
                        |    users: [Alice, Bob]
                        |    apps: [Service, Worker]
                        |users:
                        |  Alice: {roles: [Reader]}
                        |apps:
                        |  Service: {roles: [Reader]}
                        |""".stripMargin).unsafeRunSync()
    val resolved = rules.resolve("DEV")
    assertEquals(
      resolved.userGrants,
      SortedSet[envr.UserRole](
        (Ident("Alice"), Ident("RL_DEV_Reader")),
        (Ident("Bob"), Ident("RL_DEV_Reader")),
        (Ident("APP_DEV_Service"), Ident("RL_DEV_Reader")),
        (Ident("APP_DEV_Worker"), Ident("RL_DEV_Reader"))
      )
    )
    assertEquals(resolved.roles.head.name, Ident("RL_DEV_READER"))
    assertEquals(resolved.users.toList.map(_.name.value), List("APP_DEV_Service", "Alice"))

  test("named rule collection codecs retain serialization limitations"):
    intercept[UnsupportedOperationException]:
      SortedSet(User("Person", props = Props.empty)).json(using Rules.usersRW)

  List(
    "omitted" -> "{}",
    "empty"   -> "imports: {}\ndatabases: {}\nwarehouses: {}\nroles: {}\napps: {}\nusers: {}\ncompute_pools: {}\n"
  ).foreach: (label, document) =>
    test(s"$label map sections default to empty collections"):
      val rules = Rules(document).unsafeRunSync()
      assertEquals(rules, Rules(config = None, options = None))
      assertEquals(rules.imports, SortedSet.empty[Import])
      assertEquals(rules.databases, SortedSet.empty[Database])
      assertEquals(rules.warehouses, SortedSet.empty[Warehouse])
      assertEquals(rules.roles, SortedSet.empty[Role])
      assertEquals(rules.users, SortedSet.empty[User])
      assertEquals(rules.apps, SortedSet.empty[User])
      assertEquals(rules.compute_pools, SortedSet.empty[ComputePool])
      val resolved = rules.resolve("DEV")
      assertEquals(resolved.imports, SortedSet.empty[envr.Import])
      assertEquals(resolved.databases, SortedSet.empty[envr.Database])
      assertEquals(resolved.warehouses, SortedSet.empty[envr.Warehouse])
      assertEquals(resolved.roles, SortedSet.empty[envr.Role])
      assertEquals(resolved.users, SortedSet.empty[envr.User])
      assertEquals(resolved.computePools, SortedSet.empty[envr.ComputePool])
      assertEquals(resolved.userGrants, envr.UserGrants.empty)
