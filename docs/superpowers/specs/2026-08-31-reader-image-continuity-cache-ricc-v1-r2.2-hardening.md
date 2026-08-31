# Reader Image Continuity Cache / RICC-v1 — R2.2 Consolidated Hardening Baseline

**Date:** 2026-08-31  
**Revision:** R2.2 consolidated hardening  
**Status:** **SPEC FROZEN / READY FOR IMPLEMENTATION PLAN**  
**Normative base:** `2026-08-31-reader-image-continuity-cache-ricc-v1-design.md` R2  
**Supersedes:** `2026-08-31-reader-image-continuity-cache-ricc-v1-r2.1-hardening-addendum.md`  
**Scope:** correctness, identity, security, lifecycle, persistence, quota, concurrency, and current-master alignment hardening. No HES-v1 behavior change and no production implementation.

---

## 1. Purpose and normative precedence

R2 established the correct primary RICC-v1 architecture: semantic encoded-image caching outside Coil disk cache, bounded sliding working sets, persistent warm history, process-scoped single-flight, Reader/Downloads admission arbitration, one automatic-cache budget, page-local recovery, and Room schema 12.

A repository audit against current Hikari `master` found that several R2 statements were either stronger than the current project can safely guarantee or underspecified enough to permit wrong-image reuse, durable-cache resurrection, quota overspend, degraded-cache stalls, or arbiter deadlock.

This document consolidates all R2.1 hardening and the remaining repository-alignment fixes into one correction baseline.

Normative precedence for RICC-v1 planning is now:

```text
current production source + executable architecture contracts
        >
accepted HES-v1 constitution
        >
RICC-v1 R2 as amended/replaced by R2.2
        >
implementation convenience
```

Where this R2.2 document explicitly replaces, narrows, or strengthens an R2 statement, **R2.2 wins**. R2.1 is retained only as historical review evidence and is no longer required as a separate normative input.

---

## 2. Baseline corrections to R2

### 2.1 `image-only ReaderDocument` -> `image-bearing ReaderDocument`

R2 uses `image-only ReaderDocument` in several places. Current Reader persistence semantics reject local persistence when a document contains **any** `ReaderBlock.ImagePage`, not only when every block is an image.

All affected R2 wording is therefore interpreted as:

```text
image-bearing ReaderDocument
```

unless a section is intentionally describing a document consisting only of image blocks.

This correction applies especially to R2 sections 1, 3, 13, 40, 53, and 64.

### 2.2 Module count is preserved; dependency edges may be intentionally tightened

R2 section 7 says RICC “preserves the existing production module graph”, while R2 section 59 requires `:feature:reader` not to depend directly on `:downloads`.

Current master already declares a direct `:feature:reader -> :downloads` Gradle edge even though RICC should not require feature code to own Downloads persistence/cache policy.

The normative rule is therefore:

```text
RICC-v1 preserves the existing production module SET/COUNT.
Dependency edges may be intentionally tightened when removing an unused or architecturally invalid edge.
No new production module is introduced.
```

If repository mapping confirms the current `:feature:reader -> :downloads` edge is unused by production Reader code, the implementation plan SHOULD remove it and update architecture guards intentionally. If real existing production usage is discovered, the plan MUST first isolate that usage behind the existing Reader-facing ports before removing the edge.

### 2.3 Storage adapter direction must remain clean

`:storage:files` MUST remain a physical-storage adapter and MUST NOT implement a `:reader` port by adding a new `:storage:files -> :reader` dependency merely for RICC.

Preferred dependency direction is:

```text
ReaderAssetStorePort (:reader)
        <-
RICC durable/orchestration implementation (:downloads)
        ->
physical blob-store port owned by :downloads or another existing neutral storage contract
        <-
:storage:files adapter
```

Equivalent existing-project patterns are acceptable if they preserve the same direction and keep Reader free of filesystem ownership.

---

## 3. Additional constitutional invariants

Append the following to R2 section 6.

### I21 — Trust is explicit, never inferred from identifier presence

The existence of a non-blank source `stableId` MUST NOT by itself authorize `TRUSTED_STABLE`.

Identity trust is supplied by an explicit Reader-runtime/source capability fact. Without trusted evidence, RICC conservatively uses `LOCATOR_BOUND` or `NON_PERSISTENT`.

### I22 — Durable persistence scope is explicit

The existence of an image URL, plugin ID, login state, account object, or successful authenticated fetch MUST NOT implicitly authorize durable persistence.

A narrow policy/security fact determines one of:

```text
PUBLIC
ACCOUNT_SCOPED(stableNonSecretAccountNamespace)
NON_PERSISTENT_PRIVATE
```

Unknown/private-unspecified scope is `NON_PERSISTENT_PRIVATE`.

