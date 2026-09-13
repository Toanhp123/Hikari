# Hikari V2 Step 3 — Base App UX/UI Completion Design

**Status:** R1.5 — USER-APPROVED / CANONICAL STEP 3 PRODUCT + ARCHITECTURE AUTHORITY  
**Date:** 2026-09-13  
**Milestone:** Hikari V2 Step 3  
**Precondition:** Step 2 final acceptance/freeze must be recorded in the current repository before Step 3 implementation begins. In the audited Step 2 roadmap this gate was Task 18; if the current repository has renamed or replaced that checkpoint, the current accepted checkpoint remains authoritative. Known Step 1/2 structural and performance debt must remain explicitly visible; Step 3 does not reset the baseline.  
**Supersedes for implementation authority:** `2026-09-13-hikari-v2-step-3-base-app-ux-ui-completion-design-R1.4.md`, R1.3 and R0.14 after user approval. Earlier revisions remain design-history/source material.  
**Primary design direction:** **App Shell + Capability Slices**.  

---

## 0. Document authority and interpretation

This document is the Step 3 product-and-architecture contract. It deliberately separates **product semantics and ownership rules** from **implementation technology**.

The following are normative unless explicitly labeled as an implementation detail:

- capability ownership;
- data authority and lifetime;
- navigation semantics;
- activation and runtime-lifetime rules;
- user-visible state semantics;
- Step 3 / Step 4 / later-wave boundaries;
- performance and architecture acceptance rules.

The following remain implementation choices to be resolved in the implementation plan after this design is approved:

- exact Android navigation library or hand-written typed-stack mechanism;
- exact module count for each capability slice;
- whether independent semantic persistence owners share one physical Room database or use separate databases;
- exact pagination/windowing mechanism for large local collections;
- exact coroutine/operator implementation of latest-wins and single-flight semantics;
- exact numeric retained-page, process-retention, artwork-cache and concurrency limits after benchmark evidence;
- exact process-wide admission mechanism/primitive used to enforce the resource bounds in this contract;
- exact supported App-language list, provided every exposed locale has accepted Step 3 coverage.

No implementation choice may weaken the normative behavior below.

### 0.1 Source priority

For implementation work:

1. current repository code/tests and current accepted checkpoint establish what is already implemented;
2. this approved R1.5 document establishes Step 3 product and architecture intent;
3. the Step 3 implementation plan decomposes the approved design into tasks;
4. R0.14 and V1 artifacts are historical evidence, not permission to re-admit legacy topology.

### 0.2 Terminology

- **Catalog authority** — the one catalog source selected for a media discovery context. It owns catalog-local Story identity and catalog content acquisition.
- **Reading Source** — a source used later for content consumption. It is distinct from Catalog authority.
- **Library** — durable user collection truth: whether a Story is saved and the minimum presentation snapshot required for local collection UX.
- **Route state** — lightweight navigation/presentation state retained to restore user context.
- **Runtime demand** — explicit foreground demand that may activate remote/provider/storage work.
- **Durable Catalog material** — bounded persisted Discover/Story material admitted in Step 2.
- **Transient Catalog query** — Search, Section Listing, or Similar result windows that are route/session state by default and are not automatically imported into durable Catalog storage.
- **Control-plane descriptor** — cheap local capability/security/configuration metadata that may be read without opening provider payload execution or remote work.
- **Physical persistence owner** — the lifetime owner of an actual database/DataStore resource; it is distinct from a capability's logical repository/store handle when storage is shared.

---

### 0.3 R1.5 repository-realizability and performance-hardening amendments

R1.5 keeps the R1.4 product shape and capability boundaries. It preserves the repository-realizability fixes from R1.4 and closes the remaining performance/lifetime loopholes found by adversarial comparison with V1 failure modes and the Step 1/2 capability/performance constitutions:

- preserve the existing Step 1 `Unknown -> FirstRun -> Ready` startup gate and change only the post-Ready destination to the Step 3 App Shell;
- separate logical capability-store lifetime from physical database lifetime when persistence is shared;
- establish a process-shared bounded artwork infrastructure that can render Home/retained UI without activating full Catalog runtime;
- bound retained route payloads process-wide, not only per route;
- make Step 3 the explicit production remote-network admission milestone while preserving zero-network startup;
- add process-wide expensive-work admission so independent Catalog/Reading/artwork owners cannot collectively recreate unbounded execution;
- define explicit Story Reading binding clear-to-default semantics;
- freeze default-Catalog changes away from already-retained route/request authority;
- define Settings/About as app-level focused destinations;
- clarify Reading Source availability as local capability presence rather than live health;
- require intentional evolution of Step 1/2 static policy gates instead of bypassing them;
- restore the R0 language-domain rule with capability-gated Catalog-language configuration rather than an unconditional preference;
- make RETAINED routes quiesce route-scoped domain collectors by default so deep history cannot create unbounded live observers even when each observer is point-scoped;
- require explicit execution-owner policy for every new Step 3 capability so collection-scale CPU work cannot drift onto Main and blocking I/O/network cannot become UI-owned work;
- give Home local Search a physical-work/query-scaling acceptance contract, not only latest-wins semantics;
- freeze accepted Step 1/2 red performance debt as **no-growth debt** and require final Step 3 profile regeneration/rebenchmark evidence;
- prevent no-op Library snapshot writes from amplifying Room invalidation/recomposition;
- require same-artwork in-flight coalescing/single-owner semantics where request identity/security/transform match;
- add starvation/priority-inversion evidence to process-wide expensive-work admission;
- prevent shared physical Room topology from making Home startup/migration cost scale with unrelated aged Catalog history;
- make Library Add/snapshot enrichment timestamp-stable and idempotent so metadata refresh/no-op actions do not reorder or invalidate Home;
- make already-committed Settings selections semantic no-ops rather than redundant persistence/recreation work;
- separate logical retained navigation from persistent offscreen Compose-tree lifetime so inactive roots cannot keep effects/images/collectors alive;
- add continuation-progress guards so opaque cursors/zero-delta pages cannot create an unbounded pagination loop;
- bound transport retries and cross-origin sensitive-header propagation so redirects/retries cannot amplify work or leak source credentials implicitly.

These amendments do not re-open the approved Home/Search/Story/Settings UX model.

---

# 1. Why Step 3 exists

## 1.1 V2 origin

Hikari V2 is not a cosmetic rewrite of V1. The V1 whole-app performance and structural audits showed a repeated architecture failure mode:

> A bounded user action could scale primarily with unrelated global or historical application state.

Confirmed V1 patterns included:

- foreground operations rebuilding global reconciliation/canonical evidence;
- broad reactive observation of Library, progress, downloads, mappings, redirects, or provider state;
- Search/provider fan-out and persistent historical evidence growth;
- N+1 point lookups and per-entry DAO calls;
- cumulative reprocessing of paginated data;
- global cache/progress accounting on local Reader actions;
- background mapping and source orchestration triggered from unrelated user actions;
- large authorities that mixed control-plane, payload execution, persistence and presentation;
- retained process state whose lifetime exceeded the UX need that created it.

Step 1 and Step 2 already responded by shrinking startup activation, defining demand-driven capability ownership, and re-admitting only a bounded Discover → Story slice.

Step 3 must complete the base application **without rebuilding the V1 topology under new class names**.

## 1.2 Step 1 foundation

Conceptually:

```text
Process
  -> App root / first frame
  -> boot-safe launch-state resolution

NOT automatically:
  -> Catalog remote runtime
  -> Reading Source execution
  -> Reader runtime
  -> WorkManager/background orchestration
  -> global Room observation
  -> downloads/progress/chapter work
```

Step 3 inherits this as a hard invariant.

### 1.2.1 Startup handoff invariant

The accepted Step 1 startup state machine remains authoritative:

```text
Unknown
  -> FirstRun
  -> Ready
```

Step 3 does **not** replace or bypass this gate. The migration changes only the post-Ready product destination:

```text
Step 2
Ready + first-frame gate
  -> CatalogEntryPoint

Step 3
Ready + first-frame gate
  -> AppShell
      -> Home root
```

Therefore:

- `Unknown` remains a boot-resolution state;
- fresh install still reaches the real FirstRun surface;
- FirstRun completion must still persist before the app becomes Ready;
- first-frame/startup ordering remains protected;
- Home is the default **post-Ready product root**, not a replacement for FirstRun;
- Catalog/Reading remote work remains forbidden before Ready and explicit foreground demand.

The launch-state persistence authority remains narrow and must not be silently folded into a mega Settings store merely because Step 3 adds Settings.

## 1.3 Step 2 foundation

Step 2 established the narrow production-shaped Catalog slice:

```text
Catalog demand
  -> Discover
  -> Story Detail
```

and introduced important implementation evidence that Step 3 must preserve rather than rewrite wholesale:

- catalog-local `StorySourceRef` identity;
- bounded persisted Discover projection;
- keyed Story detail observation/acquisition;
- explicit active-Story retention protection;
- single-flight acquisition ownership;
- scoped image/artwork provenance and security policy;
- retained-content behavior on refresh failure;
- strict validation and bounded Catalog inputs;
- a small domain-neutral Design System;
- explicit performance evidence and accepted debt;
- real-source/plugin proof in test scope without admitting the legacy plugin runtime into the production graph.

## 1.4 Step 3 purpose

Step 3 is the **Base App UX/UI Completion** milestone.

It turns the Step 2 visual blueprint into a coherent application with:

- real App Shell and top-level navigation;
- local Home/Library;
- explicit Catalog Search;
- truthful expanded section listings where the Catalog supports them;
- completed non-reading Story interactions;
- capability-gated Similar discovery;
- Reading Source setup/mapping without content consumption;
- Settings for Theme, App language and builtin source defaults;
- shared responsive/accessibility/localization presentation foundations;
- preserved Step 1/2 activation and performance boundaries.

The product loop stops before Chapters/Reader.

---

# 2. Scope and milestone boundaries

## 2.1 Step 3 product loop

```text
Launch
  -> Home / Manga / Light Novel
  -> Browse or explicit Search
  -> Story Detail
       -> inspect metadata/synopsis
       -> Add/Remove Library
       -> Share
       -> Similar, if real Catalog capability exists
       -> inspect/configure Reading Source
  -> return to exact previous context
```

A user should be able to discover, inspect, collect and configure a Story without feeling that the app is still a two-screen prototype.

## 2.2 Step 3 owns

- App Shell and independent Manga / Home / Light Novel root histories.
- Home as a basic local Library surface.
- Basic Library membership and durable Library presentation snapshot.
- Catalog Search with explicit-submit semantics.
- Expanded Section Listing only where a real source capability exists.
- Story non-reading actions and metadata presentation.
- Similar only where a real Catalog capability exists.
- One **selected** Catalog authority per media discovery context; runtime activation remains demand-driven.
- Builtin Catalog/Reading Source configuration seams.
- One committed Reading Source binding per Story.
- Reading Source Detect and explicit human confirmation.
- Theme and App-language configuration.
- Default Catalog / Reading Source / Reading-language preferences where choices actually exist.
- App menu, Settings and About.
- Shared Design System/presentation primitives required by admitted screens.
- Localization of the active Step 3 production surface.
- Accessibility, restoration and performance regression protection.
- Intentional production remote-transport admission for the proven builtin capabilities, with startup remaining zero-network.
- Process-wide bounded admission for remote/decode/other expensive Step 3 work.

## 2.3 Step 4 boundary

Step 3 must not acquire, aggregate, synchronize or expose real functionality for:

- chapters;
- releases;
- Reader;
- reading progress;
- Continue Reading;
- previous/next chapter;
- chapter downloads;
- Reader continuity;
- content-consumption source routing;
- Reader settings.

The long-term UI may display a disabled `Read from Chapter 1` action and disabled `Chapters` tab, but they must use true disabled semantics and cause **zero hidden Step 4 work**.

## 2.4 Later-wave boundary

Step 3 does not admit:

- custom Catalog/Reading Source installation;
- plugin repository browsing;
- plugin install/update/enable/disable lifecycle;
- generic plugin settings schemas;
- cross-Catalog canonical fusion;
- automatic source matching/autolink;
- personalized recommendation engine;
- background source probing/mapping;
- full public deep-link system;
- advanced downloads/offline/background-sync products.

Builtin capability adapters may exist behind narrow V2 contracts. The generic plugin lifecycle remains a later-wave concern.

## 2.5 Media scope

Step 3 product roots are:

```text
Manga
Home
Light Novel
```

The Light Novel root admits **`LIGHT_NOVEL` only**. `WEB_NOVEL` remains unsupported/deferred in Step 3. It must not be silently coerced into `LIGHT_NOVEL`.

Every shipped media root must have a proven production builtin authority before Step 3 can be accepted. A mockup or test-only proof is not sufficient production evidence.

## 2.6 Visual authority and blueprint interpretation

The accepted Step 2 UI is the blueprint for the product shape that Step 3 activates and completes. Performance work must not improve numbers by removing current icons, sections, cards or long-term affordances that the product intends to keep. Step 3 primarily adds real behavior, state and capability behind that shape, while making the explicit UX corrections in this document.

Dantotsu remains visual inspiration rather than semantic authority. Hikari owns its own information architecture, capability boundaries and state meaning.

The written R1 contract overrides schematic reference imagery whenever they differ. In particular:

- Home filters are `All / Manga / Light Novel`, not reading-progress filters such as Reading/Completed;
- `Read from Chapter 1` and `Chapters` remain visible-but-disabled Step 4 affordances;
- Story tabs are exactly `Synopsis / Chapters / Similar`;
- source/provider controls must reflect real admitted capability rather than visual placeholders.

Accidental visual drift introduced solely by shared-component extraction is a regression. Deliberate R1 product changes are allowed and must be captured by updated screenshot evidence.

---

# 3. Step 3 architecture constitution

The following rules are intentionally short and broad because they protect the entire milestone.

## 3.1 Scope law

> A user action may expand work only to the smallest semantic authority scope required to fulfill that action.

Examples:

```text
Add Library Story A
  -> point Library write for A
  NOT -> rescan all Library
  NOT -> refresh Catalog

Detect Source B
  -> Source B + one submitted query
  NOT -> enumerate every source
  NOT -> auto-search aliases

Load Listing next page
  -> one continuation request
  NOT -> recompute all prior pages

Open Similar for Story A
  -> Story A's Catalog authority
  NOT -> current default Catalog if different
```

## 3.2 Ownership law

> Persistence follows semantic ownership. Retention follows UX restoration need. Runtime lifetime follows foreground demand. Cache lifetime never silently becomes domain truth.

Therefore:

```text
need Back restoration != must persist Room
cached             != user truth
route retained     != runtime active
looks similar      != same semantic model
```

## 3.3 V1 anti-regression law

- No bounded foreground operation may require a global historical scan unless the operation is explicitly a maintenance/global feature.
- No capability may widen authority merely to make implementation reuse easier.
- No retained route may create hidden background acquisition.
- No UI abstraction may merge independent domain truths for DRYness.
- No future-facing capability is admitted merely because V1 already contains code for it.

---

# 4. High-level architecture — App Shell + Capability Slices

## 4.1 Logical shape

