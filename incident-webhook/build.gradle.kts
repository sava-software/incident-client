plugins {
  id("software.sava.build.feature.hardening")
}

testModuleInfo {
  requires("jdk.httpserver")
  requires("org.junit.jupiter.api")
  runtimeOnly("org.junit.jupiter.engine")
}

dependencies {
  project(":incident-core")
}

hardening {
  mutation.register("format") {
    targetClasses = listOf(
      "software.sava.incident.webhook.WebhookFormat",
      "software.sava.incident.webhook.WebhookFormats",
      "software.sava.incident.webhook.WebhookFormats\$*",
      "software.sava.incident.webhook.TelegramTextFormat"
    )
    targetTests = "software.sava.incident.webhook.*Test*"
  }
  mutation.register("config") {
    targetClasses = listOf("software.sava.incident.webhook.config.*")
    // the recompiled root includes test sources; keep PIT off the tests themselves
    excludedClasses = listOf("*Test*")
    targetTests = "software.sava.incident.webhook.config.*Test*"
  }
  mutation.register("adapter") {
    targetClasses = listOf(
      "software.sava.incident.webhook.WebhookIncidentClient",
      "software.sava.incident.webhook.WebhookClientImpl",
      "software.sava.incident.webhook.WebhookClient",
      "software.sava.incident.webhook.WebhookClient\$*",
      "software.sava.incident.webhook.BaseWebhookIncidentClientFactory",
      "software.sava.incident.webhook.WebhookIncidentClientFactory",
      "software.sava.incident.webhook.SlackWebhookIncidentClientFactory",
      "software.sava.incident.webhook.TelegramWebhookIncidentClientFactory",
      "software.sava.incident.webhook.exceptions.*"
    )
    excludedClasses = listOf("*Test*")
    targetTests = "software.sava.incident.webhook.*Test*"
  }
  fuzz.register("format") {
    targetClass = "software.sava.incident.webhook.WebhookFormatFuzz"
    // alert fields are short human-entered strings; every escaping boundary lives in
    // small inputs
    maxLen = 2048
    // regression corpus, not bootstrap: the mutator reaches this flat NUL-delimited
    // format from scratch, but a finding still needs a committed seed replayed by check
    seedCorpus = layout.projectDirectory.dir("src/test/resources/fuzz/format")
  }
}
