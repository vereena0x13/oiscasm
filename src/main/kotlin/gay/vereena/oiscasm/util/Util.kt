package gay.vereena.oiscasm.util

import java.io.*

import kotlin.math.*
import kotlin.system.*

import com.github.ajalt.mordant.terminal.*
import com.github.ajalt.mordant.rendering.TextColors.*


val TERMINAL = Terminal()


fun <T> id(x: T) = x


fun ice(x: Any? = null): Nothing = panic(("INTERNAL COMPILER ERROR" + if (x == null) "" else ": $x"))

fun panic(msg: String? = null): Nothing {
    TERMINAL.println(red("panic: ${msg ?: "???"}"))
    Throwable().printStackTrace()
    exitProcess(1)
}


// NOTE TODO: is this named properly? idefk man. kms type shit.
fun unescape(s: String) = s.map {
    when (it) {
        '\t' -> "\\t"
        '\b' -> "\\b"
        '\n' -> "\\n"
        '\r' -> "\\r"
        else -> "$it"
    }
}.fold("") { acc, it -> "$acc$it" }


fun applyBitWidth(x: Int, bitWidth: Int) = when(bitWidth) {
    32 -> x and 0xFFFFFFFF.toInt()
    16 -> x and 0xFFFF
    8 -> x and 0xFF
    else -> ice()
}


fun writeOutput(outFile: File, xs: IntArray, bitWidth: Int) = with(DataOutputStream(FileOutputStream(outFile))) {
    xs.forEach {
        // NOTE TODO: this is not needed, right? -- waiting for the assert to fail... ig...
        val x = applyBitWidth(it, bitWidth)
        assert(x == it)
        if(bitWidth == 32) {
            writeByte((x shr 24) and 0xFF)
            writeByte((x shr 16) and 0xFF)
        }
        if(bitWidth >= 16) writeByte((x shr 8) and 0xFF)
        writeByte(x and 0xFF)
    }
    flush()
    close()
}


fun formatIntTable(xs: IntArray, cols: Int, maxl: Int = 4): String {
    if(xs.isEmpty()) return ""
    return max(maxl, xs.maxOrNull().toString().length).let { padTo ->
        xs.map { it.toString().padStart(padTo) }
        .chunked(cols)
        .map { it.joinToString("") }
        .reduce() { a, b -> "$a\n$b" }
    }
}