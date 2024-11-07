package gay.vereena.oiscasm.driver

import gay.vereena.oiscasm.util.ExtensionContext


abstract class WorkerCompletion : ExtensionContext.AbstractElement(Key) {
    companion object Key: ExtensionContext.IKey<WorkerCompletion>

    abstract val fn: (Driver) -> Unit
}

class WithWorkerCompletion(override val fn: (Driver) -> Unit) : WorkerCompletion()