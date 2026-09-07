# Hikari Performance Big Update — Source Revalidation Notes (2026-09-07, v3)

**Source of truth:** `Hikari-master.zip`  
**Source archive SHA-256:** `a4b7b17a5f59dde6ff5d7ae1884dae043f638f9d36c35b8b1b601a98feb1f9b1`  
**Archive commit marker:** `c5b1102387f5a310fc00b926964751212e900051`  
**Superseded planning baseline:** `Hikari-performance-big-update-baseline-2026-09-07-v2.zip`  
**Superseded baseline SHA-256:** `d3813d2a3a1c4216ccfd2c1d33e1593949ac2fc59a7d3412da87a49d3139ad0d`

This note records the final source revalidation performed while producing the v3 performance big-update baseline. v3 supersedes v2 for planning. The census is now **33 confirmed structural root-cause families + 7 evidence-gated risks**, organized into **9 implementation waves**.

The key correction from the v2 review is methodological: the final closure audit may not be Room-centric. A bounded semantic request can accidentally widen into global work through Room, filesystem/package reads, Android assets, secure storage/Keystore, process-lifetime maps, work queues, or plugin/network control-plane code. v3 therefore audits **semantic scope → physical work scope** across all of those boundaries and separately audits **failure-state amplification**.

## Corrections retained from v2 revalidation

### A6 — redirect scope

`RoomStoryMergeReversalPlanner.prepareLineage()` contains a bounded redirect decision that materializes `canonicalCatalogDao().redirects()` and then searches the returned collection. This belongs to the same bounded→global family as other redirect decisions.

Wave 1 therefore keeps the indexed `EXISTS`/`LIMIT 1` contract and requires a final production-tree census for bounded redirect decisions rather than validating only previously named callers.

### A8 — Story/UI demand

The Story surface had additional global observers beyond the original UI inventory:

- `StoryViewModel` observes broad Library state although the screen needs membership for one resolved Story.
- Story progress demand is bounded to the current Story but was backed by broad progress history.
- `DownloadViewModel.statuses` observes broad download state while `StorySectionDependencies` needs release status only for releases represented by the current Story chapter state.

Wave 0 retains independent fixture dimensions for Library entries and explicit download records. Wave 7 owns the bounded UI-demand conversion and proof that the Story surface no longer scales with unrelated global rows.

### X12 — process-lifetime retention

`CatalogMetadataCoordinator.suppressions` is a second confirmed process-lifetime retention path: cooldown/version suppression entries can remain for distinct keys that are never revisited. `inFlight` is not classified the same way because completion removes its entries.

The retention family remains distinct from simple query amplification: it is process-lifetime state growth. Its implementation owner is Wave 5, alongside Reader cache/memory ownership, after the earlier foundational work is stable.

### Compact reconciliation evidence ownership

Forward merge and controlled reversal already move canonical catalog ownership transactionally. Planned compact evidence/posting rows must follow the same ownership transition inside those transactions, including expected-owner checks during reversal. Candidate lookup after merge/reversal must prove compact evidence ownership agrees with catalog source ownership.

This is a correctness requirement, not an optional optimization. It remains part of the relevant storage/canonical waves.

## New source-confirmed root causes added in v3

### X16 — plugin control-plane bounded metadata queries widen into package/executable work

#### Evidence A: operation discovery reads executable scripts

In `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/DefaultPluginRuntime.kt`, `enabled(operation)` enumerates installed plugin state and calls `loadPackage(stored)` to discover whether each manifest supports the operation. `loadPackage()` reaches `readPackage()`, which reads and decodes both:

- `manifest.json`
- `main.js`

Capability discovery semantically requires immutable manifest metadata, not executable script bytes. Therefore its physical work can scale with installed plugin count **and total plugin script bytes**.

#### Evidence B: authentication policy lookup materializes all installed manifests

`app/src/main/kotlin/app/openstory/plugins/runtime/auth/InstalledPackageAuthenticationPolicySource.kt` calls `state.all()` and reads/parses every installed `manifest.json` to build all authentication policies. `DefaultPluginSessionService.sessionFor(request)` then selects one policy for `request.pluginId`.

The request is point-scoped; the underlying policy discovery is global.

#### Evidence C: bundled provisioning reads payload before install/update decision

`AndroidBundledPluginSource.packages()` reads every configured `.osp` asset into memory. Only afterward can provisioning compare installed state and decide that a package is already current, newer, or actually requires installation/update.

