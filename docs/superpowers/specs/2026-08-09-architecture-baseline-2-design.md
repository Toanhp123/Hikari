# Architecture Baseline 2 Design

Date: 2026-08-09  
Status: Design approved in discussion; pending written-spec review  
Execution position: Pre-Wave-06 architecture reset

## Goal

Rebuild the Hikari/OpenStory architecture before Wave 06 so future Library, chapter
aggregation, reader, storage, synchronization, and plugin work is built on explicit
ownership boundaries rather than on the implementation shape accumulated through Waves
01-05.

This baseline keeps the high-level product goal but treats every existing internal
model, module, Kotlin API, Room schema, plugin contract, package format, runtime
abstraction, UI composition pattern, test fixture, and quality gate as replaceable when
it does not survive architectural review.

The target is not full Clean Architecture. The target is a pragmatic Android
architecture for a small pre-MVP application: feature-first organization, UI and data
as the default layers, repositories as the normal state boundary, orchestration objects
only where behavior is genuinely multi-step, and hard isolation only where the trust or
platform boundary justifies it.

## Context and Motivation

Wave 05 is functionally accepted, but the accepted implementation also exposes several
structural problems that would become more expensive after Wave 06:

- feature modules know multiple concrete subsystems at once;
- persistence and plugin/runtime responsibilities are not cleanly separated;
- composition has accumulated in a hand-built application graph despite Hilt already
  being present;
- large files often combine orchestration, mapping, state, and presentation concerns;
- line-count and Detekt suppression can make a change appear compliant without improving
  cohesion;
- the plugin API mixes wire contracts with host-side Kotlin abstractions and host-owned
  models;
- declarative Selector and JavaScript runtimes duplicate a large amount of execution,
  validation, mapping, fixture, and contract-parity surface;
- roadmap-oriented domain models were introduced before the capability that owns them
  actually existed.

Because the application is still pre-MVP and no public plugin ecosystem or user-data
compatibility promise exists, this is the lowest-cost point to correct those decisions
without adding permanent compatibility debt.

## Scope

Architecture Baseline 2 may replace or remove:

- the Gradle module graph;
- package layout and source ownership;
- Kotlin interfaces and internal APIs;
- repository hierarchy;
- domain/application abstractions;
- Room schema and entities;
- Hilt bindings and manual composition;
- Navigation wiring;
- Plugin API, manifest, package layout, Selector schema, JavaScript bridge, and runtime;
- bundled plugin implementations;
- matching and catalog orchestration;
- Home, Search, and Story presentation internals;
- fixtures and tests that protect obsolete implementation details;
- Detekt configuration, suppression policy, architecture checks, and verification
  scripts;
- stale documentation that describes the superseded active architecture.

## Non-Goals

This baseline does not implement Wave 06 product behavior. In particular, it does not
add Library persistence, content-source matching for Library, chapter synchronization,
reader behavior, downloads, background work, authentication, notifications, or release
hardening. Existing Wave 01-05 behavior is preserved only when its product requirement
or invariant is explicitly revalidated for Architecture Baseline 2; prior checkpoint
acceptance alone does not make behavior normative.

It also does not change the high-level product direction into a different product.
Hikari remains an Android-native Kotlin application intended to combine catalog
metadata and readable sources through host-controlled community plugins.

## Normative Decisions

### DECISION-AB2-001 Product Goals, Not Existing Domain Models, Are the Baseline

The baseline preserves high-level product goals only:

- Android-native Kotlin application;
- local-first behavior;
- catalog metadata and readable-source capabilities;
- community plugin support through a host-controlled security boundary;
- host-owned identity and multi-source aggregation where product behavior requires it.

No current domain model or invariant is retained solely because a previous Wave defined
it. Each model must have a current owner and a current use. During R0, existing Wave
01-05 behaviors/invariants are explicitly classified as `KEEP`, `CHANGE`, or `DELETE`
with a short product rationale. Previous checkpoint acceptance is evidence of historical
intent, not proof that an invariant belongs in Baseline 2.

### DECISION-AB2-002 Pre-MVP Compatibility Is Intentionally Broken When Needed

No compatibility shim is required for development-only internal APIs, Room schemas,
plugin contracts, Selector definitions, JavaScript bridges, `.osp` package layouts,
bundled plugin fixtures, or emulator data.

