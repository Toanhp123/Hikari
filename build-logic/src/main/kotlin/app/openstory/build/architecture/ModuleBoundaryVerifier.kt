package app.openstory.build.architecture

object ModuleBoundaryVerifier {
    fun verify(
        policy: ModuleBoundaryPolicy,
        actualModules: Map<String, ActualModule>,
    ): List<ArchitectureViolation> = buildList {
        addAll(moduleSetViolations(policy, actualModules))

        val sharedModules = policy.modules.keys intersect actualModules.keys
        sharedModules.forEach { module ->
            addAll(
                moduleViolations(
                    module = module,
                    rule = policy.modules.getValue(module),
                    actual = actualModules.getValue(module),
                ),
            )
        }
    }.sorted()

    private fun moduleSetViolations(
        policy: ModuleBoundaryPolicy,
        actualModules: Map<String, ActualModule>,
    ): List<ArchitectureViolation> = buildList {
        val policyNames = policy.modules.keys
        val actualNames = actualModules.keys

        (actualNames - policyNames).forEach { module ->
            add(
                ArchitectureViolation(
                    code = "module_policy.missing_module",
                    module = module,
                    detail = actualModules.getValue(module).path,
                ),
            )
        }

        (policyNames - actualNames).forEach { module ->
            add(
                ArchitectureViolation(
                    code = "module_policy.stale_module",
                    module = module,
                    detail = policy.modules.getValue(module).path,
                ),
            )
        }
    }

    private fun moduleViolations(
        module: String,
        rule: ModuleBoundaryRule,
        actual: ActualModule,
    ): List<ArchitectureViolation> = buildList {
        pathViolation(module, rule, actual)?.let(::add)
        platformViolation(module, rule, actual)?.let(::add)
        addAll(
            dependencyViolations(
                module = module,
                deniedCode = "module_policy.production_dependency_denied",
                staleAllowanceCode =
                    "module_policy.production_dependency_allowance_stale",
                actual = actual.productionDependencies,
                allowed = rule.productionDependencies,
                mode = rule.dependencyMode,
            ),
        )
        addAll(
            dependencyViolations(
                module = module,
                deniedCode = "module_policy.test_dependency_denied",
                staleAllowanceCode =
                    "module_policy.test_dependency_allowance_stale",
                actual = actual.testDependencies,
                allowed = rule.testDependencies,
                mode = rule.dependencyMode,
            ),
        )
        addAll(unknownConfigurationViolations(module, actual))
        addAll(importViolations(module, rule, actual))
    }

    private fun pathViolation(
        module: String,
        rule: ModuleBoundaryRule,
        actual: ActualModule,
    ): ArchitectureViolation? = if (rule.path == actual.path) {
        null
    } else {
        ArchitectureViolation(
            code = "module_policy.path_mismatch",
            module = module,
            detail = "expected=${rule.path};actual=${actual.path}",
        )
    }


    private fun platformViolation(
        module: String,
        rule: ModuleBoundaryRule,
        actual: ActualModule,
    ): ArchitectureViolation? = if (rule.platform == actual.platform) {
        null
    } else {
        ArchitectureViolation(
            code = "module_policy.platform_mismatch",
            module = module,
            detail =
                "expected=${rule.platform.policyValue};" +
                "actual=${actual.platform.policyValue}",
        )
    }

    private fun dependencyViolations(
        module: String,
        deniedCode: String,
        staleAllowanceCode: String,
        actual: Set<String>,
        allowed: Set<String>,
        mode: DependencyMode,
    ): List<ArchitectureViolation> = buildList {
        (actual - allowed)
            .sorted()
            .forEach { dependency ->
                add(
                    ArchitectureViolation(
                        code = deniedCode,
                        module = module,
                        detail = dependency,
                    ),
                )
            }

        if (mode == DependencyMode.EXACT) {
            (allowed - actual)
                .sorted()
                .forEach { dependency ->
                    add(
                        ArchitectureViolation(
                            code = staleAllowanceCode,
                            module = module,
                            detail = dependency,
                        ),
                    )
                }
        }
    }

    private fun unknownConfigurationViolations(
        module: String,
        actual: ActualModule,
    ): List<ArchitectureViolation> = actual
        .unknownProjectDependencyConfigurations
        .toSortedMap()
        .flatMap { (configuration, dependencies) ->
            dependencies.sorted().map { dependency ->
                ArchitectureViolation(
                    code = "module_policy.unknown_dependency_configuration",
                    module = module,
                    detail = "$configuration:$dependency",
                )
            }
        }

    private fun importViolations(
        module: String,
        rule: ModuleBoundaryRule,
        actual: ActualModule,
    ): List<ArchitectureViolation> = actual.productionImports
        .filter { importedType ->
            rule.forbiddenProductionImports.any(importedType::startsWith)
        }
        .sorted()
        .map { importedType ->
            ArchitectureViolation(
                code = "module_policy.platform_import_denied",
                module = module,
                detail = importedType,
            )
        }
}
