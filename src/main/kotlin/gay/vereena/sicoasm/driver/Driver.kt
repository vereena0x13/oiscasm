package gay.vereena.sicoasm.driver

import kotlin.system.exitProcess
import kotlin.reflect.*
import kotlinx.coroutines.*

import gay.vereena.sicoasm.*
import gay.vereena.sicoasm.util.*


interface WorkUnit
interface Notification

class WithDriver(val driver: Driver) : ExtensionContext.AbstractElement(Key) {
    companion object Key : ExtensionContext.IKey<WithDriver>
}

suspend fun WorkerScope.waitOn(value: WorkUnit, notif: KClass<out Notification>, orElse: (() -> Unit)? = null) = with(WithDriver) { ext ->
    val st = Thread.currentThread().stackTrace.drop(1)
    suspendCancellableCoroutine {
        val roe = {
            if (orElse == null) {
                val sts = st.joinToString("\n") { x -> "    $x" }
                reportError("${(boldRed)("error:")} Worker dependency never resolved; waiting on $value for $notif\n$sts")
            } else {
                orElse.invoke()
            }
        }
        ext.driver.waitOn(value, notif, it, roe)
    }
}

fun WorkerScope.notifyOf(value: WorkUnit, notif: Notification) = withExt(WithDriver) { driver.notifyOf(value, notif) }

fun WorkerScope.onNotify(notif: KClass<out Notification>, it: (WorkUnit, Notification) -> Unit) = withExt(WithDriver) { driver.onNotify(notif, it) }

fun <T> WorkerScope.reportError(e: T) = withExt(WithDriver) {
    driver.reportError(e)
}

fun <T> WorkerScope.reportFatal(e: T, stop: Boolean = false): Nothing = withExt(WithDriver) {
    reportError(e)
    throw WorkerTerminated(stop)
}

fun WorkerScope.enqueueWorker(worker: WorkerConstructor) = withExt(WithDriver) { driver.enqueueWorker(worker) }


class Driver {
    private enum class State {
        RUNNING,
        DONE,
        STOPPED
    }

    private val driverExtension = WithDriver(this)

    private data class NotificationKey(val value: WorkUnit, val notif: KClass<out Notification>)

    private val blocked = mutableMapOf<NotificationKey, MutableList<Pair<WorkerContinuation, (() -> Unit)>>>()
    private val startQueue = ArrayDeque<WorkerConstructor>()
    private val runQueue = ArrayDeque<WorkerContinuation>()
    private val running = mutableListOf<Job>()

    private val notificationCallbacks = mutableMapOf<KClass<out Notification>, MutableList<(WorkUnit, Notification) -> Unit>>()

    private var errors = 0
    private var waits = 0
    private var notifies = 0


    private fun makeWorkerScope(extensions: ExtensionContext) = object : WorkerScope {
        override val extensions = extensions + driverExtension
    }

    fun waitOn(value: WorkUnit, notif: KClass<out Notification>, it: WorkerContinuation, orElse: () -> Unit) {
        waits++
        val key = NotificationKey(value, notif)
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

    fun notifyOf(value: WorkUnit, notif: Notification) {
        notifies++
        notificationCallbacks[notif::class]?.forEach { it(value, notif) }
        val key = NotificationKey(value, notif::class)
        val waiters = blocked[key] ?: return
        waiters.forEach { runQueue.addLast(it.first) }
        blocked.remove(key)
    }

    fun onNotify(notif: KClass<out Notification>, it: (WorkUnit, Notification) -> Unit) {
        if(notificationCallbacks[notif] == null) notificationCallbacks[notif] = mutableListOf(it)
        else notificationCallbacks[notif]!! += it
    }

    fun enqueueWorker(worker: WorkerConstructor) { startQueue.addLast(worker) }

    fun <T> reportError(e: T) {
        errors++
        TERMINAL.println(e)
        TERMINAL.println()
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
                        val job = launch(CoroutineName("co_${scope.workerName}"), CoroutineStart.LAZY) {
                            supervisorScope {
                                worker()
                            }
                        }
                        job.invokeOnCompletion {
                            if (it is WorkerTerminated && it.stop) {
                                state = State.STOPPED
                            } else {
                                // TODO: can we generalize this? i.e. WithWorkerCompletion?
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
                        cont.resume(Unit) { _, _, _ -> TODO() }
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
                g.forEach { it.second.invoke() }
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
            .fold(0) { x, y -> x + y }

        return when {
            workersBlocked > 0 -> {
                val s = if (workersBlocked > 1) "s" else ""
                eprintln("Terminated with $workersBlocked error$s!")
                exitProcess(1)
            }
            errors > 0 -> false
            else -> {
                TERMINAL.println("Finished in $duration ms!")
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