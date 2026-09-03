package app.openstory.reader.assets

class ReaderAssetWorkingSetPolicy {
    fun visibleDescriptors(
        manifest: ReaderAssetChapterManifest,
        viewport: ReaderViewportSnapshot?,
    ): List<ReaderPageAssetDescriptor> = if (viewport == null || viewport.sessionId != manifest.sessionId) {
        emptyList()
    } else {
        val leading = viewport.leadingVisibleImageOrdinal
        val trailing = viewport.trailingVisibleImageOrdinal
        if (leading == null || trailing == null) {
            emptyList()
        } else {
            (leading..trailing).mapNotNull(manifest.descriptors::getOrNull)
        }
    }

    fun memoryPrewarmBehind(
        manifest: ReaderAssetChapterManifest,
        viewport: ReaderViewportSnapshot?,
    ): List<ReaderPageAssetDescriptor> {
        if (viewport == null) return emptyList()
        val ordinals = when (viewport.direction) {
            ReaderViewportDirection.FORWARD,
            ReaderViewportDirection.IDLE,
            -> viewport.leadingVisibleImageOrdinal?.let { leading ->
                (leading - ReaderAssetRuntimePolicy.COIL_PREWARM_BEHIND until leading)
                    .filter { it >= 0 }
            }.orEmpty()
            ReaderViewportDirection.BACKWARD -> viewport.trailingVisibleImageOrdinal?.let { trailing ->
                (trailing + 1..trailing + ReaderAssetRuntimePolicy.COIL_PREWARM_BEHIND).toList()
            }.orEmpty()
        }
        return ordinals.mapNotNull(manifest.descriptors::getOrNull)
    }

    fun protections(
        manifest: ReaderAssetChapterManifest?,
        viewport: ReaderViewportSnapshot?,
        consumedKeys: Set<ReaderPageAssetKey>,
        recentManifests: List<ReaderAssetChapterManifest>,
        plan: ReaderAssetPlan,
    ): ReaderAssetActiveProtections {
        if (manifest == null) return ReaderAssetActiveProtections.EMPTY
        val protections = mutableMapOf<ReaderAssetKeyHash, ReaderAssetProtectionClass>()
        recentManifests
            .take(ReaderAssetRuntimePolicy.RECENT_COMMITTED_HISTORY_DEPTH)
            .forEachIndexed { index, recent ->
                val protectionClass = if (index == 0) {
                    ReaderAssetProtectionClass.RECENT_HISTORY_1
                } else {
                    ReaderAssetProtectionClass.RECENT_HISTORY_2
                }
                recent.descriptors.forEach { descriptor ->
                    protections.putStrongest(descriptor.key.hash, protectionClass)
                }
            }
        plan.transition.forEach { descriptor ->
            protections.putStrongest(
                descriptor.key.hash,
                ReaderAssetProtectionClass.TRANSITION_SPECULATIVE,
            )
        }
        plan.currentAhead.forEach { descriptor ->
            protections.putStrongest(
                descriptor.key.hash,
                ReaderAssetProtectionClass.CURRENT_AHEAD_SPECULATIVE,
            )
        }
        consumedKeys.forEach { key ->
            protections.putStrongest(key.hash, ReaderAssetProtectionClass.ACTIVE_CONSUMED)
        }
        (plan.interactive + visibleDescriptors(manifest, viewport)).forEach { descriptor ->
            protections.putStrongest(descriptor.key.hash, ReaderAssetProtectionClass.ACTIVE_INTERACTIVE)
        }
        return ReaderAssetActiveProtections(protections.toMap())
    }

    fun union(
        sessionProtections: Iterable<ReaderAssetActiveProtections>,
    ): ReaderAssetActiveProtections {
        val union = mutableMapOf<ReaderAssetKeyHash, ReaderAssetProtectionClass>()
        sessionProtections.forEach { protections ->
            protections.byKey.forEach { (key, protectionClass) ->
                union.putStrongest(key, protectionClass)
            }
        }
        return ReaderAssetActiveProtections(union.toMap())
    }
}

private fun MutableMap<ReaderAssetKeyHash, ReaderAssetProtectionClass>.putStrongest(
    key: ReaderAssetKeyHash,
    candidate: ReaderAssetProtectionClass,
) {
    val current = this[key]
    if (current == null || candidate.ordinal < current.ordinal) this[key] = candidate
}
