package gay.vereena.sicoasm

import kotlin.system.*

import gay.vereena.sicoasm.driver.*
import gay.vereena.sicoasm.front.*
import gay.vereena.sicoasm.back.*


fun build(cfg: Config) {
    with(Driver(WithConfig(cfg))) {
        enqueueWorker(parse(cfg.inFile))

        onNotify<TreeAssembled> { _, notif ->
            cfg.writeOutFile(notif.code)
        }

        if(!run()) exitProcess(1)
    }
}