```text
                         :app
                   App Shell / Composition
                          |
              +-----------+------------+
              |           |            |
            Manga        Home      Light Novel
              |           |            |
              |        Library         |
              |         Slice          |
              +-----------+------------+
                          |
                        Story
              +-----------+-----------+
              |           |           |
           Catalog      Library    Reading Source
            facet        facet        facet
              |                       |
           Similar                  Detect
           Search                   Mapping
           Listing                  Language
```

Screen boundaries do not define semantic ownership. A single screen may compose several capability facets while each truth remains in its owning slice.

## 4.2 App Shell responsibilities

The App Shell owns only application-level presentation/navigation concerns:

- selected top-level destination;
- three independent root histories;
- route push/pop/restoration semantics;
- global App menu entry and global chrome placement;
- foreground route identity;
- wiring feature entry points at the composition root.

The App Shell does **not** own:

- Catalog requests;
- Library persistence logic;
- Reading Source detection/mapping policy;
- pagination mechanics;
- Story metadata acquisition;
- image acquisition policy;
- Settings persistence implementation.

`:app` is where capabilities meet, not where capability business logic lives.

## 4.3 Capability slices

### Catalog slice

Owns:

- Catalog authority identity/descriptors;
- Discover acquisition/materialization;
- Story Catalog metadata/detail;
- explicit Search execution;
- expanded Section Listing execution;
- Similar execution when supported;
- Catalog-local Story identity;
- shared bounded Catalog-store lifetime and cross-authority retention coordination.

### Library slice

Owns:

- saved/not-saved membership;
- saved timestamp;
- durable presentation fallback snapshot;
- local Home query/filter/projection;
- point Add/Remove operations.

### Reading Source slice

Owns:

- builtin Reading Source descriptors;
- one committed Story Reading binding;
- Detect execution for one explicitly selected source;
- candidate-specific verified language metadata;
- atomic mapping replacement;
- Story-specific selected reading language within the binding.

### Settings/preferences slice

Owns typed app-wide preferences:

- Theme;
- App locale;
- Catalog defaults when a choice exists;
- default Reading Source per media;
- default Reading language per media.

It does not become a global capability/status aggregator.

## 4.4 Module topology

The architecture requires semantic boundaries, not a fixed module count. A reasonable implementation may create domain/storage/feature modules for Library or Reading Source, or may combine small layers while preserving dependency direction.

What is mandatory:

- Design System remains domain-neutral;
- feature UI does not import another feature's UI model for convenience;
- `:app` does not import storage implementation details;
- Catalog, Library and Reading Source persistence lifetimes remain semantically independent from one another;
- no Catalog cache reset/retention policy may destroy Library or Story Reading user truth.

---

# 5. Authority and data-lifetime model

## 5.1 Authority map

```text
APP SHELL
  -> root selection/history
  -> route restoration identity

CATALOG
  -> Catalog Story identity
  -> Discover material
  -> Story metadata
  -> Search/List/Similar execution

LIBRARY
  -> saved membership
  -> local collection
  -> durable presentation snapshot

READING SOURCE
  -> source descriptors
  -> Story Reading binding
  -> Detect candidate execution

SETTINGS
  -> Theme/App locale
  -> media defaults
```

A failure or preference change in one authority must not silently mutate another authority's truth.

## 5.2 Lifetime table

| Data/state | Owner | Lifetime | Durable by default? |
|---|---|---|---|
| Selected top-level destination | App Shell | session + small restoration | Small state only |
| Three root stack identities | App Shell | session + small restoration | Small state only |
| Scroll/tab/query UI identity | route | route/session | Small restoration only |
| Discover projection | Catalog | bounded cache | Yes |
| Story metadata/detail | Catalog | bounded cache | Yes |
| Search result pages | Search route | route/session | No |
| Section Listing pages | Listing route | route/session | No |
| Similar results | Story route | route/session | No |
| Library membership | Library | durable user truth | Yes |
| Library presentation snapshot | Library | durable fallback | Yes |
| Story Reading binding | Reading Source | durable user/config truth | Yes |
| Detect candidates | Detect route | transient | No |
| Reading Source draft | setup/Detect route | transient | No |
| Theme/App locale/defaults | Settings | durable preference | Yes |
| Decoded artwork | image layer | bounded cache | Never domain truth |
| Artwork bytes/disk cache | image layer | bounded cache | Cache only |

## 5.3 Failure mutation rule

```text
Catalog request fails
  -> Library unchanged

Reading Source unavailable
  -> committed binding unchanged

Search new request fails
  -> old displayed result may remain

Listing append fails
  -> prior pages remain

Similar fails
  -> Story metadata remains

Settings write fails
  -> previous committed preference remains
```

## 5.4 Empty, unsupported and failure are distinct

- **Unsupported** — the capability does not exist for this authority.
- **Empty** — a supported operation succeeded with zero results.
- **Failure** — a supported operation was attempted and failed.

Retry is rendered only when there is a captured retryable operation to repeat.

## 5.5 Logical store lifetime vs physical persistence lifetime

Semantic persistence ownership and physical resource ownership are separate concerns.

A Catalog/Library/Reading Source capability may hold a logical repository/store handle, but that handle may close the underlying physical database **only when that database is dedicated to that capability/lifetime**.

If multiple semantic owners share one physical Room database:

```text
neutral physical database owner
  -> Catalog logical store
  -> Library logical store
  -> Story Reading logical store
```

then:

- one Catalog authority/session quiescing or closing must not close the physical database while Library/Reading/another Catalog authority still needs it;
- Catalog Runtime Host owns Catalog logical-store/session lifetime, not an implicitly shared physical database lifetime;
- Catalog cache rebuild/eviction/migration must not cascade-delete Library membership or Story Reading bindings;
- a physical database close/reopen policy belongs to neutral storage infrastructure (wired from the composition root behind narrow factories/ports) or a dedicated database owner, not to an arbitrary feature route/session. This does not permit `:app` to import Room/storage implementation details.

The exact choice between dedicated databases and a shared physical database remains implementation-level. The lifetime rule above is normative.

Physical sharing also must not smuggle unrelated historical/startup cost into Home. If Catalog, Library and/or Story Reading share one Room database, opening the Library path on cold Home must not require synchronous Catalog-history scans, provider initialization, or migration work whose cost primarily scales with unrelated Catalog cache age. Any shared-DB topology must prove Home-only startup/query behavior against aged Catalog fixtures; if that proof fails, the implementation must separate the physical persistence lifetime/topology rather than accept hidden cross-domain startup coupling.

---

# 6. Navigation and runtime lifecycle

## 6.1 One global top-level navigation authority

Top-level navigation moves out of `:feature:catalog` and becomes App Shell ownership.

The current Step 2 single mutable `CatalogRoute` / `CatalogNavigationState` model is not sufficient for Step 3 and must be evolved.

## 6.2 Independent root histories

```text
MangaRoot
  Discover
  -> Search
  -> Section Listing
  -> Story
  -> Story ...

HomeRoot
  Library
  -> Story
  -> Story ...

LightNovelRoot
  Discover
  -> Search
  -> Section Listing
  -> Story
  -> Story ...
```

Switching roots restores the previous route stack and lightweight presentation context for that root **within the admitted process-wide route-history bound in §6.11.1**.

Re-selecting the already selected root pops that root's child stack back to its root destination. Re-select does **not** imply refresh.

System Back unwinds the current root stack. Root switching is not a Back-history chain. At a top-level root, Back follows normal platform/app exit-or-background behavior rather than jumping to the previously selected tab.

Cold launch selects Home as the default root. This default does not override later Back/navigation origin: a Story opened from Search returns to Search, a Story opened from Home returns to Home, and nested Story navigation returns to the immediate parent Story.

## 6.3 Route lifecycle

Every destination conceptually has:

```text
ACTIVE
RETAINED
RELEASED
```

### ACTIVE

Foreground destination. It may acquire demand, submit requests and collect active remote/provider state.

### RETAINED

Still in route history. It may retain lightweight route/presentation state, bounded transient collection windows and scroll/tab state. It must not start new hidden remote work merely because the owner still exists.

### RELEASED

Route popped. Route-local state is released. Durable domain/cache state lives according to its own authority.

## 6.4 Retain context, not active work

> Retain user context aggressively; retain active capability work only while semantic demand exists.

A retained route does not imply a retained provider session, decoded image, polling loop, retry loop or unnecessary active collector.

`RETAINED` is a **logical navigation/restoration state**, not a requirement to keep the destination's full Compose subtree, image consumers, `LaunchedEffect`s or heavyweight presentation owner instantiated. Implementations may dispose/recreate UI owners while preserving the bounded route state required by this contract. Short overlap during an actual navigation animation is allowed; persistent multi-root/multi-route composition that keeps inactive side effects alive is not.

A retained route starts no **new** external/expensive work. An already accepted bounded durable acquisition may complete after the route becomes retained only when completion remains keyed to the original authority/identity and cannot publish into a different route. This is safe completion of already-owned work, not retained runtime demand.

Route-scoped domain observation is **quiesced by default when the route becomes RETAINED**, even when the individual observer is point-scoped and cheap. Otherwise a deep history can recreate V1-style lifetime growth as `N retained routes -> N live collectors`. Retained presentation state is materialized/lightweight; it is not permission to keep an unbounded set of Library/binding/DAO collectors alive.

A retained local observer may remain only when all of the following are explicit and verified:

- its semantic owner genuinely requires observation while inactive;
- its aggregate cardinality is process-bounded independently of route depth;
- it cannot trigger remote/expensive work or broad invalidation cascades;
- lifecycle tests prove repeated/deep navigation does not create monotonic collector growth.

When a route becomes ACTIVE again, it re-establishes only the point/bounded observations required by the visible surface and reconciles materialized route state with current local durable truth. That local reconciliation is allowed and must not be confused with a remote refetch caused merely by Back navigation.

Route-owned **transient expensive work** follows the same lifetime boundary. Search requests, Listing append, Similar fetches and Reading Source Detect/search owned only by that route are cancelled/invalidated when the route becomes RETAINED unless the operation has already transitioned into a separately owned atomic durable commit. Retaining the route is not a reason to let transient network/CPU work continue in the background.

A previously admitted durable Catalog acquisition may either cancel or safely finish only under its capability's explicit ownership policy; if it finishes after route retention, completion must not require the retained route runtime/collector and must remain keyed to the original durable authority/identity.

## 6.5 Construction is not activation

ViewModel/presentation-owner construction must not activate remote capabilities.

The Step 2 pattern where construction can immediately `activateIfNeeded()` must be removed from any owner that can exist while its root is inactive or unvisited.

Activation follows explicit foreground demand.

## 6.6 Unvisited roots are lazy

The mere existence of three top-level navigation items does not require constructing three feature runtimes or ViewModels.

Cold launch to Home must not instantiate or activate Manga/LN remote Catalog capability merely because their destinations exist.

Likewise, switching roots must not be implemented by permanently composing/resuming all three full root destination trees just to preserve history. Independent history is retained as navigation/route state; only the selected root is foreground-active outside bounded transition overlap.

## 6.7 Nested Story routes

Story-to-Story navigation must be route-scoped:

```text
Story A ACTIVE
  Similar tab selected, scroll retained
    -> Story B ACTIVE
       Story A RETAINED
       -> Story C ACTIVE
```

Only the foreground Story acquires active Story demand. Parent Story route state remains lightweight and restorable.

There must not be one mutable global `StoryViewModel(activeRef)` that erases parent Story route state, nor a global Story-session tree that retains full runtime for every historical Story.

## 6.8 Story route arguments

Every Story navigation origin passes a lightweight contract conceptually equivalent to:

```text
StoryRouteArgs
  ref: StorySourceRef
  originMediaContext: CatalogMediaType
  preview: StoryRoutePreview?
```

`originMediaContext` is an asserted navigation context used before full detail arrives. Live Catalog detail remains the validated Catalog metadata. A mismatch is an integrity/validation issue; it is not silently rewritten.

`StoryRoutePreview` contains only small progressive-rendering data such as title and artwork presentation identity. It is not durable Story truth and must not contain full detail/runtime state.

## 6.9 Same-process vs process-death restoration

Same-process Back should restore retained query/result/scroll/tab state without gratuitous re-query.

Process death has a weaker guarantee: restore small route identity and small UI state **within the process-wide route-entry/history bound**, then reconstruct transient results from durable/cache/remote sources as needed.

Large result lists, images, runtime objects and network responses are never placed into SavedState.

## 6.10 Async completion and retention safety

Late completion safety and route lifetime are separate rules. A result must always remain keyed to the original authority/request identity and can never publish into a different active route. In addition:

- route-owned transient Search/Listing/Similar/Detect work is cancelled/invalidated when its route becomes RETAINED under §6.4;
- a late completion racing that cancellation is discarded and must not repopulate/re-inflate a compacted inactive route;
- a separately owned durable Catalog acquisition may finish only when its explicit owner/lifetime permits it and the completion can materialize safely without retained route runtime;
- reactivation does not automatically replay an explicit-submit Search/Detect request merely because the route became visible again. Any capability-specific foreground resume behavior must preserve the existing explicit-submit/demand contract.

Search generations, Listing continuations, Similar requests, Detect generations and Story acquisitions must reject stale cross-route/request publication.

## 6.11 Process-wide retained-payload budget

Per-route bounds are not sufficient by themselves. A deep Story chain plus Search/Listing/Similar windows across three root histories must not create unbounded aggregate retained presentation state.

Step 3 therefore requires a **process-wide retained-payload budget** across inactive route histories. Exact numeric limits and the compaction algorithm are benchmark-driven implementation choices.

When that budget is exceeded, Hikari preserves cheap route identity and user intent first:

```text
keep
  route identity / authority
  submitted query or section identity
  selected tab
  small scroll/restoration token where meaningful

compact first
  old transient result windows/pages
  old Similar payloads
  reconstructible presentation projections
```

Compaction does not pop navigation history and does not start hidden reload work while the route is inactive. If the user later returns to a compacted route, the foreground route may reconstruct/reload through its original frozen authority/context.

Normal same-process Back should restore retained payloads without gratuitous work **while those payloads remain inside the process retention budget**. Deep-history/memory-pressure compaction is the explicit bounded exception.

### 6.11.1 Process-wide route-entry/history metadata bound

Lightweight route identity is cheaper than result payload, but it is not free. An arbitrarily deep Story chain across three root histories can still create unbounded process memory and oversized process-death saved state even after every old payload has been compacted.

Step 3 therefore also requires a process-wide bound on retained **route-entry/history metadata**. Exact numeric depth/count and trimming/collapse policy are benchmark-driven implementation choices, but the aggregate number/size of retained route entries may not grow without bound.

Under extreme deep-history pressure the implementation preserves, in order:

```text
1. each top-level root identity
2. each root's last-selected/top route identity so switching roots can still restore that root's last visible destination
3. the current focused route's immediate parent/origin required for the next Back action
4. the most recent useful child/ancestor history within the configured bound
```

Older reconstructible child/ancestor history may be trimmed/collapsed according to an explicit deterministic policy. The resulting stack must remain valid: if older ancestors are removed, the oldest retained child/history segment reconnects to its own top-level root rather than to another root or a fabricated Story. Trimming must preserve each root's last-selected/top route identity even when deeper ancestors beneath that route are compacted away. Trimming must not trigger hidden remote reload while the trimmed history is inactive, and it must not corrupt Catalog/Library/Reading durable truth.

