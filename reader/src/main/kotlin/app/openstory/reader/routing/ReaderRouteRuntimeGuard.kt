package app.openstory.reader.routing

import app.openstory.reader.engine.AccessMode
import app.openstory.reader.engine.AttemptRole
import app.openstory.reader.engine.RouteAttempt

internal object ReaderRouteRuntimeGuard {
    fun validateSequential(attempts: List<RouteAttempt>) {
        validateCommon(attempts)
        if (attempts.isEmpty()) return
        require(attempts.first().role == AttemptRole.PRIMARY) {
            "Reader adaptive route first attempt must be PRIMARY."
        }
        require(attempts.drop(1).all { it.role == AttemptRole.FALLBACK }) {
            "Sequential adaptive execution accepts PRIMARY followed only by FALLBACK attempts."
        }
    }

    fun validateCompetitive(
        primary: RouteAttempt,
        hedge: RouteAttempt?,
        recoveryChain: List<RouteAttempt>,
    ) {
        require(primary.role == AttemptRole.PRIMARY) {
            "Reader competitive route primary attempt must be PRIMARY."
        }
        require(hedge == null || hedge.role == AttemptRole.HEDGE) {
            "Reader competitive hedge attempt must be HEDGE."
        }
        require(recoveryChain.all { it.role == AttemptRole.FALLBACK }) {
            "Reader competitive recovery accepts only FALLBACK attempts."
        }
        if (hedge != null) {
            require(primary.accessMode == AccessMode.REMOTE && hedge.accessMode == AccessMode.REMOTE) {
                "Reader competitive hedge pair must use REMOTE access."
            }
            require(primary.sourceId != hedge.sourceId) {
                "Reader competitive hedge must use a distinct source."
            }
        }
        validateCommon(
            buildList {
                add(primary)
                hedge?.let(::add)
                addAll(recoveryChain)
            },
        )
    }

    private fun validateCommon(attempts: List<RouteAttempt>) {
        require(attempts.size <= ReaderRuntimeLimits.MAX_TOTAL_FOREGROUND_ATTEMPTS) {
            "Reader route exceeds HES-v1 total attempt ceiling: ${attempts.size}"
        }
        require(
            attempts.count { it.accessMode == AccessMode.REMOTE } <=
                ReaderRuntimeLimits.MAX_FOREGROUND_REMOTE_ATTEMPTS,
        ) {
            "Reader route exceeds HES-v1 REMOTE attempt ceiling."
        }
        require(attempts.map { it.attemptId }.toSet().size == attempts.size) {
            "Reader route attempt IDs must be unique."
        }
        require(
            attempts.map { Triple(it.releaseId, it.accessMode, it.localFingerprint) }.toSet().size == attempts.size,
        ) {
            "Reader route cannot execute the same release/access/locator twice."
        }
    }
}
