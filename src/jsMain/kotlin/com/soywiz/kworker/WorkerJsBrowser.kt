package com.soywiz.kworker

import com.soywiz.kworker.internal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import org.w3c.dom.*
import org.w3c.dom.url.*
import org.w3c.files.*
import kotlin.browser.*
import kotlin.coroutines.*

@JsName("Worker")
internal external class JsWorker(script: String, options: dynamic = definedExternally) {
}

private external val self: dynamic

val WorkerInterfaceImplBrowser: WorkerInterface = object : WorkerInterface() {
    val cluster by lazy { js("(require('cluster'))") }

    val workerId by lazy { js("(process.env.KWORKER_PID)") ?: 0 }

    private var lastWorkerId = 1

    private val workers = arrayListOf<WorkerChannel>()

    override suspend fun Worker(): WorkerChannel {
        val coroutineScope = CoroutineScope(coroutineContext)
        val workerId = lastWorkerId++

        if (ENVIRONMENT_IS_WORKER) error("Can't create Worker inside another worker")

        val scripts = document.getElementsByTagName("script").asList() as List<HTMLScriptElement>
        val requireJsScript = scripts.firstOrNull { it.src.contains("require.js") || it.src.contains("require.min.js") }
        val declaredMain = requireJsScript?.getAttribute("data-main")

        //val bootstrapUrl = "kworker-helper.js"
        val bootstrapUrl = URL.createObjectURL(Blob(arrayOf("""
            self.onmessage = function(e) {
                if (e.data.type == 'import') {
                    importScripts.apply(self, e.data.urls);
                } else if (e.data.type == 'eval') {
                    eval(e.data.script);
                }
            };
        """.trimIndent()), BlobPropertyBag(type = "text/javascript")))

        val jsWorker = JsWorker(bootstrapUrl, jsObject("name" to "worker-$workerId")).asDynamic()

        //console.log(requireJsScript)
        //console.log(declaredMain)
        val currentUrl = document.location!!.href
        val baseUrl = if (currentUrl.endsWith("/")) currentUrl else currentUrl.substringBeforeLast('/')

        if (declaredMain != null) {
            jsWorker.postMessage(jsObject("type" to "import", "urls" to arrayOf(requireJsScript.src)))
            jsWorker.postMessage(jsObject("type" to "eval", "script" to "requirejs.config({ baseUrl: ${baseUrl.quote()} });"))
            jsWorker.postMessage(jsObject("type" to "eval", "script" to "require([${declaredMain.quote()}], function(module) { console.log('loaded'); }, function(e) { console.error(e); });"))
        }else {
            jsWorker.postMessage(jsObject("type" to "import", "urls" to scripts.filter { !it.src.isNullOrBlank() }.map { it.src }.toTypedArray()))
        }

        val ready = CompletableDeferred<Unit>()
        val channel = Channel<WorkerMessage>()

        jsWorker.onmessage = { e: MessageEvent ->
            val data = e.data.asDynamic()
            console.log("MAIN received message", e)
            when (data.type.toString()) {
                "ready" -> {
                    ready.complete(Unit)
                }
                "message" -> {
                }
            }
            Unit
        }

        val worker = object : WorkerChannel {
            override suspend fun send(message: WorkerMessage) {
                jsWorker.postMessage(jsObject("type" to "message", "kind" to message.type, "args" to message.args))
            }

            override suspend fun recv(): WorkerMessage = channel.receive()
            override fun terminate() {
                jsWorker.postMessage(jsObject("type" to "terminate"))
                workers -= this
                channel.close()
            }
        }

        ready.await()

        workers += worker

        return worker
    }

    override suspend fun WorkerFork(worker: suspend WorkerChannel.() -> Unit, main: suspend CoroutineScope.() -> Unit) {
        val coroutineScope = CoroutineScope(coroutineContext)

        if (ENVIRONMENT_IS_WEB) {
            main(coroutineScope)
        } else {
            val channel = Channel<WorkerMessage>()

            self.onmessage = { e: MessageEvent ->
                console.log("RECEIVED MESSAGE ON WORKER", e)
            }

            self.postMessage(jsObject("type" to "ready"))

            worker(object : WorkerChannel {
                override suspend fun send(message: WorkerMessage) {
                    self.postMessage(jsObject("type" to "message", "kind" to message.type, "args" to message.args))
                }
                override suspend fun recv(): WorkerMessage = channel.receive()
                override fun terminate(): Unit = run { channel.close() }
            })
        }
    }

    override fun getThreadId(): Int {
        return self.name.toString().split("-").last().toIntOrNull() ?: -1
    }

    override fun runEntry(context: CoroutineContext, callback: suspend () -> Unit) {
        CoroutineScope(context).launch { callback() }
    }
}
