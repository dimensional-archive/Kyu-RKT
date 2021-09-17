dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.5.30")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2-native-mt")
    implementation("com.rabbitmq:amqp-client:5.13.1")

    api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.2.2")
    api("io.github.microutils:kotlin-logging-jvm:2.0.11")
    api(project(":rkt-core"))
}
