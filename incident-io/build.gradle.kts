plugins {
  id("software.sava.build.feature.hardening")
}

testModuleInfo {
  // the wire tests serve real responses to the client's own transport
  requires("jdk.httpserver")
  requires("org.junit.jupiter.api")
  runtimeOnly("org.junit.jupiter.engine")
}

dependencies {
  project(":incident-core")
}

hardening {
  mutation.register("request") {
    targetClasses = listOf(
      "software.sava.incident.io.CreateIncidentRequestRecord",
      "software.sava.incident.io.CreateIncidentRequest",
      "software.sava.incident.io.CreateIncidentRequest\$*",
      // exact names: the suites compile test sources too, and a trailing-* prefix glob
      // such as AlertEvent* would also match AlertEventResponse* and the test classes
      "software.sava.incident.io.alert.AlertEvent",
      "software.sava.incident.io.alert.AlertEvent\$*",
      "software.sava.incident.io.alert.AlertEventRecord",
      "software.sava.incident.io.alert.AlertEventRecord\$*"
    )
    targetTests = "software.sava.incident.io.*Test*"
  }
  mutation.register("response") {
    targetClasses = listOf(
      "software.sava.incident.io.CreateIncidentResponseRecord",
      "software.sava.incident.io.CreateIncidentResponseRecord\$*",
      "software.sava.incident.io.CreateIncidentResponse",
      "software.sava.incident.io.CreateIncidentResponse\$*",
      "software.sava.incident.io.alert.AlertEventResponse",
      "software.sava.incident.io.alert.AlertEventResponse\$*",
      "software.sava.incident.io.alert.AlertEventResponseRecord",
      "software.sava.incident.io.alert.AlertEventResponseRecord\$*"
    )
    targetTests = "software.sava.incident.io.*Test*"
  }
  mutation.register("config") {
    targetClasses = listOf("software.sava.incident.io.config.*")
    // the recompiled root includes test sources; keep PIT off the tests themselves
    excludedClasses = listOf("*Test*")
    targetTests = "software.sava.incident.io.config.*Test*"
  }
  mutation.register("adapter") {
    targetClasses = listOf(
      "software.sava.incident.io.IncidentIoIncidentClient",
      "software.sava.incident.io.IncidentIoIncidentClient\$*",
      "software.sava.incident.io.IncidentIoIncidentClientFactory",
      "software.sava.incident.io.IncidentIoClient",
      "software.sava.incident.io.IncidentIoClient\$*",
      "software.sava.incident.io.IncidentIoClientImpl",
      "software.sava.incident.io.exceptions.*",
      "software.sava.incident.io.alert.IncidentIoAlertEventClient",
      "software.sava.incident.io.alert.IncidentIoAlertEventClient\$*",
      "software.sava.incident.io.alert.IncidentIoAlertEventClientImpl",
      "software.sava.incident.io.alert.IncidentIoAlertIncidentClient",
      "software.sava.incident.io.alert.IncidentIoAlertIncidentClient\$*",
      "software.sava.incident.io.alert.IncidentIoAlertIncidentClientFactory"
    )
    excludedClasses = listOf("*Test*")
    targetTests = "software.sava.incident.io.*Test*"
  }
  fuzz.register("request") {
    targetClass = "software.sava.incident.io.CreateIncidentRequestFuzz"
    // a request is a dozen short ids and human-entered strings; every escaping boundary
    // lives in small inputs
    maxLen = 2048
    // regression corpus, not bootstrap: the mutator reaches this flat NUL-delimited
    // format from scratch, but a finding still needs a committed seed replayed by check
    seedCorpus = layout.projectDirectory.dir("src/test/resources/fuzz/request")
  }
  fuzz.register("response") {
    targetClass = "software.sava.incident.io.CreateIncidentResponseFuzz"
    maxLen = 4096
    // a full incident response: the nested object/array structure would take a
    // from-scratch mutator a long time to assemble
    seedCorpus = layout.projectDirectory.dir("src/test/resources/fuzz/response")
  }
  fuzz.register("alertEvent") {
    targetClass = "software.sava.incident.io.alert.AlertEventFuzz"
    // an alert event is a title, a key, two optional strings and flat metadata; every
    // escaping boundary lives in small inputs
    maxLen = 2048
    // regression corpus, not bootstrap: the mutator reaches this flat NUL-delimited
    // format from scratch, but a finding still needs a committed seed replayed by check
    seedCorpus = layout.projectDirectory.dir("src/test/resources/fuzz/alertEvent")
  }
  fuzz.register("alertEventResponse") {
    targetClass = "software.sava.incident.io.alert.AlertEventResponseFuzz"
    maxLen = 4096
    // regression corpus, not bootstrap: a flat object of three strings is within easy
    // reach of the mutator, but a finding still needs a committed seed replayed by check
    seedCorpus = layout.projectDirectory.dir("src/test/resources/fuzz/alertEventResponse")
  }
}

//dependencyAnalysis {
//  issues {
//    onAny {
//      severity("ignore")
//    }
//  }
//}