### I23 — Explicit invalidation outranks stale immutable completion

`clear automatic cache`, `quota = 0`, logout/account removal, or security-scope invalidation revokes durable-write authority for older in-flight generations.

Valid late bytes may still satisfy an already-authorized transient consumer, but MUST NOT resurrect durable metadata or policy state that has been invalidated.

### I24 — One remote admission cannot nest another

A remote operation holding `ContentFetchArbiter` admission MUST NOT synchronously or suspendingly acquire a second `ContentFetchArbiter` admission.

Recovery work that itself requires remote access first closes/releases the current admitted operation.

### I25 — Remote admission covers the complete network-body lifetime

`ContentFetchArbiter` admission remains held until the admitted response body reaches EOF, is cancelled, or is closed.

Receiving headers or returning a response/body handle is not completion of the admitted remote operation.

### I26 — `LOCATOR_BOUND` refresh may change logical identity

For `LOCATOR_BOUND`, a changed normalized locator fingerprint produces a new logical asset key.

Refreshed bytes MUST NOT be committed under the old locator-bound key.

### I27 — Cache authority failure is not a cache miss

Failure to determine local presence because Room/filesystem/cache authority is unavailable MUST NOT be represented as `LOCAL_MISSING`.

Visible content may bypass degraded cache authority; speculative durable work is suppressed until authority recovers.

### I28 — Final durable admission is atomic across the unified automatic budget

Two concurrent asset/document completions MUST NOT independently consume the same remaining automatic-cache capacity.

All participating automatic durable commits pass through one final reservation/admission authority that atomically accounts committed bytes plus pending reservations.

### I29 — Session protection lifetime is explicit

Runtime retention/protection state belongs to a concrete Reader session owner and MUST be released when that owner ends.

No process-global stale `currentChapter`/session protection may survive owner-job completion, ViewModel teardown, or explicit session close.

### I30 — Durable logical-key encoding is versioned and canonical

Durable key hashes are derived from a versioned canonical encoding with deterministic field ordering and unambiguous framing.

Implementation-defined object/string serialization is forbidden as durable identity.

### I31 — Local blob integrity and source integrity are separate facts

RICC SHOULD use a mandatory local cryptographic checksum/digest for blobs it persists, unless the implementation proves an existing mandatory equivalent.

Source-provided checksum/revision/integrity metadata remains optional and must not be conflated with the local blob-integrity digest.

### I32 — Unknown network class is conservative connected, not unmetered

When current network classification is `UNKNOWN`, RICC may service `CRITICAL` and bounded `INTERACTIVE` demand, but MUST NOT start transition/current-ahead speculative acquisition that requires confirmed unmetered policy.

### I33 — Process recreation guarantees byte reuse, not semantic reconstruction

RICC guarantees retained image-byte reuse **after** existing Reader semantics reconstruct or reacquire the selected document/manifest.

RICC-v1 does not itself provide a durable semantic chapter manifest sufficient for offline cold-open of a currently non-persistable image-bearing Reader document.

### I34 — First consumed promotion is not a low-value access touch

The first valid `unconsumed -> consumed` transition updates retention state immediately in memory and schedules durable `lastConsumedAt` promptly.

It MUST NOT be delayed behind a long generic `lastAccessedAt` debounce.

---

## 4. Identity trust and durable key contract

This section replaces/strengthens R2 sections 10–12.

### 4.1 Full stable identity remains required

R2 section 9 remains normative: RICC must preserve the full sanitized source stable asset identity separately from the truncated UI block ID.

However, preserving the full value does **not** make it trusted.

### 4.2 Explicit trust authority

`TRUSTED_STABLE` is allowed only when an explicit source/Reader capability contract proves at least one of:

1. the stable page identity changes whenever logical image content changes; or
2. the identity is namespaced by a trusted immutable content revision that changes whenever logical image content changes.

The implementation plan MUST locate the exact current source/plugin facts that can support this. It MUST NOT invent such a guarantee from the presence of `stableId`.

If no such fact exists for a source, that source does not receive `TRUSTED_STABLE` in V1.

### 4.3 `LOCATOR_BOUND` safety

`LOCATOR_BOUND` is a conservative key mode for cases where durable reuse can safely be tied to current delivery identity.

The canonical key includes a one-way normalized locator fingerprint plus source/release/page/security/variant namespace facts.

A refreshed locator that normalizes to a different fingerprint creates a new logical key.

If a source can serve materially different bytes from the same locator with no trustworthy revision/validation fact, `LOCATOR_BOUND` is not strong enough for durable correctness and the source MUST downgrade to `NON_PERSISTENT` or use an explicit safe revalidation contract.

Wrong-image avoidance wins over hit rate.

### 4.4 Canonical durable-key encoding

