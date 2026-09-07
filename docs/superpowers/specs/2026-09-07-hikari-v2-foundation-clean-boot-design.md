# Hikari V2 Foundation + Clean Boot — Step 1 Design (Red-Team Revised)

**Date:** 2026-09-07  
**Status:** RED-TEAM REVISED — proposed V2 canonical foundation, awaiting user approval  
**Scope:** V2 Step 1 only — process start, first frame, launch-state resolution, first-run shell, returning-home shell, startup measurement, and architectural guards  
**Source snapshot:** `Hikari-perf-whole-app-big-update-v3.zip`  
**Source archive SHA-256:** `c4107742c1c06848aa48fc4e494192d4b9166fe5d364b4cb131c2b3dca2b5b5c`  
**Supersedes:** the V1 whole-app performance Big Update only for the V2 app-shell/startup direction; V1 audit/design documents remain historical evidence and are not execution instructions for V2

> Hikari V2 starts from one rule: process creation is not permission to initialize the product. The app shell renders first; only the capability the user actually enters may activate afterward.

---

## 1. Decision

Hikari V2 will be built as a clean app shell on a V2 branch, not as a long-lived `:app-v2` beside the V1 application.

Git history preserves V1 source, but Git history alone is not sufficient to preserve hard-won correctness/security knowledge. Before V1 runtime source is retired from the active V2 tree, Step 1 creates a **V1 salvage ledger** that classifies product invariants and reusable assets as `KEEP`, `REDESIGN`, or `DROP`, with the future owning capability named explicitly.

Step 1 has two gates:

1. **Foundation cutover gate** — retention/quarantine decisions, salvage ledger, V2 performance constitution, structural ratchets, and clean-install identity isolation.
2. **Clean boot gate** — the minimal production-shaped Android shell, launch-state resolution, startup guards, and baseline measurement.

Step 1 does **not** transplant Reader integration, Catalog runtime, plugins runtime, storage, downloads, background work, or the old Home implementation into the new app shell. It establishes both the boot contract **and the admission rules** every later V2 capability must satisfy.

---

## 2. Why Step 1 exists

The V1 problem is not merely that individual startup functions are slow. Startup is an architectural activation point for unrelated domains.

### 2.1 Current `Application` behavior

`app/src/main/kotlin/app/openstory/OpenStoryApplication.kt` injects application-lifetime collaborators for:

- automatic cache policy;
- background policy;
- WorkManager notification recovery;
- Reader asset image-loader installation;
- Reader asset security invalidation.

`Application.onCreate()` then starts or invokes all of them.

This means process creation has semantic meaning across cache, background, notification, plugin/Reader security, storage, and scheduling domains.

### 2.2 Current `MainActivity` behavior

`app/src/main/kotlin/app/openstory/MainActivity.kt` injects the canonical WorkManager scheduler and notification intent parser. A `LaunchedEffect` deliberately waits for one frame and then schedules canonical drain and daily safety work.

Moving unrelated work to “after the first frame” improves one timing metric but does not fix ownership. A normal foreground launch still wakes maintenance machinery.

### 2.3 Current app dependency graph

The V1 `:app` directly depends on nearly the entire product graph:

- `:catalog`, `:catalog:model`, `:catalog:engine`;
- `:library`;
- `:chapters`;
- `:reader`;
- `:downloads`;
- `:settings`;
- `:storage:room`, `:storage:files`;
- `:plugins:api`, `:plugins:runtime`;
- all current feature modules;
- Room, WorkManager, OkHttp, Coil, DataStore, Navigation, Hilt and other framework dependencies.

This makes the application composition root a place where adding a product feature can silently enlarge process-start cost.

### 2.4 Current Home is not a valid V2 bootstrap destination implementation

`feature/catalog/.../HomeDashboardViewModel.kt` observes or queries Library, Catalog projection, Reading Progress, Chapters, Mappings, Downloads, and Reader source availability.

Reader source availability calls into the plugin-backed Reader source registry, which asks the plugin runtime for enabled `CONTENT_CHAPTER` providers.

Therefore the V1 Home is an aggregate product dashboard. It is not a lightweight boot surface and must not be transplanted into Step 1.

---

## 3. Goal

Create the smallest production-shaped Hikari V2 shell that proves the following lifecycle:

```text
PROCESS CREATED
      |
      v
APPLICATION
      |
      | zero domain activation
      v
MAIN ACTIVITY
      |
      | render stable shell
      v
FIRST FRAME
      |
      v
RESOLVE INSTALL LAUNCH STATE
      |
      +-------------------+
      |                   |
      v                   v
FIRST RUN              RETURNING
      |                   |
      v                   v
FIRST-RUN SHELL         HOME SHELL
```

The first frame is allowed to know only how to draw Hikari's minimal shell. It is not allowed to know whether Catalog, Reader, Downloads, plugins, Room, or background maintenance exist.

---

## 4. Success criteria

Step 1 is successful only when all of the following are true.

### 4.1 Architectural

1. `Application.onCreate()` enters no product domain.
2. `MainActivity` enters no product domain.
3. `:app` has no production dependency on V1 runtime/integration modules.
4. The startup package cannot import Room, WorkManager, plugin runtime, Reader runtime, Catalog runtime, Downloads, Chapters, Library, network clients, image loaders, or V1 settings.
5. Accepted-transplant and quarantine/reference modules remain independently buildable/testable where useful, but none becomes a startup-shell dependency merely because it is retained.
6. No generic global “startup manager”, “performance manager”, or “service locator” is introduced.

### 4.2 Runtime

Before the first destination (`FirstRun` or `Home`) is resolved:

- no Room database is opened;
- no WorkManager scheduling occurs;
- no plugin package is provisioned or inspected;
- no plugin runtime is created;
- no Reader runtime/cache/security observer is created;
- no Catalog refresh or projection is started;
- no Downloads/Chapter/Library work is started;
- no network request is made;
- no notification recovery/maintenance work is scheduled;
- no application-wide domain `Flow` collector is started;
- no filesystem scan or reconciliation is run.

### 4.3 UX

- a stable Hikari surface appears on the first frame;
- there is no blank white/black intermediary owned by application content;
- initial launch-state resolution does not produce `loading -> empty -> content` flicker;
- initial routing has no navigation animation;
- first-run and returning launches have deterministic destinations;
- process death followed by relaunch resolves the same persisted install state correctly.

### 4.4 Measurement

- V2 has a cold fresh-install startup benchmark;
- V2 has a cold returning-launch startup benchmark;
- the blank-shell baseline is recorded before adding the first real capability;
- startup traces identify at least process/application/activity/content/first-frame/state-resolved/destination-ready milestones;
- future capability work can compare startup against this baseline.

---

## 5. Non-goals

Step 1 deliberately does **not**:

- migrate the V1 Room database;
- preserve V1 runtime compatibility;
- rebuild Discover;
- rebuild the real Home dashboard;
- rebuild Library;
- rebuild Search;
- rebuild Story details;
- integrate Reader runtime;
- integrate Downloads;
- install or provision plugins;
- schedule background work;
- implement notification delivery;
- implement deep-link routing into product domains;
- create the final onboarding UX;
- create the final design system;
- decide the final V2 DI framework;
- optimize the retained pure algorithms;
- redesign Catalog/Reader engine policies.

A Step 1 implementation that starts rebuilding any of these is out of scope.

---

## 6. V2 repository cutover

### 6.1 No long-lived `:app-v2`

V2 is a branch-level product cutover.

The repository must not maintain two application shells as peers for an extended period because that would create duplicate build logic, duplicate manifests, duplicate DI, duplicate navigation, and compatibility pressure.

### 6.2 Retention tiers — do not call every pure module “accepted V2 core”

The whole-app audit distinguishes clean module boundaries from clean runtime complexity. A module can be pure JVM and still contain algorithms that V2 must not canonize unchanged.

**Tier A — accepted transplant candidates**

- `:reader:engine` — the whole-app audit found no new independent performance defect in the pure Reader engine; retain source/tests and re-admit through Reader-specific correctness checks later;
- `:plugins:api` — retain the protocol/contract surface; no independent performance root cause was confirmed in this module;
- narrow `:core:common` primitives required by retained contracts, reviewed symbol-by-symbol rather than carrying the module by inertia.

**Tier B — quarantine/reference candidates, not accepted V2 engines yet**

- `:catalog:engine` — clean JVM boundary, but current production behavior participates directly in confirmed A1/A2/A3 and the A4 scaling risk; it must pass a Catalog Engine Admission Gate before becoming canonical V2 implementation;
- `:catalog:model` — useful source contracts, but current broad metadata/read-model shape participates in X3 data-width/allocation amplification; V2 may reuse narrow domain types while redesigning read models.

**Tier C — do not transplant implementation**

- V1 Android orchestration/runtime, repositories, feature ViewModels, background/cache/plugin runtime integration, and current Home aggregation implementation.

Retention means “available as reference or candidate and continuously verifiable where useful,” not “approved architecture” and never “initialized by the application.”

### 6.3 Removed from the active Step 1 graph

The V1 Android integration/runtime modules are not dependencies of the V2 app shell. The V2 branch should remove them from the active Gradle project graph and, after the cutover commit makes the retained set explicit, remove obsolete source rather than leaving a second dormant architecture in-tree.

This includes the current V1 implementations of:

- `:catalog` orchestration;
- `:library`;
- `:chapters`;
- `:reader` integration/runtime;
- `:downloads`;
- `:settings`;
- `:storage:room`;
- `:storage:files`;
- `:plugins:runtime`;
- current feature modules;
- current app DI/work/notification/cache integration.

These capabilities can later be rebuilt around retained or redesigned engines/contracts. Git remains the source for old implementation reference, while the Step-1 salvage ledger remains the source for **which invariants must not be forgotten**.

### 6.4 Active Step 1 graph

Target active graph:

```text
                    +--> :catalog:model --> :catalog:engine
                    |
:core:common -------+--> :reader:engine

:plugins:api        (independent retained contract)

:app                (new V2 shell; no dependency on the retained engines yet)
  ^
  |
:benchmark
```

The exact retained `:core:common` surface may be narrowed during cutover, but algorithm behavior must not change merely to make the cutover prettier.

---

## 7. V2 boot invariants

These are constitutional rules for all later V2 work.

### BOOT-01 — Process creation has no domain semantics

Creating the Android process does not mean “refresh”, “recover”, “scan”, “reconcile”, “provision”, or “resume background policy”.

### BOOT-02 — First frame is never gated by install-state I/O

The first application-owned frame must be renderable without awaiting persistent launch-state I/O. The tiny launch-state read may start concurrently with shell rendering on an appropriate dispatcher; Step 1 must not artificially serialize `first frame -> start disk read`. If the read resolves early, the destination may be ready for the first frame. If not, `Unknown` remains a valid stable shell.

### BOOT-03 — Foreground launch is not a maintenance trigger

No later V2 feature may attach unrelated durable/background maintenance to normal activity creation merely by postponing it until after the first frame.

### BOOT-04 — Capability activation follows demand

A capability may initialize when a destination or user action requires it. It may not initialize because the process exists.

### BOOT-05 — The app shell cannot know every capability

The startup/app-shell dependency boundary must remain small enough that adding a capability cannot silently add its runtime graph to process startup.

### BOOT-06 — Unknown is a renderable state

Launch state `Unknown` is not a reason to hold the splash screen or block first content. It renders the same stable base surface that the first destination will inherit.

### BOOT-07 — No hidden startup by manifest

Merged-manifest providers/initializers count as startup work even if no Kotlin code explicitly calls them. Step 1 verifies the merged manifest and dependency graph for unintended auto-initializers.

### BOOT-08 — Performance is a regression contract

A feature that increases cold-start cost must make that cost visible in benchmark comparison. Startup cost may not grow invisibly with feature count.

### V2 performance constitution — admission rules derived from the Big Update audit

The Big Update audit found 33 confirmed structural root-cause families that collapse into eight themes. V2 does **not** implement all fixes in Step 1, but it freezes the rules now so later features cannot recreate the same architecture under new filenames.

#### PERF-01 — Working-set scope
A foreground/user-local operation scales primarily with the requested working set, not unrelated historical/global state. Global maintenance must be explicit, separately owned, and budgeted.

#### PERF-02 — Narrow read/allocation ownership
Consumers receive the smallest representation needed for their semantics. Large encoded payloads and expensive derived representations have one clear owner; defensive full copies and eager unrelated fingerprints are not the default.

#### PERF-03 — Reactive scope matches semantic demand
Every observer declares its semantic key/cardinality, invalidation source, and lifetime. Table-wide invalidation or application lifetime is not accepted merely because it is convenient.

#### PERF-04 — Batch work has batch semantics
One logical batch uses bulk/delta APIs and must not repeatedly re-read or reprocess the already-handled prefix through point APIs.

#### PERF-05 — One execution owner per expensive work item
Foreground, durable recovery, worker, retry, and refresh paths may not race as independent owners of the same logical item. Critical sections protect state transitions, not long I/O. Capacity/priority is explicit for visible work.

#### PERF-06 — Lifetime/aging is bounded
Persistent history, process maps, memoization, caches, retry state, and suppression state require retention/reachability/eviction contracts. “Works on a fresh install” is not sufficient evidence.

#### PERF-07 — Control plane does not imply payload execution
Metadata/policy questions must not load executable/package payloads by default. Stable terminal failures must not amplify into repeated expensive work. Security-sensitive request construction consumes bounded point policy and coherent credential state.

#### PERF-08 — Performance evidence is part of the interface
Every real capability admission records the relevant scaling dimensions, query/work counts where deterministic, destination latency, startup delta, and aged-state dimensions once that capability owns persistent/history state.

---

## 8. Startup state model

The public state model is intentionally small:

```kotlin
sealed interface AppLaunchState {
    data object Unknown : AppLaunchState
    data object FirstRun : AppLaunchState
    data object Ready : AppLaunchState
}
```

This is a routing state, not a product/session state machine.

### 8.1 Persisted fact

Step 1 persists one install-level fact:

```text
initial_setup_completed = true | false/absent
```

Absence means `FirstRun`. `true` means `Ready`.

The flag represents completion of the initial installation bootstrap only. Future feature migrations must not repurpose this flag to force returning users through startup again.

### 8.2 Store ownership

A tiny `AppLaunchStateStore` lives inside the V2 startup boundary. It does not reuse the V1 `:settings` module.

A preferences DataStore is acceptable because it provides asynchronous durable persistence without requiring Room. Its use must remain scoped to the launch-state fact rather than becoming a new generic settings store by accident.

### 8.3 Read timing

The store is never awaited as a prerequisite for `setContent()` or the first application-owned frame.

The launch-state read may begin concurrently with shell rendering. The design optimizes for both properties at once:

- no first-frame dependency on disk I/O;
- no unnecessary `first frame -> then start read` serialization.

`Unknown` exists only for the interval in which state is genuinely unresolved. This path must not become a generic bootstrap task queue.

---

## 9. Startup UI and routing

### 9.1 `Unknown`

`Unknown` renders a stable neutral Hikari surface:

- app background;
- minimal branding or static identity if desired;
- no progress spinner unless measurement proves state resolution is perceptibly long;
- no empty-state copy;
- no real Home content placeholders that imply data is loading.

The surface must visually match the base background of both initial destinations so resolution does not flash between unrelated layouts.

### 9.2 `FirstRun`

Step 1 uses a minimal first-run shell, not the final onboarding product.

It only needs to prove:

1. first install resolves here;
2. a deliberate user action can mark initial setup complete;
3. completion transitions to `Ready`;
4. relaunch returns to Home.

No plugin provisioning, network fetch, notification permission, library import, or database creation occurs in this shell.

### 9.3 `Ready`

Step 1 Home is a shell, not `HomeDashboardViewModel` transplanted from V1.

It may contain static chrome/text needed to prove routing and rendering. It has zero repository/domain dependencies.

### 9.4 Initial transition

`Unknown -> FirstRun` and `Unknown -> Ready` are state resolution, not user navigation. They do not animate as a navigation transition.

User-driven navigation introduced later may animate independently.

---

## 9A. Clean-install identity and restore semantics

Step 1 currently assumes a clean V2 install. That assumption must be enforced rather than left to developer habit.

1. V2 development/benchmark installation must be isolated from an existing V1 data directory by a dedicated development application identity **or** by deterministic uninstall/clear-data setup owned by the test/build workflow.
2. `initial_setup_completed` is not silently restored from V1 or cloud backup unless a future migration design explicitly chooses that behavior. Backup/data-extraction rules must classify the launch-state file deliberately.
3. Installing a production V2 package over V1 with the same application ID is **not** part of Step 1. That becomes a separate migration/release gate.
4. Old V1 databases/files may not be interpreted implicitly merely because they remain in the package data directory.

---

## 10. Android entry points

### 10.1 `Application`

Target behavior:

```text
Application.onCreate()
    -> platform/base initialization only
    -> no product-domain collaborator invocation
```

Step 1 should not use `@HiltAndroidApp` merely because V1 used Hilt. Introducing Hilt or another DI framework is deferred until a real capability requires it and its startup cost/boundary can be measured.

### 10.2 `MainActivity`

Responsibilities are limited to:

- platform window configuration;
- `setContent()`;
- handing Android launch metadata to a narrow shell boundary if required.

It must not inject or call:

- WorkManager schedulers;
- notification recovery;
- Catalog/Reader/Library/Downloads repositories;
- plugin runtime;
- Room;
- network clients.

### 10.3 External intents

Step 1 does not support product-domain deep links/notification routing.

The shell must not reintroduce the V1 `NotificationIntentParser` dependency simply to preserve an unimplemented feature. Domain launch requests are added only with the owning V2 capability and must pass the same boot constraints.

---

## 11. Dependency policy for `:app`

The new `:app` production dependencies should start with only what Step 1 needs, such as:

- Android Activity/Core;
- Compose runtime/UI/Material primitives needed for the shell;
- lifecycle primitives if concretely needed;
- preferences DataStore for the one launch-state fact;
- benchmark/profile support only where required by build configuration.

Step 1 must not directly depend on:

- Room;
- WorkManager;
- OkHttp;
- Coil/network image loaders;
- `:catalog` runtime;
- `:library`;
- `:chapters`;
- `:reader` runtime;
- `:downloads`;
- `:settings`;
- `:storage:*`;
- `:plugins:runtime`;
- old feature modules;
- the retained pure engines merely “for future use”.

The dependency rule is enforced, not documented only.

---

## 12. Architectural guards

Step 1 adds a dedicated boot-boundary verifier integrated into normal architecture verification.

At minimum it must fail if production files under the startup/application shell import or reference forbidden packages/frameworks.

### 12.1 Startup boundary forbidden references

Forbidden from the startup package and Android entry points:

```text
androidx.room.*
androidx.work.*
okhttp3.*
coil.*
app.openstory.catalog.* runtime/orchestration
app.openstory.library.*
app.openstory.chapters.*
app.openstory.reader.* runtime/integration
app.openstory.downloads.*
app.openstory.plugins.runtime.*
app.openstory.storage.*
V1 settings/background/cache/work/notification packages
```

Pure Compose/Activity/Lifecycle/DataStore imports are allowed only where needed by Step 1.

### 12.2 App Gradle guard

Architecture verification also checks the `:app` project dependency allowlist. Source-import guards alone are insufficient because manifest initializers and transitive dependencies can execute without an explicit Kotlin import.

### 12.3 Manifest guard

The merged manifest for production/benchmark variants is inspected for startup providers and initializers. Any initializer must be explicitly classified as:

- required platform/shell initialization; or
- forbidden/deferred capability initialization.

Unclassified auto-initializers fail the Step 1 gate.

### 12.4 V2 structural ratchet

The structural-simplification audit showed that a clean Gradle DAG can still hide package cycles, test-only production APIs, temporary allowlists, dead surfaces, and oversized authorities. V2 starts with a zero/near-zero debt budget instead of inheriting V1 suppressions.

For code newly introduced in V2:

- no new same-module package SCC is accepted without an explicit durable reason;
- no `forTest`/`createForTest` production API exists solely for tests;
- no `temporary`, `pending extraction`, or `v1` structural allowlist entry is introduced;
- architecture/hygiene checks fail closed when an expected target disappears;
- the app shell cannot introduce a generic manager/registry with broad public surface;
- structural candidate counts are treated as a ratchet/trend, not a report that may grow indefinitely.

These are anti-regression rules, not reasons to split cohesive files merely to satisfy LOC aesthetics.

---

## 13. Benchmark contract

V2 retains the existing `:benchmark` module concept but discards V1 feature scenarios until those features return.

### 13.1 Required scenarios

#### `coldFreshInstall`

Setup:

- clear app package data;
- ensure no `initial_setup_completed` fact exists;
- force-stop/press Home as required by Macrobenchmark.

Measure:

- cold start into the first-run destination.

#### `coldReturningLaunch`

Setup:

- establish `initial_setup_completed=true` through a benchmark-only deterministic fixture path;
- force-stop the target process;
- do not pre-open product capabilities.

Measure:

- cold start into the Home shell.

### 13.2 Fixture rule

Benchmark setup may use benchmark-only components/source sets to establish launch-state persistence. Production startup code must not gain benchmark switches or intent flags that alter boot semantics.

### 13.3 Metrics

At minimum:

- `StartupTimingMetric` for both cold-start scenarios;
- trace sections or equivalent markers for:
  - application created;
  - activity created;
  - content requested;
  - first frame boundary;
  - launch state resolved;
  - destination ready.

The first implementation records a baseline rather than inventing an arbitrary absolute millisecond gate without device evidence.

### 13.4 Regression policy

After Step 1, each capability-integration phase compares against the accepted V2 baseline on the same benchmark environment. Material regression requires explanation and ownership; it cannot be hidden by moving work after first frame.

Once a capability owns persistent/history state, its admission benchmark adds an **aged-state** dimension relevant to that capability. Once a capability retains process-memory state, its admission adds a repeated-activation/lifetime check. Step 1 does not fabricate aged datasets before those semantics exist; it freezes the protocol that they become mandatory when introduced.

---

## 14. Verification strategy

### 14.1 Unit tests

`AppLaunchStateStore` / resolver coverage includes:

- absent flag -> `FirstRun`;
- completed flag -> `Ready`;
- completion writes durable state;
- read/write failure has an explicit safe policy;
- cancellation is not converted to a normal state.

### 14.2 UI/instrumentation tests

At minimum:

- first install reaches FirstRun;
- initial setup completion reaches Home;
- returning launch reaches Home;
- `Unknown` does not expose an incorrect empty-state screen;
- initial state resolution does not animate as navigation;
- process recreation resolves from persisted state.

### 14.3 Architecture tests

- forbidden startup imports/references;
- `:app` Gradle dependency allowlist;
- retained engine module dependency invariants;
- no reverse dependency from retained engines into the app;
- merged-manifest initializer audit.

### 14.4 Build/test preservation

The retained modules' existing focused tests remain green during V2 cutover:

- `:catalog:model` tests;
- `:catalog:engine` tests;
- `:reader:engine` tests;
- `:plugins:api` tests;
- narrow `:core:common` tests required by the retained graph.

This distinguishes “rebuild the shell” from “silently change the preserved engines.”

---

## 15. Failure semantics

### 15.1 Launch-state read failure

A corrupted/unreadable launch-state fact must not crash startup or incorrectly assume a fully configured installation.

The conservative Step 1 behavior is:

```text
read failure -> FirstRun-safe shell + non-sensitive diagnostic signal
```

The UI may expose a retry/reset action later if real-world evidence requires it. Step 1 does not build a generalized recovery framework.

### 15.2 Launch-state write failure

If completion cannot be persisted, the process may show Home for the current in-memory session only if the write result is clearly represented, but a relaunch must not falsely claim durable completion.

The preferred Step 1 behavior is to keep the user on FirstRun with a small retryable failure state until persistence succeeds. This avoids a confusing Home -> FirstRun regression on the next launch.

### 15.3 Cancellation

Coroutine cancellation propagates. It is not classified as a storage failure.

---

## 16. Performance ownership model after Step 1

Step 1 creates the rule later phases must follow:

```text
App shell
   |
   +-- knows destination intent
   |
   +-- asks owning capability to activate
           |
           +-- capability constructs only its runtime graph
           +-- capability owns its background/refresh policy
           +-- capability owns its storage/network costs
```

The app shell never becomes a registry that eagerly constructs every capability.

Before any real domain is admitted after Step 1, its design must fill a **Capability Admission Contract**:

1. activation trigger and owner;
2. deactivation/quiescence rule;
3. production dependency graph added to `:app`;
4. foreground working-set/cardinality contract;
5. observer keys, invalidation scope, and lifetime;
6. CPU/IO/network execution owner and concurrency/resource policy;
7. durable/background work owner and foreground-vs-worker exclusivity;
8. retention/aging/eviction rules;
9. failure/retry/terminal-state ownership;
10. benchmark/scaling delta against the accepted baseline.

This is a design checklist, **not** a generic `CapabilityManager` framework.

---

## 17. Step 1 cutover sequence at design level

This is ordering, not the detailed implementation plan.

1. Freeze the V1 source snapshot in Git history/tag/branch.
2. Create the V2 cutover branch.
3. Create the V1 salvage ledger from existing invariant/security/correctness docs before deleting integration source.
4. Classify retained modules into accepted transplant / quarantine-reference / reject implementation tiers.
5. Freeze PERF-01..08, the Capability Admission Contract, and the V2 structural ratchet.
6. Establish clean-install data/application-identity isolation for V2 development and benchmarks.
7. Reduce the active Gradle graph to the explicitly retained/quarantined candidates, new `:app`, and `:benchmark`.
8. Remove V1 runtime/integration modules from active V2 execution rather than keeping `:app-v2` beside them.
9. Build the empty V2 Android shell.
10. Add boot dependency/import/manifest/structural guards.
11. Add `AppLaunchState`, its tiny store, and non-blocking concurrent resolution.
12. Add static FirstRun and Home shells plus durable completion transition.
13. Replace V1 feature macrobenchmarks with fresh-install and returning-launch startup baselines.
14. Run merged-manifest audit, clean-install isolation checks, and retained/quarantined module regression verification.
15. Freeze the accepted V2 Foundation + Boot Contract before any real feature is allowed into `:app`.

---

## 18. Acceptance gates

Step 1 cannot close unless every gate is satisfied.

### Gate 0 — Knowledge/retention

- [ ] V1 salvage ledger exists before integration source retirement.
- [ ] each preserved module is classified as accepted transplant or quarantine/reference.
- [ ] `:catalog:engine` is not labeled accepted V2 core until A1/A2/A3 and A4 admission evidence is handled.
- [ ] clean-install/upgrade/backup semantics are explicit.

### Gate A — Graph

- [ ] `:app` has only Step 1 dependencies.
- [ ] no V1 runtime/integration module is reachable from `:app`.
- [ ] retained engines/contracts build independently.

### Gate B — Entry points

- [ ] `Application` invokes no domain code.
- [ ] `MainActivity` invokes no domain code.
- [ ] no application-lifetime domain collector exists.

### Gate C — Hidden initialization

- [ ] merged manifest has no unclassified domain initializer/provider.
- [ ] no WorkManager/Room/plugin runtime startup is reachable before destination resolution.

### Gate D — State/UX

- [ ] first install deterministically reaches FirstRun.
- [ ] completion is durable.
- [ ] returning launch deterministically reaches Home.
- [ ] `Unknown` renders a stable shell with no false empty state.
- [ ] initial resolution is non-animated.

### Gate E — Measurement

- [ ] cold fresh-install baseline captured.
- [ ] cold returning baseline captured.
- [ ] trace milestones visible.
- [ ] baseline artifact/checkpoint recorded for future comparisons.

### Gate F — Regression

- [ ] quarantined Catalog engine/model reference tests remain green until their later admission/redesign decision.
- [ ] retained Reader engine tests green.
- [ ] retained plugin API tests green.
- [ ] startup unit/UI/architecture tests green.

### Gate G — V2 constitution

- [ ] PERF-01..08 are normative V2 admission rules.
- [ ] Capability Admission Contract is required before Step 2 introduces a real domain.
- [ ] V2 structural ratchet starts without inherited temporary/v1 debt exemptions.

---

## 19. What Step 2 is allowed to be

Step 2 is **not predetermined as “port Home.”**

After Step 1 passes, the next design chooses the first real user capability and defines its activation boundary against the Boot Contract. The likely sequence remains:

```text
Boot foundation
    -> first useful Home/Discover capability
    -> Story
    -> Reader
    -> Library/Search/Downloads/background as justified
```

But Step 1 must not prebuild abstractions for those later phases. It only requires their designs to pass the Capability Admission Contract. If Catalog is selected first, `:catalog:engine` must pass its quarantine/admission review rather than being transplanted automatically.

---

## 20. Self-review ledger

The following issues were identified while reviewing this design against the current repository and resolved in the spec.

### SR-01 — “Use DataStore” could accidentally pull the V1 Settings graph into boot

**Resolution:** launch-state persistence is owned by a tiny V2 startup store; `:settings` is explicitly forbidden from Step 1.

### SR-02 — Resolving persisted state before first content would recreate a splash/blocking startup

**Resolution:** the stable `Unknown` shell renders first; launch-state I/O occurs only after the initial frame can be drawn.

### SR-03 — “After first frame” could become the same dumping ground used by V1

**Resolution:** launch-state resolution is the only shell-owned bootstrap operation. Its tiny read may overlap rendering, but it is never a generic post-first-frame task queue; BOOT-03 rejects unrelated maintenance scheduling on activity creation.

### SR-04 — A boolean could be reused later as an upgrade/migration gate

**Resolution:** `initial_setup_completed` has immutable install-bootstrap semantics. Future migrations require separate ownership and may not redefine this fact.

### SR-05 — Keeping all V1 modules in `settings.gradle.kts` would preserve architectural gravity/dead code

**Resolution:** V2 performs an active-graph cutover and then removes obsolete source from the V2 branch after the retained set is explicit. Git, not dormant modules, preserves V1.

### SR-06 — Deleting V1 integration could accidentally delete valuable pure engines

**Resolution:** the retained graph is explicit: `:catalog:model`, `:catalog:engine`, `:reader:engine`, `:plugins:api`, and required `:core:common` remain continuously verified.

### SR-07 — Carrying Hilt into Step 1 would establish an eager app-wide composition root before its need is known

**Resolution:** Step 1 does not require Hilt. DI framework choice is deferred until a real capability can be measured against the boot baseline.

### SR-08 — Source import checks alone cannot detect AndroidX Startup/provider initialization

**Resolution:** architecture verification includes Gradle dependency and merged-manifest audits.

### SR-09 — Reusing the old Home would immediately reactivate most product domains

**Resolution:** Step 1 Home is static and repository-free. The old `HomeDashboardViewModel` is not a migration target.

### SR-10 — Fresh-install benchmark setup could contaminate production startup with benchmark flags

**Resolution:** fixture state is established through benchmark-only source sets/components; production startup semantics are not conditional on benchmark extras.

### SR-11 — Treating launch-state read failure as Ready could bypass required first setup

**Resolution:** read failure falls back conservatively to FirstRun-safe behavior.

### SR-12 — Marking setup complete in memory before durable persistence could cause Home on one run and FirstRun on the next

**Resolution:** completion is accepted only after durable write succeeds; persistence failure remains retryable in FirstRun.

### SR-13 — Preserved engine modules are not perfectly dependency-minimal (`:core:common` currently includes coroutines)

**Resolution:** Step 1 preserves behavior first. Narrowing `:core:common` is allowed only as a behavior-preserving cutover task if required; it is not a reason to rewrite the engines during boot work.

### SR-14 — Removing all old capability modules at once is a large source diff

**Resolution:** the cutover is intentionally one architectural reset commit/phase with retained modules listed explicitly. This cost replaces the higher ongoing cost of maintaining two app architectures and repeated compatibility adapters.

### SR-15 — A “startup manager” could emerge from the new state resolver

**Resolution:** the startup boundary owns only install launch state and destination selection. It has no registry of domains, no task list, and no general initialization API.


### SR-16 — Pure module boundary was mistaken for performance-clean implementation

**Finding:** `:catalog:engine` has a clean JVM boundary, but A1/A2/A3 are confirmed engine-level/data-scope defects and A4 is an admitted scaling risk. `:catalog:model` also participates in X3 width/allocation amplification.

**Resolution:** retention is tiered. Reader engine/plugin API are accepted candidates; Catalog engine/model are quarantined/reference candidates until a later admission gate.

### SR-17 — Git history preserves code but not an actionable invariant map

**Finding:** deleting V1 integration before classifying security/correctness/product invariants risks rebuilding a clean but behaviorally naive V2. Existing docs already contain important KEEP/CHANGE/DELETE decisions and Reader/plugin security contracts.

**Resolution:** create a V1 salvage ledger before source retirement. Preserve contracts/knowledge, not runtime implementation.

### SR-18 — Boot-only rules do not prevent the eight whole-app root-cause themes from returning

**Finding:** a fast startup can still evolve into the same global-data, reactive-scope, N+1/batch, execution-owner, aging, and plugin-control-plane problems found in the 33-family audit.

**Resolution:** Step 1 now freezes PERF-01..08 and the Capability Admission Contract. No generic manager is implemented.

### SR-19 — Post-first-frame launch-state I/O was unnecessarily serialized

**Finding:** the previous wording required first frame before starting the tiny state read. That protects startup but can lengthen destination-ready latency and cause a neutral-shell frame even when state could resolve concurrently.

**Resolution:** the frame may not **wait for** the read, but the read may begin concurrently.

### SR-20 — “Clean install” was an assumption, not an enforced environment

**Finding:** keeping the same Android application identity can leave V1 app data present during an upgrade/development install; backup/restore can also change first-run semantics if rules later broaden.

**Resolution:** Step 1 explicitly isolates V2 development/benchmark data and defers same-application-ID V1→V2 upgrade to a separate migration/release gate.

### SR-21 — Startup guards were narrower than the repository's structural-debt failure modes

**Finding:** the structural audit found package SCCs, test-only production APIs, temporary/v1 allowlists, dead surfaces, and reporting-only debt checks. A new shell can recreate these even while BOOT rules remain green.

**Resolution:** V2 starts with a structural ratchet rather than inheriting V1 suppressions.

### SR-22 — A future feature could be activated on demand yet still own global/unbounded work incorrectly

**Finding:** demand-triggered activation alone does not guarantee bounded work, coherent observer lifetime, single execution ownership, or bounded aging.

**Resolution:** each capability must declare scope, lifecycle, resource, durable-work, retention, failure, and benchmark contracts before admission.

---

## 21. Final V2 Step 1 contract

The entire phase can be summarized as:

```text
PROCESS
  |
  |  no domain work
  v
APPLICATION
  |
  |  no domain work
  v
ACTIVITY + COMPOSE SHELL
  |
  v
FIRST FRAME
  |
  |  only now resolve tiny persisted install state
  v
STARTUP GATE
  |
  +--> FIRST RUN SHELL
  |
  +--> HOME SHELL

No Room
No WorkManager
No PluginRuntime
No Reader runtime
No Catalog runtime
No Downloads
No background maintenance
No network
```

Every later V2 feature must integrate **after** this boundary rather than expanding it, and must pass PERF-01..08 plus the Capability Admission Contract. Step 1 therefore remains deliberately small in product functionality while being broad enough in architectural safeguards to avoid rebuilding V1 debt.
