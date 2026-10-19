# Android extension runtimes

## Scope

Hikari has an Android-first runtime for **installed, trusted Keiyoushi/Mihon-compatible manga extensions** using extension-lib `1.4` and `1.6`. The runtime is an infrastructure mechanism that creates normal Hikari `MediaSource` implementations before `SourceRegistry` composition; domain and application code do not depend on the extension ABI.

```text
installed extension APK
        |
        v
Android package discovery + trust/version checks
        |
        v
extension-lib 1.4/1.6 host ABI + class loader
        |
        v
MethodChannel gateway
        |
        v
MihonMangaSource
        |
        v
MangaSearchSource + MangaSeriesSource + MangaPageSource
        |
        v
SourceRegistry / existing application workflows
```

This is intentionally **not** a universal plugin engine. Version 1 proves the manga content path end to end and keeps the extension-specific surface behind Android infrastructure.

## Why the host is native Android

Keiyoushi extension APKs are compiled Kotlin/Java Android code. They intentionally compile against host-provided `eu.kanade.tachiyomi.*` APIs and common runtime libraries rather than bundling a self-contained Dart/JavaScript plugin. Running them in Dart would require reimplementing their ABI and Android assumptions through a second RPC layer.

Hikari therefore hosts the ABI in Kotlin and exposes only normalized manga operations to Dart. The Dart side knows source descriptors, search results, chapters and pages; it never loads APK classes or depends on extension-lib types.

## Supported ABI and dependencies

The Android host implements the manga-facing compatibility surface used by extension-lib `1.4` and `1.6`:

- source contracts and models (`Source`, `CatalogueSource`, `HttpSource`, `SManga`, `SChapter`, `Page`, filters);
- network helpers and request functions;
- Injekt registrations used by current Keiyoushi core helpers;
- JSON/ProtoBuf, OkHttp, coroutines, RxJava, Jsoup and QuickJS runtime dependencies expected by extension APKs.

Dependency versions are pinned to the current Keiyoushi extension build baseline instead of floating independently. The host keeps the ABI split inside Kotlin: legacy 1.4 Rx operations are bridged into the same suspend calls used by Hikari, while 1.6 sources use their native suspend API. Extensions declaring another ABI version are ignored rather than loaded optimistically. For older APKs that predate `tachiyomix.extensionLib`, the loader follows Mihon's compatibility rule and derives `1.4`/`1.6` from the leading part of `versionName`.

Release packaging retains `com.squareup.zstd.**` classes and members: `libzstd-kmp.so` resolves compressor/decompressor class names and processed-byte fields through JNI. Disabling obfuscation alone does not prevent R8 from removing or merging these classes. Check an actual shrunk release APK with `python -I tool/check_zstd_release.py <apk> --apkanalyzer <Android SDK apkanalyzer>`; this checks defined classes and declared members without adding test references to the app.

## Discovery, trust and loading

At app startup the Android bridge enumerates installed packages and accepts a package only when all of these are true:

1. it advertises the Android feature `tachiyomi.extension`;
2. its verified current signing certificate matches built-in trust or an explicit native package/family/signer approval;
3. its manifest declares a non-empty `tachiyomi.extension.class` entry point;
4. its extension-lib version resolves to `1.4` or `1.6`;
5. every created source is a supported HTTP manga source and source IDs do not collide.

The official Keiyoushi current signing key remains built-in trust. The native plugin manager additionally stores explicit package/family/current-signer approvals in one private AtomicFile store, consulted before class loading. Approval requires reinspection of an installed package or validated opaque APK stage, never a repository declaration alone. Multisigner APKs and signer replacement/rotation are currently rejected; authenticated signing history is reported but never treated as an any-historical-signer allowlist. API 24–27 report verified current signatures without fabricated rotation history. Settings provides separate inspected signer approval and package actions; repository metadata never grants execution trust. An extension runs code inside the Hikari process; package metadata alone is not a security boundary.

On Android 8.1+ the installed APK path is loaded with `DelegateLastClassLoader`, keeping extension-bundled implementation classes ahead of host classes while resolving the compile-only `tachiyomi.*` ABI from Hikari. Android 8.0 uses `PathClassLoader`.

