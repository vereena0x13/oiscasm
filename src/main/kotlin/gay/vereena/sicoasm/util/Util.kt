package gay.vereena.sicoasm.util

import kotlin.system.*

import com.github.ajalt.mordant.terminal.*
import com.github.ajalt.mordant.rendering.TextColors.*
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream


val TERMINAL = Terminal()


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

fun writeOutput(outFile: File, xs: IntArray, bitWidth: Int) {
    val dout = DataOutputStream(FileOutputStream(outFile))
    xs.forEach {
        val x = applyBitWidth(it, bitWidth) // NOTE TODO: this is not needed, right? -- waiting for the assert to fail... ig...
        assert(x == it)
        if(bitWidth == 32) {
            dout.writeByte((x shr 24) and 0xFF)
            dout.writeByte((x shr 16) and 0xFF)
        }
        if(bitWidth >= 16) dout.writeByte((x shr 8) and 0xFF)
        dout.writeByte(x and 0xFF)
    }
    dout.flush()
    dout.close()
}