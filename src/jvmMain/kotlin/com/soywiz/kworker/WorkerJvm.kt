package com.soywiz.kworker

import kotlinx.coroutines.*
import java.util.NoSuchElementException
import java.util.concurrent.*
import kotlin.collections.LinkedHashSet
import kotlin.collections.isNotEmpty
import kotlin.collections.minusAssign
import kotlin.collections.plusAssign
import kotlin.collections.toList
import kotlin.coroutines.*
import kotlin.reflect.*

private var workerLambda: (suspend WorkerChannel.() -> Unit)? = null

internal class MyWorkerChannel : WorkerChannel {
    private var terminating = false
    val messages = ConcurrentLinkedDeque<WorkerMessage>()

    override suspend fun send(message: WorkerMessage) {
        messages.add(message)
    }

    override suspend fun recv(): WorkerMessage {
        while (true) {
            if (terminating) throw WorkerInterruptException()
            try {
                if (messages.isNotEmpty()) {
                    return messages.remove()
                }
            } catch (e: NoSuchElementException) {
            }
            delay(1L)
        }
    }

    override fun terminate() {
        terminating = true
    }
}

//fun forkClassLoader(): ClassLoader {
//    val originalClassLoader = Thread.currentThread().contextClassLoader
//    return object : ClassLoader() {
//        private  val classes = LinkedHashMap<String, Class<*>>()
//
//        override fun loadClass(name: String): Class<*> {
//
//            return classes[name] ?: try {
//                classes[name] = ClassLoader.getPlatformClassLoader().loadClass(name)
//                classes[name]!!
//            } catch (e: ClassNotFoundException) {
//                val bytes = originalClassLoader.getResourceAsStream(name.replace('.', '/') + ".class").readBytes()
//                classes[name] = defineClass(name, bytes, 0, bytes.size)
//                classes[name]!!
//            }
//        }
//    }
//}
//internal class ForkedEntryPoint(
//    val proxyRunnable: Runnable
//) : Runnable {
//    override fun run() {
//        proxyRunnable.run()
//    }
//}
//
//fun <T : Any> T.toClassLoader(classLoader: ClassLoader): Any {
//    val originalInstance = this
//    val oldInterfaces = originalInstance::class.java.interfaces
//    val newInterfaces = oldInterfaces.map {
//        classLoader.loadClass(it.name)
//    }
//    return Proxy.newProxyInstance(classLoader, newInterfaces.toTypedArray()) { proxy, method, args ->
//        val originalMethod = originalInstance.javaClass.methods.first { it.name == method.name }
//        originalMethod.invoke(originalInstance, *args.map { it.toClassLoader(classLoader) }.toTypedArray())
//    }
//}

