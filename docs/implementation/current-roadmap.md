# Current Implementation Roadmap

Date: 2026-09-13
Status: **CANONICAL repository execution roadmap**

This roadmap preserves the approved product sequence after Architecture Baseline 2 reset
the pre-Wave-06 implementation architecture. Implementation presence and checkpoint
acceptance remain separate states.

## Status vocabulary

- **Implementation present**: production code and tests exist for the boundary.
- **Patched / verification open**: source/test changes exist, but the required repository gate has not yet been reviewed.
- **Verification open**: required checkpoint evidence is missing or still `NOT RUN`.
- **In progress**: some deliverables exist and some remain.
- **Completed/accepted**: implementation and required checkpoint evidence for that boundary are closed.
- **Ready to start**: entry checkpoint is accepted, but the next wave has no
  implementation yet.
- **Planned**: approved work has not started in this repository.

## Current position

**Hikari V2 Step 2 - Discover + Story Detail Foundation completed/accepted Tasks 0-16; Task 17 is
READY TO START.** Task 16 closes with explicit user-accepted performance debt, never relabeled as
PASS or hidden by relaxed thresholds. Story Detail CPU/overrun P95 debt is memory-hit
`47.175 / 40.432 ms`, disk-hit `41.906 / 41.678 ms`, and Story-back `66.212 / 59.902 ms`; startup
TTID debt is fresh `507.138 ms` and returning `467.346 ms`. All hard correctness, query/work,
transport/decode, cache/resource, terminal ownership, navigation, profile-generation, and focused
compile/test gates are accepted. The fixture-fixed Baseline/Startup Profile contains `20,874`
byte-identical rules per file (SHA-256
`797b58730c732777698f3aba24dc6ea11302cd8bfa4b17e75c351568f4ac54eb`). The final startup runs used
the immediately preceding `99.96%`-equivalent profile and were not repeated after fixture repair by
explicit acceptance. Start only Task 17 in the next turn; Task 18 remains not run.
The owning plan is
`../superpowers/plans/2026-09-08-hikari-v2-step-2-discover-story-foundation-implementation-plan.md`;
the active checkpoint is `../internal/checkpoints/hikari-v2-step-2-discover-story-foundation.md`.
Task 0 admits the exact four-module Catalog foundation, reviewed build surface, exact graph,
package-SCC enforcement, and product authority. Earlier verification attempts exposed a
configuration-cache-unsafe `Task.project` access, a missing Step 2 shell-gate path, and Room
2.8.4's unused multi-process invalidation service in every app merged manifest; all three are
fixed. The focused suite passes 60 build-logic tests and all four app merged-manifest startup
checks; the user-owned architecture/foundation and Step 2 shell gates are accepted as PASS.
Task 1 now freezes the pure-domain identity/provenance/bounds/semantic-section/port contract and
passes 41 focused domain tests plus the direct storage/runtime/feature consumer compile checks.
The first user-owned broad run exposed three forbidden `java.net` imports and 14 Task 1 Detekt
errors. The second run passed every architecture check and exposed one remaining `ReturnCount`
error in the Task 1 Punycode encoder; that source-level finding is remediated without changing
behavior, and the focused domain suite remains green. The final user-owned
`./gradlew verifyArchitecture detekt --no-daemon` rerun is accepted as `BUILD SUCCESSFUL`.
Task 2 now has the new four-table Room v1 schema, atomic bounded Discover publication, durable
`Absent | Published(empty/content)` state, one coherent state-left-join-card observation, exact
identity/Discover uniqueness, lazy storage ownership, and compiled connected Room contracts.
The focused `assembleDebug` plus `compileDebugAndroidTestKotlin` gate passes, and the user-run
connected Room suite is accepted as PASS. The broad command passed every architecture check but
Detekt exposed four Task 2 storage findings; those findings are remediated and the focused compile
gate remains green. The user rerun of `verifyArchitecture detekt` is accepted as
`BUILD SUCCESSFUL`, so Task 2 is completed/accepted. Task 3 now adds atomic keyed Story Detail
persistence, a fixed four-query coherent observer, deterministic bounded children, explicit
non-reactive access touch, and a <=64 indexed orphan-retention/release path. The final agent-owned
assemble plus instrumentation compile gate passes. The first user-run connected gate exposed that
Room 2.8.4's generated `@Relation` adapter executes the parent SELECT twice, producing five rather
than four observed Story queries; the broad gate separately exposed one `ReturnCount` finding in
the Task 3 overflow eviction helper. Both source defects are remediated, the focused assemble plus
instrumentation compile gate remains green, and generated DAO review now shows one parent plus
three ordered child queries. The user rerun reports `BUILD SUCCESSFUL` for the required connected
Story Detail/retention classes and `verifyArchitecture detekt`, so Task 3 is completed/accepted.
Task 4 implementation now adds host-authoritative import/provenance, CPU-owned validation and
projection, one shared pin/publication mutation gate, a two-Story active pin cap, bounded bulk Room
identity publication, delta-driven cross-media retention, durable pinned orphan candidates, and
post-release cleanup. Focused runtime tests and storage assemble/instrumentation compile pass. The
first user-owned connected run passed 10/12 tests but exposed two invalid setup cards whose
`sourceVersion` did not match their publication provenance; the first broad run passed the
architecture checks but Detekt exposed one blocking `MaxLineLength` finding. Both findings are
remediated and the focused storage assemble/instrumentation compile gate is green. The user reports
`BUILD SUCCESSFUL` for both required connected and `verifyArchitecture detekt` reruns, so Task 4 is
completed/accepted. Task 5 now adds lazy demand activation, explicit absent-binding/source-
unavailable semantics, immutable host source/asset-policy authority, keyed Discover and Story
Detail observers, active-only acquisition single-flight, host-clock provenance, typed failure and
cancellation handling, one-per-demand Story access touch, pin rollback on failed activation, and
keyed session removal after release. The exact Task 5 focused gate passes 24 tests; a widened
runtime dependency-cone run passes 36 tests. The user reports `BUILD SUCCESSFUL` for the required
unfiltered runtime plus `verifyArchitecture detekt` gate, so Task 5 is completed/accepted. Task 6
now adds deterministic typed debug/benchmark sources for both media, exact 5/9/5 fixture
memberships, real compressed local covers with logical persisted identity, release-null wiring,
explicit benchmark/profile source-set reuse, real importer/Room benchmark preparation, and AAR
content checks. The fresh focused cone passes 67 build-logic tests, 37 runtime tests, one fixture
test in each non-release variant, all four feature variant compiles, and both app benchmark/profile
compiles. The user reports `BUILD SUCCESSFUL` for the required four-AAR assemble plus
`verifyArchitecture` command. Direct AAR review confirms byte-identical benchmark/non-minified
artifacts, required non-release fixture classes/assets, and release cleanliness. That review exposed
and fixed a shell matcher that did not account for AGP's packaged `drawable-nodpi-v4` directory. The
user reports `BUILD SUCCESSFUL` for the corrected shell-gate rerun, so Task 6 is completed/accepted.
Task 7 replaces the static returning Home with the narrow feature-owned `CatalogEntryPoint`, gates
activation behind Ready plus the first application-owned frame, adds the Android-free seven-label
Catalog trace authority and feature Android adapter, migrates startup/benchmark destination callers,
and adds debug-only pre-demand diagnostics plus the connected handoff test. The canonical focused
host/compile gate and widened variant/benchmark compile cone pass. The user-run connected
`CatalogLaunchHandoffTest` passes 3/3 on Redmi Note 9S/API 35. The first
`:app:verifyFoundation verifyArchitecture` run exposed a false package cycle because the app
structural verifier resolved the external `CatalogEntryPoint` import to the local
`app.openstory` ancestor package. A TDD regression and ownership-correct verifier remediation are
present; the focused verifier suite and `:app:verifyAppStructure` pass. The user reports
`BUILD SUCCESSFUL` for the remediated `:app:verifyFoundation verifyArchitecture` rerun, so Task 7
is completed/accepted. Task 8 now replaces the static activation status with a ViewModel-owned,
bounded Discover surface: Manga and Light Novel are enabled; media selection replaces exactly one
observer; new ViewModels default to Manga without durable selection state; `Absent`, durable
`Published(empty)`, retained content/refresh, and safe typed failure states map explicitly;
Popular/Latest/Top Rated render under one vertical `LazyColumn` with stable keys/tags, accessible
semantics, geometry-shaped skeletons, partial-section omission, and defensive 5/9/5 caps. The fresh
focused ViewModel suite passes 9 tests, the instrumentation source compiles, and the direct app
caller compiles. The user-run connected `DiscoverScreenInstrumentedTest` passes 4/4 on Redmi Note
9S/API 35. The first `verifyArchitecture detekt` run passes the architecture verifier but Detekt
exposes 12 blocking findings across Task 6-8 feature source sets. Those findings are remediated by
reusing domain-owned section caps, naming fixture/UI constants, preserving the intentional Task 7
diagnostic method and Task 8 unexpected-failure boundary with local suppressions, and moving
`DiscoverTestTags` to its matching file. Fresh focused debug/benchmark compiles, the 9-test
ViewModel suite, and Detekt over the changed source cone pass. The user reports `BUILD SUCCESSFUL`
for the post-remediation `verifyArchitecture detekt` rerun, so Task 8 is completed/accepted. Task 9
now adds the validated primitive saved Story route, pin-first restored/card navigation, one shared
feature runtime owner, keyed Story Detail ViewModel/UI state, inline safe retry/failure retention,
explicit Back release, and exact in-memory Discover scroll continuity. The fresh focused route/Story
suite passes 11 tests, the Story instrumentation source and direct app caller compile, and the
widened 9-test Discover reducer suite remains green. The required `verifyArchitecture detekt` gate
is accepted as `BUILD SUCCESSFUL`; its Detekt output contains warnings only. The first direct
PowerShell invocation of the connected `StoryRouteRestorationInstrumentedTest` gate did not execute
tests because PowerShell stripped the `-Pandroid` prefix and Gradle treated
`.testInstrumentationRunnerArguments...` as a task. The corrected connected run then executed 4
tests and exposed one invalid scroll-continuity fixture: it first rendered Story with a Discover
list index of 6 even though the empty Discover surface had only three top-level items, so Compose
correctly clamped the newly measured list to index 0. The instrumentation test now renders a real
three-section Discover surface at valid index 2 before Story -> Back and asserts the exact state
instance/offset; its source compiles and the user rerun reports `BUILD SUCCESSFUL`. Final self-review
then exposed an immediate Back -> reopen race where the old demand remained feature-active until
its asynchronous release coroutine ran, allowing the same Story to reuse a demand being released.
A focused RED regression now blocks release and proves reopen waits; the ViewModel detaches the old
demand synchronously and serializes reactivation after its release. Because production code changed
after the earlier device/broad evidence, both required gates were rerun; the user reports
`BUILD SUCCESSFUL` for the filtered connected class and `verifyArchitecture detekt`. Task 9 is
completed/accepted. Task 10 now adds the lazy capability-private image session, exact 32 MiB
decoded/128 MiB encoded cache ceilings, an eight-job foreground cap, zero manual prefetch, stable
`CoverAssetKey` request identity, build-type logical local-asset resolution, Android memory-pressure
handling, typed artwork-only failure state, Story route cover continuity before metadata, and
viewport-owned vertical cover demand. The final focused cone passes 22 host tests, debug and
instrumentation-source compiles, the app shell contract, all release/profile variant compiles, and
the benchmark resolver fixture with no warnings. The first user-owned connected run passed 8/9
tests and exposed an invalid metadata-visibility fixture that asserted the full cover title even
though `CoverArtwork` intentionally renders only its first placeholder character; the fixture now
renders metadata independently beside the failed cover. The first broad run passed all architecture
checks and exposed five blocking Detekt findings in the Task 10 feature cone; source-only
remediations are present, and the focused cache/failure/layout plus production/instrumentation
compile gate passes. The user reports `BUILD SUCCESSFUL` for both required reruns: the filtered
connected `LocalCoverContinuityInstrumentedTest` class and `verifyArchitecture detekt`. The fresh
closure cone passes all 22 focused host tests plus debug production and instrumentation-source
compilation. Task 10 is completed/accepted. Task 11 now adds the feature-private injected transport
seam, source-scoped initial/redirect host policy, exact 5-hop and 10s/20s timeout contract, bounded
 8 MiB streaming spool, JPEG/PNG/WebP media admission, Android bounds/animation/target-size preflight,
 policy-before-disk-hit recovery, and a main/release concrete-transport ratchet. The fresh focused cone
 passes 11 remote-policy tests, 12 domain cover/URI tests, 16 build-surface verifier tests, debug and
 instrumentation-source compilation; release dependency output contains no OkHttp or Coil network
 artifact, and manifests retain no `INTERNET`. A returned connected failure exposed extended-WebP
 `VP8X` canvas bounds being accepted before later `VP8`/`VP8L` bitstream bounds; the parser now checks
 both layers and requires consistent static-image dimensions. The user reports `BUILD SUCCESSFUL` for
 the final filtered connected class and `:app:verifyFoundation verifyArchitecture detekt`; the fresh
 focused closure also passes. Task 11 is completed/accepted. Task 12 now adds single-owner manual
 refresh with durable failure retention, synchronous terminal work cleanup, runtime/feature
 quiescence, Story pin release/reacquisition, lifecycle-aware collection and cover disposal, and
 idempotent image-before-runtime terminal teardown. Fresh runtime/feature host suites pass 87 tests
 with zero failures/errors, and the lifecycle instrumentation source compiles. The filtered
 connected lifecycle class plus `:app:verifyFoundation verifyArchitecture detekt` are accepted from
 the user's `BUILD SUCCESSFUL` results on 2026-09-10. Task 12 is completed/accepted. Task 13 adds the
 root Design System, shared stateless primitives, Discover pull-refresh wiring, and structural debt
 gates. Initial user verification exposed invalid Compose test assumptions plus two blocking Detekt
 findings; the repaired connected classes, broad release/architecture/Detekt command, build-surface
 script, and Design System slice script are all accepted from the user's final PASS confirmation on
 2026-09-10. Task 13 is completed/accepted. Task 14 now contains the exact R2.8 palette/typography
 migration, shared-state visual treatment, feature-local floating Manga/Light Novel navigation,
 artwork-first Discover composition, portrait Story Detail composition, existing content-type/update
 projection mapping, and shrink-only segmented API retirement. The final focused compile/unit cone,
 Design System slice script, and diff check pass. The user-returned Design System connected command
 passes 7 tests on Redmi Note 9S / API 35. Returned Catalog connected and broad architecture/Detekt
 failures were repaired; focused feature compile, route regression, and package-structure verification
 pass. The user reported `BUILD SUCCESSFUL` for both repaired correctness reruns on 2026-09-11.
 A follow-up Task 14 visual-rejection refactor now preserves the selected Direction 3 composition
 while removing duplicate UI state, dead compatibility metrics, repeated section caps, Canvas-based
 icon drawing, and mixed real/future-only Story composition. The fresh focused compile/unit/package
 cone and Design System slice pass. Because Catalog production UI changed after the earlier device
 and broad evidence, Task 14 is again `READY FOR USER VERIFICATION`; the affected connected Catalog
 and broad architecture/Detekt commands must pass before returning to human visual acceptance.
 The first returned rerun passed the broad command but connected Catalog executed 23 tests with
 three Story failures caused by stale visibility/text selectors after the approved LazyColumn/icon
 UI refactor. The tests now use a real detail fixture, scroll to lazy content, and target an
 accessible Back icon; the three changed-cone Detekt `LongMethod` warnings are also addressed by
 structural extraction. Fresh focused compile, unit, and package checks pass. The user reports PASS
 for both final affected reruns after the repair: connected Catalog and broad architecture/Detekt.
 The user explicitly accepted every Task 14 visual-checklist item on 2026-09-11. Task 14 is
 completed/accepted. Task 15 adds the deterministic 15-PNG compact/wide Catalog screenshot evidence
 harness and evolves both repository verification entrypoints to cover the admitted Step 2 modules.
 Focused host tests and all three instrumentation-source compiles pass. The user confirms PASS for
 the complete API 26/API 37 connected matrix and all required architecture/build-surface/full-
 repository commands. The reviewed artifact inventory contains complete non-empty compact and
 >=600dp wide 15-PNG matrices. Task 15 is completed/accepted. Task 16 is completed/accepted with
 explicit Story Detail frame and startup TTID performance debt recorded in its checkpoint and
 performance evidence owner. Task 17 is ready to start; Task 18 remains not run.