Conceptually:

```text
keySchemaVersion
sourceNamespace
cacheSecurityScope
contentVariant
imageSetNamespace
pageIdentity
```

are encoded with explicit field tags/length framing or an equivalent deterministic canonical representation, then hashed with a cryptographic digest.

Requirements:

- no map iteration order dependence;
- no locale-dependent formatting;
- no raw secret material;
- unknown future `key_schema_version` is a safe miss/repair candidate;
- key encoding has golden-vector tests.

---

## 5. Cache/security scope and anti-resurrection policy epoch

This section strengthens R2 sections 10, 27, 32, 33, 36, 41, and 51.

Every durable write carries or is associated with a persistence-policy epoch/revision captured before final durable admission.

The model must detect at minimum:

```text
global automatic-cache policy invalidation
quota transition to zero / durable automatic caching disabled
explicit clear automatic cache for affected scope
logout/account removal
security-scope invalidation
```

Before metadata becomes visible, final commit revalidates the current epoch/policy authority.

If stale:

```text
valid bytes may finish an already-authorized transient render
but durable metadata/blob publication is denied
```

### Logout/account removal

Account-scope invalidation:

1. revokes/advances the affected durable-write authority;
2. supersedes old scope work for persistence;
3. triggers bounded deletion/reconciliation of matching durable entries;
4. prevents late completion from recreating the removed scope.

### Clear automatic cache

Clear uses the same anti-resurrection rule.

An active `AssetReadLease` may delay physical unlink until the read completes, but does not authorize any stale fetch completion to reinsert durable metadata.

### Quota = 0

A transition to quota zero immediately revokes authority for new automatic durable publication. Existing entries reconcile toward zero after active read leases release.

---

## 6. Local presence becomes a four-state contract

This section replaces R2 section 15.

RICC uses:

```text
UNKNOWN
LOCAL_AVAILABLE
LOCAL_MISSING
LOCAL_UNAVAILABLE
```

Semantics:

```text
UNKNOWN
    -> local state has not yet been resolved

LOCAL_AVAILABLE
    -> local metadata/blob is usable; open under read lease

LOCAL_MISSING
    -> local authority successfully determined that no usable asset exists

LOCAL_UNAVAILABLE
    -> local authority could not answer safely because Room/filesystem/cache subsystem failed
```

Rules:

- `UNKNOWN != LOCAL_MISSING`;
- `LOCAL_UNAVAILABLE != LOCAL_MISSING`;
- batch `inspect(keys)` returns an explicit result for every requested key;
- a visible `UNKNOWN` request performs or joins one targeted bounded inspection before remote admission;
- a visible `LOCAL_UNAVAILABLE` request may use the `CRITICAL` remote **cache-bypass** path without repeating local inspection indefinitely;
- local authority failure is diagnostic/storage evidence only and does not reduce HES source health;
- speculative durable acquisition SHOULD be suppressed while durable cache authority is unavailable.

This prevents both process-restart avoidable refetch and visible-content stalls during Room/filesystem outages.

---

## 7. Network-class policy correction

This section extends R2 section 26.

### `OFFLINE`

Only local assets are usable. No source-health penalty follows merely from offline state.

### `METERED`

Allow:

```text
CRITICAL
bounded INTERACTIVE (initially up to 2 near-ahead assets)
```

Disable current-ahead speculation beyond the interactive allowance and all transition speculation.

### `UNMETERED`

Allow visible/interactive plus bounded current-ahead and bounded next-opening speculation, subject to pressure/arbitration.

### `UNKNOWN`

Treat as conservative connected state:

```text
allow CRITICAL
allow bounded INTERACTIVE needed for current UX
forbid PREFETCH/SPECULATIVE acquisition whose policy requires confirmed UNMETERED
```

`UNKNOWN` MUST NOT be treated as `UNMETERED` simply to maximize prefetch.

---

## 8. Session lifecycle and multi-session protection

This section strengthens R2 section 33 and the `releaseSession` contract.

Each `ReaderAssetSessionState` is owned by a concrete lifecycle/scope.

The implementation MUST provide one of the following equivalent guarantees:

```text
owner coroutine Job completion -> releaseSession(sessionId)
```

or

```text
explicit close()/dispose() tied to Reader ViewModel/session teardown
```

with idempotent cleanup.

Requirements:

- closing session A releases only A's active protections;
- session B protections remain intact;
- abandoned sessions cannot permanently elevate eviction classes;
- in-flight immutable bytes may finish according to single-flight consumer rules, but stale session state cannot mutate current working-set policy;
- process death reconstructs runtime session state rather than restoring pins/revisions as durable truth.

The plan MUST identify the exact current Reader owner lifecycle where this release is guaranteed.

---

