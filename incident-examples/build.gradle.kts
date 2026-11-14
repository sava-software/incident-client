dependencies {
  project(":incident-core")
  project(":incident-io")
  project(":incident-pagerduty")
  project(":incident-webhook")
}

// Examples are not held to the module-directive scope check. The plugin is present only
// under '-PdependencyAudit', so its extension is configured when it is applied: a typed
// dependencyAnalysis { } block would not compile without it.
pluginManager.withPlugin("com.autonomousapps.dependency-analysis") {
  extensions.getByName("dependencyAnalysis").withGroovyBuilder {
    "issues" {
      "onAny" {
        "severity"("ignore")
      }
    }
  }
}