Hikari V2 Step 1 - Foundation + Clean Boot remains completed and accepted on branch
`v2/foundation-clean-boot`. Its accepted checkpoint is
`../internal/checkpoints/hikari-v2-step-1-foundation-clean-boot.md`; the immutable comparison point is
`../internal/v2/startup-baseline-2026-09-07.md`.

Final Step 1 evidence on 2026-09-08:

- final runtime/source SHA: `eb4d3bfd869a5b9df78a5802de31510b3984c3c3`;
- exactly six production modules plus the `:benchmark` Android test/performance module are active;
- fast/full verification, eight startup instrumentation tests, Baseline Profile generation,
  architecture/foundation/build-logic gates, retained/quarantine module tests, and the final fast
  gate pass;
- Redmi Note 9S / API 35 cold fresh-install median: `394.210469 ms`;
- Redmi Note 9S / API 35 cold returning-launch median: `412.547813 ms`;
- one fresh and one returning Perfetto trace each contain all six required `HikariV2:*` milestones;
- generated `baseline-prof.txt` and `startup-prof.txt` both have SHA-256
  `0c414b23dc0f409cb1f082ce63f7dd9935845b016539e441507846500e696ad0`.

The reviewed Step 2 design explicitly admits the new Catalog foundation while `:catalog:model`
and `:catalog:engine` remain quarantine/reference and outside the Step 2 production graph.