Compatibility code such as `Legacy*`, `Compat*`, `V1/V2` adapters, dual mappers, or
migration facades must not be introduced merely to keep superseded development
contracts alive.

After Architecture Baseline 2 is accepted, public-facing compatibility policy may be
introduced deliberately before a real external plugin ecosystem is promised.

### DECISION-AB2-003 Android/Kotlin Is Fixed; Libraries Are Not

The implementation remains Android-native and Kotlin-based. Room, Hilt, Navigation 3,
AndroidX JavaScriptEngine, OkHttp, Jsoup, and other libraries are retained only if they
continue to fit the target architecture. A library is an implementation choice, not an
architectural invariant.

### DECISION-AB2-004 Pragmatic Android Architecture Replaces Full Clean Architecture

The default flow is:

```text
UI / ViewModel
      |
      v
Repository or focused service
      |
      v
Local or remote/plugin data source
```

A separate use-case/domain layer is not mandatory. ViewModels may call repositories
directly for simple observation and state changes. A focused service/application object
is introduced only when an operation coordinates multiple responsibilities or policies.

Examples that justify a focused service include catalog refresh, multi-source search,
detail enrichment, story matching, plugin installation, chapter aggregation, and later
synchronization. A forwarding `GetXUseCase` that merely calls one repository method is
not added.

Interfaces are introduced for real architectural seams, replaceable implementations,
or test boundaries, not by convention for every class.

### DECISION-AB2-005 Package First, Gradle Module Second

Packages are the default organization mechanism. A Gradle module is created only when
compile-time isolation has material value, such as a security/trust boundary,
platform-specific adapter boundary, public protocol surface, or independently coherent
capability.

Module count is not a quality target.

## Target Production Module Graph

The initial target is intentionally small:

```text
:app
:core:common
:catalog
:feature:catalog
:storage:room
:plugins:api
:plugins:runtime
```

`test:fixtures` or other test-only modules may remain or be rebuilt as test
infrastructure and are not part of the production architecture count.

### `:app`

Owns:

- Android application/activity entry points;
- Navigation 3 graph and top-level destinations;
- Hilt application composition and cross-module bindings;
- Android-only bootstrap required to start the product.

Does not own:

- catalog matching;
- refresh/search/details algorithms;
- Room transactions;
- plugin execution logic;
- ViewModel factories or service-locator behavior.

`:app` may depend on all production modules because it is the executable composition
root.

### `:core:common`

Owns only tiny, genuinely cross-cutting primitives such as safe result/error types,
clock abstractions, stable cross-capability identifiers or validation primitives, and
narrowly justified coroutine/platform helpers. `StoryId` belongs here because Library,
Reader, and later capabilities must be able to reference a story without depending on
the catalog implementation that currently owns the `Story` model.

It must not become a dumping ground for feature models or generic `Utils` classes.

### `:catalog`

Owns the catalog capability:

- the `Story` model and catalog meaning of story identity;
- catalog entry/source/home models;
- catalog repository contracts;
- local-source contracts needed by repository implementations;
- catalog refresh/search/details services;
- matching, canonicalization, merge, and ranking policies;
- plugin-to-catalog normalization adapters where appropriate.

It contains no Compose UI and exposes no Room entities. `CatalogRepository` is a
contract owned by `:catalog`; a Room-backed implementation such as
`RoomCatalogRepository` belongs to `:storage:room`. The contract describes durable
catalog state and semantic commits, not storage technology.

### `:feature:catalog`

Owns the current Home, Search, and Story presentation surfaces:

- Compose screens/components;
- ViewModels;
- UI state and transient presentation models;
- presentation mapping where a dedicated UI shape is useful.

It depends on `:catalog`, not directly on Room, plugin runtime internals, JavaScript,
network implementations, or DAOs.

Home, Search, and Story remain packages within one feature module until independent
compile-time isolation is justified.

### `:storage:room`

Owns Room and nothing above Room:

- database configuration;
- schema;
- entities;
- DAOs;
- transactions;
- Room-backed implementations of capability-owned persistence ports.

Room types never cross the module boundary. The module may depend on capability modules
only to implement their storage contracts. For plugin runtime state specifically,
`:storage:room` may import only the runtime's explicit persistence/SPI contract package;
it must not import execution, install, registry implementation, capability, network,
JavaScript, or diagnostics internals. It must not make matching decisions, execute
plugins, perform network requests, or own feature behavior.