## 9. ContentFetchArbiter lifetime, ordering, and no-nesting

This section replaces/strengthens R2 sections 30–31 and 42.

### 9.1 Plugin/source document work

Required order:

```text
HES probe permission when applicable
    ->
ContentSourceExecutionLane(source)
    ->
ContentFetchArbiter(priority)
    ->
remote plugin/source operation
    ->
consume/close remote body if one exists
    ->
release ContentFetchArbiter
    ->
post-network validation/persistence
    ->
release source lane according to existing source-ordering semantics
```

The implementation plan must verify whether current plugin document fetch materializes the body before returning. If a streamed body escapes the call, arbiter ownership must escape with it until EOF/cancel/close.

### 9.2 Image asset work

```text
single-flight
    ->
ContentFetchArbiter
    ->
open remote image response
    ->
stream bounded bytes to temp/transient consumer
    ->
EOF/cancel/close
    ->
release arbiter
    ->
local checksum/final durable commit
```

Remote streaming occurs while the permit is held because network transfer is still active.

### 9.3 Recovery work never nests

Remote locator refresh, credential/source refresh that performs network, or same-selected-release document re-fetch MUST start only after the current remote operation has closed and released its arbiter permit.

Nested arbiter acquisition is forbidden even when both operations are `CRITICAL`.

### 9.4 Locator refresh without new HES route choice

If the current protocol has no dedicated locator-refresh API, RICC/Reader may re-fetch delivery facts/document for the **already-selected release** through the existing source path.

This is not HES rerouting.

If the refreshed semantic manifest is incompatible with the committed selected route/image-set contract, the result escalates as typed `RouteInvalidated`; it is not silently mutated into the old manifest.

---

## 10. Fairness and preemption hardening

R2 priority ordering remains:

```text
CRITICAL > INTERACTIVE > USER_WORK > PREFETCH > SPECULATIVE > BACKGROUND
```

But “higher priority” does not mean arbitrary cancellation of all lower-priority admitted work.

Rules:

- responsiveness is provided by reserved capacity, queue priority, and cancellation/preemption of **Reader-owned speculative work** where safe;
- active explicit `USER_WORK` is not force-cancelled merely because a `CRITICAL` Reader request arrives;
- queued USER_WORK must receive bounded fairness/aging and cannot starve indefinitely;
- lower priority never preempts active higher priority;
- `Preempted`/`Superseded` are non-health failures;
- next-chapter speculative image concurrency remains initially capped at 1.

The exact reserved-capacity shape and total slot count remain implementation-plan constants.

---

## 11. Unified quota final-commit reservation

This section strengthens R2 sections 34–37 and 39–41.

`AutomaticCacheBudgetCoordinator` is the single authority over automatic Reader document bytes plus RICC image bytes.

It must account conceptually:

```text
committedAutomaticBytes
+
pendingDurableReservations
```

before final publication.

A final durable commit requires a reservation/token or equivalent serialized admission that proves:

1. current quota/policy authority still permits durable caching;
2. required evictions/overflow rules have been evaluated against the unified inventory;
3. the requested bytes are reserved so a concurrent completion cannot spend the same capacity;
4. the reservation is released on success, cancellation, denied commit, or failure.

The exact internal algorithm is not frozen, but two independent `quota = X` stores or check-then-commit races are forbidden.

If durable admission is denied or persistence fails, a visible request still renders valid bytes transiently where possible.

Quota reconciliation remains asynchronous from visible render and uses hysteresis to avoid per-page eviction thrash.

---

## 12. Progress protection source of truth

This section strengthens R2 section 35.

RICC MUST NOT introduce a second/shadow progress database or infer progress from cache state.

The existing project Reader progress truth is projected through a narrow read-only adapter into Downloads automatic-cache retention policy.

Rules:

- progress protection is advisory retention value, not routing truth;
- Downloads consumes only the narrow projection it needs;
- active RICC session protections remain separate runtime facts;
- consumed assets for a progress-protected release may receive elevated retention;
- unconsumed speculative assets do not inherit full progress protection merely because they share a release;
- no `:downloads -> :feature:reader` dependency is introduced.

The implementation plan MUST map the exact current progress repository/adapter point.

---

## 13. Process recreation guarantee clarification

This section replaces the broad wording in R2 sections 1, 4, 40, 53.5, and 64.

RICC persists image asset bytes and metadata. It does not by itself make a currently non-persistable image-bearing `ReaderDocument` semantically reconstructible.

The product guarantee is therefore:

> After process recreation, once existing Reader semantics reconstruct or reacquire the selected document/asset manifest, retained RICC image bytes are reused from durable storage without avoidable remote image refetch.

RICC-v1 does **not** guarantee this scenario:

