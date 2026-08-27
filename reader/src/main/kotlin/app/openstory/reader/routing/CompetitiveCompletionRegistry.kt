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
    val identity: ReaderAttemptIdentity,
    val attempt: RouteAttempt,
    val loaded: ReaderLoadResult.Success,
    val completedAtNanos: Long,
) {
    init {
        require(identity.attemptId == attempt.attemptId) {
            "Reader completion identity must match its route attempt."
        }
        require(completedAtNanos >= 0L) { "Reader completion time must be non-negative." }
    }
}

internal sealed interface ReaderAttemptOutcome {
    val identity: ReaderAttemptIdentity

    data class Success(val completion: ReaderValidCompletion) : ReaderAttemptOutcome {
        override val identity: ReaderAttemptIdentity
            get() = completion.identity
    }

    data class Failure(
        override val identity: ReaderAttemptIdentity,
        val failure: ReaderAttemptFailure,
    ) : ReaderAttemptOutcome
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
    val failures: List<ReaderAttemptOutcome.Failure>,
)

internal class ReaderCompetitiveExecution(
    private val scheduler: ReaderExecutionScheduler,
    private val executeAttempt: suspend (
        identity: ReaderAttemptIdentity,
        attempt: RouteAttempt,
        ownership: ReaderAttemptOwnership,
        onValidCompletion: (ReaderValidCompletion) -> Unit,
    ) -> ReaderAttemptOutcome,
    private val onAttemptStarted: suspend (
        identity: ReaderAttemptIdentity,
        attempt: RouteAttempt,
        recovering: Boolean,
        competingWithPrimary: ReaderAttemptIdentity?,
    ) -> Unit,
    private val onCompetitionLoser: suspend (RouteAttempt) -> Unit,
) {
    suspend fun execute(
        executionIdentity: ReaderExecutionIdentity,
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
        val failureByAttempt = mutableMapOf<String, ReaderAttemptOutcome.Failure>()
        val attempted = mutableListOf<RouteAttempt>()

        fun launchAttempt(
            attemptIdentity: ReaderAttemptIdentity,
            attempt: RouteAttempt,
            recovering: Boolean,
            competingWithPrimary: ReaderAttemptIdentity? = null,
        ): Job {
            val ownership = ReaderAttemptOwnership()
            ownershipByAttempt[attempt.attemptId] = ownership
            attempted += attempt
            return launch {
                try {
                    onAttemptStarted(attemptIdentity, attempt, recovering, competingWithPrimary)
                    val outcome = executeAttempt(attemptIdentity, attempt, ownership, registry::record)
                    check(outcome.identity == attemptIdentity) {
                        "Reader attempt outcome must retain the launched attempt identity."
                    }
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
        val primaryIdentity = executionIdentity.forAttempt(primary.attemptId)
        val primaryJob = launchAttempt(primaryIdentity, primary, recovering = false)
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
            launchAttempt(
                attemptIdentity = executionIdentity.forAttempt(hedge.attemptId),
                attempt = hedge,
                recovering = recovering,
                competingWithPrimary = primaryIdentity.takeUnless { recovering },
            )
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
                            failureByAttempt[event.attempt.attemptId] = outcome
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
            .map(ReaderAttemptOutcome.Failure::failure)
            .filter { it.recoveryScope == RecoveryScope.SOURCE_SCOPED }
            .mapTo(linkedSetOf()) { it.sourceId }
        for (attempt in recoveryChain) {
            if (attempt.accessMode == AccessMode.REMOTE && attempt.sourceId in suppressedSources) continue
            val attemptIdentity = executionIdentity.forAttempt(attempt.attemptId)
            val ownership = ReaderAttemptOwnership()
            attempted += attempt
            onAttemptStarted(attemptIdentity, attempt, true, null)
            val outcome = executeAttempt(attemptIdentity, attempt, ownership, registry::record)
            check(outcome.identity == attemptIdentity) {
                "Reader attempt outcome must retain the launched attempt identity."
            }
            when (outcome) {
                is ReaderAttemptOutcome.Success -> {
                    return@coroutineScope ReaderRouteExecutionOutcome(outcome.completion, emptyList())
                }
                is ReaderAttemptOutcome.Failure -> {
                    failureByAttempt[attempt.attemptId] = outcome
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
