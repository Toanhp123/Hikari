package app.openstory.reader.routing

import app.openstory.common.id.ChapterReleaseId
import app.openstory.common.id.PluginId
import app.openstory.reader.engine.AccessMode
import app.openstory.reader.engine.AttemptRole
import app.openstory.reader.engine.ReaderRoutingPolicy
import app.openstory.reader.engine.RouteAttempt
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ReaderRouteRuntimeGuardTest {
    @Test
    fun sequentialRejectsMoreThanSevenTotalAttempts() {
        val attempts = buildList {
            add(remoteAttempt("a0", "r0", "s0", AttemptRole.PRIMARY))
            repeat(7) { index ->
                add(localAttempt("a${index + 1}", "r${index + 1}", "s${index + 1}", "fp-${index + 1}"))
            }
        }

        assertFailsWith<IllegalArgumentException> {
            ReaderRouteRuntimeGuard.validateSequential(attempts)
        }
    }

    @Test
    fun competitiveRejectsMoreThanSevenTotalAttempts() {
        val primary = remoteAttempt("p", "r0", "s0", AttemptRole.PRIMARY)
        val hedge = remoteAttempt("h", "r1", "s1", AttemptRole.HEDGE)
        val recovery = (2..7).map { index ->
            localAttempt("a$index", "r$index", "s$index", "fp-$index")
        }

        assertFailsWith<IllegalArgumentException> {
            ReaderRouteRuntimeGuard.validateCompetitive(primary, hedge, recovery)
        }
    }

    @Test
    fun sequentialRejectsMoreThanFourRemoteAttempts() {
        val attempts = (0..4).map { index ->
            remoteAttempt(
                attemptId = "a$index",
                releaseId = "r$index",
                sourceId = "s$index",
                role = if (index == 0) AttemptRole.PRIMARY else AttemptRole.FALLBACK,
            )
        }

        assertFailsWith<IllegalArgumentException> {
            ReaderRouteRuntimeGuard.validateSequential(attempts)
        }
    }

    @Test
    fun competitiveRejectsMoreThanFourRemoteAttempts() {
        val primary = remoteAttempt("p", "r0", "s0", AttemptRole.PRIMARY)
        val hedge = remoteAttempt("h", "r1", "s1", AttemptRole.HEDGE)
        val recovery = (2..4).map { index ->
            remoteAttempt("a$index", "r$index", "s$index", AttemptRole.FALLBACK)
        }

        assertFailsWith<IllegalArgumentException> {
            ReaderRouteRuntimeGuard.validateCompetitive(primary, hedge, recovery)
        }
    }

    @Test
    fun sequentialRejectsDuplicateAttemptIds() {
        val attempts = listOf(
            remoteAttempt("same", "r0", "s0", AttemptRole.PRIMARY),
            localAttempt("same", "r1", "s1", "fp-1"),
        )

        assertFailsWith<IllegalArgumentException> {
            ReaderRouteRuntimeGuard.validateSequential(attempts)
        }
    }

    @Test
    fun sequentialRejectsDuplicateExecutionLocators() {
        val attempts = listOf(
            remoteAttempt("a0", "r0", "s0", AttemptRole.PRIMARY),
            remoteAttempt("a1", "r0", "s0", AttemptRole.FALLBACK),
        )

        assertFailsWith<IllegalArgumentException> {
            ReaderRouteRuntimeGuard.validateSequential(attempts)
        }
    }

    @Test
    fun sequentialRejectsHedgeRole() {
        val attempts = listOf(
            remoteAttempt("p", "r0", "s0", AttemptRole.PRIMARY),
            remoteAttempt("h", "r1", "s1", AttemptRole.HEDGE),
        )

        assertFailsWith<IllegalArgumentException> {
            ReaderRouteRuntimeGuard.validateSequential(attempts)
        }
    }

    @Test
    fun competitiveRejectsMalformedRoles() {
        val primary = remoteAttempt("p", "r0", "s0", AttemptRole.PRIMARY)
        val malformedHedge = remoteAttempt("h", "r1", "s1", AttemptRole.FALLBACK)
        val malformedRecovery = listOf(remoteAttempt("r", "r2", "s2", AttemptRole.PRIMARY))

        assertFailsWith<IllegalArgumentException> {
            ReaderRouteRuntimeGuard.validateCompetitive(primary, malformedHedge, emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            ReaderRouteRuntimeGuard.validateCompetitive(primary, null, malformedRecovery)
        }
    }

    @Test
    fun competitiveRejectsSameSourceHedge() {
        val primary = remoteAttempt("p", "r0", "same", AttemptRole.PRIMARY)
        val hedge = remoteAttempt("h", "r1", "same", AttemptRole.HEDGE)

        assertFailsWith<IllegalArgumentException> {
            ReaderRouteRuntimeGuard.validateCompetitive(primary, hedge, emptyList())
        }
    }

    @Test
    fun competitiveRejectsLocalAttemptsInHedgePair() {
        val localPrimary = localAttempt("p", "r0", "s0", "fp-0", AttemptRole.PRIMARY)
        val remoteHedge = remoteAttempt("h", "r1", "s1", AttemptRole.HEDGE)
        val remotePrimary = remoteAttempt("p2", "r2", "s2", AttemptRole.PRIMARY)
        val localHedge = localAttempt("h2", "r3", "s3", "fp-3", AttemptRole.HEDGE)

        assertFailsWith<IllegalArgumentException> {
            ReaderRouteRuntimeGuard.validateCompetitive(localPrimary, remoteHedge, emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            ReaderRouteRuntimeGuard.validateCompetitive(remotePrimary, localHedge, emptyList())
        }
    }

    @Test
    fun defaultHesPolicyFitsInsideRuntimeCeilings() {
        val policy = ReaderRoutingPolicy.v1()

        assertTrue(
            policy.maxPlannedForegroundRemoteAttempts <= ReaderRuntimeLimits.MAX_FOREGROUND_REMOTE_ATTEMPTS,
        )
        assertTrue(1 + policy.maxRecoveryAttempts <= ReaderRuntimeLimits.MAX_TOTAL_FOREGROUND_ATTEMPTS)
    }

    private fun remoteAttempt(
        attemptId: String,
        releaseId: String,
        sourceId: String,
        role: AttemptRole,
    ) = RouteAttempt(
        attemptId = attemptId,
        releaseId = ChapterReleaseId(releaseId),
        sourceId = PluginId(sourceId),
        accessMode = AccessMode.REMOTE,
        localFingerprint = null,
        role = role,
    )

    private fun localAttempt(
        attemptId: String,
        releaseId: String,
        sourceId: String,
        fingerprint: String,
        role: AttemptRole = AttemptRole.FALLBACK,
    ) = RouteAttempt(
        attemptId = attemptId,
        releaseId = ChapterReleaseId(releaseId),
        sourceId = PluginId(sourceId),
        accessMode = AccessMode.LOCAL,
        localFingerprint = fingerprint,
        role = role,
    )
}
