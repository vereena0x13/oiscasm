package gay.vereena.sicoasm

import kotlinx.coroutines.*
import gay.vereena.sicoasm.exts.*
import gay.vereena.sicoasm.util.boldRed
import gay.vereena.sicoasm.util.eprintln
import java.nio.file.Paths
import kotlin.system.exitProcess

enum class WaitType {
}

//

@DslMarker
annotation class WorkerDslMarker

@WorkerDslMarker
interface WorkerScope {
    val extensions: ExtensionContext
}

typealias WorkerResult = Unit
typealias Worker = suspend () -> WorkerResult
typealias WorkerConstructor = ((ExtensionContext) -> WorkerScope) -> Pair<WorkerScope, Worker>

@WorkerDslMarker
fun worker(
    extensions: ExtensionContext = ExtensionContext.Empty,
    block: suspend WorkerScope.() -> WorkerResult
): WorkerConstructor = { fn ->
    fn(extensions).let { Pair(it) { it.block() } }
}

private typealias WorkerContinuation = CancellableContinuation<WorkerResult>

//

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

suspend fun WorkerScope.waitOn(value: Any, waitType: WaitType, orElse: (() -> Unit)? = null) = with(WithDriver) { ext ->
    val st = Thread.currentThread().stackTrace.drop(1)
    suspendCancellableCoroutine {
        val roe = {
            if (orElse == null) {
                val sts = st.joinToString("\n") { x -> "    $x" }
                reportError("${(boldRed)("error:")} Worker dependency never resolved; waiting on $value for $waitType\n$sts")
            } else {
                orElse.invoke()
            }
        }
        ext.driver.waitOn(value, waitType, it, roe)
    }
}

fun WorkerScope.notifyOf(value: Any, waitType: WaitType) = withExt(WithDriver) { driver.notifyOf(value, waitType) }

val WorkerScope.workerName get() = extensions[WorkerName]?.name ?: "<unknown>"

fun <T> WorkerScope.reportError(e: T) = withExt(WithDriver) {
    driver.errorReported()
    TERMINAL.println(e)
    TERMINAL.println()
}

fun <T> WorkerScope.reportFatal(e: T, stop: Boolean = false): Nothing = withExt(WithDriver) {
    reportError(e)
    Throwable().printStackTrace()
    throw WorkerTerminated(stop)
}

//

inline fun <T, E: ExtensionContext.AbstractElement> WorkerScope.with(key: ExtensionContext.IKey<E>, block: (E) -> T): T = extensions.with(key, block)
inline fun <T, E: ExtensionContext.AbstractElement> WorkerScope.withExt(key: ExtensionContext.IKey<E>, block: E.() -> T): T = with(key) { it.block() }

class WithDriver(val driver: Driver) : ExtensionContext.AbstractElement(Key) {
    companion object Key : ExtensionContext.IKey<WithDriver>
}

fun WorkerScope.enqueueWorker(worker: WorkerConstructor) = withExt(WithDriver) { driver.enqueueWorker(worker) }

data class WorkerTerminated(val stop: Boolean) : CancellationException("worker terminated")

class Driver {
    private enum class State {
        RUNNING,
        DONE,
        STOPPED
    }

    private val driverExtension = WithDriver(this)

    private fun makeWorkerScope(extensions: ExtensionContext) = object : WorkerScope {
        override val extensions = extensions + driverExtension
    }

    private data class WaitKey(val value: Any, val waitType: WaitType)

    private val workingDirectory = Paths.get(".").toAbsolutePath().normalize().toString()
    private val blocked = mutableMapOf<WaitKey, MutableList<Pair<WorkerContinuation, (() -> Unit)>>>()
    private val startQueue = ArrayDeque<WorkerConstructor>()
    private val runQueue = ArrayDeque<WorkerContinuation>()
    private val running = mutableListOf<Job>()
    private var errors = 0
    private var waits = 0
    private var notifies = 0

    fun waitOn(value: Any, waitType: WaitType, it: WorkerContinuation, orElse: (() -> Unit)) {
        waits++
        val key = WaitKey(value, waitType)
        val waiters = when (val waiters = blocked[key]) {
            null -> {
                val ws = mutableListOf<Pair<WorkerContinuation, (() -> Unit)>>()
                blocked[key] = ws
                ws
            }
            else -> waiters
        }
        waiters += Pair(it, orElse)
    }

    fun notifyOf(value: Any, waitType: WaitType) {
        notifies++
        val key = WaitKey(value, waitType)
        val waiters = blocked[key] ?: return
        waiters.forEach { runQueue.addLast(it.first) }
        blocked.remove(key)
    }

    fun enqueueWorker(worker: WorkerConstructor) {
        startQueue.addLast(worker)
    }

    fun errorReported() {
        errors++
    }

    fun run(): Boolean {
        val driver = this
        var retired = 0
        var iterations = 0

        val start = System.currentTimeMillis()
        runBlocking(CoroutineName("co_driver")) {
            var state = State.RUNNING
            while (state == State.RUNNING) {
                when {
                    startQueue.isNotEmpty() -> {
                        val ctor = startQueue.removeFirst()
                        val (scope, worker) = ctor(::makeWorkerScope)
                        val workerName = scope.extensions[WorkerName]?.name ?: "anon"
                        val job = launch(CoroutineName("co_$workerName"), CoroutineStart.LAZY) {
                            supervisorScope {
                                worker()
                            }
                        }
                        job.invokeOnCompletion {
                            if (it != null) {
                                if (it is WorkerTerminated && it.stop) {
                                    state = State.STOPPED
                                }
                            } else {
                                val group = scope.extensions[WithWorkerGroup.Key]
                                if (group != null) {
                                    group.completed++
                                    if (group.completed == group.total) {
                                        if (group.ran) throw IllegalStateException()
                                        group.ran = true
                                        group.onComplete(driver)
                                    }
                                }
                            }
                            running.remove(job)
                            retired++
                        }
                        running += job
                        job.start()
                    }
                    runQueue.isNotEmpty() -> {
                        val cont = runQueue.removeFirst()
                        cont.resume(Unit) { _, _, _ ->
                            // TODO?
                        }
                    }
                    else -> {
                        yield() // TODO: understand how this is helping us; it clearly is, but.. ???
                        if (startQueue.isEmpty() && runQueue.isEmpty()) state = State.DONE
                    }
                }
                iterations++
            }

            for (f in blocked) {
                val (_, g) = f
                g.forEach {
                    it.second.invoke()
                }
            }

            if (state != State.STOPPED) {
                assert(startQueue.isEmpty())
                assert(runQueue.isEmpty())
            }

            val ss = state.toString()
            running.forEach { it.cancel(CancellationException(null, IllegalStateException(ss))) }
        }
        val duration = System.currentTimeMillis() - start

        assert(running.isEmpty())

        val workersBlocked = blocked
            .map { it.value.size }
            .fold(0) { a, b -> a + b }

        return when {
            workersBlocked > 0 -> {
                val s = if (workersBlocked > 1) "s" else ""
                eprintln("Terminated with $workersBlocked internal compiler error$s!")
                exitProcess(1)
            }
            errors > 0 -> false
            else -> {
                TERMINAL.println("Finished in $duration ms!")
                //TERMINAL.println("  Files: ${files.size}")
                TERMINAL.println("  Jobs: $retired")
                TERMINAL.println("  Iterations: $iterations")
                TERMINAL.println("  Waits: $waits")
                TERMINAL.println("  Notifies: $notifies")
                TERMINAL.println()
                true
            }
        }
    }
}