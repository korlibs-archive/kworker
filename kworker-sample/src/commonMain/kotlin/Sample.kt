import com.soywiz.kworker.*
import kotlin.coroutines.*

fun main(args: Array<String>) {
    //JobsMainSync({
    //    //Demo.demo = 20 // @TODO: Disallowed on Kotlin/Native
    //    registerXorJob()
    //}, {
    //    val jobs = Jobs()
    //    val array = byteArrayOf(1, 2, 3)
    //    println(array.toList())
    //    println(array.xor(jobs).toList())
    //    println(Demo.demo) // Ideally it should be 10 to be consistent with JS!
    //})

    WorkerInterfaceImpl.runEntry(EmptyCoroutineContext) {
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

            // @TODO: Kotlin.JS BUG! getWorkerId() is not appended
            //println("IN MAIN ${getWorkerId()} ${worker1.recv()}")
            //println("IN MAIN ${getWorkerId()} ${worker1.recv()}")
            //println("IN MAIN ${getWorkerId()} ${worker1.recv()}")
            //worker2.send(WorkerMessage("hello", "world"))
            //worker2.send(WorkerMessage("hello", "world"))
            //println("IN MAIN ${getWorkerId()} ${worker2.recv()}")
            //println("IN MAIN ${getWorkerId()} ${worker2.recv()}")
        })
    }
}

object DemoXorJob : JobDescriptor {
    override suspend fun execute(args: Array<Any?>): Array<Any?> {
        val arg = args[0] as ByteArray
        val out = ByteArray(arg.size)
        for (n in out.indices) out[n] = (arg[n].toInt() xor 0x77).toByte()
        return arrayOf(out)
    }
}

fun JobsRegister.registerXorJob() = register(DemoXorJob)
suspend fun ByteArray.xor(jobs: Jobs): ByteArray = jobs.execute(DemoXorJob, arrayOf(this))[0] as ByteArray

object Demo {
    var demo = 10
}
