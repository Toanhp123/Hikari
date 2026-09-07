# V2 Capability Admission Contract

Every Step 2+ capability must complete this design-time review record before integration into the
app. Reviewers must reject omitted fields, unsupported claims, and entries that move ownership into
the app shell. This contract records ownership and evidence; it does not define runtime machinery.

## Normative performance rules

### PERF-01 — Working-set scope

A foreground/user-local operation scales primarily with the requested working set, not unrelated
historical/global state. Global maintenance must be explicit, separately owned, and budgeted.

### PERF-02 — Narrow read/allocation ownership

Consumers receive the smallest representation needed for their semantics. Large encoded payloads
and expensive derived representations have one clear owner; defensive full copies and eager
unrelated fingerprints are not the default.

### PERF-03 — Reactive scope matches semantic demand

Every observer declares its semantic key/cardinality, invalidation source, and lifetime. Table-wide
invalidation or application lifetime is not accepted merely because it is convenient.

### PERF-04 — Batch work has batch semantics

One logical batch uses bulk/delta APIs and must not repeatedly re-read or reprocess the
already-handled prefix through point APIs.

### PERF-05 — One execution owner per expensive work item

Foreground, durable recovery, worker, retry, and refresh paths may not race as independent owners of
the same logical item. Critical sections protect state transitions, not long I/O. Capacity/priority
is explicit for visible work.

### PERF-06 — Lifetime/aging is bounded

Persistent history, process maps, memoization, caches, retry state, and suppression state require
retention/reachability/eviction contracts. "Works on a fresh install" is not sufficient evidence.

### PERF-07 — Control plane does not imply payload execution

Metadata/policy questions must not load executable/package payloads by default. Stable terminal
failures must not amplify into repeated expensive work. Security-sensitive request construction
consumes bounded point policy and coherent credential state.

### PERF-08 — Performance evidence is part of the interface

Every real capability admission records the relevant scaling dimensions, query/work counts where
deterministic, destination latency, startup delta, and aged-state dimensions once that capability
owns persistent/history state.

## Required capability record

Copy this section into the capability design and complete every field. Use `Not applicable` only
with a concrete reason and evidence that the capability does not own the named concern.

1. **Activation trigger and owner**
   - Declare the user or system demand that activates the capability and the component that owns the
     transition.
2. **Deactivation / quiescence rule**
   - Define when active work and observation stop, including cancellation and process-lifetime
     behavior.
3. **Production dependency graph**
   - List every production dependency edge added to `:app` and the runtime graph constructed only
     after activation.
4. **Foreground working-set / cardinality**
   - Name the requested working set, its scaling dimensions, and the bound on unrelated/global work.
5. **Observer keys, invalidation scope, and lifetime**
   - Record semantic keys and cardinality, invalidation sources, sharing rules, and collector
     lifetime.
6. **CPU / I/O / network execution owner**
   - Identify the single owner for each expensive item plus concurrency, capacity, priority, and
     resource policy.
7. **Durable / background work owner**
   - Identify durable or worker ownership and prove foreground and background paths cannot execute
     the same logical item independently.
8. **Retention / aging / eviction**
   - Define reachability, retention bounds, eviction, and the aged-state conditions used for review.
9. **Failure / retry / terminal-state ownership**
   - Define failure lifetime, retry authority and budget, terminal-state persistence, and how stable
     failures avoid repeated expensive work.
10. **Benchmark / scaling delta**
    - Compare with the accepted baseline using relevant scaling dimensions, deterministic query/work
      counts, destination latency, startup delta, aged-state data, and repeated-activation checks.
