plugins {
  kotlin("jvm") version "2.3.21"
  id("com.google.devtools.ksp") version "2.3.9"
  application
}

repositories { mavenCentral() }

val adk = "1.3.0"

dependencies {
  implementation("com.google.adk:google-adk-kotlin-core:$adk")
  implementation("com.google.adk:google-adk-kotlin-webserver:$adk")
  ksp("com.google.adk:google-adk-kotlin-processor:$adk")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
  testImplementation(kotlin("test"))
}

tasks.test { useJUnit() }

application { mainClass.set("triage.MainKt") }

// Forward stdin so the REPL can read the alert text.
tasks.named<JavaExec>("run") { standardInput = System.`in` }
