# ADR-009: Stable source identities and separate continuation

## Status

Accepted. Supersedes ADR-007's use of mutable continuation inside manga/chapter identity.

## Context

Library membership and reading progress use `SourceMediaRef` as a key. Embedding title, memo or chapter metadata in that key forks user state whenever a provider refreshes its metadata. Some extensions require exactly that mutable payload when continuing after restart, so dropping it is not safe.

## Decision

Mihon manga/chapter keys contain source ID, resource kind and provider URL only (`mihon-v2:`). Source-local SQLite continuation holds title, memo, chapter number, scanlator and upload date. The adapter persists continuation before returning discovery results and restores it before extension calls. Details refresh updates manga continuation. Missing/corrupt continuation is an explicit error.

Local archive identity contains the stable SAF locator and archive format (CBZ or EPUB); entry references additionally contain the archive entry. Display name is metadata and is excluded from Library/Progress keys. The unreleased encoding that included display name is not retained. A changed SAF locator still represents a different item; rename/move reconciliation is out of scope. Local archive mechanics do not share Mihon continuation storage.

UserDatabase schema 3 adds one infrastructure-only table. Valid generic `mihon-v1:` persisted references migrate with their state; duplicate keys retain latest progress and earliest Library membership, with lexical old item ID as deterministic timestamp tie-breaker. Unrecognized/malformed old references remain untouched. Version 1 and 2 unrelated user data is not reset. This does not reintroduce legacy MangaDex UUID compatibility removed by ADR-008.

## Consequences

No new domain state-store contract, dependency, canonical cross-provider identity or background reconciler is needed. URL changes still represent new source-local locators. Continuation survives source absence and database restart independently of Library membership. File-backed restart/migration tests cover Dart/SQLite behavior; actual extension loading and network fidelity still require Android verification.
