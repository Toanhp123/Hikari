# Requirement Coverage - Canonical Index

Date: 2026-08-24
Status: approved requirement mapping plus current execution annotations

This matrix answers where each MVP requirement belongs. Implementation status is owned by
`current-state.md`; later amendments may change mechanics without rewriting historical intent.

Current boundary: Waves 06-09, Architecture Baseline 2, the Design System Foundation, Product UI,
Discover semantic feeds, and CCE Phases 0-7 / Tasks 1-42 are accepted. CCE closes on Room schema 9.
The later canonical performance/durability implementation advances current source to schema 10
through `MIGRATION_9_10`; its entry verification is accepted. Wave 10 is ready to start.
Notification persistence is rebased to `10 -> 11`, and Wave 11 enters on schema 11 unless
another reviewed migration intervenes.

## Current amendments and implementation clarifications

| Area | Current authority | Current interpretation |
|---|---|---|
| Primary Discover composition | `../superpowers/specs/2026-08-19-discover-semantic-feed-redesign-design.md` | Semantic Popular -> media selector -> Latest Updates -> Top Rated; no default source selector in primary Discover |
| Discover feed identity | same | Explicit semantic feed kind and canonical `StoryId` dedupe before Compose |
| Discover persistence | same + Room schema 7 | Feed kind, publication status, and latest-update metadata persist in schema 7 |
| Canonical catalog identity/fusion | 2026-08-20 CCE design + plan | Phases 0-7 / Tasks 1-42 are verified/closed on schema 9 |
| Canonical durability | `../superpowers/specs/2026-08-24-canonical-engine-performance-and-durability-design.md` | Current source schema 10 adds leased work and a transactional catalog-change outbox; entry baseline accepted |
| Plugin execution mechanics | Architecture Baseline 2 + `../plugin-sdk/` | JavaScript-only protocol/runtime; selector/declarative-runtime rows are history |
| Wave 10 persistence | `../implementation/waves/wave-10-background-sync-auth-and-notifications.md` | Canonical foundation owns `8 -> 9`, canonical durability owns `9 -> 10`, and Wave 10 notifications own `10 -> 11` |
| Manga image Reader scope | `approved-product-design.md` + `current-state.md` | Bounded MangaDex image pages are implementation beyond the original text-only MVP baseline |

---

# Design-to-Implementation Coverage Matrix

| Approved design requirement | Primary plan | Supporting plan(s) | Exit evidence |
|---|---|---|---|
| Android-only Kotlin/Compose app | Wave 01 | Wave 11 | Launchable APK, CI, release APK |
| Local-first; no account/cloud | Wave 02 | Waves 09-10 | Room/DataStore/files only; local WorkManager |
| Combined and per-catalog Home | Wave 05 | Waves 03-04 | Bundled catalog, resilient multi-catalog UI |
| Catalog-specific scores/metadata retained | Waves 02, 05 | Wave 06 | Source scores remain inspectable |
| Catalog and content plugin separation | Wave 03 | Waves 04-07 | Separate contracts and host adapters |
| JavaScript plugin contract | Waves 03-04 | Wave 11 | Contract suite and sandbox denial tests |
| Install URL/file/repository | Waves 03, 11 | Wave 04 | Reviewable package lifecycle UI |
| Update modes and rollback | Wave 04 | Waves 10-11 | Capability diff, staged activation, rollback test |
| No native/unrestricted plugins | Wave 04 | Wave 11 | Capability sandbox and manifest audit |
| WebView source login | Wave 10 | Wave 11 | Declared-host capture, encrypted scoped cookies, usable login/logout |
| Metadata-only Library | Wave 06 | Wave 02 | Add completes without content/network |
| Story matching and manual URL mapping | Wave 06 | Wave 05 | Explainable candidates and user lock |
| Fast latest then full background sync | Wave 07 | Wave 10 | Recent/full worker chain |
| Incremental sync/fingerprint fallback | Wave 07 | Wave 10 | Cursor advances only after commit |
| Multi-source chapter aggregation | Wave 07 | Waves 02, 06 | One canonical chapter, multiple releases |
| Numbered/decimal/special chapters | Wave 07 | Wave 11 | Deterministic fixture suite |
| User merge/split corrections persist | Wave 07 | Wave 02 | Override-first rebuild test |
| Language/plugin/group filters | Wave 07 | Waves 08, 10 | Chapter filter and settings tests |
| Default release: language then continuity | Wave 08 | Wave 10 | Release-selection policy tests |
| Text reader and source switching | Wave 08 | Wave 09 | Reader UI and exact release state |
| Canonical progress plus exact position | Waves 02, 08 | Wave 09 | Process recreation and source-switch tests |
| Automatic cache | Wave 09 | Wave 08 | Quota eviction, pinned/current safeguards |
| Explicit offline downloads | Wave 09 | Wave 10 | Verified download namespace and workers |
| Cache cleanup never deletes downloads | Wave 09 | Wave 11 | Namespace and storage UI tests |
| Scheduled local chapter checks | Wave 10 | Wave 07 | Unique WorkManager schedule and batch isolation |
| Local new-chapter/preferred-language notifications | Wave 10 | Wave 07 | Deduplicated change-event classifier |
| One plugin failure does not break batches | Waves 04, 07, 10 | Wave 11 | Partial-success reports and acceptance resilience |
| Diagnostics without secret leakage | Wave 04 | Waves 10-11 | Redaction tests and security audit |
| Open source app/SDK/examples/docs | Wave 11 | Wave 03 | Contributor/plugin SDK docs and sample builds |
| APK-first distribution | Wave 11 | Wave 01 | Signed APK, checksum, SBOM, release notes |
| No paywall/DRM/CAPTCHA bypass | Waves 03-04 | Waves 10-11 | Capability docs, guarded login, safety docs |
| Manga/anime/TTS/AI/cloud excluded from MVP | Roadmap constraints | All waves | No task/module implements excluded scope |

## Self-Review Results

- Every included MVP item has a primary implementation wave.
- Excluded features do not appear as implementation tasks.
- Feature modules consume domain/repository interfaces; plugin APIs expose no Room/filesystem objects.
- High-risk boundaries receive explicit tests: package validation, network allowlists, JavaScript
  capabilities, Room migrations, chapter aggregation, progress, storage integrity, WebView sessions,
  WorkManager idempotency, notification dedupe, and notification deep links.
