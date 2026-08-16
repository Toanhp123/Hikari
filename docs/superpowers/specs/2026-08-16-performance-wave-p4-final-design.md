# Performance Wave P4 Final Design

Date: 2026-08-16

## Goal

Close the project-wide performance pass with behavior-preserving CPU/allocation improvements and a deterministic measurement path. P4 deliberately avoids speculative UI/startup rewrites when the current code has no demonstrated startup work to remove.

## Reader hashing

`ReaderDocumentSanitizer` previously built the complete canonical document text and then encoded it again before SHA-256. Documents are allowed up to two million characters, so this creates a large transient String plus UTF-8 bytes on the Reader load path. P4 feeds the exact existing canonical byte sequence directly into `MessageDigest` and uses a fixed lowercase hex table. Block IDs and document fingerprints remain byte-for-byte compatible with the previous implementation.

## Reader cached-load fast path

`ReaderDocumentRepository` previously enumerated enabled plugin Reader sources before trying local cached content. A progress fingerprint already identifies the exact cached document needed for normal resume/next navigation, so P4 defers `ReaderDocumentSourceRegistry.enabled()` until the first cache miss. Candidate priority remains unchanged: each selected candidate still gets its cache attempt followed by its source fallback before the next alternate is considered.

## Chapter aggregation

`ChapterAggregationEngine` previously materialized, filtered, and sorted every candidate chapter for every release even though only the best candidate is consumed. P4 scans existing and newly-created candidates once, keeping the highest score and the existing lexicographic chapter-id tie-break. Aggregation plans remain semantically identical.

## Deterministic benchmark fixture

Story/Reader Macrobenchmarks previously required an external `benchmarkStoryTitle` and pre-seeded data. P4 adds a `benchmarkRelease`-only Hilt Activity registered only in the `benchmarkRelease` manifest. It upserts a deterministic catalog story, library membership, 12 canonical chapters/releases, cached Reader documents, and progress fingerprints. Chapter 1 is the only incomplete progress row so the Story action deterministically exercises the Resume path from chapter 1 and leaves ten valid Next actions. Benchmark instrumentation launches this fixture outside measured iterations, waits for an explicit ready marker, and opens the fixture Story by its stable Library card tag rather than by child title text.

The fixture is absent from normal production source sets/manifests. An instrumentation argument may still override the story title for targeted developer experiments, but the default benchmark path is self-contained.

## Measurement coverage

Existing interaction benchmarks keep `FrameTimingMetric` alone. P4 adds a separate cold-start Macrobenchmark using `StartupTimingMetric` with `StartupMode.COLD`, and a separate Reader journey using `MemoryUsageMetric(Mode.Max)`. This keeps memory sampling from perturbing the frame-timing journeys while adding startup and peak-memory evidence.

`OpenStoryApplication` remains empty; P4 measures startup instead of adding speculative startup code changes.

## Verification

P4 requires fixed fingerprint/block-id vectors, randomized Reader old-vs-new equivalence, randomized chapter aggregation old-vs-new equivalence, performance lifecycle/Wave 4/P1/P2/P3/P4 policies, architecture/schema/static gates, focused Gradle tests, benchmark assemble, and device Macrobenchmark runs. No Room schema migration is allowed.
