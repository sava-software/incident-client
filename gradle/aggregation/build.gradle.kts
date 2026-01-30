plugins {
  id("software.sava.build.feature.publish-maven-central")
}

dependencies {
  nmcpAggregation(project(":incident-core"))
  nmcpAggregation(project(":incident-io"))
}

tasks.register("publishToGitHubPackages") {
  group = "publishing"
  dependsOn(
    ":incident-core:publishMavenJavaPublicationToSavaGithubPackagesRepository",
    ":incident-io:publishMavenJavaPublicationToSavaGithubPackagesRepository"
  )
}
