package com.soywiz.kworker

import org.w3c.dom.*

/*
var ENVIRONMENT_IS_NODE = js("(typeof process === 'object' && typeof require === 'function')")
var ENVIRONMENT_IS_WEB = js("(typeof window === 'object')")
var ENVIRONMENT_IS_WORKER = js("(typeof importScripts === 'function')")
var ENVIRONMENT_IS_SHELL = !ENVIRONMENT_IS_WEB && !ENVIRONMENT_IS_NODE && !ENVIRONMENT_IS_WORKER;

internal actual val WorkerImpl: WorkerCls = object : WorkerCls() {
    override fun callAfterRegister() {
        if (isWorker()) {
            val self = js("(self)")

            self.onmessage = { e: MessageEvent ->
                val data = e.data.asDynamic()
                /*
                if (data.type == "import") {
                    importScripts(e.data.url);
                } else if (e.data.type == 'eval') {
                    eval(e.data.script);
                } else if (e.data.type == 'func') {
                    const funcName = e.data.funcName;
                    const id = e.data.id;
                    const arg = e.data.arg;
                    const moduleName = e.data.module;
                    const module = require.s.contexts._.defined[moduleName]
                    const func = module[funcName]
                    //console.log('funcName', funcName, 'id', id, 'arg', arg, 'moduleName', moduleName, 'func', func, 'module', module);
                    //Object.keys(module)
                    var sample = module.sample
                    try {
                        const result = func.call(module, arg);
                        postMessage({ 'type': 'result', 'id': id, 'result': result, 'exception': null })
                    } catch (e) {
                        postMessage({ 'type': 'result', 'id': id, 'result': null, 'exception': e })
                    }
                    //func(arg);
                }
                */
            }

        } else {

        }
    }

    override suspend fun <T, R> executeInWorker(work: WorkDescriptor<T, R>, arg: T): R {
        return super.executeInWorker(work, arg)
    }

    override fun isWorker(): Boolean {
        return ENVIRONMENT_IS_WORKER
    }
}
*/