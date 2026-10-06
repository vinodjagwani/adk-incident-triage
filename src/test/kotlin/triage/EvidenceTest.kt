package triage

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EvidenceTest {
  private val known = setOf("checkout-api")

  private fun src(body: suspend () -> String) = object : EvidenceSource {
    override val label = "fake"
    override suspend fun fetch(service: String) = body()
  }

  @Test fun ok_returnsBodyVerbatim() = runBlocking {
    val e = collectEvidence(src { "p99: 4100ms" }, "checkout-api", known, 1_000)
    assertEquals(EvidenceStatus.OK, e.status)
    assertEquals("p99: 4100ms", e.render())
  }

  @Test fun slowSource_timesOut_andSaysDoNotInfer() = runBlocking {
    val e = collectEvidence(src { delay(5_000); "late" }, "checkout-api", known, 50)
    assertEquals(EvidenceStatus.TIMEOUT, e.status)
    assertTrue("Do not infer" in e.render())
  }

  @Test fun throwingSource_isIsolated() = runBlocking {
    val e = collectEvidence(src { error("boom") }, "checkout-api", known, 1_000)
    assertEquals(EvidenceStatus.ERROR, e.status)
    assertTrue("UNAVAILABLE" in e.render())
  }

  @Test fun emptySource_isNoData() = runBlocking {
    val e = collectEvidence(src { "  " }, "checkout-api", known, 1_000)
    assertEquals(EvidenceStatus.EMPTY, e.status)
  }

  @Test fun unknownService_neverCallsBackend() = runBlocking {
    var called = false
    val e = collectEvidence(src { called = true; "x" }, "UNKNOWN", known, 1_000)
    assertEquals(EvidenceStatus.UNKNOWN_SERVICE, e.status)
    assertTrue(!called)
  }

  @Test fun hugeEvidence_isCapped() = runBlocking {
    val e = collectEvidence(src { "a".repeat(100_000) }, "checkout-api", known, 1_000)
    assertEquals(MAX_EVIDENCE_CHARS, e.body.length)
  }
}