## Historical execution context (non-current)

The material below is retained for architecture/provenance. Any older wording such as `current`,
`open`, `next`, or `unblocked` describes its historical boundary and must not select present work; the
`Current position` section above is the sole next-work authority in this file.

Architecture Baseline 2 is accepted. Waves 06-09 are verified and complete. The between-wave
Design System Foundation and the full Product UI checkpoint are accepted; they preserved the historical
14-module production graph at that boundary. Wave 10 expanded the graph to 16 production modules, and
HES-v1 M0 now adds `:reader:engine` as the seventeenth production module; `:benchmark` remains the
separate Android test/performance module. The 2026-08-19 Discover semantic-feed redesign is also implemented and verified;
it advanced Room from schema 6 to **schema 7** without changing module ownership. The 2026-08-20
catalog metadata-lifecycle unification subsequently advanced Room to **schema 8**, centralized
Summary/Full freshness and single-flight in `:catalog`, and preserved the same module graph.

The **Canonical Catalog Reconciliation & Fusion Engine rollout is verified and closed on its Room schema-9 boundary**.
Phases 0-7 / Tasks 1-42 are accepted. The schema-10 durability entry gate is also accepted.
At the earlier Wave 10 remediation checkpoint, production implementation and Phase 7 coverage were
present on Room schema 11 while final host/device verification was still open. The associated
design/plan/readiness records were:

