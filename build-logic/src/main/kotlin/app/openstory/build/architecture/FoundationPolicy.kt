package app.openstory.build.architecture

data class FoundationPolicy(
    val schemaVersion: Int,
    val maxProductionKotlinLines: Int,
    val forbiddenSourceTokens: Set<String>,
    val forbiddenBuildTokens: Set<String>,
    val forbiddenBroadTypeSuffixes: Set<String>,
    val forbiddenManifestPermissions: Set<String>,
    val allowedStartupInitializers: Set<String>,
)

data class FoundationViolation(
    val code: String,
    val detail: String,
) : Comparable<FoundationViolation> {
    override fun compareTo(other: FoundationViolation): Int =
        compareValuesBy(this, other, FoundationViolation::code, FoundationViolation::detail)
}