Colliding Manga source IDs fail closed for **every** package claiming the same ID, independent of package discovery order. A displaced package contributes no sources to the frozen registry, while unrelated source IDs remain usable. A later package cannot reclaim an ID already identified as ambiguous in that startup snapshot.

Managed class-loading/initialization failures can reject a package, and managed operation failures can propagate through the bridge. Native process aborts (for example, JNI initialization calling `SIGABRT`) cannot be caught or normalized by Kotlin/Dart exception handling; extensions and shared native libraries execute inside Hikari's process.

`QUERY_ALL_PACKAGES` is currently used for package discovery. Hikari is not targeting Play Store distribution, so the v1 runtime does not add repository-specific `<queries>` generation solely to satisfy Play policy.

## Plugin management cleanup boundaries (2026-10-19)

- `PluginManagement` owns immutable-inventory list replacements. The APK and Novel
  native evidence readers fail independently: one family failing retains that
  family's last confirmed snapshot without blocking the other family from loading.
- Combined persisted `PluginManager` inventory reconciliation remains **atomic**.
  If any family is unavailable, do not publish a partial installed list and
  do not treat missing rows as removal. The manager may open for browsing and
  native-guarded actions using its previous persisted state, but sets
  `inventoryFresh == false`; update candidates are disabled until full
  reconciliation succeeds. Native trust and mutation evidence are never inferred
  from the persisted mirror.
- `PluginManagerViewModel` owns repository-browsing query, filters, and command
  status. Action ViewModels own presentation-level busy/error state. Consent
  dialogs, foreground/epoch guards and platform-authoritative work remain in
  explicit action pages and application/native gateways; view models never
  grant trust, publish a native result or hot-replace running sources.
- `ApkStages.kt` owns process-local inspected staging; `PluginTransport.kt`
  owns bounded HTTP transfer. Splitting the source files does not change the
  native channel protocol or package install lifecycle.

## Bridge operations

The platform bridge exposes five operations:

- list installed compatible sources;
- title search;
- resolve manga to chapters;
- resolve chapter to pages;
- read page bytes.

The registry is composed once during bootstrap. There is no hot loading/unloading during the current app process; installing or updating an extension requires restarting Hikari before the source list changes.

## Identity and extension state

Upstream source IDs are stable `Long` values. Hikari maps every extension source, including MangaDex, to:

```text
SourceId("mihon:<upstream-source-id>")
```

Source-local URLs remain opaque locators. Manga/chapter identities now use `mihon-v2:` resource-kind plus URL only. Extension-lib 1.6 `SManga.memo` / `SChapter.memo`, title and chapter number/date/scanlator remain provider continuation, persisted separately before references are returned. Extension-lib 1.4 uses the same storage without memo. See [REMOTE_MANGA](REMOTE_MANGA.md), [USER_STATE](USER_STATE.md), and [ADR-009](../decisions/ADR-009-stable-source-identities.md) for migration and restart contracts.

Normalized publication status includes native `publishingFinished` and `onHiatus`; raw status preserves the original numeric extension status code as text, including unknown codes.

No legacy MangaDex identity compatibility or UUID translation is retained. Hikari intentionally accepts a clean app-data reset at this stage, not migration of pre-refactor Library/Progress rows; see [ADR-008](../decisions/ADR-008-external-remote-provider-ownership.md).

Only installed compatible extensions supply remote manga providers at startup. If an extension is absent when the immutable runtime/registry is captured, its source is unavailable and existing generic missing-source handling applies; persisted rows remain intact. Removal after capture does not unload that source: evidence retains its loaded revision until restart, although later operations may fail. Reinstalling it and restarting Hikari restores resolution. Windows/iOS currently have no remote manga runtime or replacement provider. See [ADR-008](../decisions/ADR-008-external-remote-provider-ownership.md) for this change in provider ownership.

## Network behavior

The host provides a shared OkHttp client, WebView-backed cookies, a stable mobile default user agent, cache/timeouts and the interceptor class names expected by current `KeiSource` code. Android cleartext traffic is enabled because the community tree still contains HTTP sources. Page responses are bounded to 32 MiB before crossing the MethodChannel.

