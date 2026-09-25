# ADR-002: Foundation v1 toolchain and quality baseline

- Status: **Accepted**
- Date: **2026-09-25**

## Context

Hikari is still at bootstrap stage. Before Domain Core work starts, contributors and CI need to resolve the same Flutter toolchain and run the same minimum quality checks. The foundation should be reproducible without introducing a large tooling stack or committing to application libraries before requirements are known.

## Decision

Foundation v1 uses the following baseline:

- Flutter **3.47.5** is pinned in `.fvmrc`; the bundled Dart SDK is **3.13.4**.
- `pubspec.lock` remains committed because Hikari is an application.
- `flutter_lints` remains the lint baseline for Foundation v1.
- No state-management, networking, persistence, routing, code-generation, dependency-injection, or media package is added during bootstrap without a concrete requirement.
- The local Windows verification entry point is `tool/check.ps1`.
- The minimum quality gates are, in order:
  1. dependency resolution;
  2. format check;
  3. static analysis;
  4. automated tests;
  5. diff whitespace validation locally.
- GitHub Actions runs with Temurin JDK 17, the same Dart/Flutter quality gates, a lockfile freshness check, and an Android debug APK build on pushes and pull requests targeting `main` or `dev`.
- `.fvmrc` is the project source of truth for the Flutter version; CI reads that file instead of duplicating the version in workflow YAML.
- FVM-managed `.vscode/settings.json` is committed so VS Code resolves the same project SDK; `.fvmrc` remains the source of truth and FVM updates the editor path when the pinned SDK changes.
- Existing Flutter-generated AGP/Gradle/Kotlin compatibility settings are kept unless a dedicated compatibility review justifies changing them.
- The initial platform identifier is `io.github.toanhp123.hikari`, replacing Flutter's `com.example.hikari` placeholder on Android and iOS.

## Rationale

Pinning the Flutter SDK and dependency lockfile makes toolchain and dependency changes explicit. A format/analyze/test/build gate catches different failure classes while remaining simple enough to run routinely. Keeping the official `flutter_lints` baseline avoids adopting an opinionated lint policy before the codebase has demonstrated a need for one.

Android receives an explicit debug build gate because it is Hikari's first-priority platform and native/Gradle failures are not guaranteed to be caught by Dart analysis or widget tests.

## Consequences

### Positive

- Local development and CI resolve the same Flutter release.
- A fresh project already has a real automated test and CI failure surface.
- Android build integration is exercised before native dependencies accumulate.
- Foundation work remains near zero-dependency.

### Trade-offs

- FVM must be installed for the local helper script; on Windows, creating the project SDK symlink requires Developer Mode or equivalent symbolic-link privilege.
- CI currently validates Android but does not build Windows or iOS on every change.
- Release signing is intentionally deferred until release preparation; debug signing remains for current development builds.
- Coverage thresholds, git hook frameworks, monorepo tooling, and stricter third-party lint suites are deferred until evidence justifies them.
