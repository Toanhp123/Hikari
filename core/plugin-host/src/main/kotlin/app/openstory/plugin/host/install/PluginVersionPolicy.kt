package app.openstory.plugin.host.install

import app.openstory.common.AppError
import app.openstory.common.AppResult
import java.math.BigInteger

class PluginVersionPolicy {
    fun validateInstall(
        candidateVersion: String,
        activeVersion: String?,
    ): AppResult<Unit> {
        val candidate = SemanticVersion.parse(candidateVersion)
        val active = activeVersion?.let(SemanticVersion::parse)

        return when {
            candidate == null -> invalidVersion()
            activeVersion != null && active == null -> invalidVersion()
            active != null && candidate < active -> downgradeDenied()
            else -> AppResult.Success(Unit)
        }
    }
}

private data class SemanticVersion(
    val major: BigInteger,
    val minor: BigInteger,
    val patch: BigInteger,
    val preRelease: List<String>,
) : Comparable<SemanticVersion> {
    override fun compareTo(other: SemanticVersion): Int {
        val coreComparison = compareValuesBy(
            this,
            other,
            SemanticVersion::major,
            SemanticVersion::minor,
            SemanticVersion::patch,
        )

        return if (coreComparison != 0) {
            coreComparison
        } else {
            comparePreRelease(preRelease, other.preRelease)
        }
    }

    companion object {
        fun parse(value: String): SemanticVersion? {
            val match = VERSION_PATTERN.matchEntire(value)

            return match?.let {
                runCatching {
                    SemanticVersion(
                        major = match.groupValues[1].toBigInteger(),
                        minor = match.groupValues[2].toBigInteger(),
                        patch = match.groupValues[3].toBigInteger(),
                        preRelease = match.groupValues[4]
                            .takeIf(String::isNotEmpty)
                            ?.split('.')
                            .orEmpty(),
                    )
                }.getOrNull()
            }
        }

        private val VERSION_PATTERN = Regex(
            """(\d+)\.(\d+)\.(\d+)(?:-([0-9A-Za-z.-]+))?(?:\+[0-9A-Za-z.-]+)?""",
        )
    }
}

private fun comparePreRelease(
    left: List<String>,
    right: List<String>,
): Int {
    var result = 0

    when {
        left.isEmpty() && right.isNotEmpty() -> result = 1
        left.isNotEmpty() && right.isEmpty() -> result = -1
        else -> {
            val maximumSize = maxOf(left.size, right.size)
            var index = 0

            while (index < maximumSize && result == 0) {
                result = comparePreReleaseIdentifier(
                    left = left.getOrNull(index),
                    right = right.getOrNull(index),
                )
                index += 1
            }
        }
    }

    return result
}

private fun comparePreReleaseIdentifier(
    left: String?,
    right: String?,
): Int =
    when {
        left == null -> -1
        right == null -> 1
        left.all(Char::isDigit) && right.all(Char::isDigit) ->
            left.toBigInteger().compareTo(right.toBigInteger())
        left.all(Char::isDigit) -> -1
        right.all(Char::isDigit) -> 1
        else -> left.compareTo(right)
    }

private fun invalidVersion(): AppResult.Failure =
    AppResult.Failure(
        AppError.Plugin(
            code = "plugin.package_version_invalid",
            retryable = false,
        ),
    )

private fun downgradeDenied(): AppResult.Failure =
    AppResult.Failure(
        AppError.Plugin(
            code = "plugin.package_downgrade_denied",
            retryable = false,
        ),
    )
