plugins {
  id("software.sava.build.feature.publish-maven-central")
}

val incidentModules = setOf(
  "incident-core",
  "incident-io",
  "incident-pagerduty",
  "incident-webhook"
)

//dependencies {
//  for (module in incidentModules) {
//    nmcpAggregation(project(":$module"))
//  }
//}

tasks.register("publishToGitHubPackages") {
  group = "publishing"
  val publishTasks = incidentModules.map { ":$it:publishMavenJavaPublicationToSavaGithubPackagesPublishRepository" }
  dependsOn(publishTasks)
}
