# Remote manga

## Scope and boundary

Remote manga uses the same domain capabilities regardless of transport:

- `MangaSearchSource` returns normalized manga `Media`;
- `MangaSeriesSource` resolves a series into chapters;
- `MangaPageSource` resolves a chapter into pages and page bytes.

`SearchManga`, `OpenMedia` and `OpenMangaChapter` operate on those capabilities through the immutable `SourceRegistry`. Provider networking and website behavior belong to installed extensions, not Hikari core.

## Source selection

Platform bootstrap discovers compatible external sources before composition. `AppDependencies` registers the local source and supplied sources through `additionalSources`, then creates the immutable registry and generic application workflows. Duplicate source IDs fail fast.

Android can discover trusted installed manga extensions; see [EXTENSIONS](EXTENSIONS.md). Windows/iOS do not run Android extension APKs and currently have no remote manga runtime. Hikari does not construct a replacement provider when discovery returns no sources or fails.

With no usable manga search/page source, the existing capability-driven UI hides remote manga search. The app still boots; existing local media and Library functionality do not depend on a remote provider. Local SAF remains Android-only.

## Identity and user state

Source references remain source-local locators, not canonical content identities.

For all Android extension sources, including MangaDex:

```text
SourceId = "mihon:<upstream source id>"
itemId   = opaque extension-owned reference
```

Manga/chapter references use `mihon-v2:` with only resource kind and opaque provider URL. Mutable title, memo, chapter number, scanlator and upload date never participate in identity. The adapter awaits SQLite continuation writes before returning discovered references, and reads that state on later series/page calls, including after restart without searching again. Details refresh replaces manga continuation with the updated provider payload. Missing or malformed persisted state reports an error, never silently substitutes an empty memo.

Schema version 3 migrates valid prior generic `mihon-v1:` user references and their continuation; unrelated records are preserved. This does not restore the removed legacy MangaDex UUID translation. See [USER_STATE](USER_STATE.md) and [ADR-009](../decisions/ADR-009-stable-source-identities.md).

Without an extension, saved references encounter the existing missing-source error. Rows and continuation are not deleted. Reinstalling the compatible extension and restarting Hikari makes those references resolvable again.

Library stores the top-level series snapshot. Remote reader progress remains keyed by the selected chapter `SourceMediaRef` and `PagePosition`. Reopening the same chapter resumes its page. Remote page-byte caching, its initial budget and exclusions are recorded in [ADR-012](../decisions/ADR-012-reconstructible-cache-foundation.md). Decode retry refetches bytes instead of repeating cached corrupt data. Series-level “resume last chapter” remains deferred.

## Verification boundary

Automated tests cover zero-remote-source composition/UI, generic external-source workflows, extension adaptation, generic MangaDex identity, opaque persisted references across extension absence/reinstall, opaque continuation state and malformed references. They do not prove current live provider availability.

Physical Android verification should cover installed trusted extension discovery, search, series, chapters, pages, reader, progress/Library and restart/reinstall. See the device checklist in [EXTENSIONS](EXTENSIONS.md).

[ADR-008](../decisions/ADR-008-external-remote-provider-ownership.md) records why the direct provider was removed. Deferred work includes series-level last-chapter resume, canonical cross-source identity/dedup, downloads, account sync and extension repository/install/update UI.
