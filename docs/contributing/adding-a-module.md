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
   - platform (`jvm`, `android-application`, or `android-library`);
   - allowed direct production project dependencies;
   - allowed direct test project dependencies;
   - forbidden production import prefixes when applicable.
4. Focused tests for the module's first behavior.
5. An update to the README module graph.
6. CI report paths when the module produces reports not already captured.
7. Current-wave checkpoint evidence.

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
- Production source in `:core:model` cannot import Android or Compose.
- Production source in `:core:plugin-api` cannot import Android or filesystem
  APIs.
- Production source in `:core:database` may implement the neutral plugin registry port,
  but cannot import `app.openstory.plugin.host.install.*` internals.
- Public reusable contract-test utilities belong to `:core:plugin-api` test fixtures;
  internal deterministic fake implementations and data belong to `:test:fixtures`.

## Commands

Run the architecture gate first:

    ./scripts/check-module-dependencies.sh

Then run the complete fast gate:

    ./scripts/verify.sh

Before closing the wave, run API 26 and API 37 checkpoint verification:

    ./scripts/checkpoints/app-shell.sh

If the module affects database or plugin contracts, also run the corresponding
`scripts/checkpoints/database.sh` or `scripts/checkpoints/plugin-contracts.sh` gate.