- `../superpowers/specs/2026-08-24-canonical-engine-performance-and-durability-design.md`
- `../superpowers/specs/2026-08-24-wave-10-clean-background-auth-notifications-design.md`
- `waves/wave-10-background-sync-auth-and-notifications.md`
- `../internal/checkpoints/wave-10-entry-readiness-2026-08-24.md`
- `../internal/checkpoints/wave-10-production-remediation.md`

The later Canonical Engine Performance and Durability implementation advances current source to
schema 10. It owns `MIGRATION_9_10` for canonical-work leases and the transactional catalog-change
outbox. Its policy, full host, and API 26/API 37 entry verification is accepted.

Wave 10 now has production implementation for consumer-owned Reader/cache policy ports,
request-target credential scope, bounded background candidate selection, transactional
chapter-change evidence, durable notification delivery, and Settings status ports. Remediation
Phase 7 adds the planned auth, scheduling, process-death recovery, permission-denial, and ID-collision
integration contracts. The required API 26/API 37 connected matrix is now developer-confirmed GREEN. R0 for Adaptive Reader/HES
selected the explicit acceptance-rebase path after the supplied sandbox could not bootstrap Gradle.
Developer-host verification on the final HES tree has since replaced that environment-only blocker.
The triggering canonical combined host gate failed at Detekt with 74 issues, and HES M7.1 repaired that
debt without suppressions, config weakening, or baseline growth. Standalone Detekt, the original unchanged
combined host gate, and the package/current-architecture contracts are now GREEN.
**Wave 10 is accepted/closed and Wave 11 is unblocked.**

