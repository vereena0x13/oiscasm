package gay.vereena.sicoasm.driver

import gay.vereena.sicoasm.util.ExtensionContext
import gay.vereena.sicoasm.util.with
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException


@DslMarker
annotation class WorkerDslMarker


@WorkerDslMarker
interface WorkerScope {
    val extensions: ExtensionContext
}

inline fun <T, E: ExtensionContext.AbstractElement> WorkerScope.with(key: ExtensionContext.IKey<E>, block: (E) -> T): T = extensions.with(key, block)
inline fun <T, E: ExtensionContext.AbstractElement> WorkerScope.withExt(key: ExtensionContext.IKey<E>, block: E.() -> T): T = with(key) { it.block() }


typealias WorkerResult = Unit
typealias Worker = suspend () -> WorkerResult
typealias WorkerConstructor = ((ExtensionContext) -> WorkerScope) -> Pair<WorkerScope, Worker>
typealias WorkerContinuation = CancellableContinuation<WorkerResult>

@WorkerDslMarker
fun worker(
    extensions: ExtensionContext = ExtensionContext.Empty,
    block: suspend WorkerScope.() -> WorkerResult
): WorkerConstructor = { fn ->
    fn(extensions).let { Pair(it) { it.block() } }
}


data class WorkerTerminated(val stop: Boolean) : CancellationException("worker terminated")


class WorkerName(wname: String) : ExtensionContext.AbstractElement(Key) {
    companion object Key : ExtensionContext.IKey<WorkerName> {
        private val counts = mutableMapOf<String, Int>()

        private fun uniq(s: String) = when (val n = counts[s]) {
            null -> {
                counts[s] = 1
                "$s/0"
            }
            else -> {
                counts[s] = n + 1
                "$s/$n"
            }
        }
    }

    val name = uniq(wname)

    override fun toString() = "WorkerName($name)"
}

val WorkerScope.workerName get() = extensions[WorkerName]?.name ?: "anon"