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

The application is intentionally minimal while the domain and subsystem boundaries are being validated.

```bash
flutter pub get
flutter analyze
flutter run
```

Run `flutter test` whenever a Flutter/Dart test suite exists. Once implementation work begins in the domain/application layers, relevant automated tests are expected as part of the same change.

## Working on Hikari

Normal feature work is done on a dedicated branch from `dev` and must remain reviewable as a diff/patch. See [`docs/GIT_WORKFLOW.md`](docs/GIT_WORKFLOW.md) for the canonical process.
