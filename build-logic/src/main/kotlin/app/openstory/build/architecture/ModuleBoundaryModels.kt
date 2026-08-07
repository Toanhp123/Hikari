package app.openstory.build.architecture

enum class ModulePlatform(
    val policyValue: String,
) {
    JVM("jvm"),
    ANDROID_APPLICATION("android-application"),
    ANDROID_LIBRARY("android-library"),
    ;

    companion object {
        fun fromPolicyValue(value: String): ModulePlatform =
            entries.firstOrNull { it.policyValue == value }
                ?: throw IllegalArgumentException(
                    "module_policy.unknown_platform: $value",
                )
    }
}

data class ModuleBoundaryPolicy(
    val schemaVersion: Int,
    val modules: Map<String, ModuleBoundaryRule>,
)

data class ModuleBoundaryRule(
    val path: String,
    val platform: ModulePlatform,
    val productionDependencies: Set<String>,
    val testDependencies: Set<String>,
    val forbiddenProductionImports: Set<String>,
)

data class ActualModule(
    val path: String,
    val platform: ModulePlatform,
    val productionDependencies: Set<String>,
    val testDependencies: Set<String>,
    val unknownProjectDependencyConfigurations: Map<String, Set<String>>,
    val productionImports: Set<String>,
)

data class ArchitectureViolation(
    val code: String,
    val module: String?,
    val detail: String,
) : Comparable<ArchitectureViolation> {
    override fun compareTo(other: ArchitectureViolation): Int =
        compareValuesBy(
            this,
            other,
            ArchitectureViolation::code,
            { it.module.orEmpty() },
            ArchitectureViolation::detail,
        )
}