The `CloudflareInterceptor` is currently a compatibility hook, not a browser challenge solver. Extensions that require interactive Cloudflare/WebView challenge handling can therefore fail even when their ABI is otherwise compatible. Source-specified User-Agent headers are preserved; the host default is inserted only when a request does not provide one. That limitation is explicit rather than hidden behind automatic retries or source-specific hacks.

## Plugin manager

Decision rationale and rejected alternatives: [ADR-025](../decisions/ADR-025-unified-plugin-management.md).

`domain/plugins` separates repository identity (normalized explicit HTTPS manifest
URL), ecosystem-scoped package identity, per-repository offers, source descriptors,
installed inventory, update ownership and explicit consent. `application/plugins`
coordinates repository lifecycle against durable ports without mutating
`SourceRegistry`. Native runtime snapshots alone report exact loaded revisions;
Dart inventory and consent never imply activation. Inventory changes require restart.
Anime inventory is represented but its runtime is deferred.
Repository removal preserves inventory, ownership and consent; refresh failure keeps
the last good snapshot. Repository metadata never automatically approves a signer.

`infrastructure/plugins` parses bounded LNReader JSON and manga/anime v2
JSON/protobuf metadata, including bounded gzip and index chains. Legacy APK paths
use only that format's documented sibling convention; explicit APK URLs take
precedence. Novel site and auxiliary asset URLs remain metadata; auxiliary JS/CSS
are marked unsupported, never evaluated or injected. App-support `plugins.json`
stores versioned validated state using a flushed temporary file and same-directory
replacement, separately from user database schema 6 and reconstructible caches.
Historical schema-1 Dart `operations` rows are validated but not restored to
application state. The next successful save discards them and retains an empty
`operations` array for schema-1 reader compatibility.
Native `ApkInstallJournal`/OS evidence owns live install/removal outcomes;
legacy Dart rows are never replayed, promoted to success or used as authority.
When aggregate inventory is unavailable, reconciliation retains the previous
snapshot and cannot persist a partial inventory.

The Android `hikari/plugins` management channel supplies inspected manga/anime
inventory, inert metadata retrieval, APK staging and explicit native trust approval.
Inventory does not load code or claim source activation. Manga libraries 1.4/1.6
and anime libraries 14/16/17 are recognized; anime runtime remains deferred. Staging
requires exact selected package, family, version and signer expectation, rejects
downgrades and replacement of existing non-extension packages, and publishes only
fsynced platform-inspected bytes in app-private storage. Platform archive inspection
is not an independent cross-OEM verifier or proof of OS installability. Staged
approval records signer consent; future OS installation must reconcile installed
identity before code loading. Installed approval reinspects the OS-owned package.
Stages are process-local, capped at four tokens and 64 MiB each, explicitly
releasable on dismissal and removed on close/restart. No automatic eviction of
reviewed tokens occurs. Staging tokens are distinct from the durable installer sessions
and journal described below. Metadata caps at 8 MiB.
Request IDs register before queueing (one active plus four queued); cancellation
retains only accepted requests through redirects, and terminal/rejected/closed
requests release their own handles. Unknown or completed cancels retain nothing.
Network calls enforce a 60-second overall deadline,
credential-free HTTPS, at most five validated redirects, public literal addresses
and every socket DNS result. Private, reserved, documentation and transition
networks are denied; ambient proxies are disabled. The same public-address policy
hardens the existing novel host without enabling content redirects or changing VM
budgets.

`ManageApkPlugins` waits for native durable approval before inventory reconciliation;
Dart reconciliation failure is explicit and does not pretend to roll back native
consent. Native trust is not copied into independent Dart approval authority.
Dart rejects new signer approvals; legacy serialized signer rows roundtrip as inert
history. Novel code-digest/origin consent remains a post-native-success mirror.
Updates lists discover only strictly newer offers from the explicitly selected owner,
independent of signer claims or consent; native staging, approval and installation
alone determine whether an APK action is allowed.