Adaptive Reader Continuity / HES-v1 **M0–M7.5 VERIFIED/CLOSED; HES-v1 FINAL RE-FROZEN / REFERENCE-GRADE**. The graph remains 17 production modules plus `:benchmark`; `:reader:engine` remains JVM-only behind `:reader`; Room remains schema 11. M7.4 retired `AccessReason`; M7.5 closed the final completion-publication/ownership race, final session result/payload-coherence gap, and minimal test/implementation-only API debt. The fresh 2026-08-27 final-tree host matrix is GREEN. Routing formulas, trace data fields, versions, module graph, and schema remain unchanged. Wave 10 remains accepted/closed. Canonical M7.5 sources are `../superpowers/specs/2026-08-27-adaptive-reader-continuity-hes-v1-m7-5-final-freeze-hardening.md` and `../superpowers/plans/2026-08-27-adaptive-reader-continuity-hes-v1-m7-5-final-freeze-hardening.md`.

Reader integration architecture cleanup **R1–R5 IMPLEMENTED / verification pending**. This does not reopen HES-v1: `:reader:engine` stays FINAL RE-FROZEN. The effect layer now preserves LOCAL access when remote availability/registry observation fails, resolves remote sources lazily per execution, treats executable route facts—not `ReaderDecisionTrace`—as runtime control input, leaves previous/next navigation projection to Feature Reader, carries only `languageOrder` through routing state, replaces the raw JVM ownership monitor, and prunes retired legacy Reader descriptors from the checked-in baseline profile. Canonical cleanup sources are `../superpowers/specs/2026-08-27-reader-integration-architecture-cleanup-design.md` and `../superpowers/plans/2026-08-27-reader-integration-architecture-cleanup.md`.
Canonical foundation owns `MIGRATION_8_9`; canonical durability owns `MIGRATION_9_10`; Wave 10
notification persistence is rebased to `MIGRATION_10_11`; Wave 11 enters on schema 11 unless another
separately reviewed migration intervenes.

The completed Product UI and Discover implementation plans are execution records, not active next-work
instructions. Wave 01-05 checkpoints remain historical delivery evidence and do not require compatibility
with superseded development architecture.

Current Canonical Engine Phase-7 acceptance is recorded in
`../internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-7.md`; Task-39/40/41 sub-checkpoints
remain under `../internal/checkpoints/`. Phase-6 acceptance remains in
`../internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-6.md`. Phase-4 implementation/final-enable evidence is recorded in
`../internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-4.md`; Phase-3 acceptance remains in
`../internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-3.md`; Phase-2 acceptance remains in
`../internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-2.md`; accepted Phase-1 evidence remains in
`../internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-1.md`; Phase-0 acceptance remains in
`../internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-0.md`. Current Discover acceptance
evidence remains in `../internal/checkpoints/discover-semantic-feed-redesign.md`; keep the accepted Discover semantic-feed checkpoint as part of the Wave 10 entry baseline
for its completed feed/UI behavior. The schema-10 durability gate was the accepted pre-Wave-10 engineering boundary; at that historical
point, Wave 10 final host acceptance on schema 11 remained open after its required API 26/API 37 matrix passed. The accepted broader Product UI evidence remains in
`../internal/checkpoints/product-ui-redesign.md`. Wave-06 task evidence is recorded in:

- `../internal/checkpoints/wave-06-task-01-metadata-only-library.md`
- `../internal/checkpoints/wave-06-task-02-library-presentation.md`
- `../internal/checkpoints/wave-06-task-03-content-story-matching.md`
- `../internal/checkpoints/wave-06-task-04-content-source-search.md`
- `../internal/checkpoints/wave-06-task-05-protected-content-mappings.md`
- `../internal/checkpoints/wave-06-task-06-mapping-review-url-import.md`

The retained implementation uses the Baseline 2 JavaScript protocol/runtime, bounded host
capabilities, transactional package lifecycle, catalog-owned services, Room-owned
persistence, and feature-owned presentation. MyAnimeList and MangaDex are the current
production-bundled packages, while the runtime and app-owned bundled descriptor registry support
multiple catalog/content packages. The accepted Baseline 2 checkpoint freezes its historical
entry evidence, not the number of plugins allowed after that boundary.

## Current module graph

```text
:app
:core:common
:core:designsystem
:catalog
:library
:chapters
:reader
:reader:engine
:downloads
:settings
:feature:catalog
:feature:reader
:feature:settings
:storage:room
:storage:files
:plugins:api
:plugins:runtime

:benchmark  # android-test/performance; not a production module
```

