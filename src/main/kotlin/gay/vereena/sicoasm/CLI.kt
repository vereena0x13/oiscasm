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
        val driver = Driver()

        driver.enqueueWorker(parse(file))

        driver.onNotify(TreeAssembled::class) { _, notif ->
            val code = (notif as TreeAssembled).code
            val dout = DataOutputStream(FileOutputStream(outFile))
            code.forEach {
                if(bitWidth == 32) {
                    dout.writeByte((it shr 24) and 0xFF)
                    dout.writeByte((it shr 16) and 0xFF)
                }
                if(bitWidth >= 16) dout.writeByte((it shr 8) and 0xFF)
                dout.writeByte(it and 0xFF)
            }
            dout.flush()
            dout.close()
        }

        if(!driver.run()) exitProcess(1)

        // TODO
    }
}


fun main(args: Array<String>) = CLI()
    .subcommands(Build())
    .main(args)