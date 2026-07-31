package sfenv

import munit.FunSuite

class ProcessDropsSuite extends FunSuite:
  test("All - never masks, regardless of locality"):
    assertEquals(ProcessDrops.All.useMask(isForeign = true), false)
    assertEquals(ProcessDrops.All.useMask(isForeign = false), false)

  test("Never - always masks, regardless of locality"):
    assertEquals(ProcessDrops.Never.useMask(isForeign = true), true)
    assertEquals(ProcessDrops.Never.useMask(isForeign = false), true)

  test("NonLocal - masks only local objects, keeps foreign ones"):
    assertEquals(ProcessDrops.NonLocal.useMask(isForeign = true), false)
    assertEquals(ProcessDrops.NonLocal.useMask(isForeign = false), true)
