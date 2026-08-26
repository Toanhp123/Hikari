package app.openstory.reader.routing

import app.openstory.reader.content.ReaderLoadResult
import app.openstory.reader.engine.AccessMode
import app.openstory.reader.engine.AttemptRole
import app.openstory.reader.engine.HedgeDirective
import app.openstory.reader.engine.RecoveryScope
import app.openstory.reader.engine.RouteAttempt
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

internal data class ReaderValidCompletion(
    val attempt: RouteAttempt,
    val loaded: ReaderLoadResult.Success,
    val completedAtNanos: Long,
) {
    init {
        require(completedAtNanos >= 0L) { "Reader completion time must be non-negative." }
    }
}

internal sealed interface ReaderAttemptOutcome {
    data class Success(val completion: ReaderValidCompletion) : ReaderAttemptOutcome
    data class Failure(val failure: ReaderAttemptFailure) : ReaderAttemptOutcome
}

internal class ReaderAttemptOwnership {
    private val owned = AtomicBoolean(true)

    fun isOwned(): Boolean = owned.get()

    fun close(): Boolean = owned.compareAndSet(true, false)
}

internal class CompetitiveCompletionRegistry {
    private val lock = Any()
    private val completions = linkedMapOf<String, ReaderValidCompletion>()

    fun record(completion: ReaderValidCompletion) {
        synchronized(lock) {
            val previous = completions.putIfAbsent(completion.attempt.attemptId, completion)
            require(previous == null || previous == completion) {
                "Reader attempt completion cannot be recorded with conflicting facts."
            }
        }
    }

    fun contains(attemptId: String): Boolean = synchronized(lock) { attemptId in completions }

    fun winner(): ReaderValidCompletion? = synchronized(lock) {
        completions.values.minWithOrNull(COMPLETION_ORDER)
    }

    private companion object {
        val COMPLETION_ORDER = compareBy<ReaderValidCompletion>(
            ReaderValidCompletion::completedAtNanos,
            { completion ->
                when (completion.attempt.role) {
                    AttemptRole.PRIMARY -> 0
                    AttemptRole.HEDGE -> 1
                    AttemptRole.FALLBACK -> 2
                }
            },
            { it.attempt.attemptId },
        )
    }
}

internal data class ReaderRouteExecutionOutcome(
    val completion: ReaderValidCompletion?,
    val failures: List<ReaderAttemptFailure>,
)

