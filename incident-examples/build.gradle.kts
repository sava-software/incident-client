dependencies {
  project(":incident-core")
  project(":incident-io")
  project(":incident-pagerduty")
  project(":incident-webhook")
}

dependencyAnalysis {
  issues {
    onAny {
      severity("ignore")
    }
  }
}
