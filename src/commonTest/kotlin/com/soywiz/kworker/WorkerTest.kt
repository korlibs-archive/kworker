package com.soywiz.kworker

import kotlin.test.*

class WorkerTest {
    object DemoXorJob : JobDescriptor {
        override suspend fun execute(args: Array<Any?>): Array<Any?> {
            val arg = args[0] as ByteArray
            val out = ByteArray(arg.size)
            for (n in out.indices) out[n] = (arg[n].toInt() xor 0x77).toByte()
            return arrayOf(out)
        }
    }

    fun JobsRegister.registerXorJob() = register(DemoXorJob)
    suspend fun ByteArray.xor(jobs: Jobs): ByteArray = jobs.execute(DemoXorJob, arrayOf(this))[0] as ByteArray

    @Test
    fun test() = suspendTest {
        val jobs = Jobs() // We should be able to call it without the entry point, but it will be executed in the same process
        val array1 = byteArrayOf(1, 2, 3)
        val array2 = array1.xor(jobs)
        assertEquals(listOf(1, 2, 3), array1.map { it.toInt() })
        assertEquals(listOf(118, 117, 116), array2.map { it.toInt() })
    }
}