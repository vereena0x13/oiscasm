package gay.vereena.oiscasm

import gay.vereena.oiscasm.driver.*
import gay.vereena.oiscasm.util.*


class WithConfig(val config: Config) : ExtensionContext.AbstractElement(Key) {
    companion object Key : ExtensionContext.IKey<WithConfig>
}

val WorkerScope.config get() = extensions[WithConfig]!!.config