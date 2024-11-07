package gay.vereena.oiscasm.driver

import gay.vereena.oiscasm.util.*


class WorkerGroup(val onGroupComplete: (Driver) -> Unit) : WorkerCompletion() {
    private var total = 0
    private var completed = 0
    private var ran = false

    override val fn = { driver: Driver ->
        completed += 1
        assert(completed <= total)
        if(completed == total) {
            ran = true
            onGroupComplete(driver)
        }
    }

    override fun onAddedTo(exts: ExtensionContext) {
        assert(!ran)
        total++
    }
}