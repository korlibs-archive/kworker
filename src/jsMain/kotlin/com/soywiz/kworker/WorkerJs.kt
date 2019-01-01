package com.soywiz.kworker

import com.soywiz.kworker.internal.*

actual val WorkerInterfaceImpl: WorkerInterface by lazy {
    when {
        ENVIRONMENT_IS_NODE -> WorkerInterfaceImplNode
        ENVIRONMENT_IS_WEB_OR_WORKER -> WorkerInterfaceImplBrowser
        else -> error("Unsupported javascript engine (not detected either Node, or Browser)")
    }
}