Android install/update uses real `PackageInstaller` sessions and a separate
app-private `AtomicFile` operation journal. A process-wide synchronized coordinator
and explicit non-exported receiver own outcomes independently of Flutter lifetime.
Operations use opaque UUIDs, at most 100 history entries, and one unresolved
operation per package. Expected family/package/version/current signer and stage
identity persist before session creation; an operation label identifies an orphan
owned session across the precreate/session-ID write gap. Size and package name are
set before creation. Native trust and private stage identity are checked again
before session copying and fsync. Stage bytes release only after durable session
ownership transfer; failed preparation abandons its created session where possible.
API 31+ explicitly requires user action; callbacks use unique operation/session
identity and an explicit mutable, reusable broadcast `PendingIntent`.

Session submission returns durable pending state, never success. Callback success
requires exact compatible installed OS family/package/version/current signer.
Success-callback receipt persists before installed inspection so transient inspection
failure can retry reconciliation on open without losing callback evidence.
Inventory/status open reconciles owned sessions and installed identity without
replaying commit or inferring outcome from session disappearance. A matching revision
without a success callback is separately `externally_installed`, not manager success;
it remains unresolved until callback or explicit retirement so an owned session
cannot be forgotten. Pending confirmation retains only the genuine system
`EXTRA_INTENT` in memory. Confirmation lookup and coordinator initialization run on
the bounded worker, never Android main thread. Main-thread dispatch uses a per-ticket
nonblocking validity guard plus latest foreground/lifecycle epoch and closed checks;
cancellation, retirement and callbacks invalidate stale tickets before state changes.
No global installer IO lock is acquired on main. Explicit foreground continuation
launches the genuine Intent; receiver never launches Activities. After process death a lost confirmation becomes
interrupted following inventory/session reconciliation. No Intent serialization,
automatic recommit, background launch, or automatic abandonment of potentially
installing sessions is used. Inactive awaiting-result sessions become interrupted,
not failed. Explicit cancellation abandons only an owned session and reinspects
installed identity. Only a known uncommitted API 31+ session can report cancelled
from successful abandonment; committed/missing sessions retain unknown outcome
until an authoritative callback or explicit user-directed retirement. Retirement
reconciles installed identity, checks all owned sessions related to the operation or
package, and optionally abandons confirmed owned sessions only when explicitly
requested. It then re-queries ownership and session info; remaining sessions or query
failure deny retirement. With no remaining session, terminal `retired_unknown`
preserves expected identity/session/callback evidence and truthful unknown-outcome
reason, distinct from success or cancellation. This releases the package operation
lock on all supported APIs, including precreate crashes and lost-callback/external
revision cases. Late callbacks cannot rewrite retired history or affect a new
operation. Lost confirmation may therefore require explicit retirement before retry,
not merely cancel-and-retry. Install permission is gated by
`canRequestPackageInstalls`; explicit foreground settings opens normal system UI.

Dart exposes typed operation polling and separate inventory reconciliation; failures
preserve native operation evidence and do not claim activation. The Dart repository
foundation is composed once into app bootstrap without blocking readers. Settings exposes
Available/Installed/Updates browsing, explicit repository ownership, and repository
add/refresh/remove. A separate APK route exposes inspected staging, full signer evidence,
explicit native signer approval, permission/settings, install/update, identity-bound removal,
and durable operation controls. Polling is serial and route-visible/foreground only;
permission return never auto-submits. Leaving cancels transfers/discards stages, never
submitted installer sessions. Unknown removal retirement requires explicit acknowledgement
that old system UI cannot be revoked. Native evidence survives Dart reconciliation failures;
no confirmation launch, external revision or retirement implies success. A separate Novel
route stages inert bytes with site-origin-only defaults and explicit extra exact HTTPS
origins. Install, unknown-policy re-admission and rollback display full frozen tuples
before evaluation consent; rollback displays both current and historical target. Async
boundaries recheck foreground, owner, offer and inventory. Ambiguous install consumes its
submission token; refresh retries evidence/reconciliation, never evaluation. Native
publication-unknown or pointer-sync failures lock all Novel mutations for the app-owned
session, including malformed error evidence and route recreation. Confirmed, attempted
and committed evidence remain distinct; only native process restart can reopen custody.
Removal preserves Library, Progress and references. Anime runtime remains deferred.
Initialization errors are visible/retryable without replacing persisted consent.
Unsupported platforms show an Android-only notice and perform no native calls.
Typed native inventory/trust and exact frozen runtime evidence remain independent,
including removed loaded revisions and deferred Anime; no generic manager activation
state duplicates native evidence.
The existing LNReader source loader now consumes the native frozen packaged/downloaded
snapshot described below. Novel admission binds plugin identity, version, owner, code
digest and exact approved origins; digest-only manager status is not proof of runtime
admission or compatibility.

