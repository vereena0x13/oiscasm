package gay.vereena.sicoasm.util

import kotlin.system.exitProcess

fun <T> eprint(x: T) = System.err.print(x)
fun <T> eprintln(x: T) = System.err.println(x)
fun eprintln() = System.err.println()

fun ice(x: Any? = null): Nothing = throw AssertionError("INTERNAL COMPILER ERROR" + if (x == null) "" else ": $x")

fun panic(msg: String? = null): Nothing {
    eprintln("panic: ${msg ?: "???"}")
    exitProcess(1)
}

fun nop() {}

fun escape(s: String) = s.map {
    when (it) {
        '\r' -> "\\r"
        '\n' -> "\\n"
        '\t' -> "\\t"
        else -> "$it"
    }
}.fold("", { acc, it -> "$acc$it" })