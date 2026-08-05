package app.openstory.network

import java.nio.charset.Charset
import okhttp3.MediaType

internal fun decodeResponseText(
    body: ByteArray,
    declaredCharset: String?,
    contentType: MediaType?,
): String? {
    val charset =
        when {
            declaredCharset != null ->
                Charset.forName(declaredCharset)

            contentType?.isTextual() == true ->
                contentType.charset(
                    Charsets.UTF_8,
                )

            else ->
                null
        }

    return charset?.let { selectedCharset ->
        String(
            bytes = body,
            charset = selectedCharset,
        )
    }
}

private fun MediaType.isTextual(): Boolean =
    type.equals(
        other = "text",
        ignoreCase = true,
    ) ||
        subtype.equals(
            other = "json",
            ignoreCase = true,
        ) ||
        subtype.endsWith(
            suffix = "+json",
            ignoreCase = true,
        ) ||
        subtype.equals(
            other = "xml",
            ignoreCase = true,
        ) ||
        subtype.endsWith(
            suffix = "+xml",
            ignoreCase = true,
        ) ||
        subtype.equals(
            other = "javascript",
            ignoreCase = true,
        )