```text
process killed
+
image-bearing ReaderDocument has no durable semantic representation
+
device starts offline
+
cold-open chapter solely from RICC asset blobs
```

That would require a durable semantic/minimal chapter manifest and is deferred unless separately designed.

Acceptance tests MUST distinguish semantic manifest reacquisition from image-byte network activity.

---

## 14. Consumption durability hardening

This section refines R2 sections 20 and 46.

After a valid `ReaderAssetPresented(sessionId, committedManifestRevision, assetKey)` event passes current-session/revision checks:

1. in-memory retention immediately promotes the asset to consumed;
2. durable `lastConsumedAt` is scheduled promptly;
3. duplicate presentation is idempotent/coalesced;
4. repeated `lastAccessedAt` may use a coarse bounded touch interval;
5. persistence failure is non-fatal and does not retroactively make the presentation invalid.

A first consumed promotion MUST NOT wait behind the general access-touch debounce.

---

## 15. Finite manifest/page cardinality

This section extends R2 section 28.

In addition to `MAX_READER_ASSET_BYTES`, RICC requires a finite maximum image descriptors/pages per validated Reader document/manifest.

Current master already enforces `ReaderDocumentSanitizer.MAX_BLOCKS = 2_000`. Because every RICC image descriptor is derived from a validated Reader image block, RICC-v1 reuses that existing document bound rather than introducing a second independent page-count constant.

Normative bound:

```text
RICC image descriptors per validated manifest <= ReaderDocumentSanitizer.MAX_BLOCKS (= 2_000 on the frozen master baseline)
```

If the existing Reader constant changes in the future, RICC follows only after the corresponding sanitizer/RICC contract tests are updated together.

The bound protects:

- manifest memory;
- Room row count;
- filesystem/inode growth;
- batch inspection size;
- viewport/planner state;
- maliciously large speculative frontiers.

The rolling prefetch frontier remains much smaller than this defensive document-size ceiling.

---

## 16. Room schema 12 and integrity amendment

R2 section 38 remains structurally valid with the following amendments.

Conceptual metadata includes:

```text
key_schema_version
logical_asset_key_hash
story_id
canonical_chapter_id
chapter_release_id
source_namespace
security_scope_hash_nullable
content_variant
identity_mode
image_set_namespace_hash
page_identity_hash
page_ordinal
blob_id
byte_size
local_blob_checksum
source_integrity_nullable
created_at
last_accessed_at
last_consumed_at_nullable
```

Exact names/encodings may differ.

Normative rules:

- `key_schema_version` is stored or deterministically derivable and validated at lookup;
- unknown key versions are safe misses/repair candidates;
- RICC persisted image blobs use the existing Downloads `BlobChecksum` SHA-256 semantics, or an exact compatible equivalent if repository mapping requires an adapter; the local persisted-blob checksum is mandatory for a published durable RICC entry;
- source-provided checksum/revision integrity is separate and optional;
- raw signed URLs, credentials, auth headers, tokens, and raw private scope values are not stored;
- runtime persistence-policy epoch does not need to be durable metadata unless implementation evidence proves otherwise;
- migration `11 -> 12` remains non-destructive and performs no eager image warming.

---

## 17. Asset-store contract amendment

R2 section 39 conceptually becomes:

```text
inspect(keys)
openLocal(key)
beginDurableCommit(assetFacts, expectedPolicyEpoch)
commit(reservation, asset)
markConsumed(key, wallClockTime)
invalidate(key, reason)
cachePressure()
reconcile(activeProtections)
releaseSession(sessionId)
clearAutomatic(scope)
```

The exact API may combine `beginDurableCommit` and `commit`, or hide the epoch in a token/reservation.

Normative behavior:

- `inspect(keys)` produces one explicit local-presence state per requested key;
- `openLocal` distinguishes missing, unavailable, and corruption;
- final durable publication requires both current policy epoch and unified-budget admission;
- cancellation/failure releases pending byte reservation;
- clear/logout/quota-zero revokes old commit authority;
- Reader never owns SQL or direct file deletion.

---

## 18. Additional required acceptance and regression tests

Append these to R2 sections 54–58.

### 18.1 Identity/trust/security

1. non-blank `stableId` without trusted source capability does not become `TRUSTED_STABLE`;
2. unknown/private-unspecified security policy becomes non-persistent;
3. full stable asset identity survives sanitizer/manifest boundary separately from UI block ID;
4. canonical key encoding has golden vectors and is independent of map/iteration/locale ordering;
5. key schema version change cannot alias old/new keys;
6. `LOCATOR_BOUND` locator change creates a new key;
7. refreshed bytes are never committed under stale locator-bound identity;
8. a locator that can change bytes without trustworthy revision cannot silently receive durable `LOCATOR_BOUND` reuse;
9. raw secrets/locators prohibited by policy are absent from metadata;
10. source variant/security/source namespace changes cannot cross-hit.

