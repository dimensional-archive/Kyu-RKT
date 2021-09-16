import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  idea
  java
  `maven-publish`

  kotlin("jvm") version "1.5.30"
  kotlin("plugin.serialization") version "1.5.30"
}

group = "com.kyubot"
version = "1.0.2"
val archivesBaseName = "rkt"

repositories {
  mavenCentral()
}

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-reflect:1.5.30")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2-native-mt")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.2.2")
  implementation("com.rabbitmq:amqp-client:5.13.1")
}

/* task configuration */
val sourcesJar = task<Jar>("sourcesJar") {
  archiveClassifier.set("sources")
  from(sourceSets["main"].allSource)
}

tasks.withType<KotlinCompile> {
  kotlinOptions.jvmTarget = "13"
  kotlinOptions.freeCompilerArgs = listOf(
    "-Xopt-in=kotlin.RequiresOptIn",
    "-Xopt-in=kotlinx.serialization.InternalSerializationApi",
    "-Xopt-in=kotlinx.serialization.ExperimentalSerializationApi"
  )
}

tasks.build {
  dependsOn(tasks.jar)
  dependsOn(sourcesJar)
}

tasks.publish {
  dependsOn(tasks.build)
  onlyIf {
    !System.getenv("JFROG_USERNAME").isNullOrBlank() &&
      !System.getenv("JFROG_PASSWORD").isNullOrBlank()
  }
}

/* jfrog publication */
publishing {
  repositories {
    maven {
      name = "jfrog"
      url = uri("https://dimensional.jfrog.io/artifactory/maven")
      credentials {
        username = System.getenv("JFROG_USERNAME")
        password = System.getenv("JFROG_PASSWORD")
      }
    }
  }

  publications {
    create<MavenPublication>("jfrog") {
      from(components["java"])

      group = project.group as String
      version = project.version as String
      artifactId = archivesBaseName

      artifact(sourcesJar)
    }
  }
}

