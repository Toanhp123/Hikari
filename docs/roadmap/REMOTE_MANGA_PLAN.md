# Remote Manga Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Deliver the approved experimental unauthenticated MangaDex search → series chapters → reader vertical, preserving local behavior.

**Architecture:** Add provider-neutral search and chapter capabilities beside existing media source contracts. App owns a source map and navigation; MangaDex infrastructure alone owns HTTP, UUID/page encoding and temporary at-home state. Library stores series; progress stores chapters using unchanged schema v1.

**Tech Stack:** Project-pinned FVM Flutter/Dart, package:http with injected Client, existing Drift repositories and Material widgets.

## Approved specification

User approved this design and end-to-end execution on feat/remote-manga-source. No commits, branch changes, schema migration, canonical identity, plugin framework, authentication, covers or series-resume cache. Preserve all local readers. Existing app orchestration stays unless concrete duplicate state rules justify extraction.

## Concrete acceptance details

- `Future<List<Media>> search(String query)`; `Future<List<MangaChapter>> chapters(SourceMediaRef manga)`; chapter has final title, source, optional scanlator. Series/chapter refs contain their respective UUID, never interchangeable.
- Page itemId is `<chapter UUID>/<zero-based index>`. `pages(chapter)` resolves/reuses session and returns those refs. `readPage(page)` reconstructs missing/stale session from chapter UUID. Keep at most one session, TTL 15 minutes, at most 10,000 pages. API bodies capped 8 MiB; images 32 MiB; whole request timeout 30 seconds, reporting 5 seconds. Non-2xx throws deterministic source error; 429 never retries automatically. API starts paced at least 250 ms apart; at-home starts at least 1500 ms apart.
- On image 403: report failed retrieval, invalidate session, resolve once, retry image once, report final outcome. Other image failures invalidate session for next user retry, no automatic loop. Report failure never replaces original download result.
- Feed uses limit=500 per request, offsets advance by returned data count until total reached; at most 20 requests and 10,000 raw chapters. Exceeding the response-size/total bound or non-progressing pagination throws FormatException; exhausting request budget throws StateError. Neither silently truncates. translatedLanguage[]=en; order[volume]=asc and order[chapter]=asc; equal volume/chapter preserve server order and distinct UUIDs. Transport HTTP errors use StateError with status (429 asks user to wait), malformed required JSON uses FormatException.
- Add visible Search MangaDex action to both supported and unsupported LocalMediaPage AppBars. It opens generic injected remote search screen.
- Library persists existing title/type/source snapshot with series UUID. Reopen resolves source map by mangadex then requests feed using saved UUID; no search required. Progress stores chapter UUID and existing PagePosition. No persisted URL/series-last-chapter mapping.

## Task 1 — Source contracts and MangaDex transport

Files: lib/domain/media/source.dart; new lib/infrastructure/mangadex/mangadex_source.dart; pubspec.yaml/pubspec.lock; new test/mangadex_source_test.dart.

- [x] Read retained official research and existing source models before coding. Record private concise endpoint/identity/header/rate/readability/reporting evidence outside repository.
- [x] Write canned HTTP tests for mapping/title fallback/empty query, feed pagination/attribution/distinct uploads and validation. Run `fvm flutter test test/mangadex_source_test.dart`, observe missing capability behavior before implementation.
- [x] Add MediaSearchSource.search, MangaChapter(title, source, scanlator), MangaChapterSource.chapters; clarify pages(readable) semantics. No provider constants in domain.
- [x] Add direct http dependency with FVM; implement conservative English title search (20; safe/suggestive), feed (500; ascending reading order; scanlation_group expansion; no includeEmptyPages), strict required-field validation, bounded HTTP failures and no automatic 429 retries.
- [ ] Full test-first ordering was not achieved for every at-home behavior. Coverage now includes: stable chapter/index refs, fresh-session reuse, expiration, one 403 refresh/retry, success/failure reporting, report failure isolation, User-Agent, malformed data.
- [x] Implement bounded ephemeral session state, original images, measured report bytes/duration/cache headers and uploads-host exemption. Never persist transport URLs or send auth headers. Official API /report limit is NOT network image reporting limit.
- [x] Run targeted tests and format changed Dart.

## Task 2 — Generic UI and app composition

Files: new lib/features/remote_manga/remote_manga_search_page.dart and manga_chapter_page.dart; lib/features/manga_reader/manga_reader_page.dart; lib/features/local_media/local_media_page.dart; lib/app/app.dart; new test/remote_manga_test.dart and relevant existing app/readers tests.

- [x] Inspect existing UI/test conventions. Cover explicit search loading/results/empty/error/library toggle and chapter loading/error/selection. Initial widget contracts and retry regression were verified red/green; additional coverage was added after implementation.
- [x] Implement callback-injected generic screens with Material controls, visible labels, bounded actions, mounted guards and source/scanlator credits. No infrastructure imports.
- [x] Add optional reader credit; preserve existing local presentation when absent.
- [x] Add app source map and injectable remote capability test seam; route manga by capability, never provider id. Remote opening does not require Android local support.
- [x] Separate series navigation from chapter reading; load/save progress using selected chapter ref. Prevent shared opening guard from blocking nested chapter selection.
- [x] Run targeted widget tests covering remote series/chapter routing and unchanged local direct reader.

## Task 3 — Persistence and regression proof

Files: test/user_state_repository_test.dart; test/app_smoke_test.dart or new remote integration test.

- [x] Test file database close/reopen with MangaDex series snapshot; resolve chapters without new search.
- [x] Test chapter progress resume and completion semantics through real repositories/reader seams.
- [x] Test removing series Library membership preserves chapter progress. No persistence production change expected.

## Task 4 — Documentation, review and verification

Files: docs/architecture/REMOTE_MANGA.md; docs/architecture/USER_STATE.md; docs/architecture/LOCAL_MEDIA.md; docs/PROJECT_OVERVIEW.md; docs/README.md.

- [x] Update only established current-state facts, source-scoped identity, ephemeral hosts, experimental unauthenticated restrictions and deferred series-last-chapter resume. Record app orchestration pressure without speculative layer.
- [x] Run `fvm flutter pub get`; `fvm dart format lib test`; `./tool/check.ps1` (locked dependencies, format, analyze, full tests/architecture guard, diff checks).
- [x] Review complete diff for provider leakage, identity safety, bounded resources/retries, library/progress independence and unnecessary abstractions. Obtain independent spec and correctness review; fix verified findings with regression tests.
- [x] Run Android build if environment permits; explicitly report unverified native platforms/guest access. Produce patch including added files outside repository; final `git diff --stat` and status, no staging or commits.
