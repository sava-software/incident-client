plugins {
  id("software.sava.build.feature.hardening")
}

testModuleInfo {
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
      "software.sava.incident.io.CreateIncidentRequest\$*"
    )
    targetTests = "software.sava.incident.io.*Test*"
  }
  mutation.register("response") {
    targetClasses = listOf(
      "software.sava.incident.io.CreateIncidentResponseRecord",
      "software.sava.incident.io.CreateIncidentResponseRecord\$*",
      "software.sava.incident.io.CreateIncidentResponse",
      "software.sava.incident.io.CreateIncidentResponse\$*"
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
      "software.sava.incident.io.exceptions.*"
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
}

//dependencyAnalysis {
//  issues {
//    onAny {
//      severity("ignore")
//    }
//  }
//}
