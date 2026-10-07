package triage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoverageTest {
  private fun statuses(vararg s: Pair<String, EvidenceStatus>) = mapOf(*s)
  private val ok = EvidenceStatus.OK

  @Test fun allOk_isCompleteAndHigh_noBanner() {
    val c = assessCoverage(statuses("logs" to ok, "metrics" to ok, "changes" to ok))
    assertTrue(c.complete)
    assertEquals(Confidence.HIGH, c.maxConfidence)
    assertNull(c.banner())
  }

  @Test fun oneMissing_capsAtMedium_andWarns() {
    val c = assessCoverage(
      statuses("logs" to ok, "metrics" to EvidenceStatus.ERROR, "changes" to ok)
    )
    assertEquals(Confidence.MEDIUM, c.maxConfidence)
    assertEquals(listOf("metrics"), c.missingLabels)
    assertTrue("metrics" in c.render() && "MEDIUM" in c.render())
    assertTrue("MEDIUM" in assertNotNull(c.banner()))
  }

  @Test fun twoMissing_capsAtLow() {
    val c = assessCoverage(
      statuses("logs" to ok, "metrics" to EvidenceStatus.TIMEOUT, "changes" to EvidenceStatus.EMPTY)
    )
    assertEquals(Confidence.LOW, c.maxConfidence)
  }

  @Test fun noneOk_triageNotPossible() {
    val c = assessCoverage(
      statuses("logs" to EvidenceStatus.UNKNOWN_SERVICE, "metrics" to EvidenceStatus.UNKNOWN_SERVICE)
    )
    assertNull(c.maxConfidence)
    assertTrue("UNKNOWN" in c.render())
    assertTrue("manually" in assertNotNull(c.banner()))
  }

  @Test fun emptyInput_isNotComplete() {
    val c = assessCoverage(emptyMap())
    assertTrue(!c.complete)
    assertNull(c.maxConfidence)
  }
}
