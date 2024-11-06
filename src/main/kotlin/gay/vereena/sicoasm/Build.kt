package gay.vereena.sicoasm

import java.io.*

import kotlin.system.*

import gay.vereena.sicoasm.driver.*
import gay.vereena.sicoasm.front.*
import gay.vereena.sicoasm.back.*


fun build(inFile: File, cfg: Config, writeOutFile: (IntArray) -> Unit) {
    with(Driver(WithConfig(cfg))) {
        enqueueWorker(parse(inFile))

        onNotify<TreeAssembled> { _, notif ->
            writeOutFile(notif.code)
        }

        if(!run()) exitProcess(1)
    }
}