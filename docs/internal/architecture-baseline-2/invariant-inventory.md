# Architecture Baseline 2 Invariant Inventory

Date: 2026-08-09
Status: Normative migration classification for R0-R4

Previous checkpoint acceptance is historical evidence. Baseline 2 retains, changes, or
deletes behavior only through the product and architecture rationale recorded here.

| Decision | Behavior / invariant | Baseline 2 rationale |
|---|---|---|
| KEEP | `app.openstory`, JDK 17, Android minSdk 26 / target 37 | platform/bootstrap contract still valid |
| KEEP | local-first cached catalog reads | user value and resilience |
| KEEP | plugin host controls network/files/platform access | trust boundary |
| KEEP | HTTPS host allowlist + redirect revalidation + bounded responses | security invariant |
| KEEP | package bytes verified before activation; failed install leaves prior state usable | atomic plugin lifecycle |
| KEEP | update capability expansion requires review; rollback restores prior immutable version | security/lifecycle invariant |
| KEEP | source-specific catalog metadata remains source-preserving | product requirement |
| KEEP | one catalog source failure does not erase another source or the previous complete snapshot | partial source failure isolation |
| KEEP | matching/ranking is deterministic and pure | reproducibility |
| KEEP | Home, Search, and canonical Story journeys | revalidated current product surface |
| KEEP | MyAnimeList remains the production reference catalog; Home uses MAL top-manga ranking, Search uses the manga search API, Details preserves MAL manga ID/source URL/score/author/cover/genre/popularity metadata | concrete reference-plugin behavior to port through the new protocol |
| KEEP | Room as the Android persistence adapter implementation | target ownership changes; Room no longer defines domain boundaries |
| KEEP | Hilt as minimal compile-time wiring and Navigation 3 as app navigation | remove manual graph/factories rather than replacing frameworks without a concrete problem |
| KEEP | AndroidX JavaScriptEngine, OkHttp, and Jsoup inside the plugin security subsystem | they fit the single JS runtime + bounded HTTP/HTML capability design |
| CHANGE | canonical story model shape | keep concept; rebuild ownership/model |
| CHANGE | catalog repository and refresh/search/details orchestration | move to `:catalog`; durable-state repository only |
| CHANGE | plugin API and `.osp` package contract | replace Kotlin host contracts with pure wire protocol |
| CHANGE | JavaScript bridge | replace with operation protocol + capability broker |
| CHANGE | Room schema | reset to new schema 1; no dev migration chain |
| CHANGE | Hilt/manual composition | remove service-locator graph; constructor injection |
| CHANGE | Navigation Story route | canonical route carries only `StoryId` |
| CHANGE | tests/fixtures | port by invariant, not file |
| DELETE | declarative Selector runtime/schema as production execution model | JS-only runtime |
| DELETE | generic `:core:network` | current source audit shows it serves plugin HTTP/session policy; network becomes a plugin capability |
| DELETE | roadmap-wide `:core:model` | capability-owned models |
| DELETE | speculative Library/chapter/release/progress persistence before owning capability starts | YAGNI |
| DELETE | `OpenStoryAppGraph` and custom ViewModel factories | Hilt lifecycle wiring |
| DELETE | production default/selector demonstration catalogs | MAL is the production reference plugin |
| DELETE | structural suppression used only to satisfy Detekt | anti-gaming rule |
