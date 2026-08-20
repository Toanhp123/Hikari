# Performance Wave P2 — Catalog, Room, and Chapter Pagination Design

## Goal

Reduce growth-dependent CPU and database work in catalog matching/home persistence and chapter pagination without changing user-visible semantics, Room schema, or public feature contracts.

## Scope

1. Catalog matching uses a reusable in-memory index for canonical stories, source identity, pre-normalized per-source evidence, and an inverted normalized-title-token shortlist. Evidence ranking must use a single scan rather than allocating/sorting a full result list.
2. Room catalog snapshots collapse stored source entries into one match candidate per canonical Story while retaining source-specific title/author/content-type evidence. Home observation converts/groups rows once per emission and observes only entries referenced by home items. Home refresh bulk-loads existing entries once instead of one `findEntry` query per item.
3. Chapter pagination loads one graph snapshot per page-sync run, updates a rolling in-memory graph after each successful page commit, and batches canonical-chapter restore operations.

## Constraints

- No Room schema/version change.
- No change to catalog matching thresholds, deterministic tie-breaking, or minimum-lead behavior when multiple source entries belong to the same canonical Story.
- A failed/invalid source page must not partially mutate the shared matching index.
- Chapter sync state remains durable after every successful page commit.
- Existing public repository/service interfaces remain source-compatible unless an internal-only type is introduced.
- Performance policies must fail if the removed hot-path patterns return.

## Verification

- Catalog unit tests cover direct source identity, deterministic evidence resolution, source-page atomicity, and canonical-story collapse behavior.
- Chapter sync tests assert snapshot count is independent of page count and output remains durable/deterministic.
- Room instrumentation tests retain home semantics and verify match snapshot collapses multiple source entries for one Story.
- `performance-wave-p2-policy-test.sh` guards against `sortedWith` evidence ranking, repeated story lookup scans, per-entry `findEntry`, all-entry home observation, and per-page `snapshot()`.
