package com.soywiz.kworker

import com.soywiz.kworker.WorkerSample.demoXor
import kotlin.jvm.*

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
    suspend fun <T, R> executeInWorker(work: WorkDescriptor<T, R>, arg: T): R {
        return work.executeInternal(arg)
    }

    suspend fun isWorker(): Boolean {
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
    if (!WorkerImpl.isWorker()) {
        main()
    }
}

////////////////////////////////////////////////////////
////////////////////////////////////////////////////////

object WorkerSample {
    object DemoXorWork : WorkDescriptor<ByteArray, ByteArray> {
        override suspend fun executeInternal(arg: ByteArray): ByteArray {
            val out = ByteArray(arg.size)
            for (n in out.indices) out[n] = (arg[n].toInt() xor 0x77).toByte()
            return out
        }
    }

    suspend fun ByteArray.demoXor(): ByteArray = Workers.execute(DemoXorWork, this)

}

suspend fun main(args: Array<String>) {
    WorkerEntry({
        register(WorkerSample.DemoXorWork)
    }) {
        println(byteArrayOf(1, 2, 3).demoXor().toList())
    }
}
