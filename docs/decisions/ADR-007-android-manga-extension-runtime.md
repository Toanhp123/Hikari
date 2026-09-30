# ADR-007: Host compatible manga extensions behind an Android infrastructure boundary

- Status: **Accepted; direct-provider fallback and MangaDex identity exception superseded by [ADR-008](ADR-008-external-remote-provider-ownership.md)**
- Date: **2026-09-27**

ADR-008 removes the direct-provider fallback and MangaDex identity exception, accepting a clean app-data reset. This Android host decision remains accepted; current identity mechanics belong in [EXTENSIONS](../architecture/EXTENSIONS.md).

ADR-009 supersedes the historical mutable-reference choice below: [stable source identities](ADR-009-stable-source-identities.md) separate persisted continuation from identity. Current series metadata uses `MangaSeriesSource` rather than the original chapter-only capability.

## Context

ADR-005 intentionally created an immutable `SourceRegistry` and capability contracts before choosing a plugin mechanism. Hikari now has a real consumer for that seam: reuse the existing Mihon/Keiyoushi manga extension ecosystem without teaching domain/application code about APKs, Kotlin source APIs or provider-specific transport.

A JavaScript/Dart extension format would be easier to sandbox conceptually but would not run existing Keiyoushi APKs. Forking Mihon/Katari wholesale would bring a large application architecture, database and presentation stack that Hikari does not need. Reimplementing individual websites in Dart repeats source maintenance and defeats the extension goal.

The current ecosystem is split across extension-lib 1.4 and 1.6. New Keiyoushi sources can use the generated 1.6 entry point and suspend APIs, while a large installed-source population still targets the legacy Rx-based 1.4 ABI. Mihon itself supports both versions. Extension-lib 1.6 can also carry source-private state in `SManga.memo` and `SChapter.memo`.

## Decision

Implement an Android-native manga compatibility host for extension-lib 1.4 and 1.6 and keep it behind Hikari infrastructure adapters.

- Android discovers installed extension APKs, validates trusted signing certificates and supported ABI versions, then loads their declared source entry points.
- The native host supplies the shared `tachiyomi.*` surface and pinned runtime libraries expected by the extension ecosystem.
- The compatibility layer bridges the legacy Rx 1.4 calls and the suspend 1.6 calls inside Kotlin; Dart does not know which ABI a source uses.
- Kotlin normalizes source/search/chapter/page data across one MethodChannel.
- Dart adapts each native descriptor into the existing `MangaSearchSource`, `MangaChapterSource` and `MangaPageSource` capabilities.
- Extension discovery happens before immutable `SourceRegistry` composition; no hot mutable registry is introduced.
- Source-private `memo` state is encoded inside opaque source references rather than widening the domain model. All sources use the generic identity/reference path per ADR-008.
- Runtime v1 trusts the current official Keiyoushi repository signing key. Arbitrary third-party trust is not implicit.

## Security boundary

An extension APK executes in the Hikari process. Therefore “has Tachiyomi metadata” is not considered trust. Loading requires a trusted signing fingerprint. Arbitrary third-party trust, repository management and process isolation are separate future decisions.

The loader uses the installed APK path with delegate-last class loading on Android 8.1+ and a parent-first `PathClassLoader` fallback on Android 8.0. Duplicate source IDs reject the whole package load instead of partially registering it.

## Alternatives considered

### Reimplement sources in Dart

Rejected as the primary direction. It preserves Flutter portability but recreates the maintenance burden the extension ecosystem already solves. The original direct-provider fallback was subsequently removed by ADR-008.

### Embed/fork Mihon or Katari

Rejected. Their loader and ABI behavior are valuable references, but importing the application stack would duplicate Hikari's domain, persistence and presentation architecture.

### JavaScript extension engine

Deferred. It may be useful for a Hikari-native future ecosystem but does not provide compatibility with installed Keiyoushi APKs.

### Load every package advertising extension metadata

Rejected. Extension code runs with Hikari's process privileges; signature trust is required.

### Support only extension-lib 1.6

Rejected after checking the current Keiyoushi tree. A substantial number of maintained sources still build against 1.4, and Mihon's loader intentionally accepts both 1.4 and 1.6. Supporting only 1.6 would make Hikari's compatibility surface unnecessarily narrower than the ecosystem it is trying to reuse.

## Consequences

- Domain/application contracts remain unchanged; the source registry seam proved sufficient.
- Android gains access to installed compatible manga extensions without a provider-specific branch in opening workflows.
- Windows/iOS do not run Android APK extensions; ADR-008 also removes their original direct-provider fallback.
- Hikari accepts a native dependency set matching the extension ABI and must review it when the ecosystem changes.
- Trusted extension code is not sandboxed from Hikari; the signing policy is therefore part of the runtime's security model.
- Some extensions can still fail when they depend on host features v1 does not implement, especially interactive browser/Cloudflare behavior or preferences UI.
- Installing/updating extensions and adding anime/novel extension families remain future verticals, not hidden scope inside this decision.
