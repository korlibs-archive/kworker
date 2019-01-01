package com.soywiz.kworker

import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.reflect.*

class WorkerInterruptException : RuntimeException()

class WorkerMessage private constructor(@Suppress("UNUSED_PARAMETER") dummy: Boolean, val type: String, val args: Array<out Any?>) {
    companion object {
        operator fun invoke(type: String, vararg args: Any?): WorkerMessage {
            for ((index, arg) in args.withIndex()) when (arg) {
                null, is String, is Int, is Double, is ByteArray -> Unit
                else -> error("WorkerMessage args can only have null, String, Int, Double and ByteArray as arguments, but found '$arg' at index=$index.")
            }
            return WorkerMessage(true, type, args)
        }
    }

    override fun toString(): String = "WorkerMessage[$type](${args.joinToString(", ")})"
}

interface WorkerChannel {
    suspend fun send(message: WorkerMessage)
    suspend fun recv(): WorkerMessage
    fun terminate()
}

internal class MyWorkerChannel2 : WorkerChannel {
    private var terminating = false
    val messages = ArrayList<WorkerMessage>()

    override suspend fun send(message: WorkerMessage) {
        messages.add(message)
    }

    override suspend fun recv(): WorkerMessage {
        while (true) {
            if (terminating) throw WorkerInterruptException()
            try {
                if (messages.isNotEmpty()) {
                    return messages.removeAt(0)
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

class WorkerIdContext(val workerId: Int) : CoroutineContext.Element {
    companion object Key : CoroutineContext.Key<WorkerIdContext>
    override val key: CoroutineContext.Key<*> = Key
}

abstract class WorkerInterface {
    open suspend fun getWorkerId(): Int = coroutineContext[WorkerIdContext.Key]?.workerId ?: 0
    private var workerCode: (suspend WorkerChannel.() -> Unit)? = null

    private val workers = LinkedHashSet<WorkerChannel>()

    private var lastWorkerId = 1

    open suspend fun Worker(): WorkerChannel {
        val workerId = lastWorkerId++

        val toWorker: WorkerChannel = MyWorkerChannel2()
        val fromWorker: WorkerChannel = MyWorkerChannel2()

        CoroutineScope(coroutineContext + WorkerIdContext(workerId)).launch {
            try {
                workerCode?.invoke(object : WorkerChannel {
                    override fun terminate() {
                        toWorker.terminate()
                        fromWorker.terminate()
                    }

                    override suspend fun recv(): WorkerMessage = toWorker.recv()
                    override suspend fun send(message: WorkerMessage) = fromWorker.send(message)
                })
            } catch (e: WorkerInterruptException) {
            }
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

    open suspend fun WorkerFork(worker: suspend WorkerChannel.() -> Unit, main: suspend CoroutineScope.() -> Unit): Unit {
        workerCode = worker
        try {
            main(CoroutineScope(coroutineContext))
        } catch (e: WorkerInterruptException) {
        } finally {
            try {
                for (w in workers.toList()) w.terminate()
            } catch (e: WorkerInterruptException) {

            }
        }
    }

    open fun WorkerIsAvailable(): Boolean = workerCode != null

    open fun runEntry(context: CoroutineContext, callback: suspend () -> Unit): Unit = TODO()
    open fun getClassName(clazz: KClass<*>): String = clazz.simpleName ?: "unknown"

    open fun suspendTest(callback: suspend () -> Unit): Unit = TODO()
}

expect val WorkerInterfaceImpl: WorkerInterface

fun suspendTest(callback: suspend CoroutineScope.() -> Unit): Unit = WorkerInterfaceImpl.suspendTest { callback(CoroutineScope(coroutineContext)) }
suspend fun getWorkerId() = WorkerInterfaceImpl.getWorkerId()
suspend fun Worker(): WorkerChannel = WorkerInterfaceImpl.Worker()
fun WorkerIsAvailable(): Boolean = WorkerInterfaceImpl.WorkerIsAvailable()
suspend fun WorkerFork(worker: suspend WorkerChannel.() -> Unit, main: suspend CoroutineScope.() -> Unit): Unit = WorkerInterfaceImpl.WorkerFork(worker, main)