Android removal uses normal `ACTION_UNINSTALL_PACKAGE` with `EXTRA_RETURN_RESULT`,
including compatible externally installed Manga/Anime extensions where OS permits.
Native reinspects exact displayed package/family/version/current signer, rejects self
and non-extensions, and shares the durable per-package mutation guard with installs.
Worker prepares durable request binding; foreground main dispatch uses the existing
nonblocking ticket/lifecycle guard. Prepared and dispatched removal tickets survive
result-less polling, but can launch at most once; real results invalidate tickets.
Process restart loses dispatch tickets and retains unknown outcome without replay.
Submission is not success. Result receipt persists
before fresh OS presence lookup; only result OK plus absence is manager success.
Missing-before-request, removal without success result, cancellation and unknown are
distinct. Restart never replays removal; inventory absence alone does not clear an
unresolved dispatched removal. Request codes never reuse within app journal lifetime
(bounded at 16,384 removal requests); exhaustion fails explicitly.

Public Android removal Intents provide no version fence or durable revocation API.
An outstanding dialog can therefore remove a replacement. Explicit retirement of a
dispatched unknown removal requires `acknowledgeUnrevokedRemoval=true`: caller must
warn user to dismiss outstanding system confirmation and acknowledge that Hikari
cannot verify revocation. This is `retired_unknown`, not cancelled/succeeded; history
preserves request/result evidence, related owned install sessions must be gone, and
late results cannot rewrite retirement or a replacement. No timeout implies cancel.
No best-effort `finishActivity` relationship tracking is implemented in this slice.

Novel artifact staging is inert custody, not runtime admission. Native staging
uses the existing bounded HTTPS transport with a separate private directory, four
process-local tokens, and a 1 MiB strict UTF-8/nonempty/no-NUL bundle limit. Staging never
evaluates JS or discovers metadata through execution. Selected offer metadata stays
untrusted: ID, name, numeric version, explicit repository owner, site and 1–32 exact
HTTPS origins are frozen with the native SHA-256 digest. Explicit install consent
must match that complete tuple after private-byte reinspection. Owner changes need
fresh consent; downgrade is refused outside explicit rollback.

One process-wide native AtomicFile store commits exact current/previous consent and
artifact pointers together, after immutable code publication. New private directory
entries and artifact destination/source directories are fsynced before pointer commit;
a publication-sync failure leaves prior pointers and generation unchanged. A persisted
generation rejects stages after any intervening artifact mutation, including remove/reinstall.
Failure after moving stage bytes can leave an inert orphan and an unretryable token:
explicitly discard/restage; restart never promotes stages or orphan files.
AtomicFile finish returning normally does not prove publication: native code confirms
exact bounded base-file bytes before advancing memory/generation or collecting code.
Mismatch/read failure reports `novel_publication_unknown` with publication `unknown`,
last-confirmed inventory and attempted pointers; prior memory/generation stays unchanged,
all possibly referenced artifacts stay retained, and mutation/collection blocks until reopen.
Reopen reads actual persisted pointers without promoting stages/orphans. After confirmed
publication, the state directory is synced before code collection. A postcommit
directory-sync failure separately reports `novel_pointer_sync_failed` with committed
inventory evidence, blocks further mutation/collection until reopen, and never claims
rollback. Silent-finish and exception injection check ordering and recovery, not physical
power-loss behavior across Android filesystems.
Inventory rehashes referenced bytes and reports ready/missing/corrupt honestly, with
admission unknown/admitted independently of activation, and restartRequired true.
State is capped at 1 MiB/500 entries;
code/stages/state share a 64 MiB budget with atomic-write headroom. Collection keeps
only pointer-referenced current/previous code and never recursively clears user data.
Rollback requires exact displayed previous consent and valid bytes. Uninstall removes
artifact pointers/code only, not source storage, Library, Progress or catalog records.
Dart approval is a post-native-success mirror, never execution authority; typed
reconciliation failures retain native inventory evidence.

