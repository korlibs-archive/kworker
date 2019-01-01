package com.soywiz.kworker

import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.native.concurrent.*
import kotlin.reflect.*

internal object WorkerInfo {
    val demo = AtomicInt(10)
    val workerLambda = AtomicReference<(suspend WorkerChannel.() -> Unit)?>(null)
}

fun workerJob(id: Int): Int {
    runBlocking {
        WorkerInfo.workerLambda.value?.invoke(object : WorkerChannel {
            override suspend fun send(message: WorkerMessage) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override suspend fun recv(): WorkerMessage {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun terminate() {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }
        })
    }
    return id
}

actual val WorkerInterfaceImpl: WorkerInterface = object : WorkerInterface() {
    //val demo = AtomicReference<List<WorkerMessage>>(listOf())

    /*
    override suspend fun Worker(): WorkerChannel {
        val konanWorker = kotlin.native.concurrent.Worker.start()

        println(WorkerInfo.demo.value)

        konanWorker.execute(TransferMode.SAFE, { 10 }, ::workerJob).result

        println(WorkerInfo.demo.value)

        return super.Worker()
    }

    override suspend fun WorkerFork(worker: suspend WorkerChannel.() -> Unit, main: suspend CoroutineScope.() -> Unit) {
        WorkerInfo.workerLambda.value = worker
        main(CoroutineScope(coroutineContext))
    }
    */

    override fun suspendTest(callback: suspend () -> Unit) {
        runBlocking { callback() }
    }

    override fun runEntry(context: CoroutineContext, callback: suspend () -> Unit) {
        runBlocking(context) { callback() }
    }

    override fun getClassName(clazz: KClass<*>): String {
        return clazz.qualifiedName!!
    }
}

/*
actual val WorkerInterfaceImpl: WorkerInterface = object : WorkerInterface() {
    override fun getThreadId(): Int {
        return super.getThreadId()
    }

    override fun Worker(): WorkerChannel {
        val konanWorker = kotlin.native.concurrent.Worker.start()

        val scope = CoroutineScope(EmptyCoroutineContext)
        konanWorker.execute(TransferMode.SAFE)

        return object : WorkerChannel {
            override suspend fun send(message: WorkerMessage) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override suspend fun recv(): WorkerMessage {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun terminate() {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }
        }
    }

    override fun WorkerFork(worker: suspend WorkerChannel.() -> Unit, main: suspend CoroutineScope.() -> Unit) {

    }

    override fun getClassName(clazz: KClass<*>): String {
        return clazz.qualifiedName!!
    }
}
*/