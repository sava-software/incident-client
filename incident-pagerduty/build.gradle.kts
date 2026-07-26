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
  mutation.register("payload") {
    targetClasses = listOf(
      "software.sava.incident.pagerduty.event.data.PagerDutyChangeEventPayloadRecord",
      "software.sava.incident.pagerduty.event.data.PagerDutyChangeEventPayloadRecord\$*",
      "software.sava.incident.pagerduty.event.data.PagerDutyEventPayloadBuilder",
      "software.sava.incident.pagerduty.event.data.PagerDutyEventPayloadRecord",
      "software.sava.incident.pagerduty.event.data.PagerDutyLinkRef",
      "software.sava.incident.pagerduty.event.data.PagerDutyLinkRefVal",
      "software.sava.incident.pagerduty.event.data.PagerDutyLinkRefVal\$*",
      "software.sava.incident.pagerduty.event.data.PagerDutyImageRef",
      "software.sava.incident.pagerduty.event.data.PagerDutyImageRefVal",
      "software.sava.incident.pagerduty.event.data.PagerDutyImageRefVal\$*"
    )
    targetTests = "software.sava.incident.pagerduty.event.*Test*"
  }
  mutation.register("response") {
    targetClasses = listOf(
      "software.sava.incident.pagerduty.event.data.PagerDutyEventResponseVal",
      "software.sava.incident.pagerduty.event.data.PagerDutyEventResponseVal\$*"
    )
    targetTests = "software.sava.incident.pagerduty.event.*Test*"
  }
  mutation.register("config") {
    targetClasses = listOf("software.sava.incident.pagerduty.config.*")
    // the recompiled root includes test sources; keep PIT off the tests themselves
    excludedClasses = listOf("*Test*")
    targetTests = "software.sava.incident.pagerduty.config.*Test*"
  }
  mutation.register("adapter") {
    targetClasses = listOf(
      "software.sava.incident.pagerduty.event.client.PagerDutyIncidentClient",
      "software.sava.incident.pagerduty.event.client.PagerDutyEventClientImpl",
      "software.sava.incident.pagerduty.event.client.PagerDutyEventClientImpl\$*"
    )
    targetTests = "software.sava.incident.pagerduty.event.*Test*"
  }
  fuzz.register("payload") {
    targetClass = "software.sava.incident.pagerduty.event.data.PagerDutyPayloadFuzz"
    // payload fields are short human-entered strings; every escaping boundary lives in
    // small inputs
    maxLen = 2048
    // regression corpus, not bootstrap: the mutator reaches this flat NUL-delimited
    // format from scratch, but a finding still needs a committed seed replayed by check
    seedCorpus = layout.projectDirectory.dir("src/test/resources/fuzz/payload")
  }
}

//dependencyAnalysis {
//  issues {
//    onAny {
//      severity("ignore")
//    }
//  }
//}
