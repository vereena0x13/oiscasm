package gay.vereena.sicoasm

import java.io.*

import gay.vereena.sicoasm.driver.*
import gay.vereena.sicoasm.util.*


data class Config(
    val inFile: File,
    val outFile: File,
    val bitWidth: Int,
    val debug: Boolean
)


class WithConfig(val config: Config) : ExtensionContext.AbstractElement(Key) {
    companion object Key : ExtensionContext.IKey<WithConfig>
}

val WorkerScope.config get() = extensions[WithConfig]!!.config