The decision is descriptor/metadata scoped; payload materialization happens too early.

#### v3 contract

Wave 4 introduces a shared immutable manifest metadata boundary, manifest-only operation discovery, point authentication-policy access, and descriptor-first bundled provisioning. `main.js` and `.osp` bytes become payloads loaded only when an operation actually requires them.

The shared manifest cache is an optimization over immutable package identity, **not a new mutable source of truth**.

### X17 — terminal plugin failures can amplify into repeated control-plane work

#### Evidence A: provisioning success is the only durable process outcome

`BundledPluginProvisioner` memoizes `provisioningSucceeded = true` only when the provisioning pass has no failures. A package update that returns `NEEDS_REVIEW` is translated to nonretryable `plugin.update_needs_review`, but the global success flag remains false.

All later calls that pass through provisioning can therefore start another provisioning pass even though the relevant package is in a stable user-action-required state.

One plugin can consequently amplify work for unrelated plugin calls.

#### Evidence B: immutable package load successes are cached, terminal failures are not

`DefaultPluginRuntime` caches successfully loaded packages by package identity. Nonretryable package failures such as missing package entries/invalid immutable package content can still be re-read on later attempts when the immutable identity has not changed.

#### v3 contract and correction

The fix is **not** blanket negative caching. Earlier runtime policy intentionally allowed failed provisioning/package loads to retry, which is correct for transient faults.

Wave 4 therefore classifies outcomes by retry ownership:

- satisfied/success: stable until relevant identity/state changes;
- terminal/nonretryable or user-action-required: stable under the same immutable identity/state and invalidated when that identity/state changes;
- transient retryable: may retry, with existing single-flight/bounded semantics preserved;
- cancellation: is not converted into a cached terminal failure.

The implementation must preserve the original `PluginCallResult.Failure.retryable` semantics rather than flattening storage/runtime failures into one class.

### X18 — authenticated plugin HTTP request duplicates secure-session and Keystore work

#### Evidence A: one logical session lookup reads the encrypted store twice

In `PluginSessionService.kt`, `validSessionRecords()` reads `store.readAll(pluginId)` and filters valid records. It then calls `refreshSummary(...)`; the current summary path calls `store.readAll(pluginId)` again.

A single `sessionFor(request)` can therefore decode the same durable encrypted session state twice.

#### Evidence B: each credential decrypt reopens/loads Android Keystore state

`AndroidKeystorePluginSessionStore.readAll()` decrypts records individually. `decrypt()` calls `key()`, and `key()` opens/loads `AndroidKeyStore` and obtains/generates the key. Repeating that per credential multiplies Keystore work within a single high-level store operation.

Since `PluginHttpCapability.buildRequest()` asks the managed credential provider for headers on authenticated plugin HTTP requests, this work sits directly on request construction.

#### v3 contract

Wave 4 requires:

1. one secure-session snapshot per logical `sessionFor()` evaluation;
2. summary derivation from that same snapshot;
3. one SecretKey acquisition per high-level encrypted store read/write operation, reused only for the duration of that operation;
4. all existing AES/GCM, AAD, atomic-write, no-backup-directory, invalidation, and policy-security semantics preserved.

This does **not** authorize a process-lifetime plaintext credential cache.

## New evidence-gated risk added in v3

### RISK-PLUGIN-AUTH-CACHE — residual per-request secure-session cost after X16/X18

After point policy lookup and duplicate secure-session/Keystore work are removed, authenticated request construction may still require one encrypted session-file read/decrypt. Whether that remaining cost matters on representative Android hardware is empirical.

Wave 8 benchmarks at minimum:

- credential records per plugin (`Cr`): `0 / 1 / 4`;
- authenticated request builds (`Hr`): `1 / 10 / 100`;
- cold and warm process/device conditions where practical.

A longer-lived decrypted credential/session cache is forbidden unless the benchmark proves material benefit **and** a separate security/threat review defines generation, invalidation, lifetime, memory exposure, and logout/policy-change behavior. The default acceptance outcome may legitimately be **no additional cache**.

## Wave renumbering and ownership

v3 uses nine waves:

| Wave | Owner |
|---|---|
| 0 | Contracts, fixtures, baseline characterization |
| 1 | Bounded storage foundations |
| 2 | Catalog evidence and lifetime |
| 3 | Canonical execution |
| 4 | Plugin runtime control plane |
| 5 | Reader/cache/memory |
| 6 | Chapter delta/background work |
| 7 | UI demand/CPU/lifecycle |
| 8 | Risk acceptance and final freeze |