Direct dependencies are governed by
`../../config/architecture/module-boundaries.json`. The accepted Baseline 2 graph remains
historical evidence at seven modules; Wave 06 introduced `:library`, Wave 07 introduced
`:chapters`, Wave 08 added `:reader` and `:feature:reader`, and Wave 09 added
`:downloads` and `:storage:files`, producing the thirteen-module capability graph.
The approved between-wave UI foundation added `:core:designsystem`; Wave 10 then added `:settings`
and `:feature:settings`, reaching sixteen production modules. HES-v1 M0 adds `:reader:engine` as the
seventeenth production module without changing Room schema 11; verified M1/M2 add no further module/schema
change. The approved post-baseline evolution
remains defined by `../superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md`,
with Reader/HES ownership additionally governed by the 2026-08-25 HES design and plan above.

## Approved module evolution

| Wave boundary | New production modules | Capability reason |
|---|---|---|
| 06 | `:library` | Library membership and protected content mappings |
| 07 | `:chapters` | Release synchronization and canonical aggregation |
| 08 | `:reader`, `:feature:reader` | Reader policy and independent immersive presentation |
| 09 | `:downloads`, `:storage:files` | Offline/cache policy and atomic file adapter |
| Post-Wave-09 UI foundation | `:core:designsystem` | Application theme, visual tokens, and domain-neutral shared UX |
| 10 | `:settings`, `:feature:settings` | Typed policies and independent settings presentation |
| HES-v1 M0 | `:reader:engine` | Pure JVM reference-engine boundary and immutable routing contracts; no adaptive production behavior yet |
| 11 | `:feature:plugins` | Full plugin-management presentation |

No catch-all synchronization module is planned. Pure orchestration stays with its
capability; WorkManager and notification adapters stay in `:app`.

## Historical wave/capability status

This table is lifecycle/evidence history, not the current execution queue. Select present work only
from `Current position`.

| Wave | Ownership | Status | Canonical document |
|---|---|---|---|
| 01 | Foundation, architecture, CI | Implementation present; historical checkpoint evidence retained | `waves/wave-01-foundation-and-architecture.md` |
| 02 | Domain and local storage | Implementation present on Room schema 1; checkpoint acceptance remains evidence-driven | `waves/wave-02-domain-and-local-storage.md` |
| 03 | Plugin contracts and packages | Historical implementation superseded by the Baseline 2 protocol/package boundary | `waves/wave-03-plugin-contracts-and-packages.md` |
| 04 | Plugin host and security | **Implementation present; checkpoint accepted** | `waves/wave-04-plugin-host-and-security.md` |
| 05 | Catalog Home and discovery | **Implementation present; checkpoint accepted** | `waves/wave-05-catalog-home-and-discovery.md` |
| AB2 | Architecture reset between Wave 05 and Wave 06 | **Accepted** | `../internal/checkpoints/architecture-baseline-2.md` |
| 06 | Library and story matching | **Completed; Tasks 01-06 verified** | `waves/wave-06-library-and-story-matching.md` |
| 07 | Chapter sync and aggregation | **Completed; Tasks 01-06 verified** | `waves/wave-07-chapter-sync-and-aggregation.md` |
| 08 | Reader and progress | **Completed; Tasks 01-06 and checkpoint verified** | `waves/wave-08-reader-and-reading-progress.md` |
| 09 | Cache, downloads, storage | **Completed; Tasks 01-06 and checkpoint verified** | `waves/wave-09-cache-downloads-and-storage.md` |
| UIF | Between-wave design-system foundation | **Completed; checkpoint accepted 2026-08-12** | `../internal/checkpoints/design-system-foundation.md` |
| PUI | ReDantotsu-inspired Product UI redesign | **Completed; checkpoint accepted 2026-08-14** | `../internal/checkpoints/product-ui-redesign.md` |
| DSR | Discover semantic-feed redesign | **Completed; Room schema 7; focused/device/visual/benchmark verification complete** | `../internal/checkpoints/discover-semantic-feed-redesign.md` |
| CML | Catalog metadata lifecycle unification | **Implementation present; Room schema 8; unified Summary/Full lifecycle** | `../project/current-state.md` |
| CCE | Canonical Catalog Reconciliation & Fusion Engine | **Phases 0–7 / Tasks 1–42 verified/closed; Room schema 9** | `../internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-7.md` |
| CED | Canonical engine performance/durability | **Entry baseline accepted on schema 10** | `../internal/checkpoints/wave-10-entry-readiness-2026-08-24.md` |
| 10 | Background work, auth, notifications | **Verified/closed on schema 11; API 26/API 37 matrix and final host acceptance PASS** | `../internal/checkpoints/wave-10-production-remediation.md` |
| HES-M0 | Adaptive Reader Continuity / HES-v1 constitutional boundary | **Verified/closed** | `../internal/checkpoints/adaptive-reader-continuity-hes-v1-m0.md` |
| HES-M1 | Legacy-compatible pure reasoner + differential overlap envelope | **Verified/closed; Tasks 6–8** | `../internal/checkpoints/adaptive-reader-continuity-hes-v1-m1-m2.md` |
| HES-M2 | Session/coordinator compatibility boundary | **Verified/closed; Tasks 9–11** | `../internal/checkpoints/adaptive-reader-continuity-hes-v1-m1-m2.md` |
| HES-M3 | Typed observations, validation, and process health | **Verified/closed; Tasks 12–16; host Gradle + architecture gates GREEN** | `../internal/checkpoints/adaptive-reader-continuity-hes-v1-m3.md` |
| HES-M4 | Adaptive routing, bounded effect facts, reactive graph, and replanning | **Verified/closed; Tasks 17–24; host Gradle + connected Room + architecture gates GREEN** | `../internal/checkpoints/adaptive-reader-continuity-hes-v1-m4.md` |
| HES-M5 | Committed-vs-target Reader continuity + bounded N+1 prefetch | **Verified/closed; Tasks 25–26; focused + broad host Gradle + architecture/policy gates GREEN; M6 ready** | `../internal/checkpoints/adaptive-reader-continuity-hes-v1-m5.md` |
| HES-M6 | One foreground hedge + deterministic competitive execution | **Verified/closed; Tasks 27–30; focused + broad host Gradle + architecture/policy gates GREEN; M7 subsequently proceeded** | `../internal/checkpoints/adaptive-reader-continuity-hes-v1-m6.md` |
| HES-M7 | Golden/property/stress freeze + legacy ranking retirement + final governance | **Verified/closed; Tasks 31–34; required host/device boundary GREEN; HES-v1 frozen** | `../internal/checkpoints/adaptive-reader-continuity-hes-v1.md` |
| HES-M7.1 | Detekt debt closure | **Verified/closed; standalone Detekt, unchanged combined host gate, and architecture contracts GREEN** | `../internal/checkpoints/adaptive-reader-continuity-hes-v1.md` |
| HES-M7.2 | Constitutional hardening | **Verified/closed historical milestone; HES-v1 was re-frozen from fresh final-tree evidence** | `../internal/checkpoints/adaptive-reader-continuity-hes-v1-m7-2.md` |
| HES-M7.3 | Conformance repair | **Verified/closed historical milestone; fresh final-tree closure matrix GREEN** | `../internal/checkpoints/adaptive-reader-continuity-hes-v1-m7-3.md` |
| HES-M7.4 | `AccessReason` API hygiene | **Verified/closed by the accepted M7.5 final-tree matrix** | `../internal/checkpoints/adaptive-reader-continuity-hes-v1-m7-4.md` |
| HES-M7.5 | Final Reader/HES-v1 freeze hardening | **Verified/closed; HES-v1 final re-frozen/reference-grade** | `../internal/checkpoints/adaptive-reader-continuity-hes-v1-m7-5.md` |
| 11 | Hardening and open-source release | **Wave 10 boundary accepted; HES-v1 final re-frozen after M7.5 closure** | `waves/wave-11-hardening-open-source-release.md` |

