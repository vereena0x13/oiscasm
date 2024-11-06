package gay.vereena.sicoasm

import java.io.*

import com.amazon.ion.system.*
import com.amazon.ionelement.api.*

import gay.vereena.sicoasm.driver.*
import gay.vereena.sicoasm.util.*


data class DebugConfig(
    val printParsedAst: Boolean,
    val printScopedAst: Boolean,
    val printExpandedAst: Boolean,
    val printAssembledAst: Boolean
)

data class Config(
    var bitWidth: Int,
    val debugCfg: DebugConfig
)


class WithConfig(val config: Config) : ExtensionContext.AbstractElement(Key) {
    companion object Key : ExtensionContext.IKey<WithConfig>
}

val WorkerScope.config get() = extensions[WithConfig]!!.config


fun defaultConfig() = Config(
    bitWidth = 16,
    debugCfg = DebugConfig(
        printParsedAst = false,
        printScopedAst = false,
        printExpandedAst = false,
        printAssembledAst = false
    )
)

fun loadConfigFromIonFile(cfgFile: File): Config {
    var bitWidth            = 16
    var printParsedAst      = false
    var printScopedAst      = false
    var printExpandedAst    = false
    var printAssembledAst   = false

    val reader = IonReaderBuilder
        .standard()
        .build(cfgFile.inputStream())
    val c = loadSingleElement(reader).asStruct()

    if(c.containsField("bitWidth")) bitWidth = c["bitWidth"].asInt().longValue.toInt()

    if(c.containsField("debug")) {
        val d = c["debug"].asStruct()
        if(c.containsField("printParsedAst")) printParsedAst = d["printParsedAst"].asBoolean().booleanValue
        if(d.containsField("printScopedAst")) printScopedAst = d["printScopedAst"].asBoolean().booleanValue
        if(d.containsField("printExpandedAst")) printExpandedAst = d["printExpandedAst"].asBoolean().booleanValue
        if(d.containsField("printAssembledAst")) printAssembledAst = d["printAssembledAst"].asBoolean().booleanValue
    }

    return Config(
        bitWidth = bitWidth,
        debugCfg = DebugConfig(
            printParsedAst = printParsedAst,
            printScopedAst = printScopedAst,
            printExpandedAst = printExpandedAst,
            printAssembledAst = printAssembledAst
        )
    )
}