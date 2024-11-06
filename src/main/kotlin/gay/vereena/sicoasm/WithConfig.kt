package gay.vereena.sicoasm

import gay.vereena.sicoasm.driver.*
import gay.vereena.sicoasm.util.*


class WithConfig(val config: Config) : ExtensionContext.AbstractElement(Key) {
    companion object Key : ExtensionContext.IKey<WithConfig>
}

val WorkerScope.config get() = extensions[WithConfig]!!.config