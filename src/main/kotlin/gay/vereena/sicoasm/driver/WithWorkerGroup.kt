package gay.vereena.sicoasm.driver

import gay.vereena.sicoasm.util.*


class WithWorkerGroup(val total: Int, val onComplete: (Driver) -> Unit) : ExtensionContext.AbstractElement(Key) {
    companion object Key : ExtensionContext.IKey<WithWorkerGroup>

    internal var completed = 0
    internal var ran = false
}