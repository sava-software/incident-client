rootProject.name = "incident-client"

pluginManagement {
  // While the plugins block below requests version 0.0.0-test, resolve sava-build from
  // its local test repo ('publishSavaBuildTestPublicationToSavaTestRepoRepository'
  // publishes there). The useModule bypasses plugin markers, which the test repo
  // does not contain.
  resolutionStrategy.eachPlugin {
    if (requested.id.id.startsWith("software.sava.build") && requested.version == "0.0.0-test") {
      useModule("software.sava:sava-build:0.0.0-test")
    }
  }
  repositories {
    maven(url = "/Users/jim/src/sava-build/build/sava-test-repo")
    gradlePluginPortal()
    mavenCentral()
    val gprUser = providers.gradleProperty("savaGithubPackagesUsername")
      .orNull?.takeIf { it.isNotBlank() }
    val gprToken = providers.gradleProperty("savaGithubPackagesPassword")
      .orNull?.takeIf { it.isNotBlank() }
    if (gprUser != null && gprToken != null) {
      maven {
        name = "savaGithubPackages"
        url = uri("https://maven.pkg.github.com/sava-software/sava-build")
        credentials {
          username = gprUser
          password = gprToken
        }
      }
    }
  }
  // Resolve sava-build from GitHub Packages. Uncomment only while depending on an
  // unpublished sava-build change, then publish, bump the versions below, re-comment.
//  if (settingsDir.resolve("../sava-build").isDirectory) {
//    includeBuild("../sava-build")
//  }
}

plugins {
//  id("software.sava.build") version "0.0.0-test"
//  id("software.sava.build.feature.jdk-provisioning") version "0.0.0-test"
  id("software.sava.build") version "21.5.15"
  id("software.sava.build.feature.jdk-provisioning") version "21.5.15"
}

javaModules {
  directory(".") {
    group = "software.sava"
    plugin("software.sava.build.java-module")
  }
}
