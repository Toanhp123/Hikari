# MangaDex Content Plugin

Official bundled content plugin for MangaDex. It exercises the public `CONTENT` service through
the same package, installation, and runtime boundaries used by other bundled and third-party plugins.

Implemented operations:

- `content.search`
- `content.resolveUrl`

The package performs anonymous, read-only requests to `api.mangadex.org` and returns canonical
MangaDex title URLs on `mangadex.org`. Chapter and reader operations remain intentionally absent
until their owning waves.

Build a standalone test package with:

```text
./gradlew :app:packageMangaDexPlugin
```

The resulting package is written to `app/src/main/assets/plugins/mangadex-content.osp` and is
provisioned as part of the production bundled plugin set.