### 18.2 Cache authority/degradation

11. batch inspect returns explicit state for every requested key;
12. Room/filesystem inspection failure returns `LOCAL_UNAVAILABLE`, not `LOCAL_MISSING`;
13. visible `LOCAL_UNAVAILABLE` uses remote cache-bypass and renders;
14. local-cache outage does not loop targeted inspection forever;
15. speculative durable acquisition is suppressed while local durable authority is unavailable.

### 18.3 Invalidation races

16. logout after fetch start but before final commit prevents durable scope resurrection;
17. clear automatic cache after fetch start prevents reinsertion;
18. quota transition to zero before commit prevents durable publication;
19. stale-policy completion cannot mutate active cache policy state;
20. active read lease may delay unlink but cannot preserve stale write authority.

### 18.4 Arbiter/deadlock/fairness

21. arbiter permit remains occupied while remote response body is streaming;
22. headers returned/unread body does not free admission;
23. locator refresh starts only after stale asset admission is released;
24. all slots hitting stale locators cannot deadlock waiting for nested permits;
25. same-selected-release document refresh follows source-lane -> arbiter order;
26. no participating path retains old Reader-global semaphore plus new arbiter admission;
27. active USER_WORK is not force-cancelled by CRITICAL demand;
28. reserved/preemptible speculative capacity still allows CRITICAL to start promptly;
29. USER_WORK receives bounded fairness without preempting active CRITICAL.

### 18.5 Quota races

30. two concurrent final commits cannot consume the same free byte capacity;
31. pending reservation release on cancellation/failure cannot leak accounting;
32. document + image stores share one quota authority rather than each receiving full quota;
33. denied durable commit still permits visible transient render;
34. quota reconciliation does not block critical rendering.

### 18.6 Session lifecycle

35. session owner completion releases that session protection;
36. closing session A does not release session B protection;
37. repeated close/release is idempotent;
38. stale session completion may keep valid immutable bytes only when persistence authority remains current, and cannot chain new work.

### 18.7 Process recreation

39. process recreation + Reader semantic manifest reacquisition + retained asset -> zero remote **image** fetch;
40. tests do not claim RICC-only offline cold-open semantic reconstruction for non-persistable image-bearing documents;
41. `UNKNOWN` local presence after reconstruction resolves locally before remote image admission.

### 18.8 Consumption/cardinality/integrity

42. first presentation schedules durable consumed promotion independent of access-touch debounce;
43. repeated presentation is idempotent/coalesced;
44. maximum valid manifest remains bounded in metadata/work;
45. oversized page-count document is rejected/bounded before unbounded RICC structures are created;
46. rolling frontier stays bounded even at maximum valid manifest size;
47. persisted blob checksum mismatch becomes corruption/miss+repair, not a cache hit;
48. source integrity metadata remains optional/separate from local blob integrity.

### 18.9 Network UNKNOWN

49. `UNKNOWN` network permits visible/bounded interactive demand;
50. `UNKNOWN` network does not start current-ahead/transition speculation requiring confirmed unmetered connectivity.

---

## 19. Architecture gates correction

R2 section 59 becomes:

Implementation MUST preserve/extend checks so that:

- `:reader:engine` remains unaware of RICC/storage/network/UI;
- `:reader` has no Room/filesystem/Settings/Compose/Coil imports for RICC;
- `:feature:reader` has no direct Downloads/Room/filesystem ownership for RICC;
- if the current declared `:feature:reader -> :downloads` Gradle edge is confirmed unused, it is removed and architecture fixtures are updated intentionally;
- `:downloads` remains durable cache/persistence policy owner and does not depend on feature modules;
- `:storage:files` does not add a direct Reader dependency merely to implement RICC storage;
- source lane/global arbiter ownership creates one process-global admission layer for participating work;
- old Reader-global foreground/prefetch semaphore ownership is removed from migrated participating paths;
- Room migration/schema guards move intentionally from 11 to 12;
- production module **count/set** remains 17 plus `:benchmark` unless separately reviewed;
- any newly discovered multi-process content execution is explicitly reviewed because process-scoped single-flight/arbiter assumptions do not automatically become cross-process guarantees.

---

## 20. Revised Definition of Done

R2 section 64 is replaced by the following consolidated DoD.

RICC-v1 is complete only when fresh evidence proves:

