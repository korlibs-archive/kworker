package com.soywiz.kworker

import java.util.concurrent.*

class WorkerExec : Runnable {
    private val works = ConcurrentLinkedDeque<Runnable>()

    override fun run() {
        while (true) {
            if (works.isNotEmpty()) {
                val work = works.remove()
                work.run()
            }
            Thread.sleep(1L)
        }
    }
}

internal actual val WorkerImpl: WorkerCls = object : WorkerCls() {
    private lateinit var anotherClassLoader: ClassLoader

    override fun callAfterRegister() {
        val originalClassLoader = Thread.currentThread().contextClassLoader
        anotherClassLoader = object : ClassLoader() {
            override fun loadClass(name: String): Class<*> {
                println("REQUEST CLASS: $name")
                return try {
                    ClassLoader.getPlatformClassLoader().loadClass(name)
                } catch (e: ClassNotFoundException) {
                    println(" ---> REDEFINE: $name")
                    val bytes = originalClassLoader.getResourceAsStream(name.replace('.', '/') + ".class").readBytes()
                    defineClass(name, bytes, 0, bytes.size)
                }
            }
        }

        val oriClazz = originalClassLoader.loadClass(WorkerExec::class.java.name)
        val clazz = anotherClassLoader.loadClass(WorkerExec::class.java.name)
        println(oriClazz === clazz)
        val execInstance = clazz.declaredConstructors.first().newInstance() as Runnable
        Thread(execInstance).apply {
            contextClassLoader = anotherClassLoader
            start()
        }

    }

    override suspend fun <T, R> executeInWorker(work: WorkDescriptor<T, R>, arg: T): R {
        //works += Runnable {
        //    runBlocking {
        //        work.executeInternal(arg)
        //    }
        //}
        Thread.sleep(10000L)
        TODO()
    }

    override suspend fun isWorker(): Boolean {
        return super.isWorker()
    }
}

fun runBlocking(lambda: suspend () -> Unit): Unit = TODO()