### `:plugins:api`

Owns the public plugin wire/package protocol only:

- manifest schema;
- protocol versioning;
- catalog/content request and response DTOs;
- capability declarations;
- package/repository metadata needed for portable plugins.

It is pure Kotlin/JVM serialization code and does not depend on Android, Room, Compose,
`core:model`, application `AppResult`, or plugin runtime implementation.

### `:plugins:runtime`

Owns the trust/security boundary:

- package verification and installation;
- immutable package storage and registry lifecycle;
- JavaScript isolation and operation execution;
- capability broker;
- allowlisted HTTP capability;
- host-controlled HTML parsing/query capability;
- bounded safe logging;
- runtime budgets, cancellation, diagnostics, and security policy;
- persistence ports needed for runtime state, implemented elsewhere when appropriate.

The module exposes a deliberately small host facade. Consumers must not import its
internal execution/capability packages directly.

## Dependency Direction

The canonical dependency direction is:

```text
:feature:catalog ---> :catalog

:catalog -----------> :plugins:runtime ---> :plugins:api

:storage:room ------> :catalog
:storage:room ------> :plugins:runtime      # persistence/SPI contracts only

:app ---------------> all modules required for composition

all modules --------> :core:common only when a primitive is genuinely shared
```

This is intentionally pragmatic rather than a textbook dependency-rule pyramid.
`:plugins:runtime` is an isolated security subsystem with a small public facade; catalog
may depend on that facade without knowing JavaScriptEngine, HTTP clients, package files,
or capability internals.

Architecture verification must forbid, at minimum:

```text
feature -> Room APIs or storage internals
feature -> plugin runtime internals
storage -> feature
storage -> plugin execution/install/registry/capability/network/JavaScript internals
plugins:api -> Android or host application models
plugins:runtime -> feature
catalog -> Compose
```

Adding a new dependency edge to an allowlist solely to make verification pass is not an
acceptable fix.

## Package and File Ownership

Production code is organized feature-first and responsibility-first.

Representative `:catalog` layout:

```text
catalog/
├── model/
├── repository/
├── refresh/
├── search/
├── details/
└── matching/
```

Representative `:feature:catalog` layout:

```text
catalog/
├── home/
│   ├── HomeScreen.kt
│   ├── HomeViewModel.kt
│   ├── HomeUiState.kt
│   └── components/
├── search/
└── story/
```

Representative plugin runtime layout:

```text
plugins/runtime/
├── install/
├── registry/
├── execution/
├── capabilities/
│   ├── http/
│   ├── html/
│   └── log/
├── security/
└── diagnostics/
```

Files are split when responsibilities are independent, not when a numeric line threshold
is reached. Names such as `Utils`, `Helpers`, `Misc`, `Manager`, `Coordinator`, or
`Part1/Part2` require a concrete architectural reason and are not accepted as generic
containers for displaced logic.

A file/class must have one explainable owner responsibility. If its responsibility
statement naturally contains unrelated "and" clauses, it is a refactor candidate.

A Compose component is extracted because it has an independent UI responsibility,
state/behavior, reuse value, semantic boundary, or testable identity, not merely to lower
`Screen.kt` line count.

## Catalog Data Flow and Repository Boundary

### Local Read Path

```text
Room
  -> Room-backed catalog persistence adapter
  -> CatalogRepository
  -> Home/Story ViewModel
  -> UI state
  -> Compose
```

Persistent UI is driven by the local source of truth.

### Home Refresh Path

```text
HomeViewModel.refresh()
  -> CatalogRefreshService
  -> CatalogSource / PluginCatalogSource
  -> PluginRuntime
  -> validated plugin protocol payload
  -> catalog normalization
  -> matching / merge policy
  -> CatalogRepository.commitRefresh(...)
  -> one source-scoped Room transaction
  -> repository Flow emits new state
```

The UI does not receive plugin DTOs and then manually mutate persistent state.

`CatalogRepository` owns durable/local catalog state only. It does not execute plugins,
perform HTTP, run matching/ranking policy, navigate, format UI text, or expose DAOs or
raw transaction handles. Multi-step orchestration belongs to focused objects such as
`CatalogRefreshService`, `CatalogSearchService`, and `CatalogDetailsService`.

### Search Path

Search is allowed to remain transient where persistence has no product value:

