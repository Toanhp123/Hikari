# Hikari

Hikari is a cross-platform Flutter media hub for movies, series, anime, manga/comics/webtoon, and novels.

Primary targets are Android, Windows, and iOS, with Android as the first-priority platform. The current local-media vertical scans Android folders, opens video/image/text content, and adds source-keyed progress, Library snapshots and SQLite persistence.

## Project documentation

Start with the smallest document relevant to the task:

- [`CLAUDE.md`](CLAUDE.md) — agent routing, skill usage, implementation rules, and Definition of Done.
- [`docs/README.md`](docs/README.md) — documentation map and reading strategy.
- [`docs/PROJECT_OVERVIEW.md`](docs/PROJECT_OVERVIEW.md) — product identity, scope, architecture principles, roadmap direction, and open decisions.
- [`docs/GIT_WORKFLOW.md`](docs/GIT_WORKFLOW.md) — branch, commit, verification, and patch/diff workflow.

Do not treat this README as the project specification; canonical project knowledge lives in `docs/`.

## Current development baseline

Foundation v1.1 pins Flutter with FVM and enforces the dependency lock, format, static type/lint analysis, tests with an architecture guard, and an Android debug build in CI. See [local media](docs/architecture/LOCAL_MEDIA.md) and [user state](docs/architecture/USER_STATE.md) for current behavior and verification limits. Regenerate Drift records after schema changes with `fvm dart run build_runner build --delete-conflicting-outputs`.

FVM must be installed and available on `PATH`. On Windows, enable Developer Mode if `fvm install` reports symbolic-link error 1314.

```powershell
fvm install
.\tool\check.ps1
fvm flutter run
```

The canonical architecture and toolchain decisions are recorded under `docs/decisions/`.

## Working on Hikari

Normal feature work is done on a dedicated branch from `dev` and must remain reviewable as a diff/patch. See [`docs/GIT_WORKFLOW.md`](docs/GIT_WORKFLOW.md) for the canonical process.
