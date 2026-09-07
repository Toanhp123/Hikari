package app.openstory.build.architecture

internal fun foundationTestPolicy(
    maxProductionKotlinLines: Int = 300,
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
    schemaVersion = 1,
    maxProductionKotlinLines = maxProductionKotlinLines,
    forbiddenSourceTokens = forbiddenSourceTokens,
    forbiddenBuildTokens = forbiddenBuildTokens,
    forbiddenBroadTypeSuffixes = forbiddenBroadTypeSuffixes,
    forbiddenManifestPermissions = forbiddenManifestPermissions,
    allowedStartupInitializers = allowedStartupInitializers,
)
