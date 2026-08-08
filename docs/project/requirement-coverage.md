# Requirement Coverage — Canonical Index

Date: 2026-08-07  
Status: approved requirement mapping plus current execution annotation

The requirement-to-wave mapping below is the approved 2026-08-03 matrix and is
preserved rather than re-invented. Current implementation progress is tracked in
`current-state.md`; this matrix answers **where each MVP requirement belongs**, not
whether it is already complete.

Current boundary: Waves 01–04 have implementation present and the Wave 04 checkpoint is
accepted. Wave 05 Tasks 01–06 have implementation present; Tasks 02–05 verification is PASS,
and Task 06 verification is open. Waves 06–11 remain planned and Wave 06 remains blocked until
the Wave 05 checkpoint is accepted.

---

# Design-to-Implementation Coverage Matrix

This matrix is the self-review index for the approved design. Every MVP requirement is assigned to one or more executable waves.

| Approved design requirement | Primary plan | Supporting plan(s) | Exit evidence |
|---|---|---|---|
| Android-only Kotlin/Compose app | Wave 01 | Wave 11 | Launchable APK, CI, release APK |
| Local-first; no account/cloud | Wave 02 | Waves 09–10 | Room/DataStore/files only; local WorkManager |
| Combined and per-catalog Home | Wave 05 | Waves 03–04 | Bundled catalog, resilient multi-catalog UI |
| Catalog-specific scores/metadata retained | Waves 02, 05 | Wave 06 | Two catalog scores persist under one story |
| Catalog and content plugin separation | Wave 03 | Waves 04–07 | Separate contracts and host adapters |
| Selector and JavaScript plugin types | Waves 03–04 | Wave 11 | Contract suite and sandbox denial tests |
| Install URL/file/repository | Waves 03, 11 | Wave 04 | Reviewable package lifecycle UI |
| Update modes and rollback | Wave 04 | Waves 10–11 | Capability diff, staged activation, rollback test |
| No native/unrestricted plugins | Wave 04 | Wave 11 | Capability sandbox and manifest audit |
| WebView source login | Wave 10 | Wave 11 | Declared-host capture, encrypted scoped cookies |
| Metadata-only Library | Wave 06 | Wave 02 | Add completes without content/network |
| Story matching and manual URL mapping | Wave 06 | Wave 05 | Explainable candidates and user lock |
| Fast latest then full background sync | Wave 07 | Wave 10 | FAST_LATEST/FULL_INITIAL worker chain |
| Incremental sync/fingerprint fallback | Wave 07 | Wave 10 | Cursor advances only after commit |
| MangaDex-like chapter aggregation | Wave 07 | Waves 02, 06 | One canonical chapter, multiple releases |
| Numbered/decimal/special chapters | Wave 07 | Wave 11 | Deterministic fixture suite |
| User merge/split corrections persist | Wave 07 | Wave 02 | Override-first rebuild test |
| Language/plugin/group filters | Wave 07 | Waves 08, 10 | Chapter list filter and settings tests |
| Default release: language then continuity | Wave 08 | Wave 10 | Release selection policy tests |
| Text reader and source switching | Wave 08 | Wave 09 | Reader UI and exact release state |
| Canonical progress plus exact position | Waves 02, 08 | Wave 09 | Process recreation and source-switch tests |
| Automatic cache | Wave 09 | Wave 08 | Quota eviction, pinned/current safeguards |
| Explicit offline downloads | Wave 09 | Wave 10 | Verified download namespace and workers |
| Cache cleanup never deletes downloads | Wave 09 | Wave 11 | Namespace and storage UI tests |
| Scheduled local chapter checks | Wave 10 | Wave 07 | Unique WorkManager schedule and batch isolation |
| Local new-chapter/preferred-language notifications | Wave 10 | Wave 07 | Deduplicated change-event classifier |
| One plugin failure does not break batches | Waves 04, 07, 10 | Wave 11 | Partial-success reports and acceptance resilience |
| Diagnostics without secret leakage | Wave 04 | Waves 10–11 | Redaction marker tests/security audit |
| Open source app/SDK/examples/docs | Wave 11 | Wave 03 | Contributor/plugin SDK docs and sample builds |
| APK-first distribution | Wave 11 | Wave 01 | Signed APK, checksum, SBOM, release notes |
| No paywall/DRM/CAPTCHA bypass | Waves 03–04 | Waves 10–11 | Capability docs, guarded login, safety docs |
| Manga/anime/TTS/AI/cloud excluded from MVP | Roadmap/global constraints | All waves | No task/module implements excluded scope |

## Self-Review Results

- Spec coverage: every included MVP item has a primary implementation wave.
- Scope control: excluded features do not appear as implementation tasks.
- Placeholder scan: no unresolved markers or generic error-handling instructions remain.
- Type direction: feature modules consume domain/repository interfaces; plugin APIs expose no Android/Room/filesystem objects.
- High-risk boundaries receive explicit tests before feature adoption: package validation, network allowlist, JavaScript capability bridge, Room transactions, chapter aggregation, progress, storage integrity, WebView sessions, and notification dedupe.
