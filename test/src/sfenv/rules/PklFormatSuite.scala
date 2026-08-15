package sfenv
package rules

import cats.effect.unsafe.implicits.global

import java.nio.file.Files

import munit.FunSuite

class PklFormatSuite extends FunSuite:
  private def writePkl(content: String) =
    val f = Files.createTempFile("sfenv-test-", ".pkl")
    Files.writeString(f, content)
    f

  test("entry format [\"KEY\"] appears in getProperties"):
    val path = writePkl("""databases { ["ENTRY_DB"] { comment = "entry" } }""")
    val json = PklParser(path).unsafeRunSync()
    assertEquals(json("databases")("ENTRY_DB")("comment").asStr.value, "entry")

  test("property format KEY appears in getProperties"):
    val path = writePkl("""databases { PROP_DB { comment = "prop" } }""")
    val json = PklParser(path).unsafeRunSync()
    assertEquals(json("databases")("PROP_DB")("comment").asStr.value, "prop")

  test("both formats in same object produce the same structure"):
    val path = writePkl("""
      databases {
        ["ENTRY_DB"] { comment = "entry" }
        PROP_DB      { comment = "prop"  }
      }
    """)
    val json = PklParser(path).unsafeRunSync()
    assertEquals(json("databases")("ENTRY_DB")("comment").asStr.value, "entry")
    assertEquals(json("databases")("PROP_DB")("comment").asStr.value, "prop")