```text
SearchViewModel
  -> CatalogSearchService
  -> enabled catalog sources
  -> normalize
  -> match / dedupe / rank
  -> search result
```

Only metadata that has a defined durable purpose is persisted. Search results do not
implicitly become Home membership.

### Story Detail Path

Story UI observes cached canonical/source metadata from the repository. A focused
details service may fetch and enrich source metadata, then persist a validated merge.
Navigation does not need to carry plugin/source identity for a canonical-story screen.

### Failure Semantics

A failed refresh for one source keeps the previous complete snapshot for that source.
Other sources remain usable. A source-scoped successful refresh commits atomically.
Partial database replacement that clears the old snapshot before new data is complete is
not allowed.

A plugin failure during multi-source search or refresh is isolated to that plugin unless
a true global failure prevents the operation from continuing.

### Matching Ownership

Canonical matching is pure catalog behavior. Matching receives catalog models and emits
an explicit decision/mutation plan. Room persists the decision; Room does not decide
whether two source records represent the same story.

## Plugin Subsystem VNext

### Single Execution Model

Architecture Baseline 2 adopts JavaScript as the only plugin execution runtime for the
MVP.

The current declarative Selector runtime, selector binding language, selector-specific
mappers, endpoint execution pipeline, and dual-runtime parity surface are removed.

HTML extraction remains available through bounded host capabilities exposed to
JavaScript. If a declarative authoring format becomes valuable later, it may be added as
an authoring/compiler layer that targets the single runtime contract rather than as a
second peer execution model.

### Protocol Instead of Host Kotlin Interfaces

`:plugins:api` no longer exposes host-side `CatalogPlugin` or `ContentPlugin` Kotlin
interfaces tied to application `AppResult` or host models. Plugins speak a versioned
serialized protocol.

Representative operations include:

```text
catalog.home
catalog.search
catalog.details

content.search
content.story
content.chapters
content.chapter
```

Host capability modules adapt those wire operations into application-facing source
interfaces such as `CatalogSource`. Error ownership also changes at each boundary:
wire/plugin failures become bounded `PluginCallResult` failures, the catalog adapter
translates those into catalog/source failures, and presentation translates only those
application failures it needs into UI state. A single host-wide `AppError` type does not
leak through every layer.

### JavaScript Capabilities

A plugin script may use only explicitly declared, host-controlled capabilities such as:

```text
host.http(...)
host.html.query(...)
host.log(...)
```

It never receives Android `Context`, Room, filesystem paths, raw OkHttp clients, Java or
Kotlin reflection, arbitrary Android APIs, or plaintext managed credentials.

`host.html` is backed by a host parser such as Jsoup and applies document, selector,
result-count, and execution budgets.

### Runtime Invocation

A plugin invocation follows this lifecycle:

```text
resolve installed immutable version
  -> validate operation/input
  -> create isolated JavaScript execution
  -> execute through capability broker
  -> validate output size/schema
  -> return bounded PluginCallResult
  -> destroy/discard isolate
```

Timeout, cancellation, malformed bridge data, or script failure affects only that plugin
operation.

### Network Capability

The current plugin-specific network subsystem becomes part of `:plugins:runtime` rather
than remaining a generic `:core:network` module unless a non-plugin application network
capability later justifies such a module.

Network policy includes HTTPS-only declared hosts, redirect revalidation, bounded request
and response sizes, rate/request budgets, cancellation, redacted logging, and exact-host
session/credential scoping.

### Managed Credentials

Plugins do not receive secret values. Host policy may add managed headers, cookies, or
other credentials based on `(pluginId, host, request policy)` after validating the
request.

The MyAnimeList Client ID is therefore a host-managed request concern, not plugin script
data.

### Package Integrity

A package does not contain the checksum of its own complete archive bytes in a
self-referential manifest field. Exact package SHA-256 and signatures belong to detached
repository/install provenance metadata.

Installer verification hashes the exact downloaded/imported bytes before archive
extraction or trust decisions based on untrusted package content.

### Reference Plugin

The production bundled plugin set is rebuilt around one canonical MyAnimeList JavaScript
catalog plugin that passes the same protocol, package, capability, and fixture tests
expected of future community plugins.

Default/selector/JavaScript demonstration packages remain only as test fixtures when they
serve a specific contract test. They are not production architectures that must be
maintained in parity.

