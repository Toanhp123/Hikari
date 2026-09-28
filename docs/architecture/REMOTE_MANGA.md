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

For all Android extension sources, including MangaDex:

```text
SourceId = "mihon:<upstream source id>"
itemId   = opaque extension-owned reference
```

The opaque reference can include extension-lib `memo` state required to make later chapter/page calls. Domain and persisted user-state code do not parse it.

Manga/chapter references use the generic opaque `mihon-v1:` payload described in [EXTENSIONS](EXTENSIONS.md). No legacy MangaDex identity compatibility or UUID translation is retained: the project intentionally accepts a clean app-data reset at this stage instead of migrating pre-refactor Library/Progress rows, per [ADR-008](../decisions/ADR-008-external-remote-provider-ownership.md).

Without an extension, saved generic references encounter the existing missing-source error. Rows are not deleted or migrated. Reinstalling the compatible extension and restarting Hikari makes those generic references resolvable again. No database schema change is required.

Library stores the top-level series snapshot. Remote reader progress remains keyed by the selected chapter `SourceMediaRef` and `PagePosition`. Reopening the same chapter resumes its page. Series-level “resume last chapter” remains deferred.

## Verification boundary

Automated tests cover zero-remote-source composition/UI, generic external-source workflows, extension adaptation, generic MangaDex identity, opaque persisted references across extension absence/reinstall, opaque continuation state and malformed references. They do not prove current live provider availability.

Physical Android verification should cover installed trusted extension discovery, search, series, chapters, pages, reader, progress/Library and restart/reinstall. See the device checklist in [EXTENSIONS](EXTENSIONS.md).

[ADR-008](../decisions/ADR-008-external-remote-provider-ownership.md) records why the direct provider was removed. Deferred work includes series-level last-chapter resume, canonical cross-source identity/dedup, downloads, account sync and extension repository/install/update UI.
