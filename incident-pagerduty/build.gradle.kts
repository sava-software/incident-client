testModuleInfo {
  requires("jdk.httpserver")
  requires("org.junit.jupiter.api")
  runtimeOnly("org.junit.jupiter.engine")
}

dependencies {
  project(":incident-core")
}

//dependencyAnalysis {
//  issues {
//    onAny {
//      severity("ignore")
//    }
//  }
//}