Install validates exact displayed tuple, stage generation and rehashed bytes before
bounded admission evaluation and before pointer publication. Explicit `novelAdmit`
likewise validates the displayed current revision before evaluating existing code.
A fresh QuickJS context inspects exact exported ID/name/version/site and callable
searchNovels/parseNovel/parseChapter; parsePage is optional but must be callable if
provided. Provider methods are never invoked during admission, so result shapes and
provider compatibility are not proven. The admission host lazily avoids network and
preferences, allows only pure absoluteUrl, and records denied host attempts even when
JS catches them, including getters/promise jobs/serialization. Unsupported modules
remain rejected. Errors expose sanitized `novel_admission_rejected` with fixed reason
labels for denied host operations, unsupported modules, metadata mismatch, missing methods
or undifferentiated execution rejection; never raw JS exceptions/source.
Schema 2 persists policy-version markers bound to complete exact current/previous
artifacts in the same atomic state. Schema 1 reads unknown/inert without evaluation;
restart never supplies missing consent or admission. Rollback admits an unknown/older-policy
previous revision before pointer publication. Failed admission leaves prior pointers,
generation and evidence unchanged. Markers prove bounded export inspection only,
not authenticity, JS safety, universal compatibility or activation; see ADR-010.
LnReaderChannel listSources/invoke/readResource share one process-wide immutable
snapshot, initialized lazily on a content worker, never on channel construction or
management evidence reads. Under the store mutation monitor, capture rehashes admitted
current bytes and copies their exact tuples; schema-1/unknown, missing and corrupt
revisions are skipped with exact-revision issues. Packaged and eligible downloaded ID
collisions fail the whole snapshot. Failure policy has seven categories:

| Failure | Publication and evidence |
| --- | --- |
| Unknown/old admission policy | Skip exact downloaded revision; `admission-unknown` issue. |
| Missing artifact | Skip exact downloaded revision; `missing` issue. |
| Artifact-local read/validation failure, invalid UTF-8 or digest mismatch | Skip exact downloaded revision; `corrupt` issue. |
| Packaged asset/descriptor failure | Reject whole snapshot; `snapshot-failed`. |
| Aggregate host/manifest/code budget exhaustion | Reject whole snapshot; `snapshot-failed`, never partial sources. |
| Store/capture failure (including fatal runtime errors) | Reject whole snapshot; `snapshot-failed`. Fatal errors propagate, not reclassified as corrupt artifacts. |
| Duplicate source ID (downloaded/downloaded, packaged/downloaded or packaged/packaged) | Reject whole snapshot; `collision`, never choose a winner. |

Initialization failure is sticky until native process exit, including when a fatal
error interrupts capture and the process survives. Retry never reopens/captures/evaluates
code. Evidence reads return sanitized fixed labels and exact revision issues only,
without IO, capture or JS evaluation; no raw exception paths are exposed.
Host, manifests and all captured code share a 64 MiB byte ceiling (not a JVM heap bound);
the downloaded per-bundle ceiling remains 1 MiB.
Updates, rollback and uninstall do not replace captured code or reopen artifact files;
removed loaded revisions remain in `novelRuntimeSnapshot` evidence for restart reporting.
Evidence never initializes/evaluates JS or opens the artifact store. Native and strict
Dart codecs distinguish initialization/failure, exact loaded tuples and per-revision
issues; admission is not method compatibility. Native invocation validates actual
adapter arguments/results, including pagination requiring callable parsePage only when
totalPages exceeds one. Existing Dart pagination, aggregate chapter limit, duplicate
rejection, source IDs and source-namespaced storage remain unchanged. HTTPS origins
canonicalize default :443 and trailing dots without admitting subdomains or other ports.
Resource/network/storage/VM boundaries remain those in ADR-010; no new modules or
customJS/CSS execution are enabled. Authored Android fixtures are compile evidence
unless separately executed; real-provider replay remains separate verification.