1. same-chapter retained revisit works after Coil memory eviction with zero remote image refetch;
2. retained pages render offline from RICC disk after memory eviction when their semantic document/manifest is already available;
3. `A -> B -> A` works after memory eviction with zero remote image refetch for retained assets;
4. after process recreation and Reader semantic manifest reconstruction/reacquisition, retained image bytes are reused with zero remote image refetch;
5. no claim is made that RICC alone provides offline cold-open semantic reconstruction of non-persistable image-bearing Reader documents;
6. bounded next-opening prefetch works on confirmed unmetered network;
7. metered and unknown-network policies block transition speculation;
8. current rolling frontier remains bounded;
9. visible work outranks speculative work and can start promptly;
10. duplicate demand collapses to one remote asset fetch;
11. speculative-to-visible priority promotion reuses the same in-flight work;
12. source lane/global arbiter ordering preserves HES source ordering and one global admission owner;
13. arbiter admission covers the entire remote body lifetime and never nests;
14. explicit USER_WORK is not indiscriminately cancelled for Reader priority;
15. stale viewport/chapter/prefetch/session work cannot mutate current working-set policy;
16. `UNKNOWN` local presence cannot cause avoidable process-restart image refetch;
17. `LOCAL_UNAVAILABLE` cannot stall visible content or masquerade as a confirmed miss;
18. full stable identity is preserved independently from the truncated UI ID;
19. `TRUSTED_STABLE` requires explicit trust authority;
20. unsafe weak/private identity safely downgrades rather than returning wrong/cross-account bytes;
21. locator-bound refresh cannot commit refreshed bytes under stale identity;
22. durable key encoding is versioned/canonical and covered by golden vectors;
23. clear/logout/quota-zero cannot be undone by late completion;
24. cache/storage failure does not make otherwise-readable remote content unreadable;
25. page-local Retry does not reload the whole document for asset-local failures;
26. `automaticCacheQuotaBytes` controls one real unified document+image automatic-cache budget;
27. concurrent final durable commits cannot overspend the same budget capacity;
28. progress protection projects from existing Reader progress truth and does not protect speculative tails;
29. cache pressure suppresses wasteful speculative durable work;
30. encoded asset size and manifest/page cardinality are hard bounded;
31. local persisted blobs have deterministic integrity validation and corruption recovery;
32. session protections have deterministic owner-bound release;
33. HES-v1 route/ranking/health behavior and `:reader:engine` purity remain unchanged;
34. Room `11 -> 12` migration is lossless and fresh-schema equivalent;
35. architecture/package/current-state guards reflect the intentional dependency/schema changes and are green;
36. focused, broad, connected/instrumented Reader/Downloads/Feature regression suites are green;
37. final implementation self-review finds no unresolved Critical/High ownership, race, cache-correctness, security, quota, lifecycle, migration, or concurrency gap.

Debt 2 remainder outside participating Reader/Downloads work and Debt 3 WorkManager child-constraint propagation remain explicitly outside this DoD.

---

## 21. R2.2 resolution log

### SR-R2.2-01 — Stable ID presence could be treated as immutable identity

**Resolved:** `TRUSTED_STABLE` requires explicit capability evidence; otherwise conservative downgrade.

### SR-R2.2-02 — Persistence scope could be inferred from login/plugin state

**Resolved:** explicit security/persistence policy; unknown private scope is non-persistent.

### SR-R2.2-03 — Locator-bound identity could survive changed locator incorrectly

**Resolved:** changed normalized locator re-keys; refreshed bytes cannot publish under the stale key.

### SR-R2.2-04 — `UNKNOWN` could hide failed cache authority

**Resolved:** four-state local presence adds `LOCAL_UNAVAILABLE` and visible cache-bypass behavior.

### SR-R2.2-05 — Clear/logout/quota-zero could be undone by late completion

**Resolved:** final commit validates persistence-policy epoch/current durable-write authority.

### SR-R2.2-06 — Arbiter could release before body transfer completes

**Resolved:** permit lifetime reaches EOF/cancel/close.

### SR-R2.2-07 — Recovery could deadlock via nested global admission

**Resolved:** no nested arbiter admission; recovery remote work starts only after release.

### SR-R2.2-08 — Current protocol may lack dedicated locator refresh

**Resolved:** same-selected-release source re-fetch is allowed without HES rerouting; incompatible semantic change escalates `RouteInvalidated`.

### SR-R2.2-09 — Concurrent durable commits could overspend unified quota

**Resolved:** one final reservation/admission authority accounts committed + pending bytes.

### SR-R2.2-10 — Progress protection lacked a unique source of truth

**Resolved:** narrow projection from existing Reader progress truth; no shadow progress persistence.

### SR-R2.2-11 — Process recreation wording implied RICC semantic offline reconstruction

**Resolved:** guarantee is image-byte reuse after Reader document/manifest reconstruction or reacquisition.

### SR-R2.2-12 — Durable key hashing was implementation-defined

**Resolved:** versioned canonical encoding + cryptographic digest + golden-vector tests.

