# ADR-010: Bounded Android LNReader-compatible runtime

Status: Accepted with explicit compatibility limits.

## Decision

Use a separate JNI QuickJS build for reviewed APK-packaged LNReader-compatible
CommonJS bundles. Existing quickjs-java `547f5b1597` exposes evaluate/compile and
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
crash Hikari. Only reviewed packaged code is admitted, not downloaded arbitrary
plugins. JS exceptions/resource limits become normal source errors.

## Packaging and evidence

`npm ci --ignore-scripts --prefix tool/lnreader` followed by
`npm --prefix tool/lnreader run build` reproduces bundled Cheerio slim + Day.js.
Lockfile pins build-tool dependencies; generated license notices identify only
packages actually linked into host.js. Unimplemented modules are rejected.
No live provider is shipped. An explicitly enabled debug fixture exercises the
source-to-reader route without claiming production provider support.

Host-native CTest execution in CI exercises actual QuickJS timeout, heap/stack
exhaustion, pending promises, module denial and the actual bundled parser/date/
fetch/storage/URL contract. The Android debug build separately compiles the JNI
integration against the NDK. Dart tests exercise normalized metadata, paginated
chapters and duplicate rejection. These tests do not substitute for real-provider
compatibility or comprehensive host-network security tests.

## Consequences

Windows/iOS return no LNReader sources. Adding providers requires reviewed
packaged manifests/bundles, legal provenance and provider-specific verification;
no generic installation/trust UI is introduced. More upstream module aliases,
browser features, redirect behavior and other HTTP policies must be implemented
and tested before declaring plugins that require them compatible.
