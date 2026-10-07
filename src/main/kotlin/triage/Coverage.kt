package triage

enum class Confidence { LOW, MEDIUM, HIGH }

/**
 * How complete the evidence is. Computed in code from the collectors' statuses, so the ceiling on
 * confidence does not depend on the model remembering a rule.
 */
data class Coverage(
  val total: Int,
  val okLabels: List<String>,
  val missingLabels: List<String>,
  /** Null means nothing usable: triage is not possible. */
  val maxConfidence: Confidence?,
) {
  val complete: Boolean get() = missingLabels.isEmpty() && total > 0

  /** Text handed to the commander prompt. */
  fun render(): String = when {
    complete -> "COMPLETE: all $total sources returned data. Maximum allowed confidence: HIGH."
    maxConfidence == null ->
      "NONE: no source returned data. Triage is not possible. Severity must be UNKNOWN."
    else ->
      "INCOMPLETE: no data from ${missingLabels.joinToString(", ")}. " +
        "Maximum allowed confidence: ${maxConfidence.name}. Mark severity as provisional."
  }

  /** Warning appended to the final output by code, whatever the model wrote. Null when complete. */
  fun banner(): String? = when {
    complete -> null
    maxConfidence == null ->
      "WARNING: no evidence was available. Do not rely on this report. Check the service manually."
    else ->
      "WARNING: evidence is incomplete (no data from ${missingLabels.joinToString(", ")}). " +
        "Treat confidence as at most ${maxConfidence.name} and severity as provisional. " +
        "Check the missing sources manually."
  }
}

fun assessCoverage(statuses: Map<String, EvidenceStatus>): Coverage {
  val ok = statuses.filterValues { it == EvidenceStatus.OK }.keys.sorted()
  val missing = statuses.filterValues { it != EvidenceStatus.OK }.keys.sorted()
  val total = statuses.size
  val max = when {
    total == 0 || ok.isEmpty() -> null
    ok.size == total -> Confidence.HIGH
    ok.size >= 2 -> Confidence.MEDIUM
    else -> Confidence.LOW
  }
  return Coverage(total, ok, missing, max)
}
