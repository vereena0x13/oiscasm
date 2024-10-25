package gay.vereena.sicoasm.exts

import gay.vereena.sicoasm.Driver

class WithWorkerGroup(val total: Int, val onComplete: (Driver) -> Unit) : ExtensionContext.AbstractElement(Key) {
    companion object Key : ExtensionContext.IKey<WithWorkerGroup>

    internal var completed = 0
    internal var ran = false
}