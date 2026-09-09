package app.openstory.catalog.storage.discover

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "catalog_source_state",
    primaryKeys = ["source_key", "media_type"],
)
internal data class CatalogSourceStateEntity(
    @ColumnInfo(name = "source_key") val sourceKey: String,
    @ColumnInfo(name = "media_type") val mediaType: String,
    @ColumnInfo(name = "source_version") val sourceVersion: String,
    @ColumnInfo(name = "published_generation") val publishedGeneration: Long,
    @ColumnInfo(name = "published_at_epoch_ms") val publishedAtEpochMs: Long,
    @ColumnInfo(name = "last_success_epoch_ms") val lastSuccessEpochMs: Long,
)

@Entity(
    tableName = "discover_card",
    primaryKeys = [
        "source_key",
        "media_type",
        "generation",
        "section_kind",
        "item_position",
    ],
    foreignKeys = [
        ForeignKey(
            entity = CatalogSourceStateEntity::class,
            parentColumns = ["source_key", "media_type"],
            childColumns = ["source_key", "media_type"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
        ForeignKey(
            entity = app.openstory.catalog.storage.story.StorySourceIdentityEntity::class,
            parentColumns = ["story_id"],
            childColumns = ["story_id"],
            onDelete = ForeignKey.CASCADE,
            deferred = true,
        ),
    ],
    indices = [
        Index(value = ["source_key", "media_type"]),
        Index(value = ["story_id"]),
        Index(
            value = ["source_key", "media_type", "generation", "section_kind", "story_id"],
            unique = true,
        ),
    ],
)
internal data class DiscoverCardEntity(
    @ColumnInfo(name = "source_key") val sourceKey: String,
    @ColumnInfo(name = "media_type") val mediaType: String,
    @ColumnInfo(name = "source_version") val sourceVersion: String,
    val generation: Long,
    @ColumnInfo(name = "section_kind") val sectionKind: String,
    @ColumnInfo(name = "item_position") val itemPosition: Int,
    @ColumnInfo(name = "story_id") val storyId: String,
    @ColumnInfo(name = "source_story_id") val sourceStoryId: String,
    val title: String,
    @ColumnInfo(name = "content_type") val contentType: String,
    @ColumnInfo(name = "cover_locator_type") val coverLocatorType: String?,
    @ColumnInfo(name = "cover_locator_value") val coverLocatorValue: String?,
    @ColumnInfo(name = "cover_locator_aux") val coverLocatorAux: String?,
    @ColumnInfo(name = "cover_revision") val coverRevision: String?,
    @ColumnInfo(name = "rating_value") val ratingValue: Double?,
    @ColumnInfo(name = "rating_scale") val ratingScale: Double?,
    @ColumnInfo(name = "publication_status_summary") val publicationStatusSummary: String?,
    @ColumnInfo(name = "latest_update_epoch_ms") val latestUpdateEpochMs: Long?,
)

internal data class DiscoverObservationRow(
    @ColumnInfo(name = "state_source_key") val stateSourceKey: String,
    @ColumnInfo(name = "state_media_type") val stateMediaType: String,
    @ColumnInfo(name = "state_source_version") val stateSourceVersion: String,
    @ColumnInfo(name = "state_generation") val stateGeneration: Long,
    @ColumnInfo(name = "state_published_at_epoch_ms") val statePublishedAtEpochMs: Long,
    @ColumnInfo(name = "card_source_version") val cardSourceVersion: String?,
    @ColumnInfo(name = "card_section_kind") val cardSectionKind: String?,
    @ColumnInfo(name = "card_item_position") val cardItemPosition: Int?,
    @ColumnInfo(name = "card_story_id") val cardStoryId: String?,
    @ColumnInfo(name = "card_source_story_id") val cardSourceStoryId: String?,
    @ColumnInfo(name = "card_title") val cardTitle: String?,
    @ColumnInfo(name = "card_content_type") val cardContentType: String?,
    @ColumnInfo(name = "card_cover_locator_type") val cardCoverLocatorType: String?,
    @ColumnInfo(name = "card_cover_locator_value") val cardCoverLocatorValue: String?,
    @ColumnInfo(name = "card_cover_locator_aux") val cardCoverLocatorAux: String?,
    @ColumnInfo(name = "card_cover_revision") val cardCoverRevision: String?,
    @ColumnInfo(name = "card_rating_value") val cardRatingValue: Double?,
    @ColumnInfo(name = "card_rating_scale") val cardRatingScale: Double?,
    @ColumnInfo(name = "card_publication_status_summary") val cardPublicationStatusSummary: String?,
    @ColumnInfo(name = "card_latest_update_epoch_ms") val cardLatestUpdateEpochMs: Long?,
)
