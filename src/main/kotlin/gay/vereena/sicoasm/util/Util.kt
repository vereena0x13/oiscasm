package gay.vereena.sicoasm.util

import kotlin.system.*

import com.github.ajalt.mordant.terminal.*
import com.github.ajalt.mordant.rendering.TextColors.*


val TERMINAL = Terminal()


fun ice(x: Any? = null): Nothing = panic(("INTERNAL COMPILER ERROR" + if (x == null) "" else ": $x"))

fun panic(msg: String? = null): Nothing {
    TERMINAL.println(red("panic: ${msg ?: "???"}"))
    Throwable().printStackTrace()
    exitProcess(1)
}

fun escape(s: String) = s.map {
    when (it) {
        '\r' -> "\\r"
        '\n' -> "\\n"
        '\t' -> "\\t"
        else -> "$it"
    }
}.fold("") { acc, it -> "$acc$it" }