The ordinary same-process Back/restore guarantees in this spec apply to history that remains inside the admitted route-history bound. This bound also protects `SavedStateHandle`/instance-state reconstruction from growing with arbitrary navigation depth.

## 6.12 Process-wide route-collector bound

Process-wide retained-payload bounds and route-local point observations solve different problems. Step 3 therefore also requires bounded **live route-scoped collector cardinality**.

Normative default:

```text
ACTIVE route
  -> may own its required point/bounded local collectors

RETAINED route
  -> materialized lightweight state
  -> route-scoped domain collectors quiesced by default

RELEASED route
  -> route collectors/resources released
```

The number of live route-scoped Library/StoryReading/Catalog/UI-domain collectors must not grow linearly with retained navigation depth. App-shell/global preference collectors with genuinely process-level ownership are separate from route-scoped collectors and must remain small/bounded.

Reactivation may perform bounded local reconciliation before/while publishing the active surface, but it must preserve frozen route authority and must not transform a route restoration into hidden Catalog/Reading remote work.

Exact collector-count thresholds/instrumentation are implementation-level; the no-monotonic-growth property is normative.

## 6.13 App-level focused destinations

Settings and About are app-level focused destinations presented above the currently selected root history. They are not duplicated semantic destinations owned separately by Manga/Home/Light Novel.

```text
selected root stack
  -> App menu
  -> Settings / About
  -> Back
  -> exact selected root context
```

While an app-level focused destination is foreground, inactive product roots do not gain new remote demand. Settings/About keep their own focused presentation state but do not become a fourth top-level root.

---

# 7. Catalog identity, authority and runtime host

## 7.1 Catalog-local Story identity

Step 3 preserves Catalog-local identity.

Conceptually:

```text
StorySourceRef
  storyId
  catalogSourceKey
  sourceStoryId
```

There is no Step 3 canonical Story identity across Catalogs. Title, alias, external identifier, author, artwork or similarity never become automatic merge keys.

## 7.2 Current/default Catalog vs existing Story provenance

```text
NEW DISCOVERY
  -> current Catalog authority for that media context

EXISTING STORY
  -> provenance embedded in StorySourceRef / Library snapshot
```

Changing the current/default Catalog in a future configuration cannot rebind an existing Story, Library entry, retained Search route or retained Listing route.

## 7.3 Catalog Authority Resolver

A local/control-plane resolver maps media discovery contexts to the selected builtin authority.

It does not fetch content, open Room, probe providers or activate runtime.

When only one builtin Catalog exists for a media, Settings may render it as informational rather than storing a fake choice.

## 7.4 Capability descriptors

A local immutable Catalog descriptor states which capabilities are actually admitted:

- Discover;
- Story Detail;
- Search;
- expanded sections, per section;
- Similar.

Reading descriptors must be possible without provider/network activation.

Unsupported capability is a first-class absence, not a retryable error.

## 7.5 Narrow Catalog capabilities

Step 3 must not grow the existing `CatalogAcquisitionSource` into a God interface.

Conceptually, capabilities remain narrow:

```text
CatalogDiscoverCapability
CatalogStoryCapability
CatalogSearchCapability
CatalogSectionCapability
CatalogSimilarCapability
```

One builtin adapter may implement several of these, but a consumer receives only the capability it needs.

## 7.6 Multi-authority-safe Catalog Runtime Host

Step 2's single-binding session is insufficient once Manga and Light Novel may resolve to different authorities.

A lightweight Catalog Runtime Host may own only:

- authority registration/lazy instantiation;
- shared durable Catalog-store lifecycle;
- cross-authority Catalog-retention coordination;
- globally bounded active-Story protection for that shared retention domain.

It does **not** become a use-case/service coordinator for Discover/Search/Story/Library/Settings.

Multiple registered authorities do not imply multiple active runtimes.

## 7.7 Shared Catalog store and retention

If multiple authorities share one Catalog store/retention table, retention protection must consider active Stories across all authorities. A source-local active-pin universe is insufficient for global eviction.

The exact locking/granularity is an implementation-plan decision, but no authority may evict durable Catalog material for another currently active Story because it cannot see that active demand.

Shared durable-store lifetime belongs to the Catalog Runtime Host/retention domain, not to one child authority session. Closing or quiescing one authority must not tear down shared storage still required by another active authority.

Library membership is not an active Catalog pin. A large Library must not defeat bounded Catalog retention.

Retention maintenance itself must remain bounded. A point Story/Discover materialization or one eviction pass must not rebuild/reaggregate the entire historical Catalog corpus merely to decide what to evict. Candidate selection/eviction should operate in indexed/bounded batches against the shared retention domain plus the small active-pin set. Exact SQL/index/batch size is implementation-level, but aged Catalog size must not become the primary work dimension of every point acquisition.

## 7.8 Durable vs transient Catalog execution

### Durable Catalog acquisition

```text
Discover
Story Detail
  -> bounded validation/materialization
  -> Catalog storage
```

### Transient Catalog queries

```text
Search
Section Listing
Similar
  -> bounded validation/mapping
  -> route/session state
  -> no automatic permanent import
```

Transient query execution must not require opening Catalog storage merely to execute the request when persistence is not needed.

Selected transient result → Story may materialize only that selected Story as required.

## 7.9 Builtin adapters

Builtin production adapters terminate at narrow V2 capability contracts. Feature/application code must not depend on generic plugin runtime objects, manifests, JS sandbox types or legacy reconciliation/fusion services.

Pure provider mechanics may be reused after audit. Legacy multi-provider orchestration topology may not.

## 7.10 Production remote-transport admission

Step 2 deliberately kept production network capability behind test/benchmark proof. Step 3 is the milestone that may intentionally admit production remote transport because production Manga/Light Novel Catalog and Reading Source Detect require it.

Admitting Android `INTERNET` permission is **not** permission for eager networking. The production contract remains:

```text
process/startup/FirstRun/Home local Ready
  -> zero remote capability request by default

explicit foreground Catalog/Detect/artwork demand
  -> bounded admitted remote transport
```

Every builtin remote adapter must preserve or replace the Step 2 security/boundedness properties appropriate to its payload:

- HTTPS-only by default; no cleartext fallback without a separate explicit review;
- origin/host policy is explicit for builtin provider/API and artwork traffic;
- redirects are bounded and each target is revalidated against the applicable policy;
- sensitive/auth headers, cookies or source credentials are not forwarded to a redirected/different origin unless that exact propagation is explicitly allowed by the source security policy;
- automatic transport retry is bounded and never becomes an unbounded hidden semantic retry loop; user-visible semantic Retry remains owned by the feature/request context;
- request timeout/cancellation is bounded and follows route/request lifetime;
- response/body size and parsed collection/string fields are bounded before entering route/domain state;
- malformed or untrusted provider payload is validated at the narrow V2 boundary;
- no generic legacy plugin runtime is admitted merely to obtain HTTP;
- release/merged-manifest tests must prove the permission/policy transition intentionally rather than silently weakening the Step 1/2 gate.

The exact HTTP client/adapter technology remains implementation-level.

## 7.11 Default Catalog changes vs retained route authority

If Step 3 ships more than one builtin Catalog for a media, changing the media default is explicit user intent but must not hot-swap authority underneath an existing route/request chain.

Existing Story provenance and retained Search/Listing/Story contexts keep their frozen authority. A new default applies only when a **new discovery context** is created (for example after an explicit root reset/re-entry policy defined by the App Shell implementation).

Do not mutate a visible retained result set from Catalog A into Catalog B in place. Do not title-match/remap existing Stories.

If only one builtin Catalog exists for a media, no fake default selector or transition behavior is required.

---

# 8. Discover

## 8.1 Independent Manga and Light Novel roots

The Step 2 mutable `selectedMediaType` presentation model is retired for top-level destination ownership.

Manga Discover and Light Novel Discover are independent root contexts with independent scroll/history/restoration state.

## 8.2 Preserve accepted durable Discover semantics

Step 3 keeps the accepted Step 2 Discover state behavior:

- no-content loading;
- no-content failure;
- valid Empty;
- retained Content with scoped refresh activity/issue;
- pull-to-refresh owned by Discover;
- durable bounded projection.

A failed refresh never blanks retained Discover content.

## 8.3 Section descriptors

A Discover section needs enough identity to support truthful expansion without reconstructing meaning from UI labels.

Conceptually:

```text
CatalogSectionDescriptor
  stable section key
  semantic kind
  host/provider display-label source
  expansion capability
```

The descriptor remains minimal. It does not become a provider configuration bag.

Known Hikari semantic sections use localized Hikari copy. Provider-defined custom labels, if ever admitted, remain provider copy.

## 8.4 `See All`

`See All` is rendered only when a real expanded collection exists.

A preview shelf is not automatically expandable.

No disabled/fake `See All` is shown merely for visual symmetry.

## 8.5 Ranked sections

A full ranked listing such as Top Rated is valid only if the source provides:

- globally ordered continuation semantics; or
- an explicit trustworthy global rank per item.

Hikari must not sort each page locally and pretend that the result is a global ranking, nor reaggregate all historical pages on every append.

## 8.6 Truthful shelf semantics

A `LATEST_UPDATES` feed must not be labeled `Recommended for You` unless a real recommendation authority exists.

Step 3 has no personalized recommendation engine.

## 8.7 Partial shelf, Empty and refresh semantics

Discover refresh ownership remains destination-level. Step 3 does not add independent per-shelf refresh controls.

An optional shelf that validly contains no items may be omitted from the Discover composition. A dedicated Section Listing that returns no items must show an explicit scoped Empty state.

A failure in one optional shelf/source sub-operation must not blank unrelated valid shelves when the Catalog contract can still publish a coherent retained Discover surface.

## 8.8 Editorial

Editorial/banner presentation remains feature-owned. A banner becomes interactive only when a real trusted destination is supplied. Hikari does not infer a Story destination from title/artwork alone.

---

# 9. Catalog Search

## 9.1 Identity

Catalog Search is a focused destination inside a media root. Its media and Catalog authority context are frozen for the route/request chain.

Changing defaults elsewhere does not retroactively mix authorities into an existing Search result.

## 9.2 Zero-work open

Opening Search performs no Catalog request.

Initial state is genuine Idle.

Typing edits local `inputQuery` only.

Only explicit Enter/Search submits remote work.

On first entry, the focused Search destination may focus the Search field and show the keyboard. Explicit Search/Enter submits once, may dismiss the keyboard, and must not trigger any extra request from focus changes.

The clear affordance clears only editable `inputQuery`. It does **not** automatically destroy a retained `displayedResult`; result identity changes only through a new successful submitted request or route-lifecycle reset.

## 9.3 State identity

Search must distinguish:

```text
inputQuery
submittedQuery
displayedResult.query
```

A new request may run while prior results remain visible. The UI must make the old-result/new-request relationship explicit.

## 9.4 Search states

- Idle before first submit;
- initial loading with no displayed result;
- initial Failure with no result;
- Empty for a successful query with zero items;
- Content with optional continuation;
- retained Content + new-request activity;
- retained Content + scoped new-request failure;
- append loading/failure/end state.

Retry always repeats the failed submitted request context; it never silently changes authority or query.

## 9.5 Result lifetime

Search result windows are route/session state. Searching many historical queries must not grow permanent Catalog evidence merely to support Back navigation.

Same-process Story → Back restores the retained Search result/scroll without re-query when the route remains retained.

## 9.6 Search capability

The Search capability accepts one media context, one frozen authority, one submitted query and optional opaque continuation.

It does not receive text-field editing state, a list of providers, automatic alias variants or a canonical merge service.

Provider-specific Search filters remain out of Step 3 even if an older protocol already exposes them.

## 9.7 Result ordering and validation

Validated Search items preserve the Catalog's Search ordering contract.

Hikari does not globally resort Search pages unless the capability contract explicitly requires it.

Inputs and outputs are bounded at the V2 boundary: query length, page count, continuation size and result item fields must be constrained before entering route state.

## 9.8 No N+1 enrichment

Search results use the shared Catalog poster-grid/card presentation family.

A Search page must contain enough lightweight data to render degraded poster cards.

Missing optional cover/rating/author does not trigger per-item Story Detail acquisition.

Tap one result → acquire/materialize one Story as needed.

---

# 10. Section Listing

## 10.1 Route identity

A Section Listing route freezes:

```text
authority
media context
section descriptor
```

Every continuation request stays within that identity.

## 10.2 State model

- initial Loading;
- initial Failure;
- valid Empty;
- Content(items, continuation, append state).

Append failure retains already loaded content.

## 10.3 Incremental append

Each page is validated, exact duplicate Story identities from the same Catalog are removed incrementally, and only the delta is appended.

The duplicate guard uses exact `StorySourceRef`/source-local identity. It is not title/author/artwork reconciliation.

## 10.4 Single-flight

Only one append request for the current continuation may be in flight per Listing route. Retry repeats the same continuation and authority context.

## 10.5 Result lifetime

Listing pages are route/session state by default and are not a permanent extension of the Discover cache.

Retained page windows remain bounded; exact numeric caps are benchmark-driven implementation details.

## 10.6 No bulk detail enrichment

Listing cards render from listing payload. Optional missing metadata degrades visually rather than creating N per-item Story Detail calls.

Unranked listings use the shared Catalog poster-grid/card family. A truly ranked listing keeps a ranked presentation rather than being flattened into an ordinary grid merely for implementation convenience.

Step 3 Listing supports initial load, append and retry. It does **not** add a dedicated pull-to-refresh state machine; Discover retains destination-level refresh ownership unless a later product requirement explicitly admits Listing refresh.

## 10.7 Navigation restoration

Within the same process/session:

```text
Listing pages + continuation + scroll
  -> open Story
  -> Back
  -> restore the same retained Listing context
```

Back from Listing restores the originating Discover position/context.

Neither child-Story closure nor Listing→Discover Back may cause a page-1/Discover refetch solely because navigation changed. Normal freshness/explicit refresh policy remains separate.

---

# 11. Story Detail

## 11.1 Story is a composition surface

Story combines independent facets:

```text
Catalog facet
  -> summary/detail/artwork/synopsis/Similar capability

Library facet
  -> membership + local mutation

Reading Source facet
  -> committed binding + defaults/setup

App/navigation actions
  -> Back, Share, More routing
```

A thin presentation reducer may combine these facets for rendering. It must not become the business owner of all three capabilities.

## 11.2 Progressive Story open

Every Story origin may pass a small preview. The UI should render meaningful known title/artwork as early as possible and enrich with durable/live Catalog material when available.

A Story must not require every optional fact before becoming useful.

Optional absent fields are omitted/degraded; they are not acquisition failures.

## 11.3 Catalog metadata

Step 3 Story may display, when available:

- title;
- artwork;
- synopsis/description;
- authors;
- artists, if actually provided;
- genres;
- rating;
- publication status;
- latest update;
- bounded Catalog language tags.

## 11.4 Preserve bounded alternate titles

Catalog aliases/alternate titles already supplied by the provider should be preserved as bounded Story metadata instead of being discarded.

They may support user-visible alternate-title suggestions in Reading Source Detect.

They are **not** identity evidence, canonicalization input or automatic Search fan-out.

Alias count/length must be bounded and duplicates normalized at the Catalog boundary.

## 11.5 Catalog language metadata

Catalog language metadata is a bounded normalized set/list of language tags.

It is distinct from:

- App language;
- Reading language selected for a Reading Source binding.

Do not collapse Catalog language metadata into Reading preference.

## 11.6 Hero and navigation

- Back is an overlay action on the Hero and simply pops the current route.
- Back is the only Hero-overlay navigation/action affordance in Step 3.
- Back remains legible over bright or dark artwork through an appropriate top scrim/gradient or equivalent contrast treatment.
- The overlay respects status-bar/safe-area insets and uses the shared icon-action/touch-target policy.
- Moving Back onto the Hero must not broaden artwork acquisition or Story observation.
- Story does not hardcode whether the origin was Home, Discover, Search, Listing or another Story.
- More remains an action-row action; it is not duplicated in the Hero top-right area.
- The opposite Hero corner may remain intentionally empty; Step 3 must not invent a mirrored Heart/Bookmark/duplicate More merely for visual symmetry.

## 11.7 No Favorite domain

Step 3 removes duplicate heart/Favorite affordances. The collection action is Library membership.

A future Favorite feature, if desired, requires distinct semantics and a separate milestone decision.

## 11.8 Action row

```text
[ Add to Library / Saved ] [ Share ] [ More ]
```

Add to Library is the primary admitted Story action.

`Read from Chapter 1` remains visible only where the long-term Story blueprint requires it, but is truly disabled and starts no Step 4 work.

## 11.9 Tabs

Story tabs are:

```text
Synopsis | Chapters | Similar
```

- Synopsis — enabled and functional.
- Chapters — visible but disabled in Step 3; zero hidden work.
- Similar — enabled only when the Story's Catalog authority exposes a real Similar capability; otherwise visible disabled to preserve long-term structure.

Synopsis keeps the existing functional `Read More / Read Less` behavior for long text; this is presentation expansion only and causes no extra acquisition.

## 11.10 Similar

When supported:

- initial state is `NotRequested`;
- no request is made on Story open;
- first Similar-tab activation starts one request;
- result is retained in the Story route for same-process navigation;
- subsequent tab switches reuse the retained result;
- route-local single-flight prevents duplicate first-load requests;
- Empty and Failure are distinct;
- Retry repeats the same Story + same provenance Catalog + same Similar operation;
- Similar uses the Story's provenance Catalog authority, never Reading Source and never a newly changed default Catalog;
- Similar results are transient and not automatically imported into permanent Catalog history;
- Similar is bounded by default and is **not** forced into pagination merely because Search/Listing paginate;
- Step 3 adds no dedicated pull-to-refresh state machine for Similar;
- Similar uses the shared Catalog poster-grid/card family rather than inventing a parallel Similar-only card;
- opening a Similar Story pushes a new Story route while the parent retains Similar tab/scroll/result state.

If a Catalog genuinely exposes incremental Similar pagination, that capability may be admitted explicitly rather than baked into the base contract.

If no real Similar capability exists, Hikari must not fake Similar with Search or another Catalog.

## 11.11 Library facet

Membership is a point observation for the current **ACTIVE** Story route, never `observeAll Library -> filter`. When the Story route is RETAINED, this route-scoped observer follows the quiescence rule in §6.12; the materialized membership presentation may remain and is reconciled from local truth when the route becomes ACTIVE again.

The operation model is commit-first local truth:

```text
NotSaved
  -> Saving
  -> Saved

Saved
  -> Removing
  -> NotSaved

Failure
  -> previous stable state + scoped feedback/retry where meaningful
```

While Saving/Removing, the action is disabled and duplicate taps are ignored. A Library write does not blank Catalog content.

Add/Remove is idempotent and serialized per Story. Stable Saved/NotSaved state changes only after local persistence succeeds.

## 11.12 Share

Step 3 Share always supports title sharing.

It may include an app-owned stable public link only if such a stable-link contract already exists or is separately admitted. Provider/source URL is not an implicit fallback, and Step 3 does not invent a public URI format.

## 11.13 More sheet

Story More is a Level-2 contextual sheet with compact rows:

```text
Reading source       <effective source/default state>
Language             <effective/default language>
----------------
Catalog information
Copy title
```

It must not duplicate Share, Library membership, Read or Chapter actions because those already have their own admitted placement/semantics.

The Reading Source row preserves authority state explicitly:

```text
Unmapped with available default
  Reading source    Default: Source A >

Mapped + available
  Reading source    Source A >

Mapped + unavailable
  Reading source    Source A ⚠ >

Unmapped + default unavailable
  Reading source    Default unavailable ⚠ >
```

A media default must never be presented as if it were already a committed Story mapping.

The Language row shows the effective reading-language context. For an unmapped Story it may show the media default/preference but must not imply candidate compatibility has been verified. If a committed binding has exactly one verified language, the row may be informational rather than interactive.

Opening the sheet does not probe remote sources.

`Catalog information` is read-only discovery/metadata provenance in Step 3; it is not a Reading Source selector. It remains product-facing and small, primarily the Catalog display name and only other source identifiers explicitly useful to the user. Internal plugin/runtime/hash/source-key diagnostics are not exposed in production UI.

`Copy title` copies the currently displayed Story title and triggers no Catalog/Reading Source acquisition.

---

# 12. Library and Home

## 12.1 Library authority

Library membership is independent durable user truth. It is not stored as a flag on Catalog cache entities.

Semantic ownership is mandatory; a separate physical database is not. If Library shares a physical Room database with another domain, migrations/reset/retention policies must still prove that Catalog cache lifecycle cannot destroy Library truth.

## 12.2 Library entry

Conceptually:

```text
LibraryEntry
  Story provenance
  origin media context
  savedAt
  lightweight presentation snapshot
```

Provenance preserves enough Catalog identity to reconstruct the original `StorySourceRef` even if defaults change later.

No title-based remapping occurs.

## 12.3 Presentation snapshot

The Library snapshot contains only data required for local collection presentation/fallback, for example:

```text
title
safe artwork snapshot/reference
optional supporting text
```

It is not a clone of Catalog Story detail and does not become an authoritative second metadata graph.

Trusted Story metadata that is already materialized for a saved Story may opportunistically point-update that Story's snapshot. Such enrichment never triggers broad Library/Catalog scans by itself.

Snapshot enrichment is **change-aware**. If the persisted presentation snapshot is semantically identical to the proposed snapshot, Step 3 should not intentionally issue a redundant write merely to refresh timestamps or "touch" the row. Snapshot enrichment never changes `savedAt`; metadata refresh must not reorder the user's Library. No-op writes that wake Home/Library observers and cause avoidable recomposition are a performance regression pattern, not harmless bookkeeping.

## 12.4 Catalog cache independence

Catalog retention/eviction may remove cached Story material while Library membership remains durable.

Library persistence must not be coupled to bounded Catalog cache through a destructive foreign-key/cascade rule that can erase user membership when Catalog material is evicted or rebuilt.

Opening a saved Story with missing Catalog cache:

```text
Library provenance + snapshot
  -> immediate lightweight Story shell
  -> foreground demand activates provenance Catalog as needed
  -> bounded Story acquisition
```

Catalog failure never silently removes the Library entry.

## 12.5 Home identity

Home is the local Library root, not a global dashboard.

It contains:

- local Library search;
- filters exactly `All / Manga / Light Novel`;
- saved Stories in the shared poster-grid visual family;
- true-empty and filtered-empty states.

It does not contain Reading/Completed/Progress/Downloads/chapter state in Step 3.

## 12.6 Home boot invariant

Cold launch to Home must be able to reach meaningful local Ready without:

- Catalog remote activation;
- Reading Source execution;
- Reader/Chapter/Progress capability;
- plugin-runtime fan-out;
- remote Search.

Artwork loading must not become a hidden full-Catalog activation path.

## 12.7 Home local Search

Home Search is local reactive filtering. Typing may update the local query immediately.

This deliberately differs from explicit-submit Catalog Search and Detect.

Home query/filter should be executed at the narrowest storage scope and must not depend on unconditional full-history materialization for large/aged Library state.

Rapid query changes use latest-query-wins semantics; a stale local query result must not overwrite a newer input.

The default collection order is `savedAt` descending (most recently saved first). Step 3 does not add a Sort menu unless a later product decision admits one.

The implementation may use a bounded list, manual windowing or Paging based on benchmark evidence; the design does not require Paging3.

### 12.7.1 Physical query-scaling contract

Latest-wins protects correctness but does not by itself bound physical work. Home local collection/query execution — including the empty-query default collection and each typed query change — must therefore prove that visible-window work does not cause unbounded work proportional to all historical Library rows.

Because Step 3 defines no small hard cap on durable Library size, production Home Search must not rely on an unconditional full-Library materialize/filter/sort path for every input change. The chosen mechanism may use indexed/FTS/prefix-normalized querying, bounded windows or another measured design, but acceptance must show:

- query work is scoped to the submitted local query/filter and requested visible window;
- no user-space O(total Library history) transform runs on every keystroke;
- database query plans do not regress into an unbounded full historical scan/sort per keystroke without an explicit hard corpus bound and benchmark acceptance;
- cancellation/latest-wins stops stale publication **and** stale expensive work as far as the storage mechanism permits;
- aged-state slope/query-count evidence is captured, not inferred from a small empty/fresh Library.

The exact SQL/FTS/index/windowing technology remains implementation-level.

## 12.8 Empty semantics

- Library has zero entries → true Library Empty with real exploration actions for Manga and Light Novel roots (conceptually `Explore Manga` / `Explore Light Novels`).
- Library has entries but current query/filter yields none → filtered/no-match Empty.

These states must not share misleading copy/actions.

## 12.9 Add/Remove semantics

Add writes Library membership + snapshot using data already available from the current Story. It does not call Catalog again to re-fetch the Story. Repeating Add for an already-saved Story is idempotent: it does not bump `savedAt`, reorder the Library, or issue a redundant membership write merely because the action was invoked again.

Remove deletes Library membership only. It does not delete Catalog Story material, Reading Source binding or the currently open Story route.

Home reflects committed local membership from local durable truth. Add on Story → Back Home shows the Story; Remove on Story → Back Home removes it from the Home collection without a remote refresh. If Home was RETAINED behind Story, its route-scoped collection observation may be quiesced; when Home becomes ACTIVE it re-establishes the bounded local query/observation and reconciles membership while preserving Home query/filter/scroll context. This is local truth reconciliation, not Catalog refresh.

No Library event bus is required for truth propagation when committed local persistence is observable.

## 12.10 Large-Library performance

Step 3 acceptance includes aged-state Library fixtures large enough to detect:

- `observeAll` + in-memory filtering;
- O(history) transforms on every keystroke;
- large in-memory sorts;
- unbounded grid materialization;
- image/Catalog activation fan-out.

## 12.11 Durable user-truth migration/recovery

Library membership is user-created durable truth and must not be treated as rebuildable Catalog cache.

Production schema evolution must not use destructive migration for Library truth merely for implementation convenience. Corruption/recovery logic must not silently reconstruct membership from Catalog cache, titles, aliases or current defaults.

`savedAt` is generated by an application-owned/testable clock seam rather than ad-hoc UI time calls so ordering/migration tests remain deterministic.

---

# 13. Reading Source and Story Reading Binding

## 13.1 Authority separation

Catalog authority and Reading Source authority are different domains even if the same package eventually implements both.

Use distinct identity types, conceptually:

```text
CatalogSourceKey
ReadingSourceId
```

Do not collapse them into a generic `SourceId`.

## 13.2 One committed binding per Story

Step 3 uses a coherent durable aggregate conceptually equivalent to:

```text
StoryReadingBinding
  Catalog Story provenance
  readingSourceId
  readingSourceStoryId
  optional source-story display title
  selectedLanguageTag
  verifiedLanguageTags snapshot
  updatedAt
```

A Story has at most one committed Reading Source binding.

Story UI/application consumers use point-scoped binding observation/lookup for the current **ACTIVE** Story. Route-scoped binding observation quiesces while the Story is RETAINED under §6.12 and reconciles from local durable truth on reactivation. Step 3 does not require or expose a broad `observeAllMappings()` path merely for Story presentation.

Binding persistence is semantically independent of Catalog cache and Library membership. Removing from Library does not remove the binding; Catalog cache eviction does not remove it. Persistence must not use a destructive Catalog-cache cascade that can erase a committed binding when bounded Catalog material is rebuilt/evicted.

A separate physical database is optional, not required.

## 13.3 Why source + language are one coherent commit

A new source may support a different language set. Step 3 must never persist a new Reading Source with a known-incompatible old language and “fix it later”.

Source identity, source Story identity and a compatible selected language commit atomically.

The old committed binding remains authoritative until replacement succeeds.

## 13.4 Verified language snapshot

`verifiedLanguageTags` represents candidate-specific capability evidence captured at mapping/update time. It is not eternal provider truth and Step 3 does not introduce a freshness/probing framework for it.

It exists so the Story can later offer a language choice without acquiring Chapters or rerunning Detect.

## 13.5 Builtin Reading Source directory

The source picker reads cheap local descriptors:

- stable `ReadingSourceId`;
- display name;
- admitted media types;
- broad source language metadata where known;
- optional local icon/presentation identity;
- local capability presence/presentation metadata.

The compact picker presents only useful local information, conceptually:

```text
[icon] Source name
       broad supported languages
       current/default marker when applicable
       unavailable marker when locally known
```

The currently committed source remains visibly stable while the user explores alternatives.

Opening the picker performs zero remote Search/probe work.

Broad source languages are configuration hints, not candidate-specific compatibility truth.

## 13.6 Defaults and effective precedence

Settings may hold:

```text
DefaultReadingSource(media)
DefaultReadingLanguage(media)
```

Defaults apply only to unmapped Stories as starting preferences.

Effective source precedence is:

```text
committed Story binding
  > current route-local draft source
  > media default Reading Source
```

The draft affects only the in-progress setup flow; it does not replace the committed Story row until confirmation succeeds.

Changing a default is an O(1)-style preference change and does not scan/rewrite committed Story bindings.

A Story-specific source/language choice never changes the global default.

### 13.6.1 Clear Story override / return to default

A mapped Story must have an explicit way to return to the unmapped/default state.

Conceptually:

```text
committed StoryReadingBinding
  -> explicit Clear story mapping / Use default intent
  -> remove the Story-specific binding atomically
  -> Story becomes Unmapped
  -> effective source/language again resolve from media defaults
```

Clearing the Story binding:

- does not change the global default Reading Source or default Reading language;
- does not remove the Story from Library;
- does not alter Catalog provenance;
- does not trigger Chapters/Reader work;
- removes the Story-specific selected language together with the binding, because that language is part of the same coherent Story Reading aggregate.

Because this removes durable Story-specific configuration, the UI must make the intent explicit and may use the Level-4 confirmation policy where needed. Label `Use default` is appropriate only when a media default actually exists; otherwise the action is a truthful `Clear mapping`/equivalent.

## 13.7 Draft-before-commit

The committed high-level Story states remain deliberately small:

```text
Unmapped
Mapped + Available
Mapped + Unavailable
```

Draft/editing state is separate.

Selecting another source creates a route-local draft only.

The previous committed binding remains visible/authoritative until explicit confirmation succeeds.

Cancel, Back, request failure or process death before commit leaves the old binding untouched.

## 13.8 Detect

After a draft source is chosen, the setup surface exposes an explicit `Detect story` action.

Detect is a focused full destination because it requires Search, result paging, restoration and confirmation.

