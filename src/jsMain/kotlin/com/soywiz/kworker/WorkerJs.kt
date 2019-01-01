package com.soywiz.kworker

import kotlinx.coroutines.*
import kotlin.coroutines.*

actual val WorkerInterfaceImpl: WorkerInterface = object : WorkerInterface() {
    override fun runEntry(context: CoroutineContext, callback: suspend () -> Unit) {
        CoroutineScope(context).launch { callback() }
    }
}