Mihon content and management use one process-wide runtime. `runtimeSnapshot` never
triggers loading: it reports actual captured loaded source versionCode/current signer,
initialization state and sanitized package errors (`untrusted`, `incompatible`,
`collision`, `load_failure`). Inventory changes never replace this loaded snapshot;
sourceKey identities stay unchanged and SourceRegistry remains immutable. Package
identity is checked before and after class loading; this is not an atomic OS version
fence against concurrent external replacement. Source preferences remain unsupported.
Management removal never deletes Library, Progress, catalog or continuation records.

## Current non-goals

Current implementation does not include:

- a hosted marketplace or automatic repository discovery;
- private `.ext` installation inside Hikari storage;
- uninspected arbitrary signing-key trust or automatic signer rotation;
- source preference UI projection into Flutter;
- anime/video content execution (Anime repository/inventory/APK management exists);
- hot reload/unload of extension packages;
- process isolation/sandboxing;
- a claim that every Keiyoushi source works without source-specific host features such as browser challenge handling.

These are separate requirements. They should be added only when a tested source or product workflow demonstrates the need.

## Verification

Automated Dart tests cover source adaptation, capability registration, generic MangaDex identity, opaque persisted references across extension absence/reinstall, stateful memo round-tripping, malformed references and source ownership. Native loading still requires physical Android verification because package visibility, certificate data, ART class loading and extension network behavior are platform concerns.

A user-reported Android E2E run on October 19, 2026 confirmed APK
install/removal and Manga plugin search/reading. This report does not replace
the collision regression test or establish compatibility with every extension.

A useful device smoke test is:

```text
1. install an official Keiyoushi extension APK (for example MangaDex)
2. restart Hikari
3. confirm the source appears in remote manga search
4. search -> series -> chapter -> pages -> reader
5. save progress and Library state
6. restart Hikari and repeat open/resume
7. update/reinstall the extension, restart Hikari, and verify identity remains stable
```

## Android novel runtime

LNReader-compatible reviewed bundles use a **separate resource-bounded QuickJS
runtime**, not the unrestricted manga compatibility wrapper. See
[ADR-010](../decisions/ADR-010-bounded-lnreader-runtime.md) for trust boundaries,
limits and reproducible packaging. `LnReaderSourceLoader` registers normalized
`NovelSearchSource`, `NovelSeriesSource`, `NovelChapterSource` and `ArtworkSource`
capabilities. Stable references contain source ID, entity kind and original path;
provider metadata never changes identity. HTML is sanitized, images become
source-owned resource references, and image requests use the plugin export's
`imageRequestInit`. Sanitized rich chapter HTML and normalized image references enter
Hikari's remote novel cache only after adapter normalization; each uses an independent
versioned namespace and byte budget. A cache-version change is required when that
normalization/security representation changes. Explicit reader reload bypasses stored
content; local text and publication routes do not use these remote cache workflows.
There is no immutable plugin revision in the normalized source contract, so adapter
configuration/content changes can remain cached until reload, eviction, cache clear or
namespace-version change. See [ADR-012](../decisions/ADR-012-reconstructible-cache-foundation.md).

Supported calls: `searchNovels(term, page)`, `parseNovel(path)`,
`parseChapter(path)` and optional `parsePage(path, pageString)`. The upstream
`SourcePage` contract is `{ chapters: [...] }`; `parseNovel` supplies its first
page, including when that first page is empty; aggregation starts at page 2.
Aggregation allows at most 100 pages / 50,000 chapters and rejects duplicate
chapter paths rather than silently dropping data. Search pagination remains
unknown because upstream returns an array, not a next-page flag. Ratings retain
upstream's documented 0–5 scale; release labels and scanlator lists are retained.