The Detect route freezes:

```text
Catalog Story identity
origin media context
draft ReadingSourceId
```

Pressing `Detect story` is itself an explicit user command. The candidate destination may therefore:

```text
prefill current Catalog primary title
-> immediately run exactly one initial Search
   against the one selected draft Reading Source
```

This is not search-as-you-type. After that initial explicit Detect command, editing `inputQuery` performs zero remote work; subsequent Enter/Search explicitly submits the edited query.

Exactly one selected Reading Source is queried.

Step 3 does not perform:

- multi-source fan-out;
- automatic alternate-title fan-out;
- candidate scoring/confidence;
- AUTO_LINK;
- rejection history/policy-version matching machinery;
- manual URL-to-source-story mapping;
- background mapping workers.

Alternate Catalog titles may be shown as user-controlled query suggestions. Selecting one edits the input only; it does not issue hidden Search. The next request still requires explicit Enter/Search. Optional aliases never gate entering Detect or the initial primary-title request; Detect remains usable from the primary title alone.

Detect results may expose an opaque continuation when the selected source genuinely paginates. Append is single-flight for the same source/query/continuation, append failure retains already loaded candidates, and Retry preserves the same draft source + submitted query + continuation context.

Detect state keeps semantic distinctions explicit:

- a successful zero-candidate response is **Empty**, not Error;
- transport/runtime/validation failure is **Failure**;
- offline is scoped only when connectivity evidence supports that classification;
- a first-page Failure with no candidate result occupies the result region;
- append Failure retains prior candidates and exposes scoped Retry;
- Empty may surface alternate Catalog titles as user-controlled query suggestions, but choosing a suggestion edits input only and performs no hidden request.

All Detect failure/Empty paths preserve the previous committed binding.

## 13.9 Candidate contract

A Step 3 Reading Source candidate needs bounded data conceptually equivalent to:

```text
ReadingSourceCandidate
  sourceStoryId
  title
  optional authors/aliases/media type
  optional artwork
  candidate-specific verifiedLanguageTags
```

Current older content-search protocols that lack candidate-specific language metadata are insufficient by themselves for the full Step 3 mapping contract. A builtin adapter admitted to Step 3 mapping must provide this metadata **without acquiring Chapters/Reader data**.

Artwork is optional. Missing artwork uses a deterministic fallback and never triggers Catalog reconciliation/title matching.

## 13.10 Candidate semantics

Candidate order follows the selected source's Search contract.

Validation answers “is this payload structurally valid/compatible?” It does not answer “is this definitely the same Story?”. Final identity selection is human-confirmed.

No similarity percentage or “best match” is invented unless a later product feature explicitly admits such semantics.

## 13.11 Confirmation

Selecting a candidate does not commit immediately.

The confirmation surface presents enough information to compare:

- current Catalog Story;
- Reading Source display name;
- selected source Story;
- compatible Reading language.

`Use this source` is enabled only when a valid compatible language is selected.

Binding mutation is serialized per Catalog Story provenance at the Reading Source owner across all routes. Two Story routes must not own independent route-local mutexes that can race durable replacement. Accepted confirmation commands commit in owner order, so the later accepted explicit command becomes final after both succeed.

If the proposed binding is semantically identical to the currently committed source/sourceStory/selected-language/verified-language snapshot, confirmation is an idempotent no-op rather than a timestamp-only rewrite. A genuinely changed verified-language snapshot or selected language remains a real update.

## 13.12 Language selection

When preparing a new binding:

1. keep the current Story-selected language if it is verified for the new candidate;
2. else preselect the media default if verified;
3. else if exactly one candidate language exists, preselect it;
4. else require explicit user selection.

Changing language on an already committed binding uses the persisted verified-language snapshot and does not acquire Chapters.

## 13.13 Unavailable committed/default source

A committed Reading Source becoming unavailable does not delete or silently replace the binding. Story Catalog/Library presentation remains usable; the Reading Source row shows the preserved source with an unavailable indicator.

Step 3 distinguishes local registration/presence from a captured retryable execution failure. Opening the picker does not probe every source, so “available” must not imply live server health was checked at that moment.

For Step 3 Story/picker state, `Available` means the builtin Reading Source descriptor/capability is locally present/registered and admitted for the media. It is a **control-plane presence state**, not a live endpoint-health result. A transient Detect/network failure does not by itself convert a committed binding into `Mapped + Unavailable`; it remains mapped to a present source with a scoped failed operation.

Retry is shown only when a concrete retryable operation exists. If Retry is available, it repeats the same source/request context. A source that is absent/not present is not presented as a retryable network failure.

`Choose another source` starts an explicit Story-specific draft/remapping flow and does **not** change the media-wide default.

If an **unmapped** Story's media default source is unavailable, the Story row presents that fact as a default-state problem (conceptually `Default unavailable`) rather than pretending the Story has a broken committed mapping. Choosing another source for this Story remains a Story-specific override; changing the global default is Settings-only.

If no Reading Source is available for the media, Step 3 shows truthful informational state. It does not render a dead `Manage Reading Sources` control because install/enable/lifecycle management belongs to a later wave. A Settings shortcut is shown only when Settings can perform a real admitted action.

## 13.14 Hard Step 4 stop

No Step 3 observer/listener/worker may translate a `StoryReadingBinding` commit into:

- Chapter acquisition;
- release acquisition;
- Reader initialization;
- progress work;
- download work;
- background content sync.

Step 4 will read the committed binding only when explicit consumption demand begins.

## 13.15 Durable binding migration

`StoryReadingBinding` is durable user/configuration truth, not rebuildable Catalog cache.

Production schema evolution must not destructively reset committed bindings merely because a source adapter/version changes. Recovery must preserve the last committed source identity/language snapshot unless an explicit future migration contract says otherwise.

Adapter/source version may be retained for diagnostics or future verification if needed, but it is not Reading Source Story identity and a version upgrade must not make a committed binding disappear by definition.

---

# 14. Settings, Theme, App language and About

## 14.1 Information architecture

```text
Settings
  -> General
       -> Theme
       -> App language
  -> Reading
       -> Catalog Sources
       -> Reading Sources
  -> About
```

The root Settings destination is lightweight navigation/presentation. It does not instantiate every child owner.

Reading Settings is organized by the two capability domains (`Catalog Sources`, `Reading Sources`), not as separate top-level Manga/Light Novel Settings trees. Media-specific values live inside those capability sections.

This IA is intentionally stable: Wave 11 may extend `Catalog Sources` and `Reading Sources` with real custom-source/plugin lifecycle UI without redesigning the Settings root. Step 3 does not prebuild that future lifecycle.

## 14.2 No mega AppSettings contract

Step 3 does not expose one giant public `AppSettings` stream consumed by every capability.

Use narrow typed semantic ports, for example:

```text
ThemePreference
AppLocalePreference
CatalogDefaults
ReadingDefaults
```

These ports may share one physical DataStore implementation; semantic observation/update scope remains narrow.

## 14.3 Immediate apply

Settings has no full-screen Save button.

Independent scalar preferences use commit-first local persistence: validate → persist → publish committed value → apply. Selecting the value that is already committed is a semantic no-op: do not intentionally rewrite storage, recreate the Activity, restart navigation, or reapply capability defaults merely to confirm the same value.

Dependent default-source/default-language pairs commit coherently when a source change would otherwise create a known-invalid default language.

## 14.4 Theme

Modes:

```text
System
Light
Dark
```

`HikariTheme` remains stateless and receives the resolved visual mode from the app root. Design System does not depend on Settings storage.

Theme changes persist and apply in the current app session; they do not require a full app restart. Recomposition/re-rendering is expected, but the product must not intentionally reset top-level/child navigation, Search state, Library truth or Story Reading bindings.

Reading persisted Theme must not add blocking disk I/O to the Step 1 first-frame path. The implementation must also avoid a materially visible wrong-theme startup flash; both properties require verification.

## 14.5 App language

App language is distinct from Catalog metadata language and Reading language.

Conceptually:

```text
AppLocalePreference
  System
  Specific(localeTag)
```

Only locales with accepted Step 3 resource coverage are exposed to the user.

Platform locale APIs are the application mechanism, not a second semantic preference authority.

A locale-triggered Activity recreation is allowed when required by Android, but it must preserve product/navigation truth rather than start a fresh logical session.

Changing App language does not refresh Catalog, rerun Search, remap Reading Source, clear Library membership, change Reading-language defaults or intentionally reset navigation by product policy.

## 14.6 Localization boundary

All user-visible production copy and accessibility labels on the active Step 3 surface must be localizable.

Feature-specific copy remains feature-owned. Design System owns only truly generic presentation/accessibility strings that belong to the component itself, and those resources must also be localized.

Stable test tags/route identities are not localized.

This migration is limited to the active Step 3 production surface and shared primitives it uses; quarantined/historical V1/reference code is not pulled into scope merely for translation.

## 14.7 Catalog Sources settings

Step 3 configures builtins; it does not manage plugin lifecycle.

If exactly one proven builtin Catalog exists for a media, the row may be informational and no fake picker/default preference is required.

If multiple builtin choices are genuinely admitted, a compact choice surface may set the default for **new discovery contexts only**.

Existing Story provenance, retained Search/Listing authority and Library entries are never rewritten.

Catalog metadata/discovery language is a third language domain distinct from App language and Reading language. Step 3 exposes a Catalog-language preference **only when a proven builtin Catalog explicitly exposes such a configurable capability**.

If admitted, it uses a narrow typed Catalog preference scoped to the relevant media/authority and affects new or explicitly refreshed Catalog requests according to that Catalog contract. It must not:

- change App locale;
- change Reading language;
- rewrite Story/Library identity;
- silently re-run retained Search/Listing requests under a new language;
- exist as a fake global preference when the builtin Catalog does not support configuration.

Catalog language metadata already stored on Story content remains metadata, not the preference authority.

## 14.8 Reading Sources settings

Per media, Settings may configure:

- default builtin Reading Source;
- default Reading language.

Opening Settings reads local descriptors/preferences only. It does not Detect/probe every source.

A known-invalid source/language default pair is not persisted.

## 14.9 About

About is informational, not a diagnostics dashboard.

It may show:

- app name/version;
- project/license information;
- real Privacy/Terms links/documents when they exist.

No placeholder row is shown for a destination that has no real content/action.

## 14.10 Deferred Settings

Unless another admitted capability explicitly requires them, Step 3 does not implement Settings for:

- Reader behavior;
- Chapters;
- progress/sync;
- downloads/offline management;
- cache tuning/quota controls;
- background scheduler;
- notifications;
- account/cloud sync;
- app/update policy;
- advanced diagnostics/developer controls;
- theme editor/advanced theme customization;
- source/plugin install-management;
- arbitrary plugin-schema settings.

## 14.11 Settings/About routing ownership

Settings and About use the app-level focused-destination semantics defined in §6.13. One Settings/About authority is shared regardless of which top-level root opened the App menu.

Back returns to the exact selected root/child context that opened the app-level destination. Settings navigation must not clone a separate Settings stack into each media root or reset the underlying root merely because a preference screen was visited.

---

# 15. App menu and interaction-surface policy

## 15.1 Root App menu

Primary Catalog-root chrome uses the conceptual action pairing:

```text
[ Search ] [ App menu ]
```

Home has its local Search field plus the same app-level menu access in its root chrome; it does not need a duplicate remote-Catalog Search icon.

The App-menu entry should remain in the same app-shell position across root surfaces where the shared root chrome exists. A compact circular/avatar-like visual treatment is allowed, but its semantic identity remains `App menu`, not Profile/Account because Step 3 has no user-account product.

The App menu sheet is pure UI and may contain:

```text
Hikari identity/header
Settings
About
```

`About` is a shortcut to the same About destination/content authority owned by the Settings information architecture; the App menu must not create a second About state owner.

Future app-level rows are added only when the capability exists. Step 3 does not reserve visible placeholder menu rows.

Opening the App menu does not load Settings data or activate capabilities.

## 15.2 Unified interaction levels

### Level 1 — direct/inline

Primary actions, filters, tabs, Retry and immediate controls.

### Level 2 — bottom sheet

Compact secondary/context choices:

- App menu;
- Story More;
- Reading Source picker;
- Reading Language picker;
- Theme/App language/default choices when option sets remain compact.

### Level 3 — full destination

Workflows requiring Search/pagination/restoration/deeper context:

- Discover;
- Catalog Search;
- Section Listing;
- Story;
- Settings/About;
- Reading Source Detect.

### Level 4 — confirmation dialog/surface

Only for real blocking/explicit confirmation such as final Reading Source replacement.

## 15.3 No nested-sheet workflow tree

Complex workflows leave the sheet layer and become destinations. Step 3 does not build a secondary navigation framework out of nested sheets.

One contextual sheet may open one short, clearly related choice sheet (for example Story More → Reading Source choice) when the interaction remains compact. It must not grow into sheet → sheet → sheet chains; Search/pagination/restoration/multi-step work is promoted into a full destination.

Story `More` and App menu use the same app-wide modal-sheet visual family, while their semantic rows remain feature/app-owned.

Modal behavior is standard and truthful:

- Back dismisses the top sheet;
- outside-tap/swipe dismissal is allowed only when it cannot corrupt an in-progress commit;
- dismissing a draft-selection sheet never commits unconfirmed domain state;
- a sheet owns only transient modal presentation state, never Library/Reading/Settings truth;
- accessibility focus is contained/restored appropriately and background content is not exposed as the active surface while the modal is open.

## 15.4 Surface-selection decision rule

When adding a new interaction, use the smallest truthful surface:

```text
immediate direct action
  -> inline/direct

short contextual menu or compact choice set
  -> bottom sheet

Search/pagination/independent restoration/multi-step work
  -> full destination

blocking/destructive/final confirmation
  -> dialog/confirmation surface
```

Do not choose a sheet/dialog merely for local visual preference.

## 15.5 Visible-control truthfulness

Every visible control belongs to one of three semantic classes:

1. **Functional now** — a real Step 3 owner/state transition exists.
2. **Explicitly deferred capability** — only deliberate long-term Step 4 affordances such as Read/Chapters, shown with true disabled semantics and zero hidden work.
3. **Presentation only** — non-clickable and not exposed as an actionable control unless a trusted destination exists.

No Step 3 control may remain in an ambiguous “looks actionable but does nothing” category.

---

# 16. Design System and shared presentation

## 16.1 Design principle

> Share visual policy aggressively; share domain/state ownership selectively.

The Step 3 Design System grows from repeated accepted visual policy. It does not restore the V1 Design System wholesale.

## 16.2 Presentation layers

```text
Compose / Material
        ↓
:core:designsystem
  domain-neutral visual primitives/tokens
        ↓
feature-owned shared presentation
  e.g. CatalogStoryPosterCard
        ↓
destination UI/state
```

Design System may know shape, typography, spacing, touch targets, responsive rules and generic visual interactions.

It must not know Catalog models, Library membership, Reading Source identity, query policy, pagination routes, Room, networking, image acquisition runtime or navigation business semantics.

## 16.3 Promotion rule

Promote a concern into Design System only when:

1. it appears across at least two accepted surfaces or is clearly app-wide policy;
2. its meaning remains domain-neutral after extraction;
3. extraction requires no feature/runtime types.

Otherwise keep it feature-owned.

A target vocabulary is not a requirement to prebuild every primitive before vertical feature work.

Candidate Step 3 vocabulary (names remain implementation-level; responsibilities are the important contract) includes:

```text
HikariDimensions
HikariBreakpoints

HikariArtworkFrame
HikariArtworkFallback

HikariPosterCard
HikariPosterRail
HikariPosterGrid
HikariPosterSkeleton
HikariSectionHeader

HikariSearchField
HikariIconAction
HikariFilterChip

HikariFloatingDestinationNav
HikariFocusedHeader

HikariActionSheet
HikariChoiceSheet
HikariValueRow / HikariSettingsRow
HikariInfoRow
```

Do not implement the whole list up front. Admit each primitive from existing + immediate second-consumer evidence or genuine app-wide policy.

## 16.4 Shared layout policy

Current repeated evidence supports centralizing:

- the accepted compact/wide breakpoint around 600dp;
- compact/wide screen insets;
- minimum 48dp touch target;
- common icon sizes;
- common poster/grid/rail geometry after screenshot-preserving extraction.

Do not import V1's large Reader/dashboard-specific dimension taxonomy.

## 16.5 Poster geometry migration

Step 2 accepted poster sizes are close but not identical. Step 3 must first extract shared card/frame behavior while preserving current accepted visual geometry.

Only after screenshot comparison should a final standard poster aspect ratio be frozen.

Component extraction and visual redesign are separate decisions.

## 16.6 Shared poster visual vs semantic models

Design System provides a domain-neutral `HikariPosterCard`/artwork-frame family.

Feature models remain separate:

```text
CatalogStoryPosterUi
LibraryStoryPosterUi
ReadingSourceCandidateUi
```

Catalog, Library and Reading Source may render through the same visual primitive. They do not share one `GlobalStoryPosterUi`.

## 16.7 Poster layouts

Strong evidence exists for domain-neutral poster layout primitives:

- poster card;
- poster rail;
- adaptive poster grid;
- poster skeleton.

Poster skeleton geometry must match the corresponding shared poster-card/frame geometry closely enough to avoid loading→content layout jumps; features must not invent separate skeleton aspect ratios for the same poster family.

These primitives own layout/geometry only. Search/Listing/Detect pagination and state machines remain caller-owned.

## 16.8 Section header

Evolve the current simple section header to support optional subtitle/action slots.

`See All` is caller-provided localized content and is rendered only when the feature has truthful expanded capability. Do not encode a `showSeeAll` domain flag into Design System.

## 16.9 Search field

A shared Search field may own:

- shape/spacing;
- search icon;
- clear affordance;
- keyboard Search action presentation;
- focus visuals;
- minimum interaction size.

It does not own debounce/query execution semantics.

Therefore the same visual field supports:

```text
Home        -> local reactive query
Catalog     -> explicit remote submit
Detect      -> explicit one-source submit
```

## 16.10 Icon actions

A small domain-neutral icon-action primitive standardizes:

- minimum touch target;
- button semantics;
- enabled/disabled behavior;
- overlay/tonal visual variants only where proven necessary.

It does not hardcode English content descriptions.

## 16.11 Floating destination nav

The Step 2 `CatalogMediaDestinationNav` is retired as global navigation moves to the App Shell.

Design System may provide the visual `HikariFloatingDestinationNav` shell. The App Shell supplies item labels/selected state/callbacks and owns all root-stack behavior.

## 16.12 Settings/sheet rows

Repeated Step 3 geometry justifies small domain-neutral value/info/sheet-row primitives. Avoid a flag-heavy universal row API.

Clickable value rows and informational rows keep distinct accessibility semantics.

## 16.13 Sheets

Provide small visual shells for action and single-choice sheets. Do not create a generic nested-sheet navigation engine.

Shared sheet presentation may own:

- modal shape/drag-handle policy;
- padding/system insets;
- row geometry and full-row touch target;
- optional leading icon geometry;
- optional supporting/current value;
- selected/check indicator;
- divider policy;
- generic accessibility structure.

Feature/application code owns labels, source/setting identity, selected domain value and callbacks.

Step 3 does not restore the V1 Glass/backdrop framework or a large modal/navigation framework merely to implement these sheets.

## 16.14 State/feedback primitives

Keep and evolve the small V2 primitives:

- Empty;
- Error;
- inline feedback;
- skeleton;
- pull-to-refresh.

The current shared pull-to-refresh English interaction/accessibility literals (for example Refresh/Refreshing) are migration debt: Step 3 localization must move them to localized component resources or caller-supplied localized labels.

Feature semantics remain caller-owned. Unsupported/Empty/Failure need not automatically become three Design System component classes.

## 16.15 Skeleton accessibility

Skeletons are visual decoration and must not become noisy accessibility nodes.

## 16.16 Artwork visual boundary

Design System may own:

- neutral artwork frame;
- deterministic fallback/monogram presentation.

It does not own:

- URL fetching;
- Catalog asset policy;
- Coil/image runtime;
- cache authority;
- provider security.

Network/image acquisition remains outside `:core:designsystem`.

## 16.17 Deterministic fallback

A deterministic fallback based on a caller-supplied stable visual key and title is allowed as pure presentation. It is useful for Library cache miss, Reading candidates without artwork and Catalog image failure.

The fallback key is not Story identity and is not derived from title as an authority.

## 16.18 Shared state restraint

Do not introduce:

- `GlobalUiState`;
- mandatory `RetainedContentState<T>` for all screens;
- generic all-purpose paging state;
- new `:core:presentation` module merely for a few small state facets.

Story tabs remain feature-owned unless another accepted surface proves the same tab visual/interaction policy. Metadata pills or other small visual helpers likewise remain feature-local until repeated evidence justifies promotion.

Extract state facets only after exact semantics repeat across real consumers.

---

# 17. Artwork and image behavior

## 17.1 Separate concepts

Step 3 keeps three concepts distinct:

```text
artwork identity/presentation
artwork visual frame/fallback
artwork byte acquisition/cache
```

## 17.2 Library Home must not activate full Catalog runtime

Home cover rendering may use bounded cache or a narrow safe image path. A cache miss must not bootstrap full Catalog capability/Discover acquisition merely to render a thumbnail.

Home reaches content Ready without waiting for network cover fetch. After local Library content is Ready, a **visible artwork cache miss may perform one bounded image-only request** through the shared artwork infrastructure when network is available. That request must not delay Home Ready, activate Catalog acquisition, initialize Reading Source runtime or become hidden recommendation/discovery work. Offline/cache-miss behavior degrades to the deterministic fallback.

## 17.3 Route state never owns decoded image bytes

Retained Story/Search/Listing routes store only lightweight artwork identity/presentation references. Bitmap/Drawable/decoded bytes remain image-cache concerns.

This prevents deep route history from retaining decoded image trees.

## 17.4 Provenance-aware Catalog artwork

Catalog artwork policy continues to resolve by the artwork/Story's provenance source key, never by whichever Catalog is currently the media default.

## 17.5 Process-shared bounded artwork infrastructure

Step 3 needs artwork on Catalog, Home/Library, Search/Listing/Similar and Reading candidate surfaces. The solution must not make each capability own an independent unbounded image runtime, and Home must not call `CatalogRuntime.activate()` merely to obtain image security policy.

The architecture therefore requires a process-shared bounded artwork infrastructure outside `:core:designsystem` and outside route-local Catalog runtime lifetime. Its semantic responsibilities are limited to image work:

- provenance-aware artwork request identity;
- cheap local lookup of source/authority artwork security policy;
- bounded encoded/disk and decoded/memory caching;
- bounded fetch/decode concurrency integrated with the process-wide expensive-work admission contract;
- cancellation when no request owner remains;
- in-flight coalescing/single-owner semantics for equivalent artwork work: concurrent consumers with the same validated artwork identity/security policy/transform **and equivalent request-varying headers/auth/cache scope** must not intentionally create duplicate remote fetch/decode pipelines;
- shared-request cancellation semantics where one consumer leaving does not cancel work still required by another consumer;
- deterministic fallback when remote artwork is absent/unavailable.

The exact coalescing mechanism may be delegated to a proven image runtime/library or implemented in Hikari infrastructure; the observable duplicate-work bound is normative.

Artwork policy/descriptors are control-plane data and must be readable without activating full Catalog payload acquisition. Registering a Catalog authority may register its immutable artwork policy/descriptive metadata without opening storage/network/provider execution.

This infrastructure must not become Story/Catalog truth, a general plugin host or a way to activate providers from Home. Exact module/class/cache sizes remain implementation-level.

---

# 18. Shared UX state, offline and failure semantics

## 18.1 Scoped state

Step 3 does not introduce a global offline/error mode for ordinary capability failures.

Each request/surface owns its relevant issue/activity state.

## 18.2 Retained content

If a new request fails while valid old content exists, old content remains visible wherever the feature semantics support it.

Examples:

- Discover refresh failure retains Discover;
- Search new-query failure may retain previous displayed query results with explicit identity labeling;
- Listing append failure retains loaded pages;
- Similar failure leaves Story metadata;
- Library write failure keeps last stable membership;
- mapping replacement failure keeps old committed binding.

## 18.3 Offline vs provider failure vs Empty

They are not interchangeable.

Empty means the requested semantic scope validly produced no items.

Provider/network failure is an operation failure.

Unsupported means the operation does not exist for that authority.

Use offline-specific copy only when Hikari has sufficient evidence that connectivity is unavailable; do not label every provider/network failure “offline”.

If retained usable content exists, connectivity loss must not blank it.

Reading Source Detect offline/failure preserves the draft source, previous committed binding and submitted query. Retry remains the same draft source + same submitted query and never falls back to another Reading Source.

No failure recovery may silently switch authority.

## 18.4 Lifecycle cancellation is not Failure

Cancellation/invalidation caused solely by a route moving from ACTIVE to RETAINED is lifecycle control, not provider failure, offline state or valid Empty.

Therefore:

- do not surface a provider/network Error merely because Hikari cancelled route-owned transient work on retention;
- preserve already materialized content and request/query identity according to that destination's route state;
- on reactivation, the destination follows its existing demand contract: explicit-submit Search/Detect is not silently replayed, while a destination whose visible initial content is itself the admitted foreground demand may reacquire according to its capability policy;
- a real transport/provider failure that occurred before lifecycle cancellation keeps its own captured Failure semantics.

This distinction prevents normal tab/root switching from producing false error banners while still allowing aggressive runtime quiescence.

## 18.5 Local operation scope

A local operation spinner/feedback remains local to the control or facet that initiated it. Add-to-Library does not blank Story; Theme save does not freeze the entire Settings screen.

---

# 19. Accessibility and responsive behavior

## 19.1 Touch targets

All interactive surfaces meet the app-wide minimum touch target, including small visual icons.

## 19.2 True disabled semantics

Disabled Read/Chapters/unsupported Similar controls are truly non-clickable, expose disabled semantics and perform zero hidden work. Do not simulate disabled state with alpha + no-op callbacks.

## 19.3 Poster semantics

Poster cards should present concise grouped semantics instead of reading image/title/supporting text as duplicate independent nodes.

Feature wrappers provide localized meaningful semantic labels; Design System does not invent domain text.

Ranked rows include rank in accessibility semantics when rank is real.

Decorative artwork/icons that duplicate visible semantics are excluded from redundant announcements.

## 19.4 Selection semantics

Top-level navigation, filter chips, tabs and single-choice rows expose selected state semantically.

## 19.5 Feedback, loading and long text

Important failure/operation feedback and Retry actions must be discoverable by accessibility services, while repeated retained-content/skeleton transitions should not generate noisy announcements.

Skeleton pseudo-items stay out of the accessibility tree. Loading state is represented semantically at an appropriate container/surface level instead of exposing dozens of placeholder nodes.

Shared rows/sheets/buttons use minimum heights, not fixed heights that clip longer translations or larger font scales.

Keyboard/focus behavior must remain predictable for Search/choice surfaces where applicable.

## 19.6 Responsive policy

Use one shared compact/wide policy rather than repeating `600.dp` and `20/32.dp` literals in multiple features.

Adaptive grid column behavior is shared visual policy; exact column formula/min cell size is frozen with screenshot/benchmark evidence in implementation.

---

# 20. Data-contract additions required by Step 3

The Step 3 audit identified data that is required by admitted UX and should not be left for implementation to invent.

## 20.1 Story route context

Add a lightweight Story route contract containing:

- `StorySourceRef`;
- origin media context;
- optional lightweight preview.

## 20.2 Story alternate titles

Preserve bounded provider aliases/alternate titles in Story detail for display/user-controlled Detect suggestions. Do not use them for automatic identity matching.

## 20.3 Catalog language tags

Preserve bounded normalized Catalog language tags instead of collapsing provider metadata into one ambiguous `String?`.

## 20.4 Section identity/capability

Preserve a stable section key/descriptor and truthful expansion/ranking capability. UI must not reconstruct listing identity from section copy alone.

## 20.5 Catalog authority descriptor

Provide cheap local capability/descriptive metadata so UI can know whether Search/Similar/expanded sections exist without activating the source.

## 20.6 Library artwork snapshot

Library persists only a durable-safe artwork presentation snapshot/reference suitable for local Home rendering. It must not blindly serialize ephemeral provider/runtime objects.

## 20.7 Reading Source candidate language metadata

Builtin mapping adapters must expose candidate-specific verified language tags without Chapter acquisition.

## 20.8 Reading candidate artwork

Artwork is optional. The UI must support a deterministic fallback.

## 20.9 Story Reading verified-language snapshot

Persist the candidate-verified language set with the committed binding so local language changes do not require Chapters/Detect.

## 20.10 Share payload

Use an explicit small payload:

```text
title
optional app-owned stable public link
```

No invented/implicit provider URL fallback.

## 20.11 Artwork security/control-plane descriptor

The shared artwork infrastructure needs cheap local policy data keyed by provenance source/authority. The descriptor must contain only security/presentation control-plane data required to validate an artwork request, conceptually including the source key and allowed remote host/origin policy.

It must not require an activated Catalog session, open provider runtime, Room handle or remote probe. Secrets/ephemeral runtime objects do not belong in this descriptor.

## 20.12 Optional Catalog-language preference contract

If and only if a builtin Catalog exposes configurable metadata/discovery language, Step 3 may add a narrow preference scoped to that media/authority. It is separate from stored Story language tags, App locale and Reading language.

A retained request/route keeps the language/request context with which it was created; changing the preference does not silently reinterpret already displayed results.

---

# 21. Performance and activation constitution

## 21.1 Work should scale with active semantic scope

Target relation:

```text
Home
  work ~ visible/local Library query

Discover
  work ~ one Catalog + bounded shelves

Search
  work ~ one authority + one submitted query + retained bounded pages

Listing
  work ~ one authority + one section + requested pages

Story
  work ~ one Story + point Library/binding observations

Similar
  work ~ one Story + one Catalog request when tab demanded

Detect
  work ~ one selected Reading Source + one submitted query

Settings
  work ~ one local preference subsection
```

No ordinary Step 3 operation should primarily scale with all Catalog history, all Library, all mappings, all providers or all retained roots.

## 21.2 Zero-work/zero-activation assertions

Step 3 does not add speculative next-page/Similar/Detect prefetch merely to improve perceived speed. New page/query work starts from explicit submit, append/viewport demand, tab activation or another admitted user demand. Any later prefetch policy requires separate evidence and bounded ownership.

Architecture acceptance must check behavior, not only frame time.

At minimum:

```text
Home-only cold journey
  Catalog remote acquisition = 0
  Reading Source Detect = 0
  Reader/Chapter = 0

Open Catalog Search before submit
  Search remote request = 0

Open Story before Similar tab
  Similar request = 0

Open Reading Source picker
  Detect/source-search request = 0

Construct inactive/unvisited root owner
  remote capability activation = 0
```

## 21.3 Collection N+1 guard

Home/Search/Listing/Similar/Detect collection payloads must be sufficient to render their cards without automatic per-item detail acquisition.

## 21.4 Bounded route retention

Retained result/page windows are bounded. Route state never holds decoded images or runtime sessions.

The design does not freeze an arbitrary numeric navigation-depth cap before benchmark evidence exists, but it **does** require both aggregate process-retained payload and aggregate route-entry/history metadata to remain bounded under §6.11/§6.11.1. Deep history first compacts reconstructible payload; extreme route-entry depth is then trimmed/collapsed by the admitted deterministic history policy rather than silently retaining unlimited route metadata.

## 21.5 Single-flight/latest-wins

- Discover retains accepted single-owner refresh/acquisition semantics.
- Search first/next-page request identity is generation/query/authority-safe.
- Listing has one append per continuation.
- Similar first load is route-local single-flight.
- Detect request identity includes Story + draft source + submitted query + generation/continuation.
- Library mutation serialization is per Story and owned by the Library capability across routes, not a route-local mutex and not a global Library mutex unless evidence requires otherwise.
- Story Reading binding/clear mutation serialization is likewise per Story provenance at the Reading Source owner across routes; durable user truth is not protected by route-local locks.

### 21.5.1 Pagination continuation/progress guard

Opaque continuation does not mean unlimited trust. Search, Section Listing and paged Detect (and Similar only if incremental Similar is explicitly admitted) must prevent provider continuation bugs from turning one route into an infinite request/work loop.

Normative rules:

- one explicit/viewport append demand starts at most one page request; completion never recursively chains another page without new admitted demand;
- `nextContinuation` must not equal the continuation just consumed;
- a continuation already consumed in the current bounded route window must not be followed again; cyclic/non-advancing continuation is a scoped protocol/provider failure or terminal condition, not another automatic request;
- consecutive pages that produce zero new valid identities after validation/exact-identity dedupe are bounded by a small implementation policy; Hikari must not walk an unbounded sequence of empty/duplicate pages searching for progress;
- pagination guards remain source-local identity checks and do not introduce title/alias reconciliation.

Exact recent-token/no-progress thresholds are implementation-level and must fit the route-retention budget.

## 21.6 Preserve Step 1/2 evidence and no-growth debt

Step 3 adds measurements and gates; it does not erase or relax accepted Step 1/2 debt/baselines.

Accepted Step 1/2 red performance debt is **no-growth debt**:

- Step 3 may leave an explicitly accepted red journey unresolved;
- Step 3 must not materially worsen that journey under the existing accepted measurement policy merely because new App Shell/Library/Reading/Design-System work was added;
- any material worsening requires root-cause evidence, explicit review and a newly accepted debt record rather than silently moving the baseline;
- improvements may retire debt only with equivalent-or-stronger evidence.

After the final Step 3 production graph/resources are assembled, baseline/startup-profile artifacts and performance journeys affected by the new graph must be regenerated/validated as required by the existing Step 1/2 performance policy. An old profile that no longer describes the final production graph is not valid evidence.

## 21.7 Cross-capability expensive-work admission

Per-capability single-flight is necessary but not sufficient. Catalog Search/Listing/Similar, Reading Source Detect and artwork fetch/decode can be independently correct yet collectively oversubscribe network/CPU/memory.

Step 3 therefore requires process-wide bounded admission for remote/decode/other expensive work. This is an infrastructure scheduling/budget primitive, **not** a global business-orchestration queue.

Normative behavior:

- every expensive owner remains cancellable and authority-scoped;
- capabilities do not silently create private unbounded executors/lanes that bypass the process budget;
- foreground user-command work must not be starved by artwork/background-like work;
- artwork fetch/decode and other noncritical work may be throttled/deferred under pressure;
- no speculative Step 3 prefetch consumes reserved foreground capacity;
- waiting/pending expensive work is itself bounded; a semaphore with an unbounded queue of suspended callers is not sufficient;
- route/request cancellation removes or invalidates queued work promptly so inactive histories cannot leave a growing backlog;
- noncritical artwork/decode work may be dropped/deferred and requested again by a future visible consumer rather than accumulating an unbounded queue;
- cancellation releases admission promptly;
- exact lane counts/permits/priorities/pending-queue limits are benchmark-driven implementation details.

Admission must also avoid starvation/priority inversion. Under sustained noncritical artwork/decode pressure, a new foreground Search/Listing/Story/Detect command must be able to obtain admission within the bounded scheduling policy instead of waiting behind an arbitrarily long queue of already queued noncritical work. New noncritical work yields under foreground pressure; already-admitted work remains cancellable according to its owner.

This rule intentionally solves cross-capability resource contention without recreating a V1-style global semantic coordinator.

## 21.8 Execution-owner and Main-thread contract

Every new Step 3 capability must carry forward the Step 1/2 capability-admission requirement to name its observer lifetime and CPU / blocking-I/O / network execution owner. This is part of architecture acceptance, not an optional implementation note.

Normative execution policy:

```text
Main/UI
  -> Compose-facing publication
  -> small bounded UI-state mutation/reduction

CPU work whose cost scales with a page/collection/result size
  -> Default/CPU execution owner
  -> validation, normalization, mapping, sorting, dedupe, large projection

blocking storage/file/network work
  -> I/O or owned async transport execution
```

No new Home/Search/Listing/Similar/Detect/Library path may intentionally run database I/O, provider/network I/O, large decode or collection-scale CPU transforms on Main. Small constant/bounded presentation mapping on Main is allowed when it is genuinely cheap and verified.

The implementation plan must include a capability-admission record (or equivalent explicit checklist) for Home/Library, Catalog Search, Section Listing, Similar, Reading Source Detect and shared artwork work, including:

- activation trigger;
- observer keys and ACTIVE/RETAINED/RELEASED lifetime;
- CPU owner;
- blocking I/O/network owner;
- single-flight/latest-wins identity;
- boundedness/scaling dimension;
- cancellation/invalidation behavior.

---

# 22. Verification and test contract

## 22.1 Architecture gates

Where practical, static architecture checks should enforce at least:

```text
:core:designsystem
  must not import Catalog/Library/Reading Source feature/domain/runtime types
  must not import Room/WorkManager/network/image-acquisition runtime

:app
  must not import storage implementation details

Catalog feature
  must not own top-level app navigation

Library feature
  must not import Catalog runtime/storage implementation

Reading Source feature
  must not import Catalog persistence implementation

Shared artwork infrastructure
  must not import Story/Library/Reading business state ownership
  must not activate full Catalog capability merely to resolve artwork policy
```

Step 1/2 build-policy rules that intentionally block `androidx.navigation` and production `INTERNET` are migration gates, not permanent reasons to bypass the desired Step 3 architecture. If the chosen Step 3 navigation technology or proven production remote transport requires changing those exact allowlists, the implementation plan must update the policy **explicitly and narrowly**, add replacement no-eager-activation/security assertions, and keep all unrelated forbidden boundaries intact.

Exact module paths depend on the final implementation topology.

## 22.2 Navigation tests

Must prove:

- Manga/Home/Light Novel histories are independent;
- root switching restores previous stack;
- reselect active root pops child stack to root without refresh;
- system Back unwinds only the current stack;
- Search → Story → Back restores Search state/scroll;
- Listing → Story → Back restores pages/scroll;
- Story A → Similar → Story B → Back restores Story A Similar tab/scroll/result;
- inactive root cannot start new remote work;
- switching a route/root to RETAINED cancels/invalidates route-owned transient Search/Listing/Similar/Detect work and does not leave a growing pending queue;
- late transient completion after retention/cancellation cannot re-inflate an inactive compacted route or publish into another route;
- lifecycle cancellation caused by RETAINED does not surface as provider/offline Failure;
- inactive roots/retained routes do not remain persistently fully composed/resumed in a way that keeps route side effects, image consumers or collectors active;
- construction does not equal capability activation;
- Step 1 `Unknown/FirstRun/Ready` handoff remains intact and Ready enters App Shell/Home only after the accepted first-frame gate;
- Settings/About opened from any root return to the exact origin context;
- deep retained histories obey both process-wide payload and route-entry/history bounds; payload compaction/history trimming does not start hidden inactive reload work or corrupt root/durable truth.

## 22.3 Process-recreation tests

Prove restoration of small identity/state:

- selected root;
- route identities;
- Story ref/origin media context;
- selected Story tab where safe;
- Home query/filter;
- Search input/submitted query identity.

Do not require full Search/Listing/Similar payloads to survive process death.

## 22.4 Library tests

Prove:

- point membership observation;
- idempotent Add/Remove;
- Home local query/filter;
- rapid local-query changes are latest-wins;
- default ordering is most-recently-saved first;
- true Empty vs filtered Empty;
- Catalog cache eviction does not remove Library truth;
- Catalog default changes do not remap Library provenance;
- user-truth migration is non-destructive;
- large-Library behavior does not regress into unbounded foreground materialization;
- Home Search aged-state evidence rejects per-keystroke unbounded full-history scan/sort behavior and records the chosen query-plan/scaling proof;
- semantically identical Library snapshot enrichment does not issue redundant writes/invalidation or mutate `savedAt`;
- repeated Add for an already-saved Story does not bump `savedAt`/reorder the Library or issue redundant membership writes;
- if a physical Room DB is shared, an aged Catalog-cache fixture does not turn Home-only DB open/migration/query into work that primarily scales with unrelated Catalog history;
- Home-only journey has zero Catalog remote activation; visible image-only cache-miss fetch, if admitted, remains isolated in artwork infrastructure and cannot delay local Home Ready.

## 22.5 Catalog Search/Listing tests

Prove:

- Search open issues zero request;
- first-entry focus/keyboard behavior issues no request by itself;
- typing issues zero request;
- clear edits input only and does not destroy retained displayed results;
- explicit submit only;
- stale completion cannot overwrite newer query;
- old displayed results remain correctly identified during new request/failure;
- continuation identity is frozen to route authority/query;
- exact duplicate Story identity guard is incremental;
- no automatic durable import of result pages;
- no per-card Story Detail N+1;
- Search becoming RETAINED cancels/invalidates its in-flight route-owned request without producing a provider Failure; returning to Search does not silently replay an explicit-submit query solely because navigation restored the route;
- Listing append becoming RETAINED is cancelled/invalidated and late completion cannot mutate compacted inactive pages;
- Search/Listing pagination rejects non-advancing/cyclic continuation and bounds consecutive zero-delta pages; one append demand cannot recursively chain unbounded page requests;
- when Catalog-language configuration exists, a preference change does not mutate retained Search/Listing request identity or Story/Library provenance.

## 22.6 Similar tests

When capability exists:

- no request before tab activation;
- one request on first activation;
- retained result on tab switch;
- retry same Story/authority;
- nested Story push restores parent Similar context;
- no automatic persistence of Similar corpus;
- pushing a child Story makes the parent Similar route RETAINED and cancels/invalidates route-owned in-flight Similar work; a racing late result cannot publish into the child or exceed the inactive retention budget.

When unsupported:

- disabled/unavailable semantics;
- no request;
- no fake Search fallback.

## 22.7 Reading Source tests

Prove:

- picker opens with zero remote Search;
- source selection creates draft only;
- explicit `Detect story` runs exactly one initial primary-title query against that draft source;
- subsequent typing runs zero request until explicit submit;
- one selected source per Detect request;
- no automatic alias fan-out;
- paged Detect append is same-source/query/continuation single-flight;
- paged Detect rejects non-advancing/cyclic continuation and bounds zero-delta continuation walking;
- Detect Empty vs Failure vs offline remain distinct;
- offline/failure preserves draft source, submitted query and old committed binding;
- candidate language metadata is candidate-specific and obtained without Chapters;
- old binding survives cancel/failure;
- source + sourceStory + compatible language replace atomically;
- same-Story binding/clear mutations are serialized at the Reading Source owner across routes, and an identical confirmed aggregate is a no-op rather than timestamp-only churn;
- unavailable committed/default source states do not silently change authority;
- Story-specific override does not mutate global default;
- default change does not rewrite Story binding;
- mapping commit triggers zero Chapter/Reader/background consumption work;
- Detect becoming RETAINED cancels/invalidates its route-owned search/append work without producing a provider/offline Failure; returning does not silently submit a new edited query without the explicit foreground demand required by the Detect contract;
- explicit clear/use-default removes the Story-specific binding without changing global defaults/Library/Catalog and triggers zero Step 4 work;
- transient Detect/network failure does not redefine a locally present source as control-plane unavailable.

## 22.8 Localization/accessibility tests

Targeted evidence must catch:

- localized labels and accessibility descriptions, including shared pull-to-refresh labels;
- long translated labels/font scale overflow;
- true disabled controls;
- selected state;
- skeleton exclusion from accessibility tree;
- minimum touch targets;
- modal-sheet Back/dismiss/focus behavior and no commit-on-dismiss for drafts;
- re-selecting an already committed Theme/App-language/default value performs no redundant persistence write or unnecessary recreation/reconfiguration.

Screenshot evidence should remain selective rather than multiplying the full width/theme/locale Cartesian product.

### 22.8.1 Network/artwork/resource-lifetime evidence

Targeted tests must prove:

- merged production manifest admits remote permission only through the intentional Step 3 policy update;
- startup/FirstRun/Home-local journey performs zero remote Catalog/Reading request despite the permission being present;
- shared Catalog retention on aged multi-authority fixtures uses bounded/indexed eviction-candidate work rather than full historical reaggregation per point acquisition;
- provider/API/artwork redirect/host/security bounds remain enforced;
- cross-origin redirect cannot leak sensitive/source-auth headers without explicit policy, and transport retry count is bounded;
- Home artwork cache hit/miss cannot activate full Catalog acquisition merely to resolve policy;
- closing/quiescing one logical Catalog session cannot close a shared physical database used by another semantic owner;
- if storage is physically shared, cold Home open/migration/query cost is measured against aged unrelated Catalog data and does not reintroduce historical-scan startup coupling;
- aggregate retained route payload compacts under a deterministic test budget;
- aggregate retained route-entry/history metadata remains bounded under an extreme nested-Story/root-switch stress fixture and saved-state reconstruction stays bounded;
- deep/repeated retained navigation does not create monotonic growth in route-scoped Library/StoryReading/Catalog collectors; retained routes quiesce and reactivation reconciles local truth without remote refetch;
- process-wide expensive-work admission bounds concurrent **and pending** work across at least Catalog request + Reading Detect + artwork fetch/decode;
- saturated artwork/decode pressure cannot starve a newly submitted foreground Search/Detect/Story command under the configured admission policy;
- equivalent concurrent artwork consumers coalesce to bounded same-key fetch/decode work and shared cancellation behaves correctly;
- cancellation releases resource admission and does not strand work/permits;
- execution-owner tests/tracing show no intentional DB/network I/O, large decode or collection-scale CPU mapping on Main.

## 22.9 Performance journeys

Add benchmark/perf evidence for at least:

```text
cold launch -> Home
Home -> Manga
Manga -> Home
Manga -> Search
Search -> Story -> Back
Listing -> Story -> Back
Story A -> Similar -> Story B -> Back
rapid root switching
deep Story route chain
large Library local query/filter
large-Library per-keystroke query-plan/slope proof
Reading Source Detect
production network-admission startup/first-run proof
deep retained-history payload compaction + route-entry bound + collector quiescence
concurrent Catalog + Detect + artwork pressure/fairness
final Step 3 startup/baseline-profile regeneration validation
```

Where measurable, record active runtimes/requests/collectors/admission occupancy, query counts/plans and execution-thread ownership in addition to frame time.

---

# 23. Evolution from Step 2 — do not rewrite the accepted core wholesale

## 23.1 Current seams that must evolve

Step 3 is expected to replace or reshape these Step 2 ownership assumptions:

- feature-owned global media navigation;
- single mutable Catalog route instead of independent stacks;
- one mutable current Story presentation owner;
- mutable selected media type inside one Discover owner;
- one global Catalog source binding/session assumption;
- Design System hardcoded English interaction labels;
- duplicated breakpoint/screen-inset/poster/icon geometry;
- the current Step 2 `Ready -> CatalogEntryPoint` handoff, which becomes `Ready -> AppShell(Home)` without replacing `StartupGate`;
- the current capability session/store ownership where closing a Catalog session can close the opened physical Catalog database; this must not be reused unchanged if Step 3 shares one physical database across semantic owners;
- the current artwork-policy path where obtaining policy can activate the Catalog runtime; Home/Library needs cheap local policy resolution instead;
- Step 1/2 static policy prohibitions on production `INTERNET` and `androidx.navigation`, which may be evolved only through an explicit Step 3 gate migration if those admitted capabilities/technology require it.

Targeted presentation cleanup follows ownership migration:

- the current feature-owned `CatalogMediaDestinationNav` is retired/replaced by app-shell-owned navigation using the shared destination-nav visual primitive; do not keep expanding the old component;
- production `StoryPreview*` wrappers that become real Step 3 behavior should be renamed/decomposed rather than retaining “preview” names while silently owning production actions/state.

## 23.2 Current seams that should be preserved/evolved

Unless a concrete contradiction appears, preserve the proven behavior of:

- `StorySourceRef` source-local identity;
- Discover durable projection and retained refresh behavior;
- keyed Story durable observation/acquisition;
- bounded Catalog validation/import behavior for Discover/Story;
- single-owner acquisition mechanics;
- image/artwork source provenance and security boundary;
- bounded retention and active Story protection, upgraded for multi-authority safety.

## 23.3 Migration strategy principle

Do not perform a giant rewrite of `CatalogComposition` + runtime + storage + UI in one task.

The implementation plan should stage ownership migration, establish App Shell/route scoping first, then add new capabilities on the corrected ownership foundation.

In particular:

```text
route-scoped Story ownership
  BEFORE
Similar nested Story navigation
```

and:

```text
App Shell top-level ownership
  BEFORE
expanding the old Catalog navigation component
```

## 23.4 V1 reuse rule

Reusable after focused audit:

- pure provider parsing;
- HTTP mechanics;
- bounded validation utilities;
- deterministic normalization/value helpers;
- cancellation/request-identity mechanics.

Do not wrap/reuse wholesale:

- V1 multi-provider Catalog Search/reconciliation;
- canonical fusion/redirect machinery;
- V1 `ContentMappingService` orchestration;
- auto-link/scoring/rejection history;
- background mapping worker;
- V1 plugin control plane;
- broad `observeAll` repository APIs;
- Reader source/candidate routing engine for Step 3 mapping.

---

# 24. Explicit non-goals and forbidden paths

Step 3 also deliberately does **not** include notifications, account/cloud sync, update-policy product work, advanced diagnostics/developer settings, background Chapter sync, Reader settings, Reader cache-quota/tuning product work, progress/sync controls, full V1 database migration, explicit downloads/offline-management lifecycle, theme editor/advanced theme customization or generic plugin/source marketplace management.

The following are forbidden Step 3 architecture paths, even if they appear to make implementation shorter:

```text
Home -> CatalogRuntime.activate()

Home Library membership -> Catalog cache flag

Story membership -> observeAll Library entries

Search -> observe all Catalog stories

Search/Listing/Similar page -> CatalogImporter -> permanent history by default

Search/Listing/Similar/Home cards -> per-item Story Detail enrichment

Catalog point materialization/eviction -> full historical Catalog rescan/reaggregation

Detect -> enabledSources().fanOut()

Detect -> automatic alias query variants

Detect -> manual URL mapping path

Reading mapping -> AUTO_LINK / confidence engine

StoryReadingBinding commit -> ChapterSync / Reader / WorkManager

Catalog default change -> Library provenance rewrite

Reading Source default change -> committed Story binding rewrite

Design System -> Catalog image/runtime/network authority

App Shell -> storage implementation orchestration

Unsupported capability -> fake Empty/Error + Retry

Provider URL -> implicit Share fallback

Home artwork -> CatalogRuntime.activate() for policy lookup

shared physical DB -> Home cold open/migration scans unrelated aged Catalog history

Catalog session close -> close shared physical DB still used by Library/Reading/another authority

per-route bounded state * unbounded route count -> unbounded process retained payload

cheap route identity * unbounded navigation depth -> unbounded route metadata / oversized saved state

retained route depth -> one live Library/binding/domain collector per historical route

RETAINED Search/Listing/Similar/Detect routes -> continue route-owned transient remote work or accumulate queued work

late transient completion -> repopulate a compacted inactive route

inactive roots/history -> keep all full Compose destination trees resumed/composed indefinitely

Home default collection or Search typing -> unbounded full-history materialize/filter/sort or unproven full historical DB scan/sort per visible-window/query change

collection-scale validation/sort/dedupe/projection -> Main thread

semantically unchanged Library snapshot / repeated Add -> redundant Room write, `savedAt` churn or Library reorder

identical StoryReadingBinding confirmation -> timestamp-only durable rewrite / observer churn

two routes for same Story -> independent route-local binding mutation locks

equivalent same-key artwork consumers -> duplicate remote fetch/decode pipelines

Catalog + Detect + artwork -> independent unbounded executors or one bounded-concurrency primitive with an unbounded pending queue

pagination continuation -> same/cyclic cursor or zero-delta pages trigger automatic unbounded next-page walking

INTERNET permission -> startup/eager network activation

Catalog default change -> hot-swap authority inside retained Search/Listing/Story result chain
```

---

# 25. Acceptance criteria for Step 3 freeze

Step 3 is architecturally complete only when all of the following are true.

## 25.1 App Shell

- Home is the default root.
- Manga/Home/Light Novel have independent history/restoration.
- top-level navigation ownership is outside Catalog feature.
- reselect and Back semantics match this spec.
- inactive/unvisited roots do not create hidden remote work;
- Step 1 `Unknown -> FirstRun -> Ready` remains intact and only the Ready destination handoff changes to App Shell/Home;
- Settings/About are one app-level focused destination family and return to their exact origin root context.

## 25.2 Home/Library

- Home is usable from local durable Library truth only.
- Add/Remove works from Story.
- Library survives Catalog cache failure/eviction.
- local Search/filter works without Catalog.
- aged-state Library behavior is bounded and benchmarked; per-keystroke physical query work has explicit query-plan/slope evidence rather than only latest-wins correctness.
- semantically unchanged Library snapshot enrichment does not create deliberate no-op invalidation churn or reorder `savedAt`; repeated Add is idempotent without timestamp churn.

## 25.3 Catalog

- each shipped media root has a proven production Catalog authority;
- Discover retains accepted Step 2 semantics;
- Catalog Search is explicit-submit and transient by default;
- Listing exists only for real expandable sections;
- ranked listing is truthful;
- Listing → Story → Back restores retained pages/scroll and Listing → Discover restores Discover context without navigation-caused refetch;
- Similar is real-capability-gated, lazy and transient;
- no canonical fusion/provider fan-out is reintroduced;
- production remote transport is intentionally admitted with HTTPS/origin/redirect/size/timeout/cancellation validation and zero-network startup evidence;
- changing a configurable default Catalog cannot hot-swap authority under retained route/request chains.

## 25.4 Story

- progressive Story open works from all origins;
- Hero Back is the only overlay action, remains legible across artwork, respects safe insets/touch semantics, and no mirrored filler action is invented;
- Add to Library / Share / More are functional;
- Favorite heart is removed;
- Synopsis and Read More/Read Less are functional;
- Chapters/Read are true disabled Step 4 affordances;
- Similar is bounded/lazy/capability-gated with no fake refresh/fallback;
- Story More preserves effective/default/unavailable Reading Source and Language semantics without confusing defaults with committed binding;
- nested Story navigation restores parent Story context;
- Catalog, Library and Reading facets fail independently.

## 25.5 Reading Source

- builtin descriptors/picker work without remote probing and expose only truthful local icon/name/language/current/default/unavailable metadata;
- Detect searches one selected source/one submitted query;
- Detect distinguishes Empty/Failure/offline and paged append failure without losing the committed binding;
- candidate-specific verified language metadata exists without Chapters;
- confirmation atomically replaces one Story binding;
- point-scoped Story binding observation is sufficient; no broad mapping corpus observation is required for Story UX;
- defaults do not rewrite existing bindings;
- binding commit triggers zero Step 4 work;
- mapped Story can explicitly clear its Story-specific binding and return to default/unmapped semantics without mutating global defaults;
- `Available/Unavailable` state is based on truthful local capability presence, not an implicit live-health probe.

## 25.6 Settings/localization

- Theme and App language work through one committed preference authority each;
- active Step 3 production copy/accessibility labels are localizable;
- Settings remains local/lightweight and does not activate unrelated capabilities;
- Settings IA stays `General / Reading(Catalog Sources, Reading Sources) / About` and can be extended by Wave 11 without root redesign;
- deferred Reader/Chapter/progress/download/background/notification/account/update/diagnostic/theme-editor/plugin-lifecycle Settings remain outside Step 3;
- no generic plugin lifecycle/management UI is present;
- Catalog-language configuration exists only when a proven builtin Catalog exposes it and remains separate from App/Reading language.

## 25.7 Design System

- shared layout/touch/poster/nav/search/sheet primitives cover repeated Step 3 visual policy;
- poster skeleton geometry follows the shared poster-card/frame geometry;
- feature semantic models remain separate;
- Design System remains free of domain/runtime/image-acquisition authority;
- Step 2 visual migration is screenshot-reviewed where extraction touches accepted surfaces;
- shared artwork infrastructure is bounded, provenance/security aware, and Home policy lookup does not activate full Catalog runtime.

## 25.8 Performance and architecture

- Step 1/2 baselines and accepted debt remain visible; accepted red debt is frozen as no-growth debt and any material worsening requires explicit new review/debt;
- final Step 3 production graph/profile artifacts are regenerated/validated where required by the inherited performance policy;
- new Step 3 journeys have evidence;
- zero-work/activation assertions pass;
- broad/global historical regressions are absent;
- shared Catalog retention/eviction work remains batch-bounded and does not scale each point acquisition with total historical Catalog size;
- architecture boundary checks are green or any grandfathered debt is explicitly recorded with no-growth protection;
- aggregate retained payload and retained route-entry/history metadata are process-bounded under deep history;
- retained route-scoped collector cardinality does not grow monotonically with navigation depth;
- inactive roots/retained routes do not keep persistent full compositions/side-effect owners alive solely for restoration;
- paginated Search/Listing/Detect cannot enter continuation cycles or unbounded zero-progress page walking;
- route-owned transient expensive work is cancelled/invalidated on RETAINED and late completions cannot repopulate inactive compacted routes;
- process-wide expensive-work pending backlog is bounded as well as active concurrency;
- shared physical persistence lifetime cannot be accidentally closed by one logical capability owner, and physical sharing does not make Home-only startup scale with unrelated aged Catalog history;
- CPU/blocking-I/O/network execution owners are explicit and collection-scale work does not intentionally run on Main;
- cross-capability expensive-work admission bounds Catalog/Reading/artwork pressure, proves foreground fairness and does not become a semantic global coordinator;
- equivalent same-key artwork work is coalesced/bounded rather than multiplied by concurrent consumers.

---

# 25A. Decision-traceability status

A companion traceability audit maps:

1. the 106 final R0.14 acceptance decisions; and
2. the additional architecture/data decisions produced by the V1/V2 deep audit and Sections 1–11 discussion

into one of:

```text
PRESERVED
SUPERSEDED / CLARIFIED
INTENTIONALLY IMPLEMENTATION-LEVEL
DEFERRED BY MILESTONE
```

No decision may disappear merely because R1 rewrites the document structure.

R1.5's companion audit is:

```text
2026-09-13-hikari-v2-step-3-R1.5-decision-traceability-final-audit.md
```

R0.14 contradictions intentionally resolved by R1.4 include:

- unconditional Similar functionality -> real-capability-gated Similar;
- generic `Manage Reading Sources` CTA -> no dead management UI in builtin-only Step 3;
- one semantic Story-poster UI model across Catalog/Home -> shared visual primitive with separate semantic models;
- plugin enable/disable or generic plugin-management wording -> Wave 11;
- mandatory universal retained-content state -> extract only when semantics genuinely match;
- physical Library/Reading databases -> semantic persistence ownership is required; physical database topology is implementation-level.

---

# 26. Final Step 3 design summary

Step 3 completes Hikari's base discovery/collection application without re-admitting the V1 whole-app graph.

The final architecture is intentionally asymmetric:

```text
App Shell
  owns navigation/history/chrome

Catalog
  owns Catalog-local discovery identity/content

Library
  owns durable user collection truth

Reading Source
  owns one explicit Story mapping

Settings
  owns small typed defaults/preferences

Design System
  owns reusable visual policy only
```

Durability is intentionally selective:

```text
Discover + Story
  bounded durable Catalog material

Library + StoryReadingBinding + Settings
  durable user/configuration truth

Search + Listing + Similar + Detect
  bounded route/session results by default
```

Runtime behavior is intentionally stricter than route retention:

```text
web-like history/context restoration
+
Android-bounded active work
```

The primary V1 lesson carried forward is:

> A bounded user action must not scale primarily with unrelated global or historical state.

The primary Step 3 implementation rule is:

> Retain user context aggressively; retain active capability work only while semantic demand exists.

R1.4 added the repository-realizability corollaries:

> Permission is not activation. Logical-store lifetime is not automatically physical-database lifetime. Per-route payload bounds are not sufficient without a process-wide bound.

R1.5 hardens the physical-work/lifetime side further:

> Retained payload, retained route metadata, and live route-scoped collectors are three separate bounded resources. Latest-wins correctness is not a substitute for bounded physical query work. Heavy CPU/I/O execution ownership is explicit. Accepted red debt cannot silently grow.

And the resource-governance rule remains:

> Independent capability owners remain semantically independent while sharing bounded, fair process-level admission for expensive work.

Step 4 can then add Chapters/Reader/Progress on top of explicit Story Reading bindings and a stable App Shell instead of reopening the architecture problems that V2 was created to remove.
