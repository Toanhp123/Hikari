# ADR-008: External extensions own remote provider implementations

- Status: **Accepted**
- Date: **2026-09-28**
- Modifies: [ADR-005](ADR-005-application-source-registry.md) direct-provider transport and [ADR-007](ADR-007-android-manga-extension-runtime.md) fallback clauses; their capability/host decisions remain accepted.

## Context

The Android extension runtime has now been proven end to end, as confirmed by the project owner. That removes the reason to keep a second MangaDex implementation in Dart. Maintaining both duplicates provider networking, parsing and website maintenance inside Hikari core.

Current upstream evidence supports separating the host from providers: Mihon's extension manager loads externally installed packages and maps their source IDs to package ownership; Keiyoushi publishes package metadata with separate language-specific source IDs; Suwayomi also executes external Mihon-compatible extensions and documents having no default extensions. These are boundary references, not a reason to copy those applications wholesale.

## Decision

Remove the built-in Dart MangaDex source/client and automatic fallback. Hikari owns source capabilities, native compatibility hosting, trust validation, source adaptation and generic workflows. Installed compatible Android extensions own provider-specific network and website behavior.

Bootstrap discovers compatible sources before composition. The composition root registers local plus supplied sources through one generic seam, then creates the immutable registry. It does not select a privileged provider or own a provider-specific HTTP lifecycle. An empty remote-source set is a valid app configuration.

Preserve the official English MangaDex compatibility alias independently from implementation ownership. Keiyoushi's current index confirms package `eu.kanade.tachiyomi.extension.all.mangadex`, source ID `2499283573021220255`, language `en`. All three must match behind the existing trusted-package loader; a matching display name or URL is insufficient. Historical `SourceId('mangadex')` and manga/chapter UUIDs continue to translate to the extension contract. No Library/Progress migration or schema change is needed.

## Alternatives considered

- **Keep the direct fallback:** rejected because it duplicates provider maintenance and hides absent external sources.
- **Migrate saved MangaDex rows to `mihon:<id>`:** rejected because the narrow alias already preserves valid references without rewriting user state.
- **Add another cross-platform provider mechanism:** deferred; no verified requirement justifies another runtime in this cleanup.

## Consequences

- Android remote manga requires installed compatible extensions. Without one, remote search is absent; existing local/library functionality continues.
- Windows/iOS currently have no remote manga runtime and no remote MangaDex fallback. This does not add local SAF support on those platforms.
- Missing extensions leave saved rows intact and use existing generic unavailable-source handling. Reinstalling the compatible extension and restarting restores resolution.
- Generic opaque/stateful extension references, capability contracts, immutable registration, trust checks and source ownership validation remain unchanged.
- Direct-provider HTTP tests and the direct Dart `http` dependency are no longer needed. Any transitive package requirements remain Pub's responsibility.
- This decision does not add extension management UI, hot registration, additional platform runtimes or a new identity model.

## Evidence

Inspected current upstream sources on 2026-09-28:

- [Mihon extension documentation](https://mihon.app/docs/faq/browse/extensions): bring-your-own-content; extensions are external packages, not bundled providers.
- [Mihon ExtensionLoader](https://github.com/mihonapp/mihon/blob/main/app/src/main/java/eu/kanade/tachiyomi/extension/util/ExtensionLoader.kt): discovers packages, checks feature metadata, ABI and trust, then instantiates declared source classes through a class loader.
- [Keiyoushi contributor conventions](https://github.com/keiyoushi/extensions-source/blob/main/CONTRIBUTING.md#renaming-existing-sources): preserve package names for updates and explicitly retain old source IDs when names/languages change, avoiding disconnected libraries.
- [Mihon ExtensionManager](https://github.com/mihonapp/mihon/blob/main/app/src/main/java/eu/kanade/tachiyomi/extension/ExtensionManager.kt): external package loading, installation/update and source-to-package lookup.
- [Keiyoushi repository index](https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.json): exact MangaDex package and English source identity; other languages have distinct source IDs in the same package.
- [Suwayomi server](https://github.com/Suwayomi/Suwayomi-Server#what-is-suwayomi) and [Getting Extensions](https://github.com/Suwayomi/Suwayomi-Server/wiki/Getting-Extensions): external Mihon-compatible extension execution without bundled default extensions.

Current mechanics belong in [SOURCES](../architecture/SOURCES.md), [EXTENSIONS](../architecture/EXTENSIONS.md) and [REMOTE_MANGA](../architecture/REMOTE_MANGA.md), not duplicated here.
