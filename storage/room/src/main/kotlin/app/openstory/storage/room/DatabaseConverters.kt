package app.openstory.storage.room

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

internal class DatabaseConverters {
    @TypeConverter
    fun encodeStrings(values: Set<String>): String = Json.encodeToString(values.sorted())

    @TypeConverter
    fun decodeStrings(value: String): Set<String> = Json.decodeFromString<List<String>>(value).toSet()
}