actual val WorkerInterfaceImpl: WorkerInterface = object : WorkerInterface() {
    override suspend fun getWorkerId(): Int {
        //println("$threadIdToWorkerId :: ${Thread.currentThread().id}")

        return threadIdToWorkerId[Thread.currentThread().id] ?: 0
    }

    private val workers = LinkedHashSet<WorkerChannel>()
    private val threadIdToWorkerId = ConcurrentHashMap<Long, Int>()

    private var lastWorkerId = 1

    override suspend fun Worker(): WorkerChannel {
        val workerId = lastWorkerId++
        val toWorker: WorkerChannel = MyWorkerChannel()
        val fromWorker: WorkerChannel = MyWorkerChannel()

        //val forkedClassLoader = forkClassLoader()
        //val entryPoint = forkedClassLoader.loadClass(ForkedEntryPoint::class.java.name).declaredConstructors.first().newInstance(
        //    Runnable {
        //        runBlocking(newSingleThreadContext("Worker")) {
        //            val toWorker: WorkerChannel = toWorker.toClassLoader(forkedClassLoader) as WorkerChannel
        //            val fromWorker: WorkerChannel = fromWorker.toClassLoader(forkedClassLoader) as WorkerChannel
        //
        //            try {
        //                val workerLambda = workerLambda
        //                ((workerLambda?.toClassLoader(forkedClassLoader) ?: error("Must call WorkerFork at the beginning")) as (suspend WorkerChannel.() -> Unit)).invoke(object : WorkerChannel {
        //                    override fun terminate() {
        //                        toWorker.terminate()
        //                        fromWorker.terminate()
        //                    }
        //
        //                    override suspend fun recv(): WorkerMessage = toWorker.recv()
        //                    override suspend fun send(message: WorkerMessage) = fromWorker.send(message)
        //                })
        //            } catch (e: WorkerInterruptException) {
        //            } catch (e: InterruptedException) {
        //            }
        //        }
        //    }
        //) as Runnable

        val entryPoint = Runnable {
            runEntry(newSingleThreadContext("Worker")) {
                threadIdToWorkerId[Thread.currentThread().id] = workerId
                try {
                    val workerLambda = workerLambda
                    (workerLambda ?: error("Must call WorkerFork at the beginning")).invoke(object : WorkerChannel {
                        override fun terminate() {
                            toWorker.terminate()
                            fromWorker.terminate()
                        }

                        override suspend fun recv(): WorkerMessage = toWorker.recv()
                        override suspend fun send(message: WorkerMessage) = fromWorker.send(message)
                    })
                } catch (e: WorkerInterruptException) {
                } catch (e: InterruptedException) {
                } finally {
                    threadIdToWorkerId.remove(Thread.currentThread().id)
                }
            }
        }

        val thread = Thread(entryPoint).apply {
            name = "Worker"
            isDaemon = true
            start()
        }

        val worker = object : WorkerChannel {
            override suspend fun recv(): WorkerMessage = fromWorker.recv()
            override suspend fun send(message: WorkerMessage) = toWorker.send(message)
            override fun terminate() {
                toWorker.terminate()
                fromWorker.terminate()
                workers -= this
            }
        }

        workers += worker

        return worker
    }

    override fun getClassName(clazz: KClass<*>): String {
        return clazz.java.name
    }

    private var calledWorkerFork = false
    override suspend fun WorkerFork(worker: suspend WorkerChannel.() -> Unit, main: suspend CoroutineScope.() -> Unit) {
        if (calledWorkerFork) error("WorkerFork must be called just once")
        calledWorkerFork = true
        workerLambda = worker
        runBlocking(newSingleThreadContext("Main")) {
            try {
                main()
            } catch (e: InterruptedException) {
            } catch (e: WorkerInterruptException) {
            } finally {
                for (w in workers.toList()) w.terminate()
            }
        }
    }

    override fun runEntry(context: CoroutineContext, callback: suspend () -> Unit) {
        runBlocking(context) { callback() }
    }
}

/*
class WorkerExec(val jobs: ConcurrentLinkedDeque<Runnable>) : Runnable {
    override fun run() {
        while (true) {
            try {
                while (jobs.isNotEmpty()) {
                    val job = jobs.remove()
                    job.run()
                }
                Thread.sleep(1L) // @TODO: Proper primitives to notify about new jobs
            } catch (e: NoSuchElementException) {
            }
        }
    }
}

internal actual val WorkerImpl: WorkerCls = object : WorkerCls() {
    private lateinit var anotherClassLoader: ClassLoader
    private val works: ConcurrentLinkedDeque<Runnable> = ConcurrentLinkedDeque<Runnable>()

    override fun callAfterRegister() {
        val originalClassLoader = Thread.currentThread().contextClassLoader
        anotherClassLoader = object : ClassLoader() {
            override fun loadClass(name: String): Class<*> {
                //println("REQUEST CLASS: $name")
                return try {
                    ClassLoader.getPlatformClassLoader().loadClass(name)
                } catch (e: ClassNotFoundException) {
                    //println(" ---> REDEFINE: $name")
                    val bytes = originalClassLoader.getResourceAsStream(name.replace('.', '/') + ".class").readBytes()
                    defineClass(name, bytes, 0, bytes.size)
                }
            }
        }

        //val oriClazz = originalClassLoader.loadClass(WorkerExec::class.java.name)
        val clazz = anotherClassLoader.loadClass(WorkerExec::class.java.name)
        val execInstance = clazz.declaredConstructors.first().newInstance(works) as Runnable
        Thread(execInstance).apply {
            name = "Worker"
            isDaemon = true
            contextClassLoader = anotherClassLoader
            start()
        }
    }

    override suspend fun <T, R> executeInWorker(work: WorkDescriptor<T, R>, arg: T): R {
        var result: R? = null
        var resultException: Throwable? = null
        works += Runnable {
            runBlocking {
                //println(Thread.currentThread())
                try {
                    result = work.executeInternal(arg)
                } catch (e: Throwable) {
                    resultException = e
                }
            }
        }
        // @TODO: See how to notify immediately the main thread
        // @TODO: without using the Worker thread (that also has a different ClassLoader)
        var sleepMs = 0L
        while (true) {
            if (resultException != null) throw resultException!!
            if (result != null) return result!!
            delay(sleepMs)
            sleepMs++
        }
    }

    override suspend fun isWorker(): Boolean {
        return super.isWorker()
    }
}
*/
