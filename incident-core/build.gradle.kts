plugins {
  id("software.sava.build.feature.hardening")
}

testModuleInfo {
  // JulRecorder captures System.Logger output through its JUL backend
  requires("java.logging")
  requires("org.junit.jupiter.api")
  runtimeOnly("org.junit.jupiter.engine")
}

dependencies {
  project(":incident-core")
}

hardening {
  generateTestSupport = true
  // the recompiled root includes test sources, so package globs must exclude the tests
  // themselves or PIT mutates them too
  mutation.register("config") {
    targetClasses = listOf("software.sava.incident.core.config.*")
    excludedClasses = listOf("*Test*")
    targetTests = "software.sava.incident.core.config.*Test*"
  }
  mutation.register("json") {
    targetClasses = listOf("software.sava.incident.core.json.*")
    excludedClasses = listOf("*Test*")
    targetTests = "software.sava.incident.core.json.*Test*"
  }
  mutation.register("api") {
    targetClasses = listOf("software.sava.incident.core.api.*")
    excludedClasses = listOf("*Test*")
    targetTests = "software.sava.incident.core.api.*Test*"
  }
}

//dependencyAnalysis {
//  issues {
//    onAny {
//      severity("ignore")
//    }
//  }
//}
