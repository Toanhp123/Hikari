package app.openstory.build.architecture

object BootSourceBoundaryVerifier {
    fun verify(
        sources: Map<String, String>,
        buildScript: String,
        policy: FoundationPolicy,
    ): List<FoundationViolation> = buildList {
        sources.toSortedMap().forEach { (path, text) ->
            policy.forbiddenSourceTokens
                .filter(text::contains)
                .forEach { token ->
                    add(
                        FoundationViolation(
                            code = "v2_boot.forbidden_source_reference",
                            detail = "$path:$token",
                        ),
                    )
                }
        }

        policy.forbiddenBuildTokens
            .filter(buildScript::contains)
            .forEach { token ->
                add(
                    FoundationViolation(
                        code = "v2_boot.forbidden_build_reference",
                        detail = token,
                    ),
                )
            }
    }.distinct().sorted()
}
