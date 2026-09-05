# MangaDex Content Plugin

Official bundled content plugin for MangaDex. It exercises the public `CONTENT` service through
the same package, installation, and runtime boundaries used by other bundled and third-party plugins.

Implemented operations:

- `content.search`
- `content.resolveUrl`
- `content.chapters`
- `content.chapter`

The package performs anonymous, read-only requests to `api.mangadex.org` and returns canonical
MangaDex title URLs on `mangadex.org`. Chapter bodies use MangaDex@Home delivery metadata from
`/at-home/server/:chapterId` and return ordered HTTPS image-page descriptors for the host Reader.
The delivery base URL is treated as ephemeral and is never hardcoded. The package explicitly opts into
host-rendered remote images with `capabilities.reader.remoteImages: true`. Image chapters are online-only,
so `capabilities.reader.offlineDownload` remains `false`. Hikari also maintains an explicit Reader image
identity contract for this adapter: `stableId` is exactly `chapter.hash + "/" + filename`, the locator
base URL is not part of that identity, and public persistence is allowed. This contract is encoded as
`imageIdentity: STABLE_ID_CHANGES_WITH_CONTENT`, `imageLocator: MUTABLE_OR_UNKNOWN`, and
`imagePersistence: PUBLIC`; it must be downgraded to fail-closed non-persistent behavior if the reviewed
MangaDex semantics stop supporting the assumption.

Evidence reviewed 2026-09-01:

- MangaDex API docs revision `60a3bb3c9f25675435a479c925d857015201739e` documents that
  `/at-home/server/:chapterId` returns a time-limited `baseUrl`, `chapter.hash`, and ordered filenames,
  and defines delivery paths as `baseUrl / quality / chapterHash / filename`.
- MangaDex@Cloud revision `1064b874fd3b0eeeea423d4a283c261a64688a11` preserves the `/data/...`
  pathname when selecting upstream images and uses that path for cache delivery, matching the
  chapter-hash/filename identity used by this adapter.

MangaDex@Home load reporting to `api.mangadex.network/report` is not implemented yet; Retry refreshes
delivery metadata, while provider reporting is tracked as release hardening rather than being hidden
behind the reader capability.

Build a standalone test package with:

```text
./gradlew :app:packageMangaDexPlugin
```

The resulting package is written to `app/src/main/assets/plugins/mangadex-content.osp` and is
provisioned as part of the production bundled plugin set.