### SR-R2.2-13 — Explicit download preemption could create cancellation churn

**Resolved:** use reserved capacity/queue priority/speculative Reader cancellation; active USER_WORK is not force-cancelled merely by Reader priority.

### SR-R2.2-14 — First consumed promotion could be lost behind access-touch coalescing

**Resolved:** first consumption is a prompt high-value retention promotion.

### SR-R2.2-15 — Byte limit alone did not bound metadata/inode growth

**Resolved:** finite image-manifest/page cardinality is mandatory.

### SR-R2.2-16 — R2 claimed existing module graph is preserved while architecture gate tightens a current edge

**Resolved:** preserve production module set/count; dependency edges may be intentionally tightened and verified.

### SR-R2.2-17 — `image-only` terminology was narrower than current persistence behavior

**Resolved:** normative wording is `image-bearing ReaderDocument` wherever any image block makes the current document non-persistable.

### SR-R2.2-18 — Network `UNKNOWN` had no RICC policy

**Resolved:** allow critical/bounded interactive; forbid speculation requiring confirmed unmetered state.

### SR-R2.2-19 — Session protection had no guaranteed owner-bound release

**Resolved:** owner Job/ViewModel/session teardown must idempotently call/reach `releaseSession`.

### SR-R2.2-20 — Room checksum optionality was weaker than existing durable-blob integrity expectations

**Resolved:** local persisted blob integrity digest is mandatory unless an existing mandatory equivalent is proven; source integrity remains optional/separate.

### SR-R2.2-21 — Storage adapter could gain a backwards Reader dependency

**Resolved:** `:downloads` remains the Reader-port implementation/orchestration owner; `:storage:files` stays a physical adapter behind a lower storage contract.

---

## 22. Freeze result

After the R2 repository audit, prior R2.1 hardening, and the additional current-master alignment above, no known unresolved Critical/High **design** contradiction remains in RICC-v1.

Remaining open values are implementation-policy constants or exact repository mappings, not permission to weaken the architecture:

- total process fetch slots;
- critical reserved-capacity shape;
- encoded asset maximum bytes;
- any future change to the frozen `ReaderDocumentSanitizer.MAX_BLOCKS = 2_000` manifest bound;
- retry count/backoff;
- high/low quota watermarks;
- access timestamp touch interval;
- concrete canonical encoding helper/type;
- concrete Coil local-data source/fetcher type;
- exact source trust/security policy adapters;
- exact session teardown seam;
- exact current plugin document-fetch body lifetime;
- exact current progress projection;
- exact final durable reservation implementation.

These MUST be selected in the detailed implementation plan from current source, tests, and measurement rationale.

**RICC-v1 R2 + this R2.2 consolidated hardening baseline are therefore frozen for implementation planning.**

---

## 23. Plan gate

The next artifact is a detailed implementation plan, not direct production implementation.

Before implementation, the plan MUST map and sequence at least:

1. the exact Reader sanitizer/document point where full image stable identity is retained;
2. the exact source/plugin capability facts, if any, that can authorize identity trust;
3. the exact security/persistence-scope adapter and conservative fallback;
4. the canonical key schema/version and golden vectors;
5. the contract tests tying RICC manifest cardinality to the current `ReaderDocumentSanitizer.MAX_BLOCKS = 2_000` bound;
6. Room schema-11 registration/guards and `MIGRATION_11_12` fresh-schema equivalence;
7. the physical file/blob API and mandatory local integrity digest;
8. the unified document+image automatic-cache inventory and final reservation authority;
9. Settings `automaticCacheQuotaBytes` wiring and quota-zero invalidation semantics;
10. the existing Reader progress projection used for retention protection;
11. exact `ReaderSourceExecutionLimiter` source/global/probe responsibilities to migrate without HES behavior regression;
12. current plugin response/body lifetime so arbiter ownership covers the real network transfer;
13. no-nested remote recovery paths, including same-selected-release locator/document refresh;
14. process-scoped single-flight and asset priority promotion;
15. session owner lifecycle and deterministic `releaseSession` path;
16. network `UNKNOWN` behavior;
17. cache `LOCAL_UNAVAILABLE` degradation path;
18. Coil model/fetcher integration while generic Reader disk cache remains disabled;
19. page-local Retry and typed route invalidation escalation;
20. architecture dependency cleanup, including review of the current `:feature:reader -> :downloads` edge;
21. exact application process model; any cross-process participating fetch path requires separate arbitration review;
22. focused TDD checkpoints, broad regression suites, connected/instrumented storage/Room tests, and final deep self-review.

The plan MUST NOT silently invent source trust, private-cache authority, or durability guarantees absent from current project contracts, and MUST NOT weaken HES-v1 to simplify RICC.
