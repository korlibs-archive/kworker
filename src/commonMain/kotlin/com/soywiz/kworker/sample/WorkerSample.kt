package com.soywiz.kworker.sample

import com.soywiz.kworker.*

object WorkerSample {
    object DemoXorWork : WorkDescriptor<ByteArray, ByteArray> {
        override suspend fun executeInternal(arg: ByteArray): ByteArray {
            val out = ByteArray(arg.size)
            for (n in out.indices) out[n] = (arg[n].toInt() xor 0x77).toByte()
            return out
        }
    }
}

suspend fun ByteArray.demoXor(): ByteArray = Workers.execute(WorkerSample.DemoXorWork, this)

suspend fun main(args: Array<String>) {
    WorkerEntry({
        register(WorkerSample.DemoXorWork)
    }) {
        println(byteArrayOf(1, 2, 3).demoXor().toList())
    }
}
