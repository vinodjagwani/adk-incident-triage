# Incident Triage Agent (Google ADK for Kotlin)

A small, production-minded demo of an AI on-call assistant built with [ADK for Kotlin](https://github.com/google/adk-kotlin).

## What problem does it solve?

When a production alert fires, the on-call engineer spends 15-30 minutes jumping between tools: checking logs, metrics dashboards and the recent deploy history to work out what broke. This agent does that investigation in seconds and writes a short triage report:

- **Severity** (SEV1-3)
- **Likely root cause**, with a confidence level
- **Ruled out** (what it is not)
- **Next steps**, safest mitigation first
- **Missing evidence**, if any source was unavailable

Example: for `ALERT: checkout-api p99 latency 4s, error rate 12%` it finds that deploy `v2.14.3` cut the DB pool size from 50 to 5, cites the logs and metrics that prove it, rules out CPU and traffic, and recommends a rollback.

## How it works

```
alert
  -> intake        (LLM: extracts the service name only)
  -> gather        (3 collectors run in parallel, NO LLM: logs, metrics, recent changes)
  -> commander     (LLM: writes the report from verified evidence only)
```

The key design choice: **the LLM never sits between your data and the report.** In an earlier version, LLM "specialists" summarized the tool output and invented details (wrong versions, errors that never happened). Now plain code fetches the evidence and puts it into session state verbatim, and the LLM only reads it and writes the report.

### Safety and reliability features

| Concern | How it is handled |
|---|---|
| Hallucinated data | Collectors are deterministic code. Raw backend text goes straight to the report prompt. |
| Slow or broken backend | Per-source 5s timeout. Failures become `UNAVAILABLE: do not infer`, and the report lowers its confidence. |
| Unknown service | Validated against a registry in code. No backend calls, severity `UNKNOWN`, asks for manual check. |
| Missing evidence | Code counts how many sources returned data and caps the report's confidence (3 sources: HIGH, 2: MEDIUM, 1: LOW, 0: triage not possible). The report prompt is told the cap, and a final code step always appends a `WARNING` when evidence is incomplete, whatever the model wrote. |
| Unverifiable claims | Every claim must cite `(logs)`, `(metrics)` or `(changes)`. |
| Prompt injection via log text | Evidence is capped at 4000 chars and labelled as data to ignore instructions in. |
| Determinism | `temperature = 0` on both LLM agents. |

## Project layout

```
src/main/kotlin/triage/
  Evidence.kt   EvidenceSource interface, timeout and error isolation
  Sources.kt    MOCK logs/metrics/changes (replace with real clients)
  Agents.kt     collector agent, agent wiring, prompts
  Main.kt       entry point: terminal chat, or HTTP with --serve
src/test/kotlin/triage/
  EvidenceTest.kt   6 unit tests for the evidence logic (no API key needed)
```

## Requirements

- JDK 17 or newer (ADK's stated minimum; this project was run on JDK 25)
- Gradle (or add the Gradle wrapper with `gradle wrapper`)
- A Google Gemini API key from [Google AI Studio](https://aistudio.google.com/apikey)

## Run it

**1. Go to the project folder**

```bash
cd adk-incident-triage
```

**2. Set your API key** (current terminal only, do not commit it)

Git Bash / macOS / Linux:
```bash
export GOOGLE_API_KEY=your_key_here
```
PowerShell:
```powershell
$env:GOOGLE_API_KEY = "your_key_here"
```

**3. Start the agent**

```bash
gradle run --console=plain
```

The first run downloads dependencies and can take a few minutes.

**4. Paste an alert at the `You >` prompt**

```
ALERT: checkout-api p99 latency 4s, error rate 12%
```

Wait 10-20 seconds. You will see the intake result, the three evidence collectors, then the report from `incident_commander`. Type `exit` to quit.

## Things to try

- **Unknown service** (should say severity `UNKNOWN`, not guess):
  `ALERT: billing-worker queue depth growing`
- **Broken backend:** in `Sources.kt`, make `MockMetrics.fetch` throw (`error("down")`), rerun, and watch the report flag metrics as missing and lower its confidence.
- **Run the tests** (no API key needed):
  ```bash
  gradle test
  ```
- **Different model:**
  ```bash
  export GEMINI_MODEL=gemini-flash-latest
  gradle run --console=plain
  ```

## Human approval demo (`@Tool` + `requireConfirmation`)

`Actions.kt` has one tool that changes something, `rollbackService`, marked `@Tool(requireConfirmation = true)`. ADK pauses until a person approves. It runs in its own small agent:

```bash
gradle run --args="--rollback" --console=plain
```

Then type `Roll back checkout-api to v2.14.2`. Answer `yes` and the agent confirms the rollback started; answer `no` and nothing runs. The tool still validates the service and version in code after approval.

It is a separate agent on purpose: in the simple terminal runner, approving a call inside a multi-step workflow made the earlier steps run again. The rollback is a mock that only returns a "rollback started" result.

## HTTP mode (for a pager webhook)

```bash
gradle run --args="--serve"
```

Serves the agent on `localhost:8080` using ADK's built-in web server. This has **no authentication**: keep it on loopback or put an authenticating proxy in front before exposing it.

## Make it real

The three telemetry sources are mocks that return a scripted `checkout-api` incident. To use real systems, implement `EvidenceSource` for each backend (for example Loki or Datadog for logs, Prometheus for metrics, GitHub or your CD tool for deploys) and register them in `TriageConfig` in `Agents.kt`. Add your real service names to `KNOWN_SERVICES` in `Sources.kt`. Nothing else changes.

## Not production-ready yet

- No authentication on the HTTP server.
- Sessions are in memory only (lost on restart).
- Telemetry sources are mocks.
- Tested only with `gemini-3.1-flash-lite`.
- No persistent audit log of reports.

## Security note

Never commit your API key. Pass it through the environment. If a key is ever pasted into a chat or committed, rotate it in Google AI Studio.