internal class ReaderCompetitiveExecution(
    private val scheduler: ReaderExecutionScheduler,
    private val executeAttempt: suspend (
        attempt: RouteAttempt,
        ownership: ReaderAttemptOwnership,
        onValidCompletion: (ReaderValidCompletion) -> Unit,
    ) -> ReaderAttemptOutcome,
    private val onAttemptStarted: suspend (RouteAttempt, Boolean) -> Unit,
    private val onCompetitionLoser: suspend (RouteAttempt) -> Unit,
) {
    suspend fun execute(
        primary: RouteAttempt,
        hedgeDirective: HedgeDirective,
        recoveryChain: List<RouteAttempt>,
    ): ReaderRouteExecutionOutcome = coroutineScope {
        val hedge = (hedgeDirective as? HedgeDirective.Launch)?.attempt
        ReaderRouteRuntimeGuard.validateCompetitive(primary, hedge, recoveryChain)
        val registry = CompetitiveCompletionRegistry()
        val terminalEvents = Channel<TerminalEvent>(Channel.UNLIMITED)
        val ownershipByAttempt = mutableMapOf<String, ReaderAttemptOwnership>()
        val jobsByAttempt = mutableMapOf<String, Job>()
        val failureByAttempt = mutableMapOf<String, ReaderAttemptFailure>()
        val attempted = mutableListOf<RouteAttempt>()

        fun launchAttempt(attempt: RouteAttempt, recovering: Boolean): Job {
            val ownership = ReaderAttemptOwnership()
            ownershipByAttempt[attempt.attemptId] = ownership
            attempted += attempt
            return launch {
                try {
                    onAttemptStarted(attempt, recovering)
                    val outcome = executeAttempt(attempt, ownership, registry::record)
                    terminalEvents.send(TerminalEvent.Completed(attempt, outcome))
                } catch (cancelled: CancellationException) {
                    ownership.close()
                    terminalEvents.trySend(TerminalEvent.Cancelled(attempt))
                    throw cancelled
                }
            }.also { jobsByAttempt[attempt.attemptId] = it }
        }

        var hedgeStarted = false
        var primaryTerminal = false
        var hedgeTerminal = hedge == null
        val primaryJob = launchAttempt(primary, recovering = false)
        val hedgeDelayJob = hedge?.let {
            launch {
                scheduler.delayMillis(hedgeDirective.delayMillis)
                terminalEvents.send(TerminalEvent.HedgeDelayElapsed)
            }
        }

        suspend fun startHedge(recovering: Boolean) {
            if (hedge == null || hedgeStarted) return
            hedgeStarted = true
            hedgeTerminal = false
            launchAttempt(hedge, recovering)
        }

        suspend fun closeCompetitionLosers(winner: ReaderValidCompletion) {
            jobsByAttempt.forEach { (attemptId, job) ->
                if (attemptId == winner.attempt.attemptId || !job.isActive) return@forEach
                val ownership = ownershipByAttempt.getValue(attemptId)
                if (ownership.close()) {
                    val loser = attempted.first { it.attemptId == attemptId }
                    onCompetitionLoser(loser)
                }
                job.cancel()
            }
        }

        while (true) {
            val event = terminalEvents.receive()
            when (event) {
                TerminalEvent.HedgeDelayElapsed -> {
                    if (
                        !primaryTerminal &&
                        primaryJob.isActive &&
                        !registry.contains(primary.attemptId)
                    ) {
                        startHedge(recovering = false)
                    }
                }
                is TerminalEvent.Cancelled -> {
                    if (event.attempt.attemptId == primary.attemptId) primaryTerminal = true
                    if (event.attempt.attemptId == hedge?.attemptId) hedgeTerminal = true
                }
                is TerminalEvent.Completed -> {
                    if (event.attempt.attemptId == primary.attemptId) primaryTerminal = true
                    if (event.attempt.attemptId == hedge?.attemptId) hedgeTerminal = true
                    when (val outcome = event.outcome) {
                        is ReaderAttemptOutcome.Success -> {
                            hedgeDelayJob?.cancel()
                            val winner = checkNotNull(registry.winner())
                            closeCompetitionLosers(winner)
                            return@coroutineScope ReaderRouteExecutionOutcome(winner, emptyList())
                        }
                        is ReaderAttemptOutcome.Failure -> {
                            failureByAttempt[event.attempt.attemptId] = outcome.failure
                            if (event.attempt.attemptId == primary.attemptId && !hedgeStarted) {
                                hedgeDelayJob?.cancel()
                                startHedge(recovering = true)
                            }
                        }
                    }
                }
            }
            if (primaryTerminal && hedgeTerminal) break
        }

        hedgeDelayJob?.cancel()
        val suppressedSources = failureByAttempt.values
            .asSequence()
            .filter { it.recoveryScope == RecoveryScope.SOURCE_SCOPED }
            .mapTo(linkedSetOf()) { it.sourceId }
        for (attempt in recoveryChain) {
            if (attempt.accessMode == AccessMode.REMOTE && attempt.sourceId in suppressedSources) continue
            val ownership = ReaderAttemptOwnership()
            attempted += attempt
            onAttemptStarted(attempt, true)
            when (val outcome = executeAttempt(attempt, ownership, registry::record)) {
                is ReaderAttemptOutcome.Success -> {
                    return@coroutineScope ReaderRouteExecutionOutcome(outcome.completion, emptyList())
                }
                is ReaderAttemptOutcome.Failure -> {
                    failureByAttempt[attempt.attemptId] = outcome.failure
                    if (outcome.failure.recoveryScope == RecoveryScope.SOURCE_SCOPED) {
                        suppressedSources += outcome.failure.sourceId
                    }
                }
            }
        }

        ReaderRouteExecutionOutcome(
            completion = null,
            failures = attempted.mapNotNull { failureByAttempt[it.attemptId] },
        )
    }

    private sealed interface TerminalEvent {
        data object HedgeDelayElapsed : TerminalEvent
        data class Completed(
            val attempt: RouteAttempt,
            val outcome: ReaderAttemptOutcome,
        ) : TerminalEvent
        data class Cancelled(val attempt: RouteAttempt) : TerminalEvent
    }
}
