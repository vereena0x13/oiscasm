package gay.vereena.oiscasm.driver

import kotlin.reflect.*
import kotlinx.coroutines.*

import gay.vereena.oiscasm.util.*


interface Notification

class WithDriver(val driver: Driver) : ExtensionContext.AbstractElement(Key) {
    companion object Key : ExtensionContext.IKey<WithDriver>
}

suspend inline fun <reified T: Notification> WorkerScope.waitOn(value: Any) = waitOn(value, T::class, null)
suspend inline fun <reified T: Notification> WorkerScope.waitOn(value: Any, noinline orElse: (() -> Unit)) = waitOn(value, T::class, orElse)

suspend fun WorkerScope.waitOn(value: Any, notif: KClass<out Notification>, orElse: (() -> Unit)? = null) = with(WithDriver) { ext ->
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

fun WorkerScope.notifyOf(value: Any, notif: Notification) = withExt(WithDriver) { driver.notifyOf(value, notif) }

inline fun <reified T: Notification> WorkerScope.onNotify(crossinline it: (Any, T) -> Unit) = withExt(WithDriver) { driver.onNotify(it) }

fun <T> WorkerScope.reportError(e: T) = withExt(WithDriver) { driver.reportError(e) }

fun <T> WorkerScope.reportFatal(e: T, stop: Boolean = false): Nothing = withExt(WithDriver) {
    reportError(e)
    throw WorkerTerminated(stop)
}

fun WorkerScope.enqueueWorker(worker: WorkerConstructor) = withExt(WithDriver) { driver.enqueueWorker(worker) }


class Driver(private val exts: ExtensionContext = ExtensionContext.Empty) {
    private enum class State {
        RUNNING,
        DONE,
        STOPPED
    }

    private data class NotificationKey(val value: Any, val notif: KClass<out Notification>)


    private val driverExtension         = WithDriver(this)

    private val blocked                 = mutableMapOf<NotificationKey, MutableList<Pair<WorkerContinuation, (() -> Unit)>>>()
    private val startQueue              = ArrayDeque<WorkerConstructor>()
    private val runQueue                = ArrayDeque<WorkerContinuation>()
    private val running                 = mutableListOf<Job>()

    private val notificationCallbacks   = mutableMapOf<KClass<out Notification>, MutableList<(Any, Notification) -> Unit>>()

    private var errors                  = 0
    private var waits                   = 0
    private var notifies                = 0


    private fun makeWorkerScope(extensions: ExtensionContext) = object : WorkerScope {
        override val extensions = exts + extensions + driverExtension
    }


    fun waitOn(value: Any, notif: KClass<out Notification>, it: WorkerContinuation, orElse: () -> Unit) {
        waits++
        val key = NotificationKey(value, notif)
        val waiters = when (val waiters = blocked[key]) {
            null -> mutableListOf<Pair<WorkerContinuation, (() -> Unit)>>().also { blocked[key] = it }
            else -> waiters
        }
        waiters += Pair(it, orElse)
    }

    fun notifyOf(value: Any, notif: Notification) {
        notifies++
        notificationCallbacks[notif::class]?.forEach { it(value, notif) }
        val key = NotificationKey(value, notif::class)
        val waiters = blocked[key] ?: return
        waiters.forEach { runQueue.addLast(it.first) }
        waiters.clear()
    }

    inline fun <reified T: Notification> onNotify(crossinline it: (Any, T) -> Unit) {
        onNotify(T::class) { wu, n -> it(wu, n as T) }
    }

    fun onNotify(notif: KClass<out Notification>, it: (Any, Notification) -> Unit) {
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
                            supervisorScope { worker() }
                        }
                        job.invokeOnCompletion {
                            if (it is WorkerTerminated && it.stop) {
                                state = State.STOPPED
                            } else {
                                scope.extensions[WorkerCompletion.Key]?.fn?.invoke(driver)
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

            running.forEach { it.cancel(CancellationException(null, IllegalStateException(state.toString()))) }
        }
        val duration = System.currentTimeMillis() - start

        assert(running.isEmpty())

        val workersBlocked = blocked.asSequence()
            .map { it.value.size }
            .fold(0) { x, y -> x + y }

        return when {
            workersBlocked > 0 -> {
                val s = if (workersBlocked > 1) "s" else ""
                panic("Terminated with $workersBlocked error$s!")
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