package sfenv

import cats.effect.unsafe.implicits.global

import java.nio.file.Files

import munit.FunSuite

class KclFormatSuite extends FunSuite:
  private def writeKcl(content: String) =
    val f = Files.createTempFile("sfenv-test-", ".k")
    Files.writeString(f, content.stripMargin)
    f

  test("simple database definition"):
    val path = writeKcl("""databases = {DB1 = {comment = "test"}}""")
    val json = KclParser(path).unsafeRunSync()
    assertEquals(json("databases")("DB1")("comment").asStr.value, "test")

  test("nested schema inside database"):
    val path = writeKcl(
      """|databases = {
         |  EDW = {
         |    schemas = {
         |      CUSTOMER = {
         |        managed = True
         |      }
         |    }
         |  }
         |}""")
    val json = KclParser(path).unsafeRunSync()
    assertEquals(json("databases")("EDW")("schemas")("CUSTOMER")("managed").asBool.value, true)

  test("multiple sections produce the same structure"):
    val path = writeKcl(
      """|databases = {
         |  DB1 = {comment = "db one"}
         |}
         |warehouses = {
         |  WH1 = {warehouse_size = "SMALL"}
         |}""")
    val json = KclParser(path).unsafeRunSync()
    assertEquals(json("databases")("DB1")("comment").asStr.value, "db one")
    assertEquals(json("warehouses")("WH1")("warehouse_size").asStr.value, "SMALL")
