package app.universalmedia.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Verification task has no reusable output")
abstract class VerifyArchitectureTask : DefaultTask() {
    @get:Input
    abstract val moduleBuildScripts: MapProperty<String, String>

    @get:Input
    abstract val allowedProjectDependencies: MapProperty<String, List<String>>

    @get:Input
    abstract val dependencyPattern: Property<String>

    @TaskAction
    fun verify() {
        val scripts = moduleBuildScripts.get()
        val allowed = allowedProjectDependencies.get()
        val errors = mutableListOf<String>()

        val missingPolicy = scripts.keys - allowed.keys
        if (missingPolicy.isNotEmpty()) {
            errors += "Missing architecture allow-list entries: ${missingPolicy.sorted()}"
        }

        val stalePolicy = allowed.keys - scripts.keys
        if (stalePolicy.isNotEmpty()) {
            errors += "Architecture allow-list contains unknown modules: ${stalePolicy.sorted()}"
        }

        val pattern = Regex(dependencyPattern.get())
        scripts.forEach { (modulePath, scriptText) ->
            val moduleAllowed = allowed[modulePath].orEmpty().toSet()
            val actual = pattern
                .findAll(scriptText)
                .map { match -> match.groupValues[1] }
                .toSet()
            val forbidden = actual - moduleAllowed
            if (forbidden.isNotEmpty()) {
                errors += "$modulePath has forbidden project dependencies: ${forbidden.sorted()}"
            }
        }

        check(errors.isEmpty()) { errors.joinToString(separator = "\n") }
    }
}
