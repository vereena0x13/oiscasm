package gay.vereena.oiscasm

import java.io.*

import kotlinx.serialization.Serializable
import kotlinx.serialization.*

import com.akuleshov7.ktoml.*
import com.akuleshov7.ktoml.source.*


@Serializable
data class DebugConfig(
    val printParsedAst: Boolean,
    val printScopedAst: Boolean,
    val printExpandedAst: Boolean,
    val printAssembledAst: Boolean
)

@Serializable
data class Config(
    val signed: Boolean,
    val bitWidth: Int,
    val debug: DebugConfig
)


fun defaultConfig() = Config(
    signed = false,
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