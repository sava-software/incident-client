dependencies {
  project(":incident-core")
  project(":incident-io")
  project(":incident-pagerduty")
}

dependencyAnalysis {
  issues {
    onAny {
      severity("ignore")
    }
  }
}
