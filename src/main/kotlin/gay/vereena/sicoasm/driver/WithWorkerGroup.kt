package gay.vereena.sicoasm.driver


// TODO: make this an actual ExtensionContext.AbstractElement so that we can just
// add it to an ExtensionContext with the + operator like we would for any other
// extension. To do this, we'll need to have a function that is called when
// an extension is added to an ExtensionContext that we can use here to
// increment the total.
// Theoretically this should work fine and be pretty easy to implement.
// Once we implement this, we'll rename WorkerGroup to WithWorkerGroup.
class WorkerGroup(private val total: Int, val onGroupComplete: (Driver) -> Unit) {
    private var workerExtsCreated = 0
    private var completed = 0

    fun workerExt(): WithWorkerCompletion {
        workerExtsCreated++
        if(workerExtsCreated > total) throw Exception("Attempted to create more worker completion extensions than the worker group expected ($total)")
        return WithWorkerCompletion { driver ->
            completed += 1
            if(completed == total) onGroupComplete(driver)
        }
    }
}