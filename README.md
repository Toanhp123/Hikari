# Hikari

Hikari is a cross-platform Flutter media hub for movies, series, anime, manga/comics/webtoon, and novels.

Primary targets are Android, Windows, and iOS, with Android as the first-priority platform. The project is currently in its foundation/bootstrap stage; product scope and architecture choices are documented before implementation is expanded.

## Project documentation

Start with the smallest document relevant to the task:

- [`CLAUDE.md`](CLAUDE.md) — agent routing, skill usage, implementation rules, and Definition of Done.
- [`docs/README.md`](docs/README.md) — documentation map and reading strategy.
- [`docs/PROJECT_OVERVIEW.md`](docs/PROJECT_OVERVIEW.md) — product identity, scope, architecture principles, roadmap direction, and open decisions.
- [`docs/GIT_WORKFLOW.md`](docs/GIT_WORKFLOW.md) — branch, commit, verification, and patch/diff workflow.

Do not treat this README as the project specification; canonical project knowledge lives in `docs/`.

## Current bootstrap

Foundation v1 pins Flutter with FVM and enforces format, analysis, tests, and an Android debug build in CI. The runtime app intentionally remains minimal while Domain Core is designed.

FVM must be installed and available on `PATH`. On Windows, enable Developer Mode if `fvm install` reports symbolic-link error 1314.

```powershell
fvm install
.\tool\check.ps1
fvm flutter run
```

The canonical architecture and toolchain decisions are recorded under `docs/decisions/`.

## Working on Hikari

Normal feature work is done on a dedicated branch from `dev` and must remain reviewable as a diff/patch. See [`docs/GIT_WORKFLOW.md`](docs/GIT_WORKFLOW.md) for the canonical process.
