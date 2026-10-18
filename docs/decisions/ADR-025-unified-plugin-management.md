# ADR-025: Unified management, native trust and frozen source activation

- Status: **Accepted; implementation present, device/provider verification pending**
- Date: **2026-10-18**

## Context

Hikari needs one place to manage explicit repository ownership and package lifecycle
across Manga, Anime and Novel without replacing existing source capabilities or
creating a second reader. Repository claims, installed files and running code are
different evidence. APKs execute with app privileges; downloaded JavaScript requires
its own exact code/origin consent, not inherited repository or signer approval.

## Decision

Keep repository identity, offers, selected update owner and inventory policy in pure
Dart domain/application contracts. Compose one app-owned management session; Settings
uses separate APK and Novel consent routes with injected gateways. Anime package
management does not imply an Anime content runtime.

Keep execution authority native and independent from the Dart persistence mirror:

- APK approval binds inspected package, family and verified current signer. OS-owned
  installation/removal uses native durable operation evidence; submission, external
  replacement and unknown outcomes are not success.
- Novel custody binds full artifact tuple and exact HTTPS origins. Staging is inert;
  admission may evaluate untrusted exports only after explicit consent. It establishes
  neither authenticity, JS safety nor provider compatibility. Historical rollback
  does not transfer repository ownership. Native committed evidence survives mirror
  reconciliation failure; evidence refresh never replays evaluation.
- Publication uncertainty locks Novel mutations until native process restart, even
  when error evidence is malformed. Confirmed, attempted and committed pointers must
  remain distinguishable.

Preserve immutable source composition and process-wide frozen runtime snapshots.
Management changes stored/OS state, never hot-replaces running sources. Removal does
not delete Library, Progress, catalog or continuation records. A loaded removed
revision remains evidence until restart, not proof that later operations will work.

Detailed lifecycle, validation, resource limits and platform constraints have one
canonical home: [EXTENSIONS](../architecture/EXTENSIONS.md). The bounded Novel engine
decision remains [ADR-010](ADR-010-bounded-lnreader-runtime.md). This ADR supersedes
[ADR-007](ADR-007-android-manga-extension-runtime.md)'s management/trust deferrals,
not its native ABI boundary or historical rationale.

## Alternatives considered

- **One generic runtime/installer:** rejected; Android APK trust and OS sessions are
  not JavaScript custody/admission. Sharing their authority would hide different
  failure and compatibility contracts.
- **Repository metadata or Dart approvals as execution authority:** rejected;
  self-declared signers/digests and a fallible mirror cannot authorize native code.
- **Automatic owner fallback, consent transfer or mutation replay:** rejected;
  ownership is explicit and ambiguous native outcomes cannot safely be resubmitted.
- **Duplicate-ID winner selection or partial publication after global capture failure:**
  rejected; ambiguous identity and incomplete custody/budget evidence must fail closed.
  Recoverable per-artifact issues remain isolated as specified in EXTENSIONS.
- **Parallel Dart activation/signer authority:** rejected; inventory and historical
  mirror rows cannot prove which exact native revision is running or trusted.
- **Hot mutable SourceRegistry:** rejected; it breaks startup-pinned references and
  introduces code replacement into active reader workflows.
- **Import upstream application stacks:** rejected; existing Hikari capabilities,
  readers and persistence already provide the content path.

## Consequences and evidence limits

Android has repository browsing/ownership, inspected APK trust and real package
controls, plus exact-consented Novel custody actions. Windows/iOS remain unsupported
for native management. No marketplace, process isolation, arbitrary module support
or universal provider compatibility is claimed.

Focused Dart/widget tests and analysis verify policy/UI contracts; Android debug
compilation verifies native integration only. Native fixture authorship/compilation
is not device execution. The Release-safe host harness can be checked with an isolated
stub, which is not QuickJS bounds evidence. Actual engine, device lifecycle and
provider compatibility verification remain separate gates.