LNReader v3 compatibility surface: `cheerio` (real slim/htmlparser2 build),
`htmlparser2` (`Parser`), `dayjs`, `@libs/novelStatus` (`NovelStatus`),
`@libs/filterInputs` (`FilterTypes`), `@libs/fetch` (`fetchApi`, `fetchText`),
`@libs/storage` and `@libs/url` (`absoluteUrl(base, path)`). The host additionally
provides bounded text `FormData` and a minimal `URL` (href/toString) plus
`URLSearchParams` subset. The Kotlin host constructs multipart/form-data bodies
with its own boundary, 32-field / 64-KiB cap, restricted names and fixed origin
policy; it rejects binary file fields and user-forced multipart Content-Type.
These capabilities target actual imports used by upstream 69shu, Hako and Light
Novel VN, not a claim of complete WHATWG/browser compatibility. The
`tool/lnreader/contract.js` fixture and Android `NovelAdmissionTest` exercise
bounded, mocked host contracts; online provider success still needs separate
verification. Unsupported modules remain denied and no provider methods execute
during admission.

Native host requests support GET/HEAD/POST/PUT/PATCH/DELETE, selected bounded headers (including DNT and Upgrade-Insecure-Requests required by 69shu),
1 MiB response bodies and source-manifest HTTPS origins. Redirects are disabled;
providers requiring redirects, WebViews/challenges, arbitrary modules or other
header names remain unsupported. Persistent storage is source-namespaced,
bounded to 128 keys and 64 KiB. No arbitrary script arrives through MethodChannel.
The 5-second operation deadline and 16-request cap can still reject long or
multi-page provider operations; do not interpret passing admission as proof that
such operations will succeed.

No live novel provider ships by default. Build the authored debug fixture using
`android/gradlew.bat -p android :app:assembleDebug -PlnreaderContractFixture=true`.
Its source ID is `lnreader:hikari-contract`; name explicitly identifies it as a
fixture. The Gradle property adds fixture assets only to debug, never release.
Without the property, no fixture source is registered. Normal provider discovery
uses the frozen packaged/downloaded snapshot described in the Novel artifact section above.
Windows/iOS have no novel runtime. Missing sources preserve persisted rows.

License inventory and its remaining pre-existing manga-host audit boundary are
recorded in root `NOTICE`; full notices for shipped JS/native code are APK assets.

## Upstream references

- Keiyoushi extension source/build baseline: <https://github.com/keiyoushi/extensions-source>
- Keiyoushi extension-lib ABI: <https://github.com/keiyoushi/extensions-lib>
- Keiyoushi repository metadata/signing fingerprint: <https://github.com/keiyoushi/extensions/blob/repo/repo.json>
- Katari extension loader/compatibility host reference: <https://github.com/pa2x2/katari>
- Android `DelegateLastClassLoader`: <https://developer.android.com/reference/dalvik/system/DelegateLastClassLoader>

### Acquisition interoperability (2026-10)

Transport validates HTTPS, DNS/each redirect, limits, signer identity and
explicit consent. URL traversal checks apply to the **raw path before URL
canonicalization**, never to opaque GitHub Release/CDN query parameters
(`rsct=application%2Foctet-stream` is legitimate). Relative redirect paths
are checked before resolution. Network, rejected redirect, HTTP, byte-limit,
and inspected APK mismatch failures produce bounded machine-readable error
codes; the UI never displays signed URLs, native stack traces or JS bytes.

LNReader upstream plugin identities can contain interior dots such as
`ln.hako` and `lightnovel.vn`. The entire Dart/native source and artifact
pipeline preserves these IDs, rejecting empty segments, separators and path
traversal; artifact filenames remain digest-derived, not ID-derived.

Plugins declaring auxiliary CSS/JS or unsupported origins remain discoverable,
but the Novel actions UI explains why staging is unavailable. Admission is
still distinct from runtime/provider compatibility. This change does **not**
relax HTTPS, trust, size, JS execution consent or frozen runtime policy.

Regression fixtures cover signed GitHub Release redirects, rejected traversal,
HTTP classification, dotted IDs and native Novel persistence. Actual provider
installation and device network verification remain separately required.
