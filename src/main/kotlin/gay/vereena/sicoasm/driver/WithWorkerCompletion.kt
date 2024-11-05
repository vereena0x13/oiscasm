package gay.vereena.sicoasm.driver

import gay.vereena.sicoasm.util.ExtensionContext


class WithWorkerCompletion(val fn: (Driver) -> Unit) : ExtensionContext.AbstractElement(Key) {
    companion object Key: ExtensionContext.IKey<WithWorkerCompletion>
}