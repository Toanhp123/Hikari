package app.openstory.database

import androidx.room.TypeConverter
import app.openstory.model.ReaderPosition
import java.math.BigDecimal
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object DatabaseConverters {

    @Serializable
    private data class PositionDto(
        val kind: String,
        val index: Int = 0,
        val fraction: Float = 0f,
    )

    @TypeConverter
    fun fromReaderPosition(
        value: ReaderPosition,
    ): String =
        Json.encodeToString(
            PositionDto.serializer(),
            when (value) {
                ReaderPosition.Start ->
                    PositionDto(
                        kind = "start",
                    )

                is ReaderPosition.Paragraph ->
                    PositionDto(
                        kind = "paragraph",
                        index = value.index,
                        fraction = value.fraction,
                    )
            },
        )

    @TypeConverter
    fun toReaderPosition(
        value: String,
    ): ReaderPosition {
        val stored =
            Json.decodeFromString(
                PositionDto.serializer(),
                value,
            )

        return when (stored.kind) {
            "start" ->
                ReaderPosition.Start

            "paragraph" ->
                ReaderPosition.Paragraph(
                    index = stored.index,
                    fraction = stored.fraction,
                )

            else ->
                error(
                    "Unsupported reader position kind",
                )
        }
    }

    @TypeConverter
    fun fromBigDecimal(
        value: BigDecimal,
    ): String =
        value.toPlainString()

    @TypeConverter
    fun toBigDecimal(
        value: String,
    ): BigDecimal =
        BigDecimal(value)
}
