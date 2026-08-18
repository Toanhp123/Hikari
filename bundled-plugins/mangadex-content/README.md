# MangaDex Content Plugin

Official bundled content plugin for MangaDex. It exercises the public `CONTENT` service through
the same package, installation, and runtime boundaries used by other bundled and third-party plugins.

Implemented operations:

- `content.search`
- `content.resolveUrl`
- `content.chapters`

The package performs anonymous, read-only requests to `api.mangadex.org` and returns canonical
MangaDex title URLs on `mangadex.org`. It can list chapter releases but intentionally does not
implement `content.chapter`, because the current Reader accepts structured text documents rather
than MangaDex image pages.

Build a standalone test package with:

```text
./gradlew :app:packageMangaDexPlugin
```

The resulting package is written to `app/src/main/assets/plugins/mangadex-content.osp` and is
provisioned as part of the production bundled plugin set.
