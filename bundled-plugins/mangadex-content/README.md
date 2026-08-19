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
so `capabilities.reader.offlineDownload` remains `false` until a page-asset store exists.
MangaDex@Home load reporting to `api.mangadex.network/report` is not implemented yet; Retry refreshes
delivery metadata, while provider reporting is tracked as release hardening rather than being hidden
behind the reader capability.

Build a standalone test package with:

```text
./gradlew :app:packageMangaDexPlugin
```

The resulting package is written to `app/src/main/assets/plugins/mangadex-content.osp` and is
provisioned as part of the production bundled plugin set.
