package gay.vereena.sicoasm

import java.io.*

import kotlin.system.*

import gay.vereena.sicoasm.driver.*
import gay.vereena.sicoasm.front.*
import gay.vereena.sicoasm.back.*
import gay.vereena.sicoasm.util.*


fun build(cfg: Config) {
    with(Driver(WithConfig(cfg))) {
        enqueueWorker(parse(cfg.inFile))

        onNotify<TreeAssembled> { _, notif ->
            cfg.writeOutFile(notif.code)
        }

        if(!run()) exitProcess(1)
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