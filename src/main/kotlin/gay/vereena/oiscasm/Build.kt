package gay.vereena.oiscasm

import java.io.*

import kotlin.system.*

import gay.vereena.oiscasm.driver.*
import gay.vereena.oiscasm.front.*
import gay.vereena.oiscasm.back.*


fun build(inFile: File, cfg: Config, writeOutFile: (IntArray) -> Unit) {
    with(Driver(WithConfig(cfg))) {
        enqueueWorker(parse(inFile))

        onNotify<TreeAssembled> { _, notif ->
            writeOutFile(notif.code)
        }

        if(!run()) exitProcess(1)
    }
}