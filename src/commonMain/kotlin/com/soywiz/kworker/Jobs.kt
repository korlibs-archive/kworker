package com.soywiz.kworker

import kotlinx.coroutines.*
import kotlin.coroutines.*

interface JobDescriptor {
    suspend fun execute(args: Array<Any?>): Array<Any?>
}

fun JobDescriptor.getName(): String = WorkerInterfaceImpl.getClassName(this::class)

interface JobsRegister {
    fun register(descriptor: JobDescriptor)
}

class Jobs private constructor(val worker: WorkerChannel?) {
    private val deferreds = LinkedHashMap<Int, CompletableDeferred<Array<Any?>>>()
    private var lastId = 0
    suspend fun execute(job: JobDescriptor, args: Array<Any?>): Array<Any?> {
        if (worker != null) {
            val id = lastId++
            val deferred = CompletableDeferred<Array<Any?>>()
            deferreds[id] = deferred
            worker.send(WorkerMessage(job.getName(), id, *args))
            return deferred.await()
        } else {
            return job.execute(args)
        }
    }

    fun terminate() {
        worker?.terminate()
    }

    companion object {
        suspend operator fun invoke(scope: CoroutineScope): Jobs {
            val worker = if (WorkerInterfaceImpl.WorkerIsAvailable()) Worker() else null
            val jobs = Jobs(worker)
            if (worker != null) {
                scope.launch {
                    try {
                        while (true) {
                            val message = worker.recv()
                            val id = message.args[0] as Int
                            val args = message.args.sliceArray(1 until message.args.size)
                            val deferred = jobs.deferreds.remove(id)
                            deferred?.complete(args as Array<Any?>)
                        }
                    } catch (e: WorkerInterruptException) {
                    }
                }
            }
            return jobs
        }
    }
}

suspend fun CoroutineScope.Jobs(): Jobs = Jobs(this)

suspend fun JobsMain(register: suspend JobsRegister.() -> Unit, main: suspend CoroutineScope.() -> Unit) {
    WorkerFork({
        val descriptors = LinkedHashMap<String, JobDescriptor>()
        register(object : JobsRegister {
            override fun register(descriptor: JobDescriptor) {
                descriptors[descriptor.getName()] = descriptor
            }
        })
        while (true) {
            val message = recv()
            val descriptor = descriptors[message.type]
            val id = message.args[0] as Int
            val result = descriptor!!.execute(message.args.sliceArray(1 until message.args.size) as Array<Any?>)
            send(WorkerMessage(descriptor.getName(), id, *result))
        }
    }, {
        main()
    })
}

fun JobsMainSync(register: suspend JobsRegister.() -> Unit, main: suspend CoroutineScope.() -> Unit) {
    WorkerInterfaceImpl.runEntry(EmptyCoroutineContext) {
        JobsMain(register, main)
    }
}
