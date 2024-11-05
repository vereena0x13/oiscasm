package gay.vereena.sicoasm.driver

import gay.vereena.sicoasm.util.ExtensionContext


class WithWorkerGroup(val onGroupComplete: (Driver) -> Unit) : AbstractWithWorkerCompletion() {
    private var total = 0
    private var completed = 0

    override val fn = { driver: Driver ->
        completed += 1
        if(completed == total) onGroupComplete(driver)
    }

    override fun onAddedTo(exts: ExtensionContext) { total++ }
}