## Wave 04 decomposition

| Task | Outcome | State |
|---|---|---|
| 04.01 | Allowlisted HTTP gateway, shared URL policy, bounded body reader, budgets, sessions, decoding, redaction | Implementation present |
| 04.02 | Transactional install, neutral registry port, Room adapter, rollback | Implementation present |
| 04.03 | Historical selector runtime | Superseded and removed by Architecture Baseline 2 |
| 04.04 | JavaScript capability sandbox | Replaced by the Baseline 2 protocol/runtime boundary |
| 04.05 | Update and capability-diff lifecycle | Implementation present |
| 04.06 | Redacted diagnostics and unified host facade | Implementation present |

## Wave 05 decomposition

| Task | Outcome | State |
|---|---|---|
| 05.01 | Source-preserving catalog ingestion and cached Home persistence | Verified by Wave 05 checkpoint |
| 05.02 | Canonical MyAnimeList reference package and safe bootstrap/update boundary | Verified |
| 05.03 | Deterministic cross-catalog matching and aggregate ranking | Verified |
| 05.04 | Cached Home refresh/orchestration | Verified |
| 05.05 | Combined and catalog-specific Home UI | Verified |
| 05.06 | Search, filters, and source-preserving story detail | Verified |

## Wave 06 decomposition

| Task | Outcome | State |
|---|---|---|
| 06.01 | `:library`, metadata-only membership, Room schema 2, current architecture verifier | [Verified](../internal/checkpoints/wave-06-task-01-metadata-only-library.md) |
| 06.02 | Library presentation in `:feature:catalog` | [Verified](../internal/checkpoints/wave-06-task-02-library-presentation.md) |
| 06.03 | Explainable content-story matching | [Verified](../internal/checkpoints/wave-06-task-03-content-story-matching.md) |
| 06.04 | Quick/deferred content-plugin search | [Verified](../internal/checkpoints/wave-06-task-04-content-source-search.md) |
| 06.05 | Protected content mappings and Room schema 3 | [Verified](../internal/checkpoints/wave-06-task-05-protected-content-mappings.md) |
| 06.06 | Mapping review and URL import UI | [Verified](../internal/checkpoints/wave-06-task-06-mapping-review-url-import.md) |

## Wave 07 decomposition

