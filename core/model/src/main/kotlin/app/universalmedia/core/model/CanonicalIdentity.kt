package app.universalmedia.core.model

import java.util.UUID

@JvmInline
value class MediaId(val value: UUID) {
    companion object {
        fun generate(): MediaId = MediaId(UUID.randomUUID())
    }
}

@JvmInline
value class UnitId(val value: UUID) {
    companion object {
        fun generate(): UnitId = UnitId(UUID.randomUUID())
    }
}

@JvmInline
value class RootId(val value: UUID) {
    companion object {
        fun generate(): RootId = RootId(UUID.randomUUID())
    }
}

@JvmInline
value class SourceId(val value: UUID) {
    companion object {
        fun generate(): SourceId = SourceId(UUID.randomUUID())
    }
}

@JvmInline
value class SourceBindingId(val value: UUID) {
    companion object {
        fun generate(): SourceBindingId = SourceBindingId(UUID.randomUUID())
    }
}

@JvmInline
value class AssetId(val value: UUID) {
    companion object {
        fun generate(): AssetId = AssetId(UUID.randomUUID())
    }
}

@JvmInline
value class ScanRunId(val value: UUID) {
    companion object {
        fun generate(): ScanRunId = ScanRunId(UUID.randomUUID())
    }
}
