package com.soywiz.kworker.internal

internal val ENVIRONMENT_IS_NODE: Boolean by lazy { js("(typeof process === 'object' && typeof require === 'function')") }
internal val ENVIRONMENT_IS_WEB : Boolean by lazy { js("(typeof window === 'object')") }
internal val ENVIRONMENT_IS_WORKER: Boolean by lazy { js("(typeof importScripts === 'function')") }
internal val ENVIRONMENT_IS_WEB_OR_WORKER: Boolean by lazy { ENVIRONMENT_IS_WEB || ENVIRONMENT_IS_WORKER }
internal val ENVIRONMENT_IS_SHELL: Boolean by lazy { !ENVIRONMENT_IS_WEB && !ENVIRONMENT_IS_NODE && !ENVIRONMENT_IS_WORKER; }


internal external fun encodeURIComponent(str: String): String
internal external fun decodeURIComponent(str: String): String

internal val hex = "0123456789abcdef"

internal fun Char.digit(): Int = when (this) {
    in '0'..'9' -> (this - '0').toInt()
    in 'a'..'f' -> (this - 'a' + 10).toInt()
    in 'A'..'F' -> (this - 'A' + 10).toInt()
    else -> error("Invalid digit")
}

internal fun ByteArray.hex(): String {
    val sb = StringBuilder(this.size * 2)
    for (n in 0 until size) {
        val v = this[n].toInt() and 0xFF
        sb.append(hex[(v ushr 4) and 0xF])
        sb.append(hex[(v ushr 0) and 0xF])
    }
    return sb.toString()
}

internal fun String.unhex(): ByteArray {
    val ba = ByteArray(this.length / 2)
    for (n in 0 until ba.size) {
        val hi = this[n * 2 + 0].digit()
        val lo = this[n * 2 + 1].digit()
        ba[n] = ((hi shl 4) or lo).toByte()
    }
    return ba
}

internal fun internalSerialize(args: Array<Any?>): String = args.map {
    when (it) {
        null -> "n"
        is Int, is Double -> "d$it"
        is String -> "s" + encodeURIComponent(it)
        is ByteArray -> "b" + it.hex()
        else -> error("Don't know how to serialize object")
    }
}.joinToString(":")

internal fun internalDeserialize(str: String): Array<Any?> = str.split(":").map {
    when (it[0]) {
        'n' -> null as Any?
        'd' -> it.substring(1).toInt() as Any?
        's' -> decodeURIComponent(it.substring(1)) as Any?
        'b' -> it.substring(1).unhex() as Any?
        else -> error("Don't know how to deserialize '$it'")
    }
}.toTypedArray()

internal fun jsObject(vararg pairs: Pair<String, Any?>): dynamic {
    val out = js("({})")
    for (pair in pairs) out[pair.first] = pair.second
    return out
}

fun String.quote(): String {
    val out = StringBuilder(this.length + 2)
    out.append('"')
    for (c in this) {
        when (c) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            else -> out.append(c)
        }
    }
    out.append('"')
    return out.toString()
}
