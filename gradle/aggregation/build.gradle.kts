plugins {
  id("software.sava.build.feature.publish-maven-central")
}

dependencies {
  nmcpAggregation(project(":incident-core"))
//  nmcpAggregation(project(":incident-io"))
  nmcpAggregation(project(":incident-io"))
//  nmcpAggregation(project(":incident-io"))
  nmcpAggregation(project(":incident-pagerduty"))
}

tasks.register("publishToGitHubPackages") {
  group = "publishing"
  dependsOn(
    ":incident-core:publishMavenJavaPublicationToSavaGithubPackagesPublishRepository",
    ":incident-pagerduty:publishMavenJavaPublicationToSavaGithubPackagesPublishRepository"
  )
}
