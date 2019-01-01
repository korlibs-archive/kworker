# Kworker

Workers on Multiplatform Kotlin 1.3.
Allows to process data (ByteArray, String, Int and Double) in a separate Worker/Thread in Kotlin Common.

Working on JVM, JS (Node.JS and Browser) and Native.

## Usage:

To be compatible with JS (Node.JS and Browser), you have to provide two code blocks in your main.
The first code block is the code of any Worker spawned, and the other code block the main code.
It is like this because in JS we load the whole code again in a separate isolate or process and
send messages via IPC.

### Raw

With this API, you have a Workers that can receive (`recv`) and send (`send`) messages.

```kotlin
suspend fun main(args: Array<String>) {
    WorkerFork({
        while (true) {
            val message = recv()
            println("IN WORKER ${getWorkerId()} $message")
            send(WorkerMessage("reply", "demo"))
        }
    }, {
        val worker1 = Worker()
        val worker2 = Worker()
        println("Sending messages")
        worker1.send(WorkerMessage("hello", "world"))
        worker1.send(WorkerMessage("hello", "world"))
        worker1.send(WorkerMessage("hello", "world"))

        val workerId = getWorkerId()
        println("IN MAIN $workerId ${worker1.recv()}")
        println("IN MAIN $workerId ${worker1.recv()}")
        println("IN MAIN $workerId ${worker1.recv()}")
        worker2.send(WorkerMessage("hello", "world"))
        worker2.send(WorkerMessage("hello", "world"))
        println("IN MAIN $workerId ${worker2.recv()}")
        println("IN MAIN $workerId ${worker2.recv()}")
    })
}
```

### Jobs

With this API, you register `JobDescriptor` in the Worker, and then execute the jobs remotely.
The Jobs API can be used in tests without having to register stuff, by effectively running the jobs
in the same Thread. 

```kotlin
fun main(args: Array<String>) {
    JobsMainSync({
        registerXorJob()
    }, {
        val jobs = Jobs()
        val array = byteArrayOf(1, 2, 3)
        println(array.toList())
        println(array.xor(jobs).toList())
        println(Demo.demo) // Ideally it should be 10 to be consistent with JS!
    })
}

fun JobsRegister.registerXorJob() = register(DemoXorJob)
suspend fun ByteArray.xor(jobs: Jobs): ByteArray = jobs.execute(DemoXorJob, arrayOf(this))[0] as ByteArray

object DemoXorJob : JobDescriptor {
    override suspend fun execute(args: Array<Any?>): Array<Any?> {
        val arg = args[0] as ByteArray
        val out = ByteArray(arg.size)
        for (n in out.indices) out[n] = (arg[n].toInt() xor 0x77).toByte()
        return arrayOf(out)
    }
}
```

## Gradle:

```kotlin
def kworkerVersion = "0.9.0"

repositories {
    maven { url "https://dl.bintray.com/soywiz/soywiz" }
}

dependencies {
    // For multiplatform projects
    commonMainApi "com.soywiz:kworker:$kworkerVersion"
}
```

## Design Notes

In the entry point you have to define two lambdas. One lambda is executed per work in an isolated worker,
the other lambda is executed for the main thread once.

### JVM:
* Create a new class loader sharing only platform classes
* Execute worker code in a separate Thread in the other class loader, so it is completely isolated from the main thread

### JS:
* Load all the code in a worker (similar to forking). Using `importScripts` to load all the scripts from the main page.
  Potentially using requirejs. Might require a small js file to have the same domain. (See OLD_PROOF_OF_CONCEPT)
* The entrypoint should switch  
  
### Native:
* Use Kotlin-Native Worker API.