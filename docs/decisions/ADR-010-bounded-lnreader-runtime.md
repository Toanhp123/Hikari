# ADR-010: Bounded Android LNReader-compatible runtime

Status: Accepted with explicit compatibility limits.

## Decision

Use a separate JNI QuickJS build for LNReader-compatible CommonJS bundles:
reviewed APK-packaged code and downloaded artifacts admitted with exact native
consent. Admission evaluation and later content invocation are distinct operations;
only admitted current downloaded revisions enter the frozen content snapshot.
Existing quickjs-java `547f5b1597` exposes evaluate/compile and
Java bridging, but no heap limit, stack limit, interrupt handler or pending-job
pump. A Future timeout around that API cannot interrupt an infinite JS loop.
Do not use that wrapper for novel plugins.

Pin QuickJS 2025-09-13 archive and SHA-256 in CMake. Every operation gets a fresh
runtime/context: 32 MiB JS heap, 512 KiB JS stack, 5-second monotonic interrupt
budget, 4 MiB input/output ceiling. Promise jobs run under the same limits.
No QuickJS std/os modules or unrestricted module loader are installed. JNI uses
UTF-8 byte arrays rather than modified-UTF-8 JNI string conversion.

The host owns HTTPS origin allowlists, public-address DNS checks, request/header
validation, bounded responses and source-namespaced storage. Network workers are
fixed-size with a bounded queue; caller wait is bounded and cancels the call.
OS DNS cancellation is not guaranteed, so blocked lookups can exhaust this small
pool, producing failure rather than unbounded workers. Redirects are disabled.
Storage commit latency is OS-dependent. This is a resource-bounded VM trust
boundary, not native-process isolation: a native memory-safety crash can still
crash Hikari. Source invocation uses the process-wide frozen packaged/downloaded
snapshot described in [EXTENSIONS](../architecture/EXTENSIONS.md).
Downloaded bundles may execute for bounded export admission only after native exact
tuple consent, generation validation and byte reinspection, before pointer publication.
Admission denies network/storage/unknown host operations with a sticky native flag;
only pure URL resolution is allowed and no provider methods are invoked. Exact exported
identity and required callable methods are checked; this does not establish authenticity,
method result shapes, provider compatibility or JS safety. Schema-2 policy markers bind
the exact revision; schema-1 custody stays unknown until explicit consented admission.
Admitted current code is rehashed and captured under the artifact-store monitor before
content use. One lazy worker-initialized snapshot survives management mutations and
channel recreation; initialization failure/collisions fail closed until process exit.
Management evidence does not initialize/evaluate code and retains removed loaded tuples.
The 64 MiB host/code snapshot byte ceiling is not a JVM allocation bound. Invocation
validates arguments and consumed result fields, not universal provider compatibility.
JS exceptions/resource limits become sanitized admission rejection or normal source errors.

## Packaging and evidence

`npm ci --ignore-scripts --prefix tool/lnreader` followed by
`npm --prefix tool/lnreader run build` reproduces bundled Cheerio slim + Day.js.
Lockfile pins build-tool dependencies; generated license notices identify only
packages actually linked into host.js. Unimplemented modules are rejected.
No live provider is shipped. An explicitly enabled debug fixture exercises the
source-to-reader route without claiming production provider support.

The host-native CTest target is intended to exercise actual QuickJS timeout,
heap/stack exhaustion, pending promises, module denial and the bundled parser/date/
fetch/storage/URL contract. As of 2026-10-18, its harness uses always-on checks and
fixture IO validation even under `NDEBUG`, with a finite CTest timeout. Earlier
Release runs stripped side-effecting `assert` calls and are not bounds/contract
execution evidence. An isolated Release stub check proves harness call/check
execution, not engine behavior; actual host QuickJS execution remains unverified
in this change. Android debug builds compile JNI against the NDK, not execute it.
Dart tests exercise normalized metadata, paginated chapters and duplicate rejection.
None substitutes for real-provider/device compatibility or comprehensive host-network
security verification.

## Consequences

Windows/iOS return no LNReader sources. Adding providers requires reviewed packaged
manifests/bundles or exact consented native admission of downloaded artifacts, plus
legal provenance and provider-specific verification before claiming compatibility;
management UI and trust/custody rationale now belong to
[ADR-025](ADR-025-unified-plugin-management.md). More upstream module aliases,
browser features, redirect behavior and other HTTP policies must be implemented
and tested before declaring plugins that require them compatible.
