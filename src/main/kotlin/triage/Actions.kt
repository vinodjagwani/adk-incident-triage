package triage

import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool

/**
 * The one tool in this project that changes something, so it needs a human "yes".
 * `requireConfirmation = true` makes ADK pause the agent until a person approves the call.
 * Replace the body with a call to your real deploy system.
 */
class RollbackTools {

  @Tool(requireConfirmation = true)
  fun rollbackService(
    @Param("Service name, e.g. checkout-api") service: String,
    @Param("Version to roll back to, e.g. v2.14.2") version: String,
  ): Map<String, String> {
    // Code-level checks still apply even after a human approves.
    if (service !in KNOWN_SERVICES) return mapOf("error" to "unknown service")
    if (!VERSION_PATTERN.matches(version)) return mapOf("error" to "invalid version format")
    return mapOf("status" to "rollback started", "service" to service, "version" to version)
  }

  private companion object {
    val VERSION_PATTERN = Regex("""v\d+\.\d+\.\d+""")
  }
}
