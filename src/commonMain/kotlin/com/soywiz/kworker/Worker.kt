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

abstract class WorkerInterface {
    open fun getWorkerId(): Int = -1
    private var workerCode: (suspend WorkerChannel.() -> Unit)? = null

    private val workers = LinkedHashSet<WorkerChannel>()

    open suspend fun Worker(): WorkerChannel {
        val toWorker: WorkerChannel = MyWorkerChannel2()
        val fromWorker: WorkerChannel = MyWorkerChannel2()

        CoroutineScope(coroutineContext).launch {
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

    open fun runEntry(context: CoroutineContext, callback: suspend () -> Unit): Unit = TODO()
    open fun getClassName(clazz: KClass<*>): String = TODO()
}

expect val WorkerInterfaceImpl: WorkerInterface

fun getWorkerId() = WorkerInterfaceImpl.getWorkerId()
suspend fun Worker(): WorkerChannel = WorkerInterfaceImpl.Worker()
suspend fun WorkerFork(worker: suspend WorkerChannel.() -> Unit, main: suspend CoroutineScope.() -> Unit): Unit = WorkerInterfaceImpl.WorkerFork(worker, main)

/*
interface WorkDescriptor<T, R> {
    suspend fun executeInternal(arg: T): R
}

interface WorkerInterfaceRegister {
    fun <T, R> register(work: WorkDescriptor<T, R>)
}

@PublishedApi
internal val registeredWorks = LinkedHashSet<WorkDescriptor<*, *>>()

@PublishedApi
internal var workerRegister: (suspend WorkerInterfaceRegister.() -> Unit)? = null

@PublishedApi
internal var workerMain: (suspend () -> Unit)? = null

internal object InternalWorkerInterfaceRegister : WorkerInterfaceRegister {
    override fun <T, R> register(work: WorkDescriptor<T, R>) {
        registeredWorks += work
    }
}

internal open class WorkerCls {
    open fun callAfterRegister() {
    }

    open suspend fun <T, R> executeInWorker(work: WorkDescriptor<T, R>, arg: T): R {
        return work.executeInternal(arg)
    }

    open fun isWorker(): Boolean {
        return false
    }
}

internal expect val WorkerImpl: WorkerCls

object Workers {
    fun registered(work: WorkDescriptor<*, *>) = work in registeredWorks

    suspend fun <T, R> execute(work: WorkDescriptor<T, R>, arg: T): R {
        //println("registeredWorks: $registeredWorks")
        return if (!registered(work)) {
            //error("Can't execute work $work. You must register it in the WorkerEntry.register callback")
            work.executeInternal(arg)
        } else {
            WorkerImpl.executeInWorker(work, arg)
        }
    }
}

private var workerEntryExecuted = false

suspend fun WorkerEntry(register: suspend WorkerInterfaceRegister.() -> Unit, main: suspend () -> Unit) {
    if (workerEntryExecuted) {
        error("Can't call WorkerEntry twice. Please, use it as part of your entry point.")
    }
    workerEntryExecuted = true
    workerRegister = register
    workerMain = main
    register(InternalWorkerInterfaceRegister)
    WorkerImpl.callAfterRegister()
    if (!WorkerImpl.isWorker()) {
        main()
    }
}
*/
