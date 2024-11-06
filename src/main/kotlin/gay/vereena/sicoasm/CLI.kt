package gay.vereena.sicoasm

import java.io.*

import com.github.ajalt.clikt.core.*
import com.github.ajalt.clikt.parameters.arguments.*
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.*

import gay.vereena.sicoasm.util.*


private class CLI : NoOpCliktCommand() {
    override val printHelpOnEmptyArgs = true
}

private class Build : CliktCommand(name = "build") {
    val inFile: File by argument(name = "FILE")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true, canBeSymlink = false)

    val outFile: File by option("-o", "--out")
        .file(mustExist = false, canBeDir = false, mustBeWritable = true, canBeSymlink = false)
        .default(File("out.bin"))

    val cfgFile: File? by option("--cfg")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true, canBeSymlink = false)


    override fun run() {
        val cfg = if(cfgFile != null) loadConfigFromIonFile(cfgFile!!) else defaultConfig()
        build(inFile, cfg) { code ->
            writeOutput(outFile, code, cfg.bitWidth)
        }
    }
}

fun main(args: Array<String>) = CLI()
    .subcommands(Build())
    .main(args)