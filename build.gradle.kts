import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java

    kotlin("jvm") version "1.5.30" apply false
    kotlin("plugin.serialization") version "1.5.30" apply false
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "maven-publish")
    apply(plugin = "kotlin")
    apply(plugin = "kotlinx-serialization")

    repositories {
        mavenCentral()
    }

    group = "com.kyubot"
    version = "2.0.2"

    /* task configuration */
    val sourcesJar = task<Jar>("sourcesJar") {
        archiveClassifier.set("sources")
        from(sourceSets["main"].allSource)
    }

    tasks.withType<KotlinCompile> {
        sourceCompatibility = "16"
        targetCompatibility = "16"
        kotlinOptions {
            jvmTarget = "16"
            incremental = true
            freeCompilerArgs = listOf(
                "-Xopt-in=kotlin.RequiresOptIn",
                "-Xopt-in=kotlin.ExperimentalStdlibApi",
                "-Xopt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-Xopt-in=kotlinx.serialization.InternalSerializationApi",
                "-Xopt-in=kotlinx.serialization.ExperimentalSerializationApi",
                "-Xopt-in=kotlin.time.ExperimentalTime",
                "-Xinline-classes",
            )
        }
    }

    tasks.build {
        dependsOn(tasks.jar)
        dependsOn(sourcesJar)
    }

    tasks.withType<PublishToMavenRepository> {
        dependsOn(tasks.build)
        onlyIf {
            !System.getenv("JFROG_USERNAME").isNullOrBlank() &&
                !System.getenv("JFROG_PASSWORD").isNullOrBlank()
        }
    }

    /* jfrog publication */
    configure<PublishingExtension> {
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
                artifactId = project.name

                artifact(sourcesJar)
            }
        }
    }
}
