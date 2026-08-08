package app.openstory.database.mapping

import app.openstory.database.dao.StoryAggregate
import app.openstory.database.entity.CanonicalStoryEntity
import app.openstory.database.entity.CatalogEntryEntity
import app.openstory.model.CanonicalStory
import app.openstory.model.CatalogEntry
import app.openstory.model.CatalogEntryId
import app.openstory.model.ContentType
import app.openstory.model.LanguageTag
import app.openstory.model.PluginId
import app.openstory.model.StoryId
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal fun CanonicalStory.toEntity():
    CanonicalStoryEntity =
    CanonicalStoryEntity(
        storyId = id.value,
        contentType = contentType.name,
        preferredTitle = preferredTitle,
        aliasesJson =
            Json.encodeToString(aliases),
    )

internal fun CatalogEntry.toEntity():
    CatalogEntryEntity =
    CatalogEntryEntity(
        catalogEntryId = id.value,
        catalogPluginId =
            catalogPluginId.value,
        externalStoryId = externalStoryId,
        sourceUrl = sourceUrl,
        title = title,
        aliasesJson = Json.encodeToString(aliases),
        authorsJson = Json.encodeToString(authors),
        description = description,
        genresJson = Json.encodeToString(genres),
        contentType = contentType.name,
        languageTagsJson =
            Json.encodeToString(
                languageTags.map(LanguageTag::value).toSet(),
            ),
        coverReference = coverReference,
        publicationStatus = publicationStatus,
        score = score,
        scoreScale = scoreScale,
        popularityRank = popularityRank,
        pluginVersion = pluginVersion,
        fetchedAtEpochMillis = fetchedAtEpochMillis,
    )

internal fun StoryAggregate.toDomain():
    CanonicalStory =
    CanonicalStory(
        id = StoryId(story.storyId),
        contentType =
            ContentType.valueOf(
                story.contentType,
            ),
        preferredTitle =
            story.preferredTitle,
        aliases =
            Json.decodeFromString(
                story.aliasesJson,
            ),
        catalogEntries =
            catalogEntries.map { entry ->
                entry.toDomain()
            },
    )

internal fun CatalogEntryEntity.toDomain():
    CatalogEntry =
    CatalogEntry(
        id =
            CatalogEntryId(
                catalogEntryId,
            ),
        catalogPluginId =
            PluginId(
                catalogPluginId,
            ),
        externalStoryId = externalStoryId,
        sourceUrl = sourceUrl,
        title = title,
        aliases = Json.decodeFromString(aliasesJson),
        authors = Json.decodeFromString(authorsJson),
        description = description,
        genres = Json.decodeFromString(genresJson),
        contentType = ContentType.valueOf(contentType),
        languageTags =
            Json.decodeFromString<Set<String>>(
                languageTagsJson,
            ).map { value -> LanguageTag(value) }.toSet(),
        coverReference = coverReference,
        publicationStatus = publicationStatus,
        score = score,
        scoreScale = scoreScale,
        popularityRank = popularityRank,
        pluginVersion = pluginVersion,
        fetchedAtEpochMillis = fetchedAtEpochMillis,
    )
