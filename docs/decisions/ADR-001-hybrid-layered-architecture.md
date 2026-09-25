# ADR-001: Hybrid layered architecture with explicit application and infrastructure boundaries

- Status: **Accepted**
- Date: **2026-09-25**

## Context

Hikari must support several media families, multiple replaceable content providers, persistent progress/library data, multiple playback/reader engines, and Android/Windows/iOS platform integrations without coupling product rules to one framework or implementation.

A purely layer-first tree makes UI features easy to scatter across the repository. A purely feature-first tree makes shared domain concepts, providers, persistence, and engines easy to duplicate or couple to presentation concerns. A full Clean Architecture scaffold for every feature would add substantial ceremony before requirements justify it.

The project therefore needs stable dependency boundaries without creating speculative files, packages, or abstractions.

## Decision

Hikari uses a **hybrid layered architecture**:

```text
lib/
├── app/              # composition root, bootstrap, routing, global app configuration
├── core/             # very small cross-cutting primitives that belong to no domain
├── domain/           # pure Dart business concepts, rules, and ports/contracts
├── application/      # use cases and workflows that orchestrate domain contracts
├── infrastructure/   # replaceable technical implementations
└── features/         # Flutter presentation/state organized by user-facing feature
```

The tree above is an architectural map, not a requirement to create empty directories. A directory is created only when real code needs that boundary.

### Dependency direction

```text
features ─────────> application
   |                    |
   └────────────────────v
                      domain
                        ^
                        |
infrastructure ----------┘

app wires concrete implementations to their consumers.
core may be used only for genuinely cross-cutting primitives.
```

Rules:

1. `domain/` is pure Dart and must not import Flutter, database libraries, HTTP clients, media engines, or platform APIs.
2. Repository/provider/engine contracts belong with the domain concept that needs them, not beside their implementation.
3. `application/` owns cross-domain workflows and use cases. It can depend on domain contracts but not concrete infrastructure.
4. `infrastructure/` owns external-system details such as persistence, HTTP, provider implementations, media engines, and platform adapters.
5. `features/` owns Flutter UI and presentation state, organized by user-facing feature. It can depend on application workflows and domain contracts, but never concrete infrastructure.
6. `app/` is the composition root. It may know concrete implementations so it can wire the application, but it must not become a business-logic layer.
7. `core/` is intentionally small. Generic `helpers`, `managers`, or dumping-ground utilities are not accepted without a demonstrated cross-cutting responsibility.
8. Database records/DTOs are distinct from domain models and are mapped at the infrastructure boundary.
9. Provider-specific types must not leak into application or UI contracts.
10. Use cases and value objects are introduced when they enforce real rules or coordinate real workflows, not one-per-operation by default.

### Repository implementation placement

Repository implementations are not nested under a concrete data source such as `local/`. A repository can coordinate local persistence, remote sync, caches, or several services over time.

Preferred direction:

```text
infrastructure/
├── persistence/
│   ├── database/
│   ├── records/
│   └── mappers/
└── repositories/
```

### Source/provider model

A content source can expose one or more capabilities. The source subsystem should favor capability-oriented contracts over assuming each source belongs permanently to exactly one media type. The exact capability API remains a Domain Core decision and is intentionally not scaffolded by this ADR.

## Consequences

### Positive

- Domain and application behavior remain testable without Flutter or platform dependencies.
- Storage, networking, provider runtimes, players, and native integrations can be replaced behind stable contracts.
- UI remains feature-oriented without forcing shared infrastructure into feature folders.
- Cross-repository workflows have an explicit home instead of accumulating inside widgets, state objects, or repositories.
- The repository can grow incrementally instead of starting with a large empty Clean Architecture skeleton.

### Trade-offs

- The project has more conceptual boundaries than a minimal Flutter app.
- A repository architecture guard enforces the mapped dependency direction, but semantic separation still requires review; see ADR-003.
- Some simple features can legitimately call a domain repository directly until an application use case is justified. The architecture must not require pass-through use cases solely for symmetry.

## Rejected alternatives

### Pure layer-first architecture

Rejected because presentation features become fragmented as the app grows and shared technical layers can become excessively broad.

### Pure feature-first Clean Architecture for every feature

Rejected for now because Hikari has several genuinely shared domain and infrastructure subsystems, and duplicating `data/domain/presentation` inside every feature would add ceremony before it provides value.

### Full architecture scaffold at bootstrap

Rejected because empty folders and speculative interfaces create false certainty. Structure is added when executable code proves the need.
