plugins {
  id("software.sava.build.feature.publish-maven-central")
}

dependencies {
  for (module in setOf("incident-core", "incident-io", "incident-pagerduty", "incident-webhook")) {
    centralPortalAggregation(project(":$module"))
  }
}
