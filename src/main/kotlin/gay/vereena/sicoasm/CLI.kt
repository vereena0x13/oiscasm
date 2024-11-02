package gay.vereena.sicoasm

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.NoOpCliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.mordant.terminal.Terminal
import gay.vereena.sicoasm.driver.Driver
import gay.vereena.sicoasm.front.parse
import java.io.File
import kotlin.system.exitProcess


val TERMINAL = Terminal()


private class CLI : NoOpCliktCommand() {
    override val printHelpOnEmptyArgs = true
}


private class Build : CliktCommand(name = "build") {
    val file: File by argument(name = "FILE", help = "root source file path").file(mustExist = true, canBeDir = false, mustBeReadable = true, canBeSymlink = false)
    val outFile: File by option("-o", "--out").file(mustExist = false, canBeDir = false, mustBeWritable = true, canBeSymlink = false).default(File("out.bin"))

    override fun run() {
        val driver = Driver()

        driver.enqueueWorker(parse(file, outFile))

        if(!driver.run()) exitProcess(1)


        // TODO
    }
}


fun main(args: Array<String>) = CLI()
    .subcommands(Build())
    .main(args)