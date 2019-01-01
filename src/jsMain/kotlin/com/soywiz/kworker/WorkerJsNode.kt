package com.soywiz.kworker

import com.soywiz.kworker.internal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.w3c.dom.*
import kotlin.coroutines.*

val WorkerInterfaceImplNode: WorkerInterface = object : WorkerInterface() {
    val cluster by lazy { js("(require('cluster'))") }

    val workerId by lazy { js("(process.env.KWORKER_PID)") ?: 0 }

    private var lastWorkerId = 1

    private val workers = arrayListOf<WorkerChannel>()

    override suspend fun Worker(): WorkerChannel {
        val coroutineScope = CoroutineScope(coroutineContext)
        val workerId = lastWorkerId++
        if (cluster.isMaster) error("Can't create Worker inside another worker")

        // https://nodejs.org/docs/latest/api/cluster.html#cluster_cluster_fork_env
        val nodeWorker = cluster.fork(jsObject("KWORKER_PID" to workerId))

        val ready = CompletableDeferred<Unit>()

        val channel = Channel<WorkerMessage>()

        nodeWorker.on("message") { e: Array<Any?> ->
            val type = e[0]?.toString()
            //println("RECEIVED FROM WORKER MESSAGE ${e.toList()}")
            when (type) {
                "ready" -> ready.complete(Unit)
                "message" -> {
                    coroutineScope.launch {
                        channel.send(WorkerMessage(e[1].toString(), *internalDeserialize(e[2].toString())))
                    }
                }
            }
            Unit
        }

        val worker = object : WorkerChannel {
            override suspend fun send(message: WorkerMessage) {
                nodeWorker.send(arrayOf("message", message.type, internalSerialize(message.args as Array<Any?>)))
            }

            override suspend fun recv(): WorkerMessage = channel.receive()

            override fun terminate() {
                nodeWorker.send(arrayOf("terminate"))
                nodeWorker.removeAllListeners("message")
                channel.close()
                workers -= this
            }
        }

        ready.await()

        workers += worker

        return worker
    }

    override suspend fun WorkerFork(worker: suspend WorkerChannel.() -> Unit, main: suspend CoroutineScope.() -> Unit) {
        val coroutineScope = CoroutineScope(coroutineContext)

        if (cluster.isMaster) {
            var exitCode = 0
            try {
                main(coroutineScope)
            } catch (e: Throwable) {
                exitCode = -1
                throw e
            } finally {
                for (w in workers.toList()) w.terminate()
                js("(process)").exit(exitCode)
            }
        } else {
            val parent = js("(process)")

            val channel = Channel<WorkerMessage>()

            parent.on("message") { e: Array<Any?> ->
                val type = e[0]?.toString()
                //println("RECEIVED FROM PARENT MESSAGE ${e.toList()}")
                when (type) {
                    "terminate" -> {
                        parent.removeAllListeners("message")
                        channel.close()
                    }
                    "message" -> {
                        coroutineScope.launch {
                            channel.send(WorkerMessage(e[1].toString(), *internalDeserialize(e[2].toString())))
                        }
                    }
                }
                Unit
            }

            parent.send(arrayOf("ready"))

            try {
                worker(object : WorkerChannel {
                    override suspend fun send(message: WorkerMessage) {
                        parent.send(arrayOf("message", message.type, internalSerialize(message.args as Array<Any?>)))
                    }

                    override suspend fun recv(): WorkerMessage = channel.receive()

                    override fun terminate() {
                        channel.close()
                    }
                })
            } catch (e: ClosedReceiveChannelException) {
            }
        }
    }

    override fun getThreadId(): Int {
        return when {
            ENVIRONMENT_IS_NODE -> workerId
            else -> super.getThreadId()
        }
    }

    override fun runEntry(context: CoroutineContext, callback: suspend () -> Unit) {
        CoroutineScope(context).launch { callback() }
    }
}

