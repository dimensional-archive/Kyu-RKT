# RKT ![version badge](https://img.shields.io/maven-metadata/v?metadataUrl=https://dimensional.jfrog.io/artifactory/maven/com/kyubot/rkt/maven-metadata.xml)

Utilities for using rabbitmq java client in Kotlin, this was made with the amqp broker in mind.

<sub>The API is inspired by <a href="https://github.com/spec-tacles/spectacles.js/tree/master/packages/brokers">
Spec-tacles JS</a></sub>

## Installation

###### Groovy

```groovy
repositories {
  maven { url 'https://dimensional.jfrog.io/artifactory/maven' }
}

dependencies {
  implementation 'com.kyubot:rkt:VERSION'
}
```

###### Kotlin

```kotlin
repositories {
  maven("https://dimensional.jfrog.io/artifactory/maven")
}

dependencies {
  implementation("com.kyubot:rkt:VERSION")
}
```

## Usage

This is pretty WIP

###### Setup

```kotlin
import com.kyubot.rkt.Amqp

fun main() {
  val broker = Amqp {
    group("main")

    // or for a sub group
    group("main".."sub")
  }
  
  broker.connect()
}
```

###### Basic Publish & Consume

```kotlin
import com.kyubot.rkt.Queue

fun main() {
  // subscribe to some events.
  broker.on<Hello> {
    ack()
    println(data.content) // World
  }

  // in another project or something
  broker.publish(Hello("World"))
}

@Queue("hello")
@JvmInline
value class Hello(val content: String)
```

###### Data Callback

```kotlin
import com.kyubot.rkt.Queue
import com.kyubot.rkt.Callable

fun main() {
  // call some data
  val result = broker.call(Operation("add")).await()
  println(result.num)
}

@Queue("operation")
data class Operation(val name: String) : Callable<Operation.Result> {
  data class Result(val num: Int)
}
```

---

Kyu Discord 
