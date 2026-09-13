package app.openstory.build.architecture

internal fun foundationTestPolicy(
    maxProductionKotlinLines: Int = 300,
    productionKotlinLineBudgets: Map<String, Int> = mapOf("*" to Int.MAX_VALUE),
    forbiddenSourceTokens: Set<String> = emptySet(),
    forbiddenBuildTokens: Set<String> = emptySet(),
    forbiddenBroadTypeSuffixes: Set<String> = setOf(
        "Manager",
        "Coordinator",
        "Registry",
        "ServiceLocator",
    ),
    forbiddenManifestPermissions: Set<String> = setOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.POST_NOTIFICATIONS",
    ),
    allowedStartupInitializers: Set<String> = setOf(
        "androidx.profileinstaller.ProfileInstallerInitializer",
    ),
): FoundationPolicy = FoundationPolicy(
    schemaVersion = 2,
    maxProductionKotlinLines = maxProductionKotlinLines,
    productionKotlinLineBudgets = productionKotlinLineBudgets,
    forbiddenSourceTokens = forbiddenSourceTokens,
    forbiddenBuildTokens = forbiddenBuildTokens,
    forbiddenBroadTypeSuffixes = forbiddenBroadTypeSuffixes,
    forbiddenManifestPermissions = forbiddenManifestPermissions,
    allowedStartupInitializers = allowedStartupInitializers,
)
