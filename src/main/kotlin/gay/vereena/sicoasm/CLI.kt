package gay.vereena.sicoasm

import java.io.*

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.arguments.*
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.*


private class CLI : NoOpCliktCommand() {
    override val printHelpOnEmptyArgs = true
}

private class Build : CliktCommand(name = "build") {
    val inFile: File by argument(name = "FILE")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true, canBeSymlink = false)

    val outFile: File by option("-o", "--out")
        .file(mustExist = false, canBeDir = false, mustBeWritable = true, canBeSymlink = false)
        .default(File("out.bin"))

    val bitWidth: Int by option("-w", "--width")
        .int()
        .default(16)
        .check { it == 8 || it == 16 || it == 32 }

    val debug: Boolean by option("-d", "--debug")
        .flag(default = false)

    override fun run() {
        build(Config(
            inFile,
            bitWidth,
            debug,
        ) { code ->
            writeOutput(outFile, code, bitWidth)
        })
    }
}

fun main(args: Array<String>) = CLI()
    .subcommands(Build())
    .main(args)