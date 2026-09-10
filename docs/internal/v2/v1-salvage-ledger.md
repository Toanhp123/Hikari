# Hikari V2 V1 Salvage Ledger

Date: 2026-09-10
Status: Canonical V1 salvage classification through V2 Step 2 Task 13

This ledger preserves contracts and architectural evidence that must survive the V1 runtime cutover. It does not approve quarantined implementations, prescribe later capability internals, or keep obsolete runtime composition alive.

- `KEEP` preserves the contract or asset without changing its meaning.
- `REDESIGN` preserves the problem and required semantics while rejecting the current ownership or cost shape.
- `DROP` intentionally carries neither the implementation nor a migration obligation into V2.
- `TRANSPLANT` is an accepted retained candidate, subject to its focused verification.
- `QUARANTINE` remains buildable/referenceable but is not accepted V2 runtime.
- `REFERENCE` preserves the contract and evidence for a future owner without transplanting V1 integration.
- `NONE` carries no implementation or contract forward.

| ID | Contract / asset | Decision | Retention | Future owner | Evidence / reason |
|---|---|---|---|---|---|
| SAL-001 | `:reader:engine` | KEEP | TRANSPLANT | Reader capability admission | The whole-app audit found no independent defect in the pure routing/evaluation engine; retain source and tests without activating Reader runtime during Step 1. |
| SAL-002 | `:plugins:api` | KEEP | TRANSPLANT | Plugin SDK and future Plugin capability | The protocol/contract boundary has no confirmed independent performance defect and remains the public compatibility surface. |
| SAL-003 | reviewed pure `:core:common` primitives needed by retained contracts | KEEP | TRANSPLANT | The retained contract that consumes each reviewed symbol | The audit found no independent hotspot, but retention is symbol-by-symbol rather than approval by module inertia. |
| SAL-004 | `:catalog:engine` | REDESIGN | QUARANTINE | Future Catalog Engine Admission Gate | A1, A2, and A3 are confirmed engine/data-scope defects; A4 remains a collision-heavy scaling risk requiring admission evidence. |
| SAL-005 | `:catalog:model` | REDESIGN | QUARANTINE | Future Catalog model/read boundary | X3 shows that the broad model shape amplifies decoding, allocation, hashing, and byte-width cost; narrow types may be reused only after review. |
| SAL-006 | `app.openstory` release identity; JDK 17; minSdk 26; targetSdk 37 | KEEP | REFERENCE | `:app` and build logic | Architecture Baseline 2 preserves these platform/bootstrap invariants; Step 1 development and benchmark identities remain isolated from release identity. |
| SAL-007 | clean-install, upgrade, and backup semantics | REDESIGN | REFERENCE | `:app` release and launch-state owners | Step 1 disables backup so V1/cloud state cannot seed V2 launch state; same-application-ID V1-to-V2 upgrade is explicitly outside Step 1 and requires a later reviewed contract. |
| SAL-008 | local-first product resilience semantics | KEEP | REFERENCE | Each admitted data capability | Cached usable state remains valuable during remote failure; future capabilities must define bounded ownership rather than reuse V1 orchestration. |
| SAL-009 | plugin host network/files/platform trust boundary | KEEP | REFERENCE | Future Plugin host/runtime capability | Architecture Baseline 2 and the Wave 04 checkpoint establish host-owned privileged access and fail-closed behavior. |
| SAL-010 | HTTPS allowlist + redirect revalidation + bounded responses | KEEP | REFERENCE | Future Plugin HTTP capability | Wave 04 security evidence covers undeclared hosts, redirects, oversized bodies, and timeout handling. |
| SAL-011 | package verification + atomic activation/rollback semantics | KEEP | REFERENCE | Future Plugin package lifecycle owner | Package bytes must be verified before activation; failed install or activation must leave the prior usable version intact. |
| SAL-012 | capability expansion review and rollback semantics | KEEP | REFERENCE | Future Plugin policy and package lifecycle owners | Expanded privileges require explicit review, while rollback restores the prior immutable version; performance work must not auto-approve permissions. |
| SAL-013 | per-source failure isolation | KEEP | REFERENCE | Future Catalog ingestion owner | One source failure must not erase another source or the previous complete snapshot. |
| SAL-014 | deterministic matching/ranking as a semantic property | KEEP | REFERENCE | Future Catalog engine owner | Reproducible matching/ranking remains required even though the current Catalog engine is quarantined. |
| SAL-015 | Reader stable-ID route and release/progress continuity semantics | KEEP | REFERENCE | Future Reader integration and progress owners | Reader checkpoints preserve stable route identity, exact release identity, fingerprinted position, deterministic fallback, and stale-completion rejection. |
| SAL-016 | Reader checksum/security invalidation semantics | KEEP | REFERENCE | Future Reader asset/cache integration owner | Confirmed corruption invalidates only the proven bad exact asset; missing or I/O failure is not corruption, and stale plans cannot commit after hard invalidation. |
| SAL-017 | integrity verification must not be removed merely for fewer copies | KEEP | REFERENCE | Reader and storage payload owners | X11 identifies allocation/copy amplification while explicitly retaining checksum and integrity validation as correctness/security contracts. |
| SAL-018 | explicit download durability semantics | KEEP | REFERENCE | Future Downloads capability | User-requested durable content requires explicit lifecycle, storage, recovery, and deletion ownership distinct from automatic cache policy. |
| SAL-019 | source-specific metadata preservation | KEEP | REFERENCE | Future Catalog and Plugin protocol owners | Source identity and source-provided metadata remain preserved even when canonical read models and orchestration are redesigned. |
| SAL-020 | Catalog reconciliation/indexing families A1/A2/A3/A4 | REDESIGN | REFERENCE | Future Catalog Engine Admission Gate | Foreground/global evidence rebuilds, fork/index rebuilding, repeated Story membership reconstruction, and candidate fan-out need bounded or incremental contracts. |
| SAL-021 | broad canonical read/projection families A5/A6/A7/A8 | REDESIGN | REFERENCE | Future Catalog, Library, Downloads, and storage query owners | Point or bounded semantic questions must not materialize or observe unrelated global canonical, redirect, mapping, progress, or download state. |
| SAL-022 | foreground execution ownership families L1/L3/L4/L5/L6/L7/L8 | REDESIGN | REFERENCE | Each admitted foreground capability and shared execution policy | CPU context, single-flight, bounded concurrency, fusion coalescing, batch persistence, and subscription demand require explicit owners. |
| SAL-023 | historical evidence lifetime D1 | REDESIGN | REFERENCE | Future Catalog evidence owner | Historical evidence needs provenance, reachability, and retention semantics; blind expiry is not an acceptable identity/canonical policy. |
| SAL-024 | cross-capability performance families X1-X7 and X19 | REDESIGN | REFERENCE | Owning Catalog, Library, storage, and execution capabilities | Semantic invalidation, bounded cache policy inputs, narrow models, coherent snapshots, exclusive work ownership, backlog isolation, and batch APIs must be re-admitted with scoped evidence. |
| SAL-025 | Room/schema ownership | REDESIGN | REFERENCE | Each future capability plus Android storage adapters | Room may remain an adapter, but schemas and queries must follow capability boundaries rather than define domain ownership or preserve a V1 migration chain by default. |
| SAL-026 | background/durable execution ownership | REDESIGN | REFERENCE | Each admitted capability and app scheduling boundary | Durable work, retries, continuation, recovery, and foreground joins require one authoritative owner; process launch is not a maintenance trigger. |
| SAL-027 | plugin runtime control-plane/payload split X16-X18 | REDESIGN | REFERENCE | Future Plugin runtime, package, policy, and secure-session owners | Metadata questions must not load executable/package payloads; retry lifetime and secure-session work must remain bounded without weakening security. |
| SAL-028 | Reader asset/cache integration X8-X12 | REDESIGN | REFERENCE | Future Reader, Downloads, and storage integration owners | Viewport-local planning, cache accounting, critical sections, payload ownership, and process memoization require bounded working-set and lifetime contracts. |
| SAL-029 | Chapter sync X13-X15 | REDESIGN | REFERENCE | Future Chapters and durable scheduling owners | Multi-page aggregation, commit, and continuation work must use delta/batch semantics and storage-bounded continuation rather than cumulative rescans. |
| SAL-030 | Home, Search, and canonical Story user journeys | KEEP | REFERENCE | Product design and future admitted capability owners | Architecture Baseline 2 preserves the user journeys, but their V1 aggregate implementation and dependency graph are not migration targets. |
| SAL-031 | MyAnimeList reference catalog behavior and source metadata | KEEP | REFERENCE | Future reference Plugin and Catalog owners | Preserve the concrete reference behavior and identifiers as acceptance knowledge; do not carry production demonstration/runtime wiring into Step 1. |
| SAL-032 | Hilt and Navigation 3 as prior framework choices | REDESIGN | REFERENCE | Future app composition and navigation owners | Baseline 2 accepted these tools, but Step 1 intentionally admits neither; later use requires a real capability need and evidence against the boot boundary. |
| SAL-033 | JavaScriptEngine, OkHttp, and Jsoup in the Plugin security subsystem | REDESIGN | REFERENCE | Future Plugin runtime and capability owners | These remain reviewed implementation candidates only inside the host-controlled security boundary; they are not Step 1 shell dependencies. |
| SAL-034 | canonical Story model shape and Catalog repository/orchestration | REDESIGN | REFERENCE | Future Catalog model, persistence, and orchestration owners | Preserve stable product identity and source facts while replacing broad models, global reads, and V1 refresh/search/details ownership. |
| SAL-035 | Plugin package/runtime bridge evolution | REDESIGN | REFERENCE | Future Plugin package and runtime owners | Keep the transplanted `:plugins:api` contract surface while redesigning `.osp` lifecycle integration, operation bridging, and capability brokerage around bounded host policy. |
| SAL-036 | V1 tests and fixtures with surviving invariants | REDESIGN | REFERENCE | The future owner of each preserved contract | Port evidence by invariant and realistic boundary behavior rather than copying test files or V1 composition scaffolding. |
| SAL-037 | declarative Selector runtime/schema as production execution model | DROP | NONE | Future Plugin runtime owner | The obsolete production execution model does not survive; reference catalog behavior is preserved through the reviewed Plugin contract instead. |
| SAL-038 | generic `:core:network` and roadmap-wide `:core:model` ownership | DROP | NONE | Capability-owned network policy and models | Generic shared ownership would obscure the Plugin trust boundary and recreate over-wide cross-capability models. |
| SAL-039 | speculative Library/chapter/release/progress persistence | DROP | NONE | Future capability owners | Persistence is introduced only when an admitted capability owns concrete durability semantics. |
| SAL-040 | production default/selector demonstration catalogs | DROP | NONE | Future reference Plugin owner | Demonstration catalogs are not production migration assets; the preserved MAL reference behavior is rebuilt through the reviewed protocol. |
| SAL-041 | V1 Home aggregate implementation | DROP | NONE | V2 shell and later destination owners | The aggregate directly reactivates broad V1 domains and is not a migration target; product journeys may be rebuilt only through admitted capabilities. |
| SAL-042 | V1 Android orchestration/composition root | DROP | NONE | V2 app shell | V1 app graph, manual factories, DI wiring, and cross-domain startup composition must not survive the clean boot boundary. |
| SAL-043 | current app-wide startup observers/coordinators | DROP | NONE | V2 boot boundary | Process creation and foreground launch must not imply refresh, recovery, scan, reconciliation, provisioning, or scheduling. |
| SAL-044 | V1 feature ViewModels as migration targets | DROP | NONE | Future feature owners | Preserve user-facing semantics where approved, not V1 presentation/orchestration classes or their dependency graphs. |
| SAL-045 | V1 benchmark product journeys | DROP | NONE | V2 benchmark owner | Step 1 establishes fresh-install and returning-launch baselines; old product journeys do not define the clean-shell performance contract. |
| SAL-046 | old runtime-specific structural suppressions/temporary source-layout allowances | DROP | NONE | V2 architecture verification | V2 starts with no inherited temporary debt exemptions or test-only production escape hatches. |
| SAL-047 | V1 Design System theme and interaction semantics | REDESIGN | REFERENCE | `:core:designsystem` and feature presentation owners | Preserve the reviewed base Material 3 palette, typography, shapes, accessibility intent, static skeleton concept, and pull-refresh semantics. Reject wholesale module/build/test transplantation, semantic token families, artwork/network/backdrop ownership, app-wide component catalog, Roborazzi/Robolectric surface, and Story/Chapters pull-refresh policy. |

## Source authority

- `docs/internal/architecture-baseline-2/invariant-inventory.md`
- `docs/internal/performance-big-update-v3/Hikari-performance-architecture-audit-whole-app-big-update-2026-09-07.md`
- `docs/internal/performance-big-update-v3/Hikari-structural-simplification-deep-audit-2026-09-07.md`
- `docs/internal/checkpoints/wave-04-plugin-host-and-security.md`
- `docs/internal/checkpoints/wave-08-reader-and-reading-progress.md`
- `docs/internal/checkpoints/adaptive-reader-continuity-hes-v1.md` and its accepted milestone checkpoints
- `docs/internal/checkpoints/reader-image-continuity-cache-ricc-v1.md`
- `docs/superpowers/specs/2026-09-07-hikari-v2-foundation-clean-boot-design.md`
