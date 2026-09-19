package sfenv
package envr

import scala.collection.immutable.SortedSet

import munit.FunSuite

class DatabaseSuite extends FunSuite:
  test("create - basic"):
    assertEquals(Database("EDW_DEV").create.sqls(0), "CREATE DATABASE IF NOT EXISTS EDW_DEV")

  test("create - grant"):
    val sqls = Database("EDW_DEV").create.sqls
    assertEquals(sqls.length, 2)
    assertEquals(sqls(1), "GRANT USAGE, CREATE DATABASE ROLE ON DATABASE EDW_DEV TO ROLE DEV_SECADM")

  test("create - options"):
    val actual = Database(
      "EDW_DEV",
      transient = true,
      tags = Tags("TAG1" -> "TAG1 VALUE", "TAG2" -> "TAG2 VALUE"),
      comment = Some("EDW Core database"),
      props = Props("data_retention_time_in_days" -> 10)
    ).create.sqls(0)
    val expected = """|CREATE TRANSIENT DATABASE IF NOT EXISTS EDW_DEV
                      |    COMMENT = 'EDW Core database'
                      |    DATA_RETENTION_TIME_IN_DAYS = 10
                      |    WITH TAG TAG1 = 'TAG1 VALUE', TAG2 = 'TAG2 VALUE'""".stripMargin
    assertEquals(actual, expected)

  test("drop"):
    val expected = List(
      "REVOKE USAGE, CREATE DATABASE ROLE ON DATABASE EDW_DEV FROM ROLE DEV_SECADM",
      "DROP DATABASE IF EXISTS EDW_DEV"
    )
    assertEquals(Database("EDW_DEV").drop.sqls, expected)

  test("alter"):
    val db1      = Database("EDW_DEV", comment = Some("EDW core database"), props = Props("data_retention_time_in_days" -> 10))
    val db2      = Database("EDW_DEV", comment = Some("EDW core database2"))
    val expected = List(
      "ALTER DATABASE IF EXISTS EDW_DEV SET COMMENT = 'EDW core database2'",
      "ALTER DATABASE IF EXISTS EDW_DEV UNSET DATA_RETENTION_TIME_IN_DAYS"
    )
    assertEquals(db2.update(db1).sqls, expected)

  test("schema creation and dropping follow name order"):
    val first  = Schema("EDW_DEV", "ALPHA").getOrElse(fail("schema construction failed"))
    val second = Schema("EDW_DEV", "ZETA").getOrElse(fail("schema construction failed"))
    val database = Database("EDW_DEV", schemas = SortedSet(second, first))
    assertEquals(database.schemas.toList.map(_.name.sch), List(Ident("ALPHA"), Ident("ZETA")))
    assertEquals(
      database.create.sqls.takeRight(2),
      List("CREATE SCHEMA IF NOT EXISTS EDW_DEV.ALPHA", "CREATE SCHEMA IF NOT EXISTS EDW_DEV.ZETA")
    )
    assertEquals(
      database.drop.sqls.take(2),
      List("DROP SCHEMA IF EXISTS EDW_DEV.ZETA", "DROP SCHEMA IF EXISTS EDW_DEV.ALPHA")
    )

  test("database set diff detects changes inside a same-name schema"):
    val schema = Schema("EDW_DEV", "CUSTOMER").getOrElse(fail("schema construction failed"))
    val previous = Database("EDW_DEV", schemas = SortedSet(schema))
    val current = previous.copy(schemas = SortedSet(schema.copy(managed = true)))
    assertEquals(
      summon[CDA[SortedSet[Database]]].update(SortedSet(current))(SortedSet(previous)).sqls,
      List("ALTER SCHEMA IF EXISTS EDW_DEV.CUSTOMER ENABLE MANAGED ACCESS")
    )
    assertEquals(summon[CDA[SortedSet[Database]]].update(SortedSet(current))(SortedSet(current)).sqls, Nil)

  test("database set diff adds removes and recreates schemas"):
    val removed = Schema("EDW_DEV", "REMOVED").getOrElse(fail("schema construction failed"))
    val added = Schema("EDW_DEV", "ADDED").getOrElse(fail("schema construction failed"))
    val previous = Database("EDW_DEV", schemas = SortedSet(removed))
    val current = previous.copy(schemas = SortedSet(added))
    assertEquals(
      summon[CDA[SortedSet[Database]]].update(SortedSet(current))(SortedSet(previous)).sqls,
      List("DROP SCHEMA IF EXISTS EDW_DEV.REMOVED", "CREATE SCHEMA IF NOT EXISTS EDW_DEV.ADDED")
    )
    val transient = current.copy(schemas = SortedSet(added.copy(transient = true)))
    assertEquals(
      summon[CDA[SortedSet[Database]]].update(SortedSet(transient))(SortedSet(current)).sqls,
      List("DROP SCHEMA IF EXISTS EDW_DEV.ADDED", "CREATE TRANSIENT SCHEMA IF NOT EXISTS EDW_DEV.ADDED")
    )
