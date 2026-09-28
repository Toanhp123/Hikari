# Android manga extension runtime

## Scope

Hikari has an Android-first runtime for **installed, trusted Keiyoushi/Mihon-compatible manga extensions** using extension-lib `1.4` and `1.6`. The runtime is an infrastructure mechanism that creates normal Hikari `MediaSource` implementations before `SourceRegistry` composition; domain and application code do not depend on the extension ABI.

```text
installed extension APK
        |
        v
Android package discovery + trust/version checks
        |
        v
extension-lib 1.4/1.6 host ABI + class loader
        |
        v
MethodChannel gateway
        |
        v
MihonMangaSource
        |
        v
MangaSearchSource + MangaChapterSource + MangaPageSource
        |
        v
SourceRegistry / existing application workflows
```

This is intentionally **not** a universal plugin engine. Version 1 proves the manga content path end to end and keeps the extension-specific surface behind Android infrastructure.

## Why the host is native Android

Keiyoushi extension APKs are compiled Kotlin/Java Android code. They intentionally compile against host-provided `eu.kanade.tachiyomi.*` APIs and common runtime libraries rather than bundling a self-contained Dart/JavaScript plugin. Running them in Dart would require reimplementing their ABI and Android assumptions through a second RPC layer.

Hikari therefore hosts the ABI in Kotlin and exposes only normalized manga operations to Dart. The Dart side knows source descriptors, search results, chapters and pages; it never loads APK classes or depends on extension-lib types.

## Supported ABI and dependencies

The Android host implements the manga-facing compatibility surface used by extension-lib `1.4` and `1.6`:

- source contracts and models (`Source`, `CatalogueSource`, `HttpSource`, `SManga`, `SChapter`, `Page`, filters);
- network helpers and request functions;
- Injekt registrations used by current Keiyoushi core helpers;
- JSON/ProtoBuf, OkHttp, coroutines, RxJava, Jsoup and QuickJS runtime dependencies expected by extension APKs.

Dependency versions are pinned to the current Keiyoushi extension build baseline instead of floating independently. The host keeps the ABI split inside Kotlin: legacy 1.4 Rx operations are bridged into the same suspend calls used by Hikari, while 1.6 sources use their native suspend API. Extensions declaring another ABI version are ignored rather than loaded optimistically. For older APKs that predate `tachiyomix.extensionLib`, the loader follows Mihon's compatibility rule and derives `1.4`/`1.6` from the leading part of `versionName`.

## Discovery, trust and loading

At app startup the Android bridge enumerates installed packages and accepts a package only when all of these are true:

1. it advertises the Android feature `tachiyomi.extension`;
2. its signing certificate matches a trusted extension-repository fingerprint;
3. its manifest declares a non-empty `tachiyomi.extension.class` entry point;
4. its extension-lib version resolves to `1.4` or `1.6`;
5. every created source is a supported HTTP manga source and source IDs do not collide.

The current trusted key is the official Keiyoushi repository signing key. Hikari does **not** provide a UI for trusting arbitrary third-party certificates yet. This is deliberate: an extension runs code inside the Hikari process, so package metadata alone is not a security boundary.

On Android 8.1+ the installed APK path is loaded with `DelegateLastClassLoader`, keeping extension-bundled implementation classes ahead of host classes while resolving the compile-only `tachiyomi.*` ABI from Hikari. Android 8.0 uses `PathClassLoader`.

`QUERY_ALL_PACKAGES` is currently used for package discovery. Hikari is not targeting Play Store distribution, so the v1 runtime does not add repository-specific `<queries>` generation solely to satisfy Play policy.

## Bridge operations

The platform bridge exposes five operations:

- list installed compatible sources;
- title search;
- resolve manga to chapters;
- resolve chapter to pages;
- read page bytes.

The registry is composed once during bootstrap. There is no hot loading/unloading during the current app process; installing or updating an extension requires restarting Hikari before the source list changes.

## Identity and extension state

Upstream source IDs are stable `Long` values. Hikari maps every extension source, including MangaDex, to:

```text
SourceId("mihon:<upstream-source-id>")
```

Source-local URLs remain opaque locators. Extension-lib 1.6 also allows `SManga.memo` and `SChapter.memo` to carry source-private JSON state required by later calls, and some sources read chapter title/number/date/scanlator while resolving pages. Hikari keeps manga/chapter URLs and required continuation state inside an opaque `SourceMediaRef.itemId` payload (`mihon-v1:`) for every source. Extension-lib 1.4 sources use the same payload without `memo`. This avoids widening the domain model or SQLite schema in runtime v1.

That payload is transport state, not a cross-provider canonical identity. If upstream continuation state changes, the opaque item ID can also change; stable generic item identity is therefore not claimed by v1.

No legacy MangaDex identity compatibility or UUID translation is retained. Hikari intentionally accepts a clean app-data reset at this stage, not migration of pre-refactor Library/Progress rows; see [ADR-008](../decisions/ADR-008-external-remote-provider-ownership.md).

Only installed compatible extensions supply remote providers. If the extension is absent, its source is unavailable and existing generic missing-source handling applies; persisted rows remain intact. Reinstalling it and restarting Hikari restores resolution. Windows/iOS currently have no remote manga runtime or replacement provider. See [ADR-008](../decisions/ADR-008-external-remote-provider-ownership.md) for this change in provider ownership.

## Network behavior

The host provides a shared OkHttp client, WebView-backed cookies, a stable mobile default user agent, cache/timeouts and the interceptor class names expected by current `KeiSource` code. Android cleartext traffic is enabled because the community tree still contains HTTP sources. Page responses are bounded to 32 MiB before crossing the MethodChannel.

The `CloudflareInterceptor` is currently a compatibility hook, not a browser challenge solver. Extensions that require interactive Cloudflare/WebView challenge handling can therefore fail even when their ABI is otherwise compatible. Source-specified User-Agent headers are preserved; the host default is inserted only when a request does not provide one. That limitation is explicit rather than hidden behind automatic retries or source-specific hacks.

## Current non-goals

Runtime v1 does not include:

- extension repository browsing, download, install or update UI;
- private `.ext` installation inside Hikari storage;
- user-trusted arbitrary signing keys;
- source preference UI projection into Flutter;
- anime/video or novel extension APIs;
- hot reload/unload of extension packages;
- process isolation/sandboxing;
- a claim that every Keiyoushi source works without source-specific host features such as browser challenge handling.

These are separate requirements. They should be added only when a tested source or product workflow demonstrates the need.

## Verification

Automated Dart tests cover source adaptation, capability registration, generic MangaDex identity, opaque persisted references across extension absence/reinstall, stateful memo round-tripping, malformed references and source ownership. Native loading still requires physical Android verification because package visibility, certificate data, ART class loading and extension network behavior are platform concerns.

A useful device smoke test is:

```text
1. install an official Keiyoushi extension APK (for example MangaDex)
2. restart Hikari
3. confirm the source appears in remote manga search
4. search -> series -> chapter -> pages -> reader
5. save progress and Library state
6. restart Hikari and repeat open/resume
7. update/reinstall the extension, restart Hikari, and verify identity remains stable
```

## Upstream references

- Keiyoushi extension source/build baseline: <https://github.com/keiyoushi/extensions-source>
- Keiyoushi extension-lib ABI: <https://github.com/keiyoushi/extensions-lib>
- Keiyoushi repository metadata/signing fingerprint: <https://github.com/keiyoushi/extensions/blob/repo/repo.json>
- Katari extension loader/compatibility host reference: <https://github.com/pa2x2/katari>
- Android `DelegateLastClassLoader`: <https://developer.android.com/reference/dalvik/system/DelegateLastClassLoader>
