package triage

/**
 * Mock telemetry for one scenario: checkout-api degraded after a 14:02 deploy that shrank the DB
 * pool. Replace these three classes with real clients (Loki, Prometheus, your deploy API);
 * nothing else in the project changes.
 */
val KNOWN_SERVICES = setOf("checkout-api", "search-api", "auth-service")

class MockLogs : EvidenceSource {
  override val label = "logs"
  override suspend fun fetch(service: String) = when (service) {
    "checkout-api" -> """
      412x 14:05-14:35 HikariPool-1 - Connection is not available, request timed out after 30000ms
      388x 14:05-14:35 SQLTransientConnectionException: pool exhausted (active=5, max=5)
        9x 14:20-14:22 upstream payments-gateway 502 (retried OK)
    """.trimIndent()
    else -> ""
  }
}

class MockMetrics : EvidenceSource {
  override val label = "metrics"
  override suspend fun fetch(service: String) = when (service) {
    "checkout-api" -> """
      p99_latency: 180ms (13:30) -> 4100ms (14:30)
      error_rate: 0.2% -> 12.4%
      cpu: 41% -> 43%
      db_connections_active: 22 -> 5 (flat at 5 since 14:05)
      request_rate: 820 rps -> 835 rps
    """.trimIndent()
    else -> "p99_latency: 90ms (stable)\nerror_rate: 0.1% (stable)"
  }
}

class MockChanges : EvidenceSource {
  override val label = "changes"
  override suspend fun fetch(service: String) = when (service) {
    "checkout-api" -> """
      14:02 deploy v2.14.3 by j.rivera "tune resources" (diff: db.pool.maxSize 50 -> 5)
      09:40 deploy v2.14.2 by a.chen "copy fix on receipt page"
    """.trimIndent()
    else -> ""
  }
}
