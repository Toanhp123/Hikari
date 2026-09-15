# Adding a Gradle Module

A module addition changes the repository architecture and must be reviewable as
one atomic change.

## Required changes

The same commit must include:

1. `include(":new:module")` in `settings.gradle.kts`.
2. The module build file and the correct OpenStory convention plugin.
3. One entry in `config/architecture/module-boundaries.json` containing:
   - module path;
   - repository directory;
   - platform (`jvm`, `android-application`, `android-library`, or tooling-only `android-test`);
   - allowed direct production project dependencies;
   - allowed direct test project dependencies;
   - forbidden production import prefixes when applicable.
4. Focused tests for the module's first behavior.
5. An update to `docs/project/current-state.md` and any affected navigation docs; do not duplicate an
   exact module graph in README prose.
6. A source/package layout that follows `../project/file-package-ownership-policy.md`.
7. CI report paths when the module produces reports not already captured.
8. Current-wave checkpoint evidence.

## Review rules

- Do not add a dependency to the policy merely to make verification green.
  Confirm that its direction matches the approved architecture first.
- Declare exactly the current direct project dependencies; do not duplicate
  transitive library dependencies or retain permissions for dependencies that
  are no longer declared.
- A dependency allowed only for tests belongs in `testDependencies` and must use
  a test configuration.
- Unknown dependency configurations fail closed. Extend the architecture plugin
  only when the configuration has a reviewed architectural meaning.
- Production source in `:plugins:api` is pure Kotlin/JVM protocol code and cannot
  import Android or host application models.
- Presentation modules may consume `:core:designsystem` for domain-neutral
  Compose theme, tokens, and shared UX surfaces. Capability, storage, and plugin
  runtime modules must remain independent from it unless the exact architecture
  policy is explicitly redesigned and reviewed.
- Production source may import only packages exposed by the module's exact project dependencies and
  must remain inside its responsibility described by `module-boundaries.json` and the canonical
  file/package ownership policy.
- Reusable protocol fixtures belong to the owning module's test resources; deterministic fakes and
  builders stay in the narrowest test source set that owns them.
- Do not create `utils`, `helpers`, `misc`, capability-local `common`, or `impl` as convenience buckets
  during module creation. Name packages by cohesive responsibility.

## Commands

Run the focused dependency gate first:

    ./scripts/check-module-dependencies.sh

Then run the current fast host gate:

    ./scripts/verify-fast.sh

Before closing the owning task/checkpoint, follow `../implementation/current-roadmap.md` and its named
plan/checkpoint for the required full host, connected/device, migration, benchmark, or profile evidence.
Do not hard-code historical API/device commands into a new module solely because an older wave used them.
Plugin protocol changes must also run the contract commands documented in
`../plugin-sdk/contract-testing.md`.
