package gay.vereena.sicoasm

import java.io.*

import kotlinx.serialization.Serializable
import kotlinx.serialization.*

import com.akuleshov7.ktoml.*
import com.akuleshov7.ktoml.source.*

import gay.vereena.sicoasm.driver.*
import gay.vereena.sicoasm.util.*


@Serializable
data class DebugConfig(
    val printParsedAst: Boolean,
    val printScopedAst: Boolean,
    val printExpandedAst: Boolean,
    val printAssembledAst: Boolean
)

@Serializable
data class Config(
    var bitWidth: Int,
    val debug: DebugConfig
)


class WithConfig(val config: Config) : ExtensionContext.AbstractElement(Key) {
    companion object Key : ExtensionContext.IKey<WithConfig>
}

val WorkerScope.config get() = extensions[WithConfig]!!.config


fun defaultConfig() = Config(
    bitWidth = 16,
    debug = DebugConfig(
        printParsedAst = false,
        printScopedAst = false,
        printExpandedAst = false,
        printAssembledAst = false
    )
)

fun loadConfig(cfgFile: File): Config {
    val inputConfig = TomlInputConfig(
        ignoreUnknownNames = false,
        allowEmptyValues = false,
        allowNullValues = false,
        allowEscapedQuotesInLiteralStrings = true,
        allowEmptyToml = false
    )
    val outputConfig = TomlOutputConfig(
        indentation = TomlIndentation.FOUR_SPACES
    )
    val toml = Toml(inputConfig, outputConfig)

    if(cfgFile.exists()) {
        return toml.decodeFromStream<Config>(FileInputStream(cfgFile))
    } else {
        val cfg = defaultConfig()
        cfgFile.writeText(toml.encodeToString<Config>(cfg))
        return cfg
    }
}