| Task | Outcome | State |
|---|---|---|
| 07.01 | `:chapters` and deterministic chapter-label normalization | Verified |
| 07.02 | Pure deterministic release aggregation and protected overrides | Verified |
| 07.03 | Provider-neutral chapter operations through the runtime facade | Verified |
| 07.04 | Transactional chapter graph persistence and Room schema 4 | Verified |
| 07.05 | Recent/full/incremental synchronization and initial worker adapter | Verified |
| 07.06 | Canonical chapter-list presentation and correction controls | Verified |

## Wave 08 decomposition

| Task | Outcome | State |
|---|---|---|
| 08.01 | Reader modules and bounded structured text/image-document validation | Implementation present |
| 08.02 | Pure deterministic release selection | Implementation present |
| 08.03 | Store-first sanitized content loading and fallback | Implementation present |
| 08.04 | Debounced exact progress and Room schema 5 | Implementation present |
| 08.05 | Stable-ID navigation and process-restorable Reader state | Implementation present |
| 08.06 | Accessible structured text / vertical image-page Compose Reader UI | Implementation present |

## Critical dependency chain

```text
architecture
  -> canonical domain and Room
    -> plugin contracts and package validation
      -> secure plugin execution
        -> catalog discovery
             ^ Wave 06 complete: Library + protected mappings + review/URL import verified
          -> story matching
            -> chapter aggregation
                 ^ Wave 07 complete: sync, aggregation, schema 4, and presentation verified
              -> reader
                   ^ Wave 08 complete: Reader, progress, schema 5, and device checkpoint verified
                -> offline storage
                     ^ Wave 09 complete: cache, downloads, schema 6, reconciliation, and offline UI verified
                  -> UI foundation
                    -> Product UI redesign
                      -> semantic Discover redesign + Room schema 7
                        -> unified catalog metadata lifecycle + Room schema 8
                          -> canonical catalog reconciliation/fusion engine (Phase 1 verified/closed)
                            -> canonical schema foundation (schema 9 accepted)
                              -> metadata fusion/read-path cutover (Phase 2 Tasks 12-21 verified/closed)
                                -> reconciliation observe-only (Phase 3 Tasks 22-25 verified/closed)
                                  -> atomic Story graph merge + guarded auto-merge (Phase 4 Tasks 26-32 verified/closed)
                                    -> durable review resolution/UI (Phase 5 Tasks 33-35 verified/closed)
                                      -> shared evidence-change orchestration (Phase 6 Task 36 verified/closed)
                                        -> operation-level Full metadata fallback (Phase 6 Task 37 verified/closed)
                                          -> retroactive reconciliation + post-merge correction review (Phase 6 Task 38 verified/closed)
                                            -> durable engine-work drain + policy safety passes (Phase 7 Task 39 verified/closed)
                                              -> controlled reversal for provably safe historical merges (Phase 7 Task 40 verified/closed)
                                                -> structured decision traces + invariant diagnostics (Phase 7 Task 41 verified/closed)
                                                  -> final governance/docs + acceptance/profile/performance matrix (Phase 7 Task 42 verified/closed)
                                  -> canonical durability leases/outbox (schema 9 -> 10; entry accepted)
                                    -> local background/auth/notifications (Wave 10; schema 10 -> 11)
                      -> release hardening
```

## Execution rules

1. Select active work only from `Current position`, then follow its named checkpoint and owning plan.
2. Use `../project/current-state.md`, code, and tests for what is implemented now; a historical plan or
   lifecycle row is never proof of current behavior.
3. Treat completed Wave/HES/CCE records as architecture/evidence provenance unless the active task or a
   concrete root-cause trail requires them.
4. Preserve established migration ownership: canonical foundation owns `MIGRATION_8_9`, canonical
   durability owns `MIGRATION_9_10`, and Wave 10 notification persistence owns `MIGRATION_10_11`.
5. Evolve modules only at the owning boundary defined by the applicable approved architecture/design.
6. When a needed contract is absent or insufficient in current code, create it in the consuming
   capability and migrate the current consumer in the same task; do not encode a false existing-port
   assumption in implementation docs.
7. Update current state/checkpoints only after actual command/device evidence is reviewed.

Discover / Home / Library remains the top-level product model. Downloads, Updates, and Settings remain
utility surfaces rather than top-level navigation. Current task/Wave sequencing is intentionally not
duplicated here; use `Current position`.

## Verification workflow

Use `./scripts/verify-fast.sh` during implementation iterations. It runs repository/static
contracts, exact architecture verification, local tests, Detekt, and Room schema stability
without Android lint or app assembly. Before a task/checkpoint is accepted, run
`./scripts/verify.sh`; it is still the canonical full host gate and additionally runs
`lintDebug` and `:app:assembleDebug`. Both entry points use strict dependency verification.
The full gate executes `verifyArchitecture` in the same Gradle invocation as the rest of the
Gradle workload, while project-level daemon reuse, configuration cache, parallel execution,
and local build cache reduce repeated verification cost without removing checks.

## Verification principle

Plans and source presence are not proof that a checkpoint passed. Checkpoint records must
name the command, environment, and result. Historical `NOT RUN` entries are preserved
rather than rewritten from later assumptions.
