package com.soywiz.kworker

import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.*

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
