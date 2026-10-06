package triage

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.milliseconds

/** One telemetry backend (Loki, Prometheus, deploy API...). Implement this for real systems. */
interface EvidenceSource {
  /** Short label used in the report, e.g. "logs". Also the citation tag. */
  val label: String

  /** Returns plain-text facts for [service]. Throw on failure; the caller isolates it. */
  suspend fun fetch(service: String): String
}

enum class EvidenceStatus { OK, EMPTY, TIMEOUT, ERROR, UNKNOWN_SERVICE }

data class Evidence(val label: String, val status: EvidenceStatus, val body: String) {
  /** Text handed to the LLM. Failures are explicit so the model cannot silently fill gaps. */
  fun render(): String = when (status) {
    EvidenceStatus.OK -> body
    EvidenceStatus.EMPTY -> "NO DATA: source returned nothing."
    EvidenceStatus.TIMEOUT -> "UNAVAILABLE: source timed out. Do not infer anything from this source."
    EvidenceStatus.ERROR -> "UNAVAILABLE: source failed ($body). Do not infer anything from this source."
    EvidenceStatus.UNKNOWN_SERVICE -> "UNAVAILABLE: service not in registry."
  }
}

/**
 * Fetches from one source with a hard timeout. Never throws (except cancellation), so one broken
 * backend degrades the report instead of killing the whole triage.
 */
suspend fun collectEvidence(
  source: EvidenceSource,
  service: String,
  knownServices: Set<String>,
  timeoutMs: Long,
): Evidence {
  if (service !in knownServices) return Evidence(source.label, EvidenceStatus.UNKNOWN_SERVICE, "")
  return try {
    val text = withTimeout(timeoutMs.milliseconds) { source.fetch(service) }.trim()
    if (text.isEmpty()) Evidence(source.label, EvidenceStatus.EMPTY, "")
    else Evidence(source.label, EvidenceStatus.OK, text.take(MAX_EVIDENCE_CHARS))
  } catch (e: TimeoutCancellationException) {
    Evidence(source.label, EvidenceStatus.TIMEOUT, "")
  } catch (e: kotlinx.coroutines.CancellationException) {
    throw e
  } catch (e: Exception) {
    Evidence(source.label, EvidenceStatus.ERROR, e.javaClass.simpleName)
  }
}

/** Caps what reaches the prompt: bounds cost and limits prompt-injection surface from log text. */
const val MAX_EVIDENCE_CHARS = 4000
