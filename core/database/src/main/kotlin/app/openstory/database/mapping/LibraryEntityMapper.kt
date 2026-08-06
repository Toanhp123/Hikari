package app.openstory.database.mapping

import app.openstory.database.entity.LibraryEntryEntity
import app.openstory.model.LibraryEntry
import app.openstory.model.LibraryStatus
import app.openstory.model.StoryId

internal fun LibraryEntryEntity.toDomain():
    LibraryEntry =
    LibraryEntry(
        storyId = StoryId(storyId),
        status =
            LibraryStatus.valueOf(
                status,
            ),
        addedAtEpochMillis =
            addedAtEpochMillis,
        updatedAtEpochMillis =
            updatedAtEpochMillis,
    )
