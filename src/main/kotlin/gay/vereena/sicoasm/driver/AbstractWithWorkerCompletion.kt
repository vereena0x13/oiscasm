package gay.vereena.sicoasm.driver

import gay.vereena.sicoasm.util.ExtensionContext


abstract class AbstractWithWorkerCompletion : ExtensionContext.AbstractElement(Key) {
    companion object Key: ExtensionContext.IKey<AbstractWithWorkerCompletion>

    abstract val fn: (Driver) -> Unit
}

class WithWorkerCompletion(f: (Driver) -> Unit, override val fn: (Driver) -> Unit = f) : AbstractWithWorkerCompletion()