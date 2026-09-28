# Remote manga

## Scope and boundary

Remote manga uses the same domain capabilities regardless of transport:

- `MangaSearchSource` returns normalized manga `Media`;
- `MangaChapterSource` resolves a series into chapters;
- `MangaPageSource` resolves a chapter into pages and page bytes.

`SearchManga`, `OpenMedia` and `OpenMangaChapter` operate on those capabilities through the immutable `SourceRegistry`. They do not know whether a source is the built-in Dart MangaDex implementation or an Android extension adapter.

Android can discover compatible installed manga extensions before composition; see [EXTENSIONS](EXTENSIONS.md). Windows/iOS do not run Android extension APKs.

## Source selection and fallback

Hikari still ships the direct Dart `MangaDexSource` so the existing cross-platform remote-manga vertical does not disappear when no extension runtime is available.

At composition:

```text
installed sources discovered first
        |
        +-- official English MangaDex extension owns SourceId('mangadex')
        |       -> do not create/register built-in MangaDex
        |
        +-- no extension owns SourceId('mangadex')
                -> register built-in MangaDexSource fallback
```

Other installed extension sources use their own stable IDs and coexist with the fallback.

## Identity and user state

Source references remain source-local locators, not canonical content identities.

For ordinary Android extension sources:

```text
SourceId = "mihon:<upstream source id>"
itemId   = opaque extension-owned reference
```

The opaque reference can include extension-lib `memo` state required to make later chapter/page calls. Domain and persisted user-state code do not parse it.

### MangaDex migration compatibility

The official English MangaDex extension is deliberately exposed as the same identity used by the direct source:

- source: `SourceId('mangadex')`;
- series: MangaDex manga UUID;
- chapter: MangaDex chapter UUID.

This lets existing Library and Progress rows survive switching between the direct source and the extension-backed source. The alias is granted only to the official MangaDex package, its legacy English upstream source ID, and language; a source merely calling itself “MangaDex” does not get that identity.

Library stores the top-level series snapshot. Remote reader progress remains keyed by the selected chapter `SourceMediaRef` and `PagePosition`. Reopening the same chapter resumes its page. Series-level “resume last chapter” is still deferred.

## Built-in MangaDex fallback

The direct fallback keeps MangaDex-specific HTTP behavior inside `MangaDexClient`; it is not a generic network layer. Its source implementation remains useful for cross-platform behavior, tests and a no-extension Android fallback.

The extension-backed MangaDex path delegates provider behavior to the installed extension instead. Hikari must not duplicate provider-specific parsing/rate-limit logic in the Dart adapter just to imitate an extension.

## Verification boundary

Automated tests cover provider-neutral remote workflows, the direct MangaDex fallback and extension adapter/reference behavior. They do not prove current live provider availability.

Physical Android verification for the extension path should cover:

```text
install trusted extension
-> restart Hikari
-> source discovery
-> search
-> series
-> chapter
-> pages
-> reader
-> progress/library
-> process restart
```

The direct fallback should continue to be exercised by its existing HTTP/fake tests and cross-platform quality gates.

Current deferred work includes series-level last-chapter resume, canonical cross-source identity/dedup, downloads, account sync and a user-facing extension repository/install/update flow.
