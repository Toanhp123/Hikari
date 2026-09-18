package app.universalmedia.core.model

sealed interface ConsumptionTargetRef {
    data class MediaTarget(val mediaId: MediaId) : ConsumptionTargetRef

    data class UnitTarget(val unitId: UnitId) : ConsumptionTargetRef
}

enum class MediaKind { VIDEO }

enum class RepresentationFamily { VIDEO }
