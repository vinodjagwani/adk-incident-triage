package triage

import com.google.adk.kt.runners.ReplRunner
import com.google.adk.kt.webserver.AdkApiServer
import com.google.adk.kt.webserver.AdkServerConfig

fun main(args: Array<String>) {
  require(!System.getenv("GOOGLE_API_KEY").isNullOrBlank()) { "Set GOOGLE_API_KEY" }
  // --rollback: chat with the approval-gated rollback agent instead of the triage pipeline
  val agent = if ("--rollback" in args) buildRollbackAgent() else buildTriageAgent()
  if ("--serve" in args) {
    // POST alerts from a pager webhook. Loopback + unauthenticated: add auth before exposing.
    AdkApiServer(AdkServerConfig.inMemory(agent)).start(wait = true)
  } else {
    ReplRunner(agent).start() // paste an alert, get a report
  }
}