Wave 4 is intentionally separate from Reader. It fixes plugin control-plane work that can affect Reader, Chapter, Search, Mapping, and other plugin consumers. It can proceed independently of Room schema migrations once Wave 0 contracts exist.

`RISK-PLUGIN-ISOLATE` must be measured only after Wave 4 removes X16-X18 contamination; otherwise package/script discovery, provisioning retries, or secure-session overhead could be misattributed to isolate startup.

## Final closure methodology added in v3

### 1. Semantic-scope → physical-work census

For every bounded operation in a hot or repeated path, the final audit asks:

> What is the semantic scope requested by the caller, and what is the largest physical data/work scope materialized underneath it?

The census includes, at minimum:

- Room queries/observations;
- filesystem/blob/package reads;
- Android bundled assets;
- secure session files and Android Keystore operations;
- process-lifetime maps/caches;
- work-manager/background scans;
- plugin/network control-plane discovery.

This replaces signature-only checks such as searching for `observeAll()` or `redirects()`. Those searches remain useful evidence, but they are not sufficient closure proof.

### 2. Failure-amplification census

Every expensive lazy initializer, provisioner, loader, cache, or coordinator is reviewed under:

- success/satisfied;
- transient retryable failure;
- terminal/nonretryable failure;
- user-action-required state;
- cancellation.

For an unchanged immutable input/state, the audit asks which outcomes cause expensive work to run again, which are memoized, what invalidates them, and whether retry ownership matches the failure classification.

### 3. Production-binding regression guard

Interfaces that provide convenience defaults by implementing a bounded API through a global API are allowed only when production bindings demonstrably override the global fallback on hot paths. v3 requires a fail-closed source/architecture regression guard where such defaults would silently reintroduce global observation/materialization. Wave 7 owns the concrete guard for the bounded Catalog projection, content-mapping, reading-progress, Chapter and download production bindings used by the UI.

### 4. Process-lifetime-map false-positive review

The generalized census also rechecked mutable maps/locks rather than labeling every process object as X12. Several are intentionally excluded because their code already proves bounded lifetime: `PluginChapterSourceRegistry` and `PluginContentSourceRegistry` call `retainAll(active)`; `ContentSourceExecutionLane`, `ChapterBlobFileLocks`, `ReaderAssetBlobFileLocks`, half-open probe and single-flight registries remove idle/completed entries; `InvocationScriptBuilder` is explicitly LRU-capped. Installed-plugin/source-operation caches remain part of the final X12 census if their lifecycle contract changes, but are not promoted merely because they use a map.

This negative evidence matters: v3 is intended to find unbounded ownership, not to replace every mutable structure with an arbitrary cache abstraction.

## Self-review corrections and exclusions

- The earlier review claim that several planned test paths were incorrectly labeled `Test:` rather than `Create:` was rechecked: those tests are created in earlier waves and referenced later. v3 does not preserve that false-positive correction.
- Persistent top-level composition is **not** promoted to a confirmed lifecycle defect from static Compose inspection alone. `PersistentTopLevelNavDisplay` retains visited tabs, but destination lifecycle determines whether `collectAsStateWithLifecycle()` keeps upstream work active. `RISK-LIFECYCLE` therefore remains evidence-gated and Wave 8 must measure hidden-route work before changing navigation architecture.
- Fresh JavaScript isolate-per-invocation remains evidence-gated. v3 does not introduce isolate pooling without benchmark evidence and explicit state/security semantics.
- X16, X17, and X18 are kept separate because their invalidation and correctness contracts differ: metadata/payload scope, retry-state ownership, and credential/cryptographic work cannot safely share one generic cache fix.

## Freeze rule

The final program is not frozen merely because the traceability matrix contains 33 rows. Wave 8 must re-enumerate the final production tree and demonstrate:

1. all **33/33 confirmed root-cause families** have a closed implementation/measurement contract;
2. all **7/7 risks** have explicit measured acceptance or rejection;
3. no unexplained bounded→global physical-work path remains across the listed storage/runtime boundaries;
4. no terminal/stable failure repeatedly re-triggers expensive work without an invalidation reason;
5. security/correctness invariants remain intact, especially canonical ownership, plugin capability review, credential protection, and retry semantics.

Any unexplained path reopens the relevant family or creates a new root-cause family before freeze.
