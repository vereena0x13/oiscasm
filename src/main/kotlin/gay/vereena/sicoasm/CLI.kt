package gay.vereena.sicoasm

import kotlin.system.*

import java.io.*

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.arguments.*
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.*

import gay.vereena.sicoasm.driver.*
import gay.vereena.sicoasm.front.*
import gay.vereena.sicoasm.back.*
import gay.vereena.sicoasm.util.*


private class CLI : NoOpCliktCommand() {
    override val printHelpOnEmptyArgs = true
}

private class Build : CliktCommand(name = "build") {
    val file: File by argument(name = "FILE", help = "root source file path")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true, canBeSymlink = false)

    val outFile: File by option("-o", "--out")
        .file(mustExist = false, canBeDir = false, mustBeWritable = true, canBeSymlink = false)
        .default(File("out.bin"))

    val bitWidth: Int by option("-w", "--width")
        .int()
        .default(16)
        .check { it == 8 || it == 16 || it == 32 }

    override fun run() {
        with(Driver()) {
            enqueueWorker(parse(file))

            onNotify<TreeAssembled> { _, notif -> writeOutput(outFile, notif.code, bitWidth) }

            if(!run()) exitProcess(1)
        }

        // TODO
    }
}

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

fun main(args: Array<String>) = CLI()
    .subcommands(Build())
    .main(args)