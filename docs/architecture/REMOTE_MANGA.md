# Remote manga

## Scope and boundary

Remote manga uses the same domain capabilities regardless of transport:

- `MangaSearchSource` returns normalized manga `Media`;
- `MangaChapterSource` resolves a series into chapters;
- `MangaPageSource` resolves a chapter into pages and page bytes.

`SearchManga`, `OpenMedia` and `OpenMangaChapter` operate on those capabilities through the immutable `SourceRegistry`. Provider networking and website behavior belong to installed extensions, not Hikari core.

## Source selection

Platform bootstrap discovers compatible external sources before composition. `AppDependencies` registers the local source and supplied sources through `additionalSources`, then creates the immutable registry and generic application workflows. Duplicate source IDs fail fast.

Android can discover trusted installed manga extensions; see [EXTENSIONS](EXTENSIONS.md). Windows/iOS do not run Android extension APKs and currently have no remote manga runtime. Hikari does not construct a replacement provider when discovery returns no sources or fails.

With no usable manga search/page source, the existing capability-driven UI hides remote manga search. The app still boots; existing local media and Library functionality do not depend on a remote provider. Local SAF remains Android-only.

## Identity and user state

Source references remain source-local locators, not canonical content identities.

For ordinary Android extension sources:

```text
SourceId = "mihon:<upstream source id>"
itemId   = opaque extension-owned reference
```

The opaque reference can include extension-lib `memo` state required to make later chapter/page calls. Domain and persisted user-state code do not parse it.

### MangaDex migration compatibility

The official English MangaDex extension retains the identity used by historical Hikari Library and Progress rows:

- source: `SourceId('mangadex')`;
- series: MangaDex manga UUID;
- chapter: MangaDex chapter UUID.

The alias requires the exact package, upstream English source ID and language documented in [EXTENSIONS](EXTENSIONS.md). Name/base URL alone never grants it. The adapter translates legacy UUIDs to the extension's manga/chapter URL contract; it does not implement MangaDex networking.

Without that extension, saved references encounter the existing generic missing-source error. Rows are not deleted or migrated. Reinstalling the compatible extension and restarting Hikari makes the same references resolvable again. No database schema change is required.

Library stores the top-level series snapshot. Remote reader progress remains keyed by the selected chapter `SourceMediaRef` and `PagePosition`. Reopening the same chapter resumes its page. Series-level “resume last chapter” remains deferred.

## Verification boundary

Automated tests cover zero-remote-source composition/UI, generic external-source workflows, extension adaptation, narrow MangaDex identity matching, persisted legacy reference translation, opaque continuation state and malformed references. They do not prove current live provider availability.

Physical Android verification should cover installed trusted extension discovery, search, series, chapters, pages, reader, progress/Library and restart/reinstall. See the device checklist in [EXTENSIONS](EXTENSIONS.md).

[ADR-008](../decisions/ADR-008-external-remote-provider-ownership.md) records why the direct provider was removed. Deferred work includes series-level last-chapter resume, canonical cross-source identity/dedup, downloads, account sync and extension repository/install/update UI.
