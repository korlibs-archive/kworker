package com.soywiz.kworker

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.w3c.dom.*
import kotlin.coroutines.*

var ENVIRONMENT_IS_NODE = js("(typeof process === 'object' && typeof require === 'function')")
var ENVIRONMENT_IS_WEB = js("(typeof window === 'object')")
var ENVIRONMENT_IS_WORKER = js("(typeof importScripts === 'function')")
var ENVIRONMENT_IS_SHELL = !ENVIRONMENT_IS_WEB && !ENVIRONMENT_IS_NODE && !ENVIRONMENT_IS_WORKER;

actual val WorkerInterfaceImpl: WorkerInterface = object : WorkerInterface() {
    val cluster by lazy { js("(require('cluster'))") }

    val workerId by lazy { js("(process.env.KWORKER_PID)") ?: 0 }

    private var lastWorkerId = 1

    private val workers = arrayListOf<WorkerChannel>()

    override suspend fun Worker(): WorkerChannel {
        val coroutineScope = CoroutineScope(coroutineContext)
        val workerId = lastWorkerId++
        when {
            ENVIRONMENT_IS_NODE -> {
                // https://nodejs.org/docs/latest/api/cluster.html#cluster_cluster_fork_env
                val envs = js("({})")
                envs["KWORKER_PID"] = workerId
                val nodeWorker = cluster.fork(envs)

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
            else -> {
                return super.Worker()
            }
        }
    }

    override suspend fun WorkerFork(worker: suspend WorkerChannel.() -> Unit, main: suspend CoroutineScope.() -> Unit) {
        val coroutineScope = CoroutineScope(coroutineContext)

        when {
            ENVIRONMENT_IS_NODE -> {
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
            else -> {
                super.WorkerFork(worker, main)
            }
        }
    }

    override fun getThreadId(): Int {
        return when {
            ENVIRONMENT_IS_NODE -> workerId
            else ->super.getThreadId()
        }
    }

    override fun runEntry(context: CoroutineContext, callback: suspend () -> Unit) {
        CoroutineScope(context).launch { callback() }
    }
}

external fun encodeURIComponent(str: String): String
external fun decodeURIComponent(str: String): String

val hex = "0123456789abcdef"

fun Char.digit(): Int = when (this) {
    in '0'..'9' -> (this - '0').toInt()
    in 'a'..'f' -> (this - 'a' + 10).toInt()
    in 'A'..'F' -> (this - 'A' + 10).toInt()
    else -> error("Invalid digit")
}

fun ByteArray.hex(): String {
    val sb = StringBuilder(this.size * 2)
    for (n in 0 until size) {
        val v = this[n].toInt() and 0xFF
        sb.append(hex[(v ushr 4) and 0xF])
        sb.append(hex[(v ushr 0) and 0xF])
    }
    return sb.toString()
}

fun String.unhex(): ByteArray {
    val ba = ByteArray(this.length / 2)
    for (n in 0 until ba.size) {
        val hi = this[n * 2 + 0].digit()
        val lo = this[n * 2 + 1].digit()
        ba[n] = ((hi shl 4) or lo).toByte()
    }
    return ba
}

private fun internalSerialize(args: Array<Any?>): String = args.map {
    when (it) {
        null -> "n"
        is Int, is Double -> "d$it"
        is String -> "s" + encodeURIComponent(it)
        is ByteArray -> "b" + it.hex()
        else -> error("Don't know how to serialize object")
    }
}.joinToString(":")

private fun internalDeserialize(str: String): Array<Any?> = str.split(":").map {
    when (it[0]) {
        'n' -> null as Any?
        'd' -> it.substring(1).toInt() as Any?
        's' -> decodeURIComponent(it.substring(1)) as Any?
        'b' -> it.substring(1).unhex() as Any?
        else -> error("Don't know how to deserialize '$it'")
    }
}.toTypedArray()