## Model and Persistence Reset

### Capability-Owned Models

The repository no longer uses a roadmap-wide `core:model` as the mandatory home for all
future product concepts.

Before Wave 06, only currently owned models are retained:

- tiny stable/common identity primitives where genuinely shared;
- catalog-owned models needed by product behavior explicitly revalidated for this baseline;
- plugin wire DTOs owned by `:plugins:api`;
- Room records owned only by `:storage:room`.

Library models are introduced by Library, reader models by Reader, and download models by
Downloads when those capabilities begin.

### Minimal Story Identity

A canonical story may remain because multi-catalog identity is part of the product goal,
but it stays small. `StoryId` is a stable cross-capability identifier owned by
`:core:common`; the `Story` model itself is owned by `:catalog`. Catalog source metadata
is represented separately.

Conceptually:

```text
Story
  <- CatalogEntry from MAL
  <- CatalogEntry from another catalog
```

`Story` does not become an aggregate containing Library state, chapter graph, reading
progress, downloads, and plugin state.

### Technical Provenance

Technical persistence metadata such as plugin version, fetch timestamps, checksums,
repository provenance, or runtime details is stored only where behavior/diagnostics need
it. It is not automatically promoted into core catalog domain models.

### Room Is Private

Room entities and DAOs never leave `:storage:room`. Repository/persistence APIs are
capability-oriented, not one repository per table.

The schema is designed around current invariants, transactions, and query projections,
not speculative future normalization.

### Fresh Schema Baseline

Because the project is pre-MVP, Architecture Baseline 2 may replace the current
development Room schema with a new complete schema version 1. No development-only
migration chain is carried forward solely to preserve emulator data from earlier
architectures.

Migration discipline resumes from the accepted Architecture Baseline 2 schema onward.

### Transaction Ownership

Application/catalog code requests semantic commits such as a source refresh or source
metadata enrichment. The Room adapter owns `withTransaction` and translates semantic
mutations into tables/rows atomically.

Pure matching/merge logic emits decisions before persistence. It never mutates DAOs.

## Dependency Injection and Navigation

### Hilt Strategy

Hilt is retained as a wiring tool, not as an architectural layer.

The existing manual `OpenStoryAppGraph`/custom ViewModel-factory pattern is removed.
Constructor injection is the default. `@Binds` is used for interface implementations and
`@Provides` only for objects that cannot be constructor-injected or require framework
creation such as Room, Android platform objects, package storage roots, or JavaScript
engine factories.

Objects are scoped only when shared mutable state, lifecycle, or construction cost
justifies the scope.

Android `Context` is confined to Android adapters that genuinely require it, such as
Room/database creation, filesystem/package storage, JavaScript engine integration, and
later platform services. It is not passed into catalog services, repositories, or pure
policies merely for convenience.

Dispatcher injection is similarly selective. ViewModels use `viewModelScope`; adapters
that perform blocking work are responsible for main-safety at their boundary; pure
services receive dispatcher abstractions only when scheduling/concurrency is part of
their actual responsibility. A project-wide dispatcher dependency is not injected into
every class by convention.

### ViewModels

ViewModels use normal Android lifecycle ownership and `viewModelScope`. They may:

- observe repositories;
- own transient screen state;
- invoke focused services/repository actions;
- translate application failures into presentation state.

They must not parse plugin DTOs, execute JavaScript, build plugin network requests,
perform Room transactions, or implement canonical matching.

### Navigation

Navigation 3 remains unless implementation work proves a concrete problem.

`:app` owns a small navigation surface such as:

```text
AppRoute
AppNavigator
AppNavHost
TopLevelDestination
```

Routes carry stable navigation identities, not whole domain objects or incidental
source/plugin details.

A canonical story route is conceptually `Story(storyId)`. If source-specific navigation
is a real user concept, it receives a distinct source-specific route rather than adding
unrelated IDs to the canonical route.

No generic modular-navigation framework is introduced at the current project size.

## Quality and Architecture Enforcement

### Architecture Gates

Gradle dependency rules enforce macro boundaries. Targeted source/package import checks
may enforce sensitive intra-module rules without creating unnecessary Gradle modules.

Forbidden dependencies are policy errors. The default response to a failed gate is to
fix ownership/direction, not to expand the allowlist.

### Detekt

Detekt is a code-smell detector, not proof of good architecture.

Rules such as large class, long method, complex method, too many functions, long
parameter lists, nesting, and cyclomatic complexity remain useful signals. Passing
Detekt does not justify a mixed-responsibility file.

### Suppression Policy

Production `@Suppress`/`@file:Suppress` for structural Detekt rules is forbidden by
default. Any exceptional suppression must be narrowly scoped and recorded in an explicit
quality allowlist with path, rule, reason, and removal condition. No generic Detekt
baseline debt file is introduced.

### Size Policy

Line count is a smoke alarm, not an architecture definition.

Approximately 300 production lines should trigger review. Approximately 500 production
lines is a hard gate unless a documented exception proves one cohesive responsibility.
A smaller file may still fail architectural review when it mixes unrelated concerns.

Structural review also considers class size, long functions (roughly 40-50 lines as a
review signal, not an automatic architectural verdict), constructor dependency count,
public-method surface, nesting/branching, and imports spanning multiple subsystems.
Splitting one responsibility across `Part1`/`Part2`, helper files, extension buckets, or
thin forwarding classes without reducing coupling or clarifying ownership is explicitly
not considered a valid fix for size/complexity findings.

## Testing Strategy

Tests protect product invariants and architectural boundaries, not historical class
shapes.

### Pure JVM Tests

Use plain deterministic JVM tests for:

- matching and ranking;
- merge/refresh planning;
- plugin protocol validation;
- manifest/package policy that does not require Android;
- pure transformations and state policies.

### Room Instrumentation

Room tests focus on:

- schema and constraints;
- foreign keys;
- transactions and rollback;
- projections/query semantics;
- capability-owned persistence contracts.

Matching or plugin execution behavior is not tested through Room merely for coverage.

### Plugin Runtime Tests

Runtime/security tests focus on:

- undeclared-host denial;
- redirect revalidation;
- request/response budgets;
- invalid output rejection;
- timeout/cancellation isolation;
- archive/path traversal rejection;
- capability denial;
- credential secrecy;
- redacted diagnostics.

### Plugin Contract Tests

The MyAnimeList reference plugin passes the same fixture-based package/protocol contract
suite available to community plugins. Bundled plugins do not receive a private path that
bypasses community validation.

### ViewModel Tests

ViewModel tests cover observable application state, actions, errors, and presentation
transitions using small fakes at the repository/service boundary. A ViewModel test that
requires Room, plugin runtime, HTTP, and many low-level mocks simultaneously is treated
as an architecture smell.

### Compose Instrumentation

Compose instrumentation is reserved for semantics, navigation, state restoration,
interaction, focus/accessibility, and layout behavior that actually requires Android UI.

### Test Fixture Policy

Fixtures remain bounded and capability-specific. Mega-fixtures that configure database,
plugin runtime, network, ViewModel, clock, dispatchers, and many behavior flags are not
used to hide production coupling.

## Verification Strategy

The normal fast verification path should run, in order:

```text
architecture/dependency rules
source/suppression policy
JVM and Android local unit tests
Detekt
Lint
Debug assembly
```

Android instrumentation remains a checkpoint/CI lane because of cost. Full architecture
acceptance runs Room, plugin runtime/security, and core Compose journeys on supported
Android test targets.

Architecture Baseline 2 is not accepted solely because `scripts/verify.sh` is green. A
manual responsibility/dependency audit is part of checkpoint acceptance.

## Execution Strategy

The refactor uses parallel replacement by vertical slice, not a single big-bang commit
and not indefinite in-place compatibility refactoring.

Temporary old/new implementations may coexist inside a checkpoint, but every replaced
subsystem has exactly one canonical implementation when its checkpoint closes. Every
R0-R6 checkpoint ends in a buildable, testable, independently reviewable repository
state. Temporary migration bridges, when absolutely necessary to keep a checkpoint
buildable, must be visibly migration-scoped and have a fixed deletion checkpoint. They
are not public dependencies for new code.

A green checkpoint must represent working behavior, not placeholders introduced only to
satisfy gates. Production paths are not switched to implementations whose only behavior
is `TODO()`, `error("not implemented")`, unconditional empty results, broad suppressions,
or equivalent stubs. If a replacement slice is incomplete, consumers remain on the old
slice until the new one satisfies its focused invariants and is ready to become
canonical.

### R0 — Freeze and Guardrails

- freeze Wave 06 implementation;
- commit this Architecture Baseline 2 design and execution state;
- introduce architecture/suppression rules that prevent new debt;
- inventory legacy modules/files/tests to replace or delete;
- classify existing Wave 01-05 behaviors/invariants as `KEEP`, `CHANGE`, or `DELETE`
  with rationale;
- classify old tests by invariant rather than file ownership.

### R1 — Foundation and Module Graph

- create the target production module boundaries;
- establish dependency rules and package visibility;
- move or recreate only the minimum common primitives required by the new graph;
- keep product behavior on old slices until a replacement slice is complete.

### R2 — Plugin Subsystem VNext

- define the new pure wire/package protocol;
- implement the JS-only isolated runtime and bounded capabilities;
- rebuild package verification/registry as needed;
- rebuild MyAnimeList as the canonical reference package;
- switch consumers;
- delete Selector runtime, old JS bridge, old plugin API, and obsolete production
  fixtures before R2 closes.

### R3 — Catalog Core and Persistence

- rebuild current catalog models and ownership;
- rebuild repository/storage contracts;
- rebuild matching/ranking/refresh/search/details orchestration;
- establish the new Room schema 1 and adapters;
- port only Wave 05 requirements/invariants explicitly revalidated during R0/R3;
- delete replaced `core:model`, `core:matching`, database catalog behavior, and cross-layer
  dependencies before R3 closes.

### R4 — Presentation, Navigation, and DI

- port Home, Search, and Story UI to `:feature:catalog`;
- use repository/service boundaries only;
- remove `OpenStoryAppGraph` and custom ViewModel factories;
- finish Hilt constructor-injection composition;
- simplify Navigation 3 wiring and routes;
- delete superseded Wave 05 presentation code.

### R5 — Repository Cleanup

No product behavior is added. This checkpoint removes:

- dead modules/packages;
- unused compatibility/migration bridges;
- stale fixtures and samples;
- obsolete scripts/configuration;
- stale active documentation;
- unjustified interfaces;
- production structural suppressions;
- ambiguous generic abstractions and leftover version/legacy naming.

R5 ends only when the old architecture survives solely in Git/documentation history, not
in active production source.

### R6 — Architecture Acceptance

Run the complete unit, Room, plugin runtime/security, Compose journey, architecture,
Detekt, lint, and APK assembly gates. Then manually audit module ownership, dependency
direction, large files/classes, constructor dependency counts, public API surface, and
interface justification.

Only after R6 is accepted does `docs/project/current-state.md` advance to:

```text
Architecture Baseline 2: ACCEPTED
Next: Wave 06 Task 01
```

## Commit and Review Policy

Do not produce one giant `refactor architecture` commit.

Commits are semantic and independently reviewable, for example:

```text
architecture: introduce baseline module boundaries
plugins: define vnext wire protocol
plugins: add isolated javascript runtime
plugins: port myanimelist reference package
plugins: remove legacy selector runtime
catalog: introduce catalog ownership model
storage: replace catalog room schema
catalog: move matching behind catalog boundary
ui: port home to catalog feature
app: replace manual graph with hilt wiring
architecture: remove legacy modules
```

Every task that changes behavior follows TDD: demonstrate the focused failing invariant,
implement the smallest behavior, run the focused test, run the affected module suite,
then commit.

## Legacy Test Migration Rule

Tests are ported by invariant, not by file.

For each old test:

1. identify the product/security invariant it protects;
2. keep/rewrite it if the invariant remains valid;
3. delete it if it protects only a superseded implementation;
4. replace overly coupled tests with boundary-focused tests in the new owner module.

Old tests do not veto architectural correction.

## Definition of Done

Architecture Baseline 2 is complete only when each active responsibility has an
unambiguous owner and the following questions have one clear answer:

- Who owns this model?
- Who may call this class?
- Who stores this data?
- Who executes plugins?
- Who decides canonical matching?
- Who owns the transaction?
- Who converts application state into UI state?

The checkpoint is not accepted when the practical answer is "several unrelated modules"
or "whichever layer is convenient".

The final codebase must look like one coherent architecture, not Wave 05 code split into
smaller files.

## Handoff

After the written specification is reviewed and accepted, create a detailed
implementation plan for Architecture Baseline 2. Do not begin Wave 06 until the R6
architecture checkpoint is accepted.
