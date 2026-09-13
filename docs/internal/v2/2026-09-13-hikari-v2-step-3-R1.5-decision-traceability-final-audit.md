# Hikari V2 Step 3 R1.5 — Decision Traceability + Repository-Realizability + Performance-Hardening Final Self-Review Audit

**Date:** 2026-09-13  
**Reviewed spec:** `2026-09-13-hikari-v2-step-3-base-app-ux-ui-completion-design-R1.5.md`  
**Direct predecessor:** `2026-09-13-hikari-v2-step-3-base-app-ux-ui-completion-design-R1.4.md`  
**Historical source:** `2026-09-13-hikari-v2-step-3-base-app-ux-ui-completion-design-R0.14.md`  
**Purpose:** prove that the R1 rewrite did not silently drop product/architecture decisions, verify repository realizability, and adversarially harden Step 3 against V1-style performance/lifetime failure modes before architecture freeze.

## 1. Audit method

This audit uses four classes:

- **PRESERVED** — same semantic decision remains normative in R1.5.
- **SUPERSEDED / CLARIFIED** — R0 wording was intentionally changed by later source/code audit.
- **INTENTIONALLY IMPLEMENTATION-LEVEL** — design deliberately does not freeze the mechanism.
- **DEFERRED BY MILESTONE** — product/capability is explicitly outside Step 3.

A decision is considered lost only if it belongs to none of those classes.

## 2. R0.14 final acceptance decisions (106/106 traced)

| # | R0.14 decision | Trace status | R1.5 authority | Note |
|---:|---|---|---|---|
| 1 | The accepted Step 2 visual identity survives. | **PRESERVED** | §2.6, §23, §25.7 |  |
| 2 | Manga, Home and Light Novel are real top-level destinations. | **PRESERVED** | §6.2, §25.1 |  |
| 3 | Search is real and context-aware. | **PRESERVED** | §9 |  |
| 4 | Basic Library is real and independent of Reader/Progress. | **PRESERVED** | §12 |  |
| 5 | Story can add/remove Library membership. | **PRESERVED** | §11.11, §12.9 |  |
| 6 | Story Share and Similar behavior are resolved and functional. | **SUPERSEDED / CLARIFIED** | §11.10–11.12 | Share remains functional; Similar is capability-gated rather than unconditionally functional. |
| 7 | Duplicate Heart/Library semantics are removed. | **PRESERVED** | §11.7 |  |
| 8 | `See All` is either functional or deliberately non-actionable; no fake CTA remains. | **PRESERVED** | §8.4 |  |
| 9 | Manga and Light Novel each use one built-in default Catalog authority. | **PRESERVED** | §7.2–7.4, §25.3 |  |
| 10 | No cross-catalog merge, multi-source Search fan-out or silent fallback exists in Step 3. | **PRESERVED** | §7.1–7.9, §24 |  |
| 11 | Chapter/Reader/Progress remain outside the Step 3 production activation graph. | **PRESERVED** | §2.3, §13.14 |  |
| 12 | Home does not become a global aggregator. | **PRESERVED** | §12.6, §21 |  |
| 13 | Catalog Search runs only on explicit submit and does not query on each keystroke. | **PRESERVED** | §9.2 |  |
| 14 | Search restoration does not automatically re-query after returning from Story. | **PRESERVED** | §9.5, §6.9 |  |
| 15 | New capability work is demand-driven. | **PRESERVED** | §6, §21 |  |
| 16 | Step 1/2 regression journeys remain within accepted performance policy. | **PRESERVED** | §21.6, §22.9 |  |
| 17 | Compact/wide loading/ready/empty/error/partial states are visually coherent. | **PRESERVED** | §8–19, §25.7 |  |
| 18 | Accessibility semantics match actual interaction. | **PRESERVED** | §19, §22.8 |  |
| 19 | Final UI contains no action-looking no-op except an explicitly accepted deferred Step 4 affordance with clear semantics. | **PRESERVED** | §15.5 |  |
| 20 | Discover, Home Library, Search, Section Listing and Similar poster presentations use the same standard Story poster visual authority. | **SUPERSEDED / CLARIFIED** | §16.6–16.7 | Visual consistency is preserved, but Catalog/Library/Reading Source keep separate semantic poster models over shared visual primitives. |
| 21 | Standard poster grids use one responsive grid policy rather than screen-local column/spacing constants. | **PRESERVED** | §16.7, §19.6 |  |
| 22 | Shared poster skeleton geometry matches shared poster-card geometry. | **PRESERVED** | §16.7, §25.7 |  |
| 23 | Search/Home may share the Search field visual primitive while retaining different execution semantics. | **PRESERVED** | §16.9, §12.7 |  |
| 24 | App-wide icon actions satisfy one touch-target/semantics contract. | **PRESERVED** | §16.10, §19.1 |  |
| 25 | Generic Design System code imports no Catalog/Library/Reader/domain/runtime model. | **PRESERVED** | §16.2, §22.1 |  |
| 26 | Destination state remains explicit; no universal global UI state is introduced. | **PRESERVED** | §16.18 |  |
| 27 | Retained-content and pagination behavior use shared state facets only where their semantics actually match. | **PRESERVED** | §16.18, §18.2 |  |
| 28 | Story has no Favorite/Heart domain or duplicate collection action. | **PRESERVED** | §11.7 |  |
| 29 | Story Back is hero-overlaid with safe contrast/insets and shared touch semantics. | **PRESERVED** | §11.6, §25.4 |  |
| 30 | Story `More` remains in the action row; hero overlay is navigation-only. | **PRESERVED** | §11.6, §11.13 |  |
| 31 | `Read from Chapter 1` and `Chapters` remain visible but semantically disabled with zero hidden Step 4 activation. | **PRESERVED** | §2.3, §11.8–11.9 |  |
| 32 | Story keeps `Synopsis \| Chapters \| Similar`; Similar is functional and uses the shared poster/grid authority. | **SUPERSEDED / CLARIFIED** | §11.9–11.10 | The three-tab shape is preserved; Similar is enabled only when a real Catalog Similar capability is proven. |
| 33 | Add to Library is the primary Story action; Share and More are secondary. | **PRESERVED** | §11.8 |  |
| 34 | Story `More` distinguishes Catalog information from Reading Source selection. | **PRESERVED** | §11.13 |  |
| 35 | Reading Source mapping can be persisted in Step 3 without triggering Chapters/Reader. | **PRESERVED** | §13.2–13.15 |  |
| 36 | Reading language preference is independent of Catalog identity and can be consumed by Step 4. | **PRESERVED** | §13.2, §13.12 |  |
| 37 | Unsupported source/language combinations cannot remain silently valid. | **PRESERVED** | §13.3, §13.9, §13.12 |  |
| 38 | Wave 11 custom Catalog extensibility is not conflated with Reading Source capability. | **PRESERVED** | §13.1, §2.4 |  |
| 39 | Media default Reading Source applies only to unmapped Stories. | **PRESERVED** | §13.6 |  |
| 40 | Choosing another Reading Source creates a draft and preserves the old committed mapping until confirmation. | **PRESERVED** | §13.7 |  |
| 41 | Detect searches exactly one selected Reading Source. | **PRESERVED** | §13.8 |  |
| 42 | Detect may auto-run one initial primary-title query only because the user explicitly invoked Detect. | **PRESERVED** | §13.8 |  |
| 43 | Reading Source candidate confirmation is required before replacing a committed mapping. | **PRESERVED** | §13.11 |  |
| 44 | Mapping replacement is atomic. | **PRESERVED** | §13.3, §13.11 |  |
| 45 | Candidate-specific supported languages override broad plugin language metadata. | **PRESERVED** | §13.4, §13.9 |  |
| 46 | A failed draft/detection flow leaves the last committed mapping intact. | **PRESERVED** | §13.7, §18 |  |
| 47 | Detect transport/runtime failure and zero-result Empty are distinct states. | **PRESERVED** | §13.8, §18 |  |
| 48 | An unavailable committed Reading Source does not invalidate or delete its mapping. | **PRESERVED** | §13.13 |  |
| 49 | A Story-specific source override never mutates the media-wide default. | **PRESERVED** | §13.6, §13.13 |  |
| 50 | Changing the global default never remaps committed Stories. | **PRESERVED** | §13.6, §14.8 |  |
| 51 | Source/language compatibility is validated before replacement mapping commit. | **PRESERVED** | §13.12 |  |
| 52 | Recovery actions preserve authority: Retry same source, Choose another source explicit, Change default Settings-only. | **PRESERVED** | §13.13, §18.3 |  |
| 53 | Settings root contains only General, Reading and About. | **PRESERVED** | §14.1 |  |
| 54 | Reading Settings is organized by Catalog Sources and Reading Sources, not by top-level Manga/Light Novel sections. | **PRESERVED** | §14.1, §14.7–14.8 |  |
| 55 | Catalog Source and Reading Source configuration remain separate capability domains even when one plugin package implements both. | **PRESERVED** | §13.1 |  |
| 56 | App language, Catalog language and Reading language are distinct preferences. | **SUPERSEDED / CLARIFIED** | §11.5, §13.12, §14.5, §14.7 | Three language domains remain distinct. A Catalog-language preference exists only when a proven builtin Catalog exposes configurable metadata/discovery language; it is not an unconditional global preference. |
| 57 | Changing defaults does not silently rewrite committed Story mappings or Catalog identities. | **PRESERVED** | §7.2, §13.6, §14.7–14.8 |  |
| 58 | Step 3 does not introduce an unrestricted generic plugin-settings schema without a concrete admitted requirement. | **PRESERVED** | §2.4, §14, §24 |  |
| 59 | Step 3 source configuration is builtin-only. | **PRESERVED** | §2.4, §7.9, §13.5 |  |
| 60 | Source/plugin install, uninstall, enable/disable, update and repository lifecycle are deferred to Wave 11. | **PRESERVED** | §2.4, §24 |  |
| 61 | Step 3 does not expose dead or placeholder Manage Sources screens. | **PRESERVED** | §13.13, §14.9, §24 |  |
| 62 | Stable Settings IA is preserved so Wave 11 can extend Catalog Sources and Reading Sources without root redesign. | **PRESERVED** | §14.1 |  |
| 63 | Similar uses exactly the current Story Catalog authority and never Reading Source. | **PRESERVED** | §11.10 |  |
| 64 | Similar loads only after explicit Similar-tab activation. | **PRESERVED** | §11.10 |  |
| 65 | Similar result and scroll are retained across child Story navigation in the same app session. | **PRESERVED** | §6.7, §11.10 |  |
| 66 | Opening a Similar Story pushes navigation history; Back restores the immediate previous Story origin. | **PRESERVED** | §6.2, §6.7, §11.10 |  |
| 67 | Inactive Story routes must not continue unnecessary remote/runtime work merely because they remain on the back stack. | **PRESERVED** | §6.4 |  |
| 68 | Route retention does not imply retention of heavyweight artwork/runtime resources. | **PRESERVED** | §6.4, §17.3 |  |
| 69 | Saved-state persistence remains small/bounded and does not serialize full Story runtime or large Similar payloads. | **PRESERVED** | §6.9, §21.4 |  |
| 70 | `See All` exists only when a meaningful expanded listing destination exists. | **PRESERVED** | §8.4 |  |
| 71 | Section listings paginate incrementally and do not cumulatively reprocess all prior pages. | **PRESERVED** | §10.3 |  |
| 72 | Append failure preserves prior items and exposes scoped Retry. | **PRESERVED** | §10.2–10.4 |  |
| 73 | Optional empty Discover shelves may be omitted; dedicated listing Empty is explicit. | **PRESERVED** | §8.7, §10.2 |  |
| 74 | One shelf failure does not blank otherwise usable Discover content. | **PRESERVED** | §8.7 |  |
| 75 | Top Rated preserves explicit rank semantics in both preview and full listing. | **PRESERVED** | §8.5, §10.6 |  |
| 76 | Back from child Story restores listing pages and scroll without page-1 refetch caused solely by navigation. | **PRESERVED** | §10.7, §22.2 |  |
| 77 | Back from listing restores the originating Discover position. | **PRESERVED** | §10.7, §22.2 |  |
| 78 | Manga, Home and Light Novel maintain independent top-level navigation histories. | **PRESERVED** | §6.2 |  |
| 79 | Switching top-level destination restores its previous stack rather than resetting to root. | **PRESERVED** | §6.2 |  |
| 80 | Only the foreground top-level destination may begin new demand-driven runtime work. | **PRESERVED** | §6.4 |  |
| 81 | Re-selecting the active top-level destination pops its child stack to root without forcing refresh. | **PRESERVED** | §6.2 |  |
| 82 | System Back unwinds the active destination stack and does not traverse top-level tab selection history at root. | **PRESERVED** | §6.2 |  |
| 83 | Home is the default launch destination but is not a universal Back target. | **PRESERVED** | §6.2 |  |
| 84 | Top-level navigation authority belongs to the app shell, not a feature module. | **PRESERVED** | §4.2, §6.1 |  |
| 85 | Step 3 Settings applies independent values immediately and has no full-screen Save flow. | **PRESERVED** | §14.3 |  |
| 86 | Opening Catalog Search performs zero request; typing does not query; explicit Search/Enter submits. | **PRESERVED** | §9.2 |  |
| 87 | Search retained-result labeling never confuses edited input with displayed-result identity. | **PRESERVED** | §9.3–9.4 |  |
| 88 | Library membership changes become stable only after local persistence succeeds. | **PRESERVED** | §11.11 |  |
| 89 | Add/remove operations for one Story cannot overlap and failures restore the previous stable membership. | **PRESERVED** | §11.11 |  |
| 90 | Offline, provider failure and valid Empty remain separate UX states. | **PRESERVED** | §5.4, §18.3 |  |
| 91 | Retained usable content is not blanked solely because a new network request fails/offline. | **PRESERVED** | §18.2–18.3 |  |
| 92 | Theme applies without intentional navigation/domain reset. | **PRESERVED** | §14.4 |  |
| 93 | App-language changes do not alter Catalog/Reading language or Story identity. | **PRESERVED** | §14.5 |  |
| 94 | Interactive icon controls expose meaningful accessibility semantics. | **PRESERVED** | §16.10, §19.1 |  |
| 95 | Deferred Read/Chapters controls expose true disabled semantics and trigger zero hidden work. | **PRESERVED** | §2.3, §19.2 |  |
| 96 | Top Rated rank is exposed to accessibility services. | **PRESERVED** | §8.5, §19.3 |  |
| 97 | Skeleton placeholders do not create noisy accessibility pseudo-items. | **PRESERVED** | §16.15, §19.5 |  |
| 98 | One app-wide minimum touch-target policy applies across Step 3 interactive controls. | **PRESERVED** | §19.1 |  |
| 99 | App-level secondary actions use an `App menu` entry beside Search where the shared root header supports it. | **PRESERVED** | §15.1 |  |
| 100 | App menu uses a modal bottom sheet and does not imply Profile/Account before those products exist. | **PRESERVED** | §15.1 |  |
| 101 | Story `More` uses the shared modal action-sheet family. | **PRESERVED** | §15.3, §16.13 |  |
| 102 | Compact source/language/theme choice sets use shared choice-sheet presentation where appropriate. | **PRESERVED** | §15.2, §16.13 |  |
| 103 | Reading Source Detect candidate search remains a full-screen destination. | **PRESERVED** | §13.8, §15.2 |  |
| 104 | Modal sheets do not form a deep navigation stack. | **PRESERVED** | §15.3 |  |
| 105 | Confirmation dialogs are reserved for genuinely blocking/destructive confirmation. | **SUPERSEDED / CLARIFIED** | §15.2, §15.4 | Clarified: Level-4 confirmation is for blocking/destructive/final commit confirmation, not generic option menus. |
| 106 | Generic sheet visuals/semantics belong to Design System while domain state/actions remain feature-owned. | **PRESERVED** | §16.13 |  |

### 2.1 Intentional R0 supersessions

- **R0 #6** — Story Share and Similar behavior are resolved and functional.  
  **R1.4:** Share remains functional; Similar is capability-gated rather than unconditionally functional.
- **R0 #20** — Discover, Home Library, Search, Section Listing and Similar poster presentations use the same standard Story poster visual authority.  
  **R1.4:** Visual consistency is preserved, but Catalog/Library/Reading Source keep separate semantic poster models over shared visual primitives.
- **R0 #32** — Story keeps `Synopsis | Chapters | Similar`; Similar is functional and uses the shared poster/grid authority.  
  **R1.4:** The three-tab shape is preserved; Similar is enabled only when a real Catalog Similar capability is proven.
- **R0 #105** — Confirmation dialogs are reserved for genuinely blocking/destructive confirmation.  
  **R1.4:** Clarified: Level-4 confirmation is for blocking/destructive/final commit confirmation, not generic option menus.
- **R0 #56** — App language, Catalog language and Reading language are distinct preferences.  
  **R1.4:** Preserves the three separate language domains, but makes Catalog-language *configuration* capability-gated. If the builtin Catalog exposes no configurable metadata/discovery language, Hikari does not invent a fake Catalog-language preference.

These are not omissions. They are explicit corrections from later V1/V2/source-capability audit and the R1.4 repository-realizability pass.

## 3. Decisions added by the V1/V2 deep audit and Sections 1–11 discussion

| ID | Added decision | Status | R1.5 authority |
|---|---|---|---|
| P01 | App Shell + capability slices; screens do not define domain boundaries. | **PRESERVED** | §4 |
| P02 | App Shell owns navigation/chrome; composition wiring must not become business orchestration. | **PRESERVED** | §4.2, §22.1 |
| P03 | Semantic persistence ownership is mandatory; separate physical databases are not. | **PRESERVED** | §0, §12, §13 |
| P04 | Route lifecycle is ACTIVE / RETAINED / RELEASED. | **PRESERVED** | §6.3 |
| P05 | Construction/composition does not equal remote capability activation. | **PRESERVED** | §6.5 |
| P06 | Unvisited roots are lazy. | **PRESERVED** | §6.6 |
| P07 | Navigation-history retention does not retain full Story/provider runtime. | **PRESERVED** | §6.4, §6.7 |
| P08 | Story routes carry provenance ref + origin media context + lightweight preview only. | **PRESERVED** | §6.8, §20.1 |
| P09 | Process-death restoration is intentionally weaker than same-process Back restoration. | **PRESERVED** | §6.9 |
| P10 | Late async completion remains keyed to its original route/request authority. | **PRESERVED** | §6.10 |
| P11 | One selected Catalog authority per media context; registration/selection does not imply activation. | **PRESERVED** | §2.2, §7 |
| P12 | Existing Story provenance outranks later default-Catalog changes. | **PRESERVED** | §7.2 |
| P13 | Catalog capability descriptors are cheap local control-plane data. | **PRESERVED** | §7.4 |
| P14 | Catalog Runtime Host is limited to registration/lazy instantiation/shared-store/retention coordination, not use-case orchestration. | **PRESERVED** | §7.6–7.7 |
| P15 | Shared Catalog store lifetime/global active-Story protection are multi-authority safe. | **PRESERVED** | §7.7 |
| P16 | Discover/Story are durable Catalog paths; Search/Listing/Similar are transient by default. | **PRESERVED** | §7.8, §5.2 |
| P17 | Transient Catalog query execution does not require durable Catalog import/storage merely to run. | **PRESERVED** | §7.8 |
| P18 | Collection cards never create per-item Story Detail N+1 enrichment. | **PRESERVED** | §9.8, §10.6, §21.3 |
| P19 | Section identity/capability and ranked continuation semantics are explicit. | **PRESERVED** | §8.3–8.5, §20.4 |
| P20 | Latest Updates cannot be mislabeled Recommended for You without a real recommendation authority. | **PRESERVED** | §8.6 |
| P21 | Provider-specific Catalog Search filter UI is deferred. | **PRESERVED** | §9.6 |
| P22 | Library owns durable provenance + fallback presentation snapshot independent of Catalog cache. | **PRESERVED** | §12.1–12.4 |
| P23 | Library user truth must not be erased by Catalog-cache FK/cascade/reset semantics. | **PRESERVED** | §12.4, §12.11 |
| P24 | Home local query is latest-wins; default collection ordering is savedAt descending. | **PRESERVED** | §12.7 |
| P25 | Library and StoryReadingBinding schema migration is non-destructive user-truth migration. | **PRESERVED** | §12.11, §13.15 |
| P26 | StoryReadingBinding is one coherent durable source+sourceStory+language aggregate. | **PRESERVED** | §13.2–13.4 |
| P27 | Story binding lookup is point-scoped; no observe-all mapping corpus is required for Story UX. | **PRESERVED** | §13.2 |
| P28 | Candidate-specific verified languages are mandatory for compatibility claims and must not require Chapters. | **PRESERVED** | §13.4, §13.9 |
| P29 | verifiedLanguageTags are a commit-time capability snapshot, not a freshness/probing subsystem. | **PRESERVED** | §13.4 |
| P30 | Reading candidate artwork is optional; missing artwork uses deterministic fallback without Catalog reconciliation. | **PRESERVED** | §13.9, §16.17 |
| P31 | Reading Source source-story display title may be retained as a small presentation snapshot but is not identity. | **PRESERVED** | §13.2 |
| P32 | Reading Source adapter/source version is not binding identity and upgrades do not erase mappings. | **PRESERVED** | §13.15 |
| P33 | Detect candidates preserve source ordering; no similarity/confidence/best-match engine. | **PRESERVED** | §13.10 |
| P34 | Settings uses narrow typed preference ports; one physical DataStore may back them. | **PRESERVED** | §14.2 |
| P35 | Theme boot resolution must avoid both blocking first frame and materially visible wrong-theme flash. | **PRESERVED** | §14.4 |
| P36 | AppLocale has one semantic authority; Android locale API is mechanism, not competing truth. | **PRESERVED** | §14.5 |
| P37 | Localization scope is active Step 3 production surfaces/shared primitives, not historical V1 code. | **PRESERVED** | §14.6 |
| P38 | Design System shares visual policy; Catalog/Library/Reading Source keep separate semantic poster models. | **PRESERVED** | §16.1–16.7 |
| P39 | Poster extraction preserves accepted Step 2 visuals before final geometry/aspect-ratio standardization. | **PRESERVED** | §16.5 |
| P40 | Poster skeleton geometry follows poster-card geometry. | **PRESERVED** | §16.7 |
| P41 | V1 Glass/backdrop/modal framework is not restored merely for Step 3 polish. | **PRESERVED** | §16.13 |
| P42 | Artwork visual frame/fallback may be shared; network/image/cache authority stays outside Design System. | **PRESERVED** | §16.16–17 |
| P43 | Design System Refresh/Refreshing English literals are localization debt and must be localized. | **PRESERVED** | §16.14 |
| P44 | No mandatory GlobalUiState/RetainedContentState/core:presentation abstraction is introduced prematurely. | **PRESERVED** | §16.18 |
| P45 | Stable test tags/route identities are not localized. | **PRESERVED** | §14.6, §22.8 |
| P46 | Share always supports title; stable app link is optional only behind a real app-owned link contract. | **PRESERVED** | §11.12, §20.10 |
| P47 | Catalog information remains minimal product-facing provenance, not runtime/plugin diagnostics. | **PRESERVED** | §11.13 |
| P48 | WEB_NOVEL is explicitly deferred; Light Novel root admits LIGHT_NOVEL only in Step 3. | **PRESERVED** | §2.5 |
| P49 | Step 2 final acceptance/current checkpoint is a hard precondition and accepted debt remains visible. | **PRESERVED** | document precondition, §21.6 |
| P50 | Similar and See All are real-capability-gated rather than mockup-mandated. | **PRESERVED** | §8.4, §11.9–11.10 |
| P51 | Home artwork path cannot bootstrap full Catalog runtime. | **PRESERVED** | §17.2, §21.2 |
| P52 | Source availability is not live-health probing; picker opening performs no broad probe. | **PRESERVED** | §13.5, §13.13 |
| P53 | Builtin adapters terminate at narrow V2 capability ports; generic plugin runtime stays later-wave. | **PRESERVED** | §7.9, §13, §2.4 |
| P54 | Current feature-owned global nav/preview wrappers are retired or renamed as ownership becomes real Step 3 behavior. | **PRESERVED** | §23.1 |
| P55 | Step 3 does not add speculative next-page/Similar/Detect prefetch; new work follows explicit/viewport/tab demand. | **PRESERVED** | §21.2 |
| P56 | V1 manual URL-to-source-story mapping and mapping policy-version machinery are not admitted into Step 3 Reading Source setup. | **PRESERVED** | §13.8, §24 |
| P57 | Step 1 `Unknown -> FirstRun -> Ready` startup semantics remain; Step 3 changes only the post-Ready handoff to App Shell/Home. | **PRESERVED** | §1.2.1, §25.1 |
| P58 | Logical capability-store lifetime is distinct from physical DB lifetime; one logical owner cannot close a shared physical DB used by other semantic owners. | **PRESERVED** | §5.5, §25.8 |
| P59 | Home/Library artwork uses a process-shared bounded artwork infrastructure and cheap local policy lookup; artwork policy resolution must not activate full Catalog runtime. | **PRESERVED** | §17.5, §25.7 |
| P60 | Retained route payload is bounded process-wide across all root histories; deep history may compact reconstructible payload while preserving cheap route identity. | **PRESERVED** | §6.11, §21.4, §25.8 |
| P61 | Step 3 intentionally admits production remote transport/`INTERNET` for proven builtin capabilities while preserving zero-network startup and strict transport/security bounds. | **PRESERVED** | §7.10, §22.8.1, §25.3 |
| P62 | Catalog/Reading/artwork keep semantic ownership separate but share process-wide bounded admission for expensive work; no private unbounded executor may bypass it. | **PRESERVED** | §21.7, §25.8 |
| P63 | A mapped Story can explicitly clear its Story-specific Reading binding and return to Unmapped/default semantics without changing global defaults or starting Step 4 work. | **PRESERVED** | §13.6.1, §25.5 |
| P64 | Changing a configurable default Catalog never hot-swaps authority inside an existing retained route/request chain; the new default applies to a newly created discovery context. | **PRESERVED** | §7.11, §14.7, §25.3 |
| P65 | Settings/About are one app-level focused-destination family above the selected root and Back returns to the exact origin context. | **PRESERVED** | §6.13, §14.11, §25.1 |
| P66 | Reading Source `Available` is local admitted capability presence, not implicit live-health probing; transient request failure does not invalidate presence or the committed binding. | **PRESERVED** | §13.13, §25.5 |
| P67 | Step 1/2 static gates forbidding `androidx.navigation` and production `INTERNET` may be evolved only explicitly/narrowly with replacement no-regression assertions; implementation must not bypass them. | **PRESERVED** | §22.1, §23.1 |
| P68 | The three language domains remain separate; Catalog-language configuration is admitted only when a real builtin Catalog exposes configurable metadata/discovery language. | **PRESERVED** | §11.5, §14.7 |
| P69 | RETAINED routes quiesce route-scoped domain collectors by default; live route-scoped collector cardinality must not grow with navigation depth. | **PRESERVED** | §6.4, §6.12, §25.8 |
| P70 | Home local Search needs physical query-scaling/query-plan evidence; latest-wins alone is not sufficient and per-keystroke unbounded historical scan/materialization is forbidden. | **PRESERVED** | §12.7.1, §22.4, §25.2 |
| P71 | Every new Step 3 capability declares observer/CPU/I/O/network execution ownership; database/network I/O, large decode and collection-scale CPU work must not intentionally run on Main. | **PRESERVED** | §21.8, §22.8.1, §25.8 |
| P72 | Accepted Step 1/2 red performance debt is frozen as no-growth debt; final Step 3 graph/profile artifacts must be regenerated/validated under inherited performance policy. | **PRESERVED** | §21.6, §22.9, §25.8 |
| P73 | Library presentation-snapshot enrichment is change-aware and must not deliberately issue semantically identical writes that amplify Room invalidation/recomposition. | **PRESERVED** | §12.3, §22.4, §25.2 |
| P74 | Equivalent concurrent artwork requests use bounded same-key in-flight coalescing/single-owner semantics with correct shared cancellation. | **PRESERVED** | §17.5, §22.8.1, §25.8 |
| P75 | Process-wide expensive-work admission must prove foreground fairness/non-starvation under artwork/decode pressure, not only a concurrency ceiling. | **PRESERVED** | §21.7, §22.8.1, §25.8 |
| P76 | Aggregate route-entry/history metadata is bounded separately from retained payload; exact depth/trim policy is benchmark-driven but arbitrary route-depth memory/saved-state growth is forbidden. | **PRESERVED** | §6.11.1, §21.4, §25.8 |
| P77 | Route-owned transient Search/Listing/Similar/Detect expensive work is cancelled/invalidated when its route becomes RETAINED; racing late completion cannot re-inflate inactive/compacted route state or cross-publish. | **PRESERVED** | §6.4, §6.10, §22.2, §25.8 |
| P78 | Process-wide expensive-work admission bounds pending backlog as well as active concurrency; route cancellation invalidates queued work and noncritical artwork work cannot accumulate an unbounded wait queue. | **PRESERVED** | §21.7, §22.8.1, §25.8 |
| P79 | Lifecycle cancellation caused by ACTIVE -> RETAINED is not provider/offline Failure or Empty; retained content/query identity survives according to route semantics and reactivation follows the destination demand contract. | **PRESERVED** | §18.4, §22.2, §22.5, §22.7 |
| P80 | Shared physical Room topology is allowed only if Home-only open/migration/query does not scale primarily with unrelated aged Catalog history; otherwise physical storage must be separated. | **PRESERVED** | §5.5, §22.4, §22.8.1, §25.8 |
| P81 | Library snapshot enrichment and repeated Add are timestamp-stable/idempotent: no semantically identical write, no `savedAt` bump and no Library reorder. | **PRESERVED** | §12.3, §12.9, §22.4, §25.2 |
| P82 | Re-selecting an already committed Theme/App-language/default setting is a semantic no-op and must not cause redundant persistence, recreation or capability reconfiguration. | **PRESERVED** | §14.3, §22.8 |
| P83 | RETAINED is logical navigation state, not permission to keep every inactive root/route fully composed/resumed; persistent offscreen composition must not keep effects/images/collectors active. | **PRESERVED** | §6.4, §6.6, §22.2, §25.8 |
| P84 | Paginated Search/Listing/Detect use continuation-progress guards: no recursive auto-chain, no non-advancing/cyclic cursor reuse, and bounded zero-delta page walking. | **PRESERVED** | §21.5.1, §22.5, §22.7, §25.8 |
| P85 | Production remote transport bounds automatic retry and cross-origin credential/header propagation; redirects cannot leak source auth implicitly. | **PRESERVED** | §7.10, §22.8.1 |
| P86 | StoryReadingBinding/clear mutations are serialized per Story provenance at the Reading Source owner across routes; semantically identical confirmed binding is a no-op rather than timestamp-only churn. | **PRESERVED** | §13.11, §21.5, §22.7 |
| P87 | Shared Catalog retention/eviction is batch/index bounded; a point acquisition/eviction pass must not rescan/reaggregate total historical Catalog state or use Library size as retention work. | **PRESERVED** | §7.7, §22.8.1, §25.8 |

## 4. Deliberately implementation-level choices

| ID | Choice | Status | Authority |
|---|---|---|---|
| I01 | Exact navigation technology (Navigation Compose vs typed hand-written stack). | **INTENTIONALLY IMPLEMENTATION-LEVEL** | §0 |
| I02 | Exact module count/package split for capability slices. | **INTENTIONALLY IMPLEMENTATION-LEVEL** | §0, §4.4 |
| I03 | Separate physical Room DBs vs shared physical DB with semantic boundaries. | **INTENTIONALLY IMPLEMENTATION-LEVEL** | §0, §12, §13 |
| I04 | Paging3/manual window/bounded-list implementation for large local collections. | **INTENTIONALLY IMPLEMENTATION-LEVEL** | §0, §12.7 |
| I05 | Exact numeric retained-page/memory caps. | **INTENTIONALLY IMPLEMENTATION-LEVEL** | §0, §21.4 |
| I06 | Exact supported App-locale list, provided coverage is complete for every exposed locale. | **INTENTIONALLY IMPLEMENTATION-LEVEL** | §0, §14.5 |
| I07 | Exact icon glyphs, animation durations and non-semantic visual polish values. | **INTENTIONALLY IMPLEMENTATION-LEVEL** | §16, §19 |
| I08 | Exact process-wide retained-payload budget and compaction algorithm. | **INTENTIONALLY IMPLEMENTATION-LEVEL** | §6.11, §21.4 |
| I09 | Exact artwork cache sizes and process-wide expensive-work lane/permit counts or scheduling primitive. | **INTENTIONALLY IMPLEMENTATION-LEVEL** | §17.5, §21.7 |
| I10 | Exact physical DB topology/lifetime mechanism, provided shared DB lifetime cannot be closed by one logical capability owner. | **INTENTIONALLY IMPLEMENTATION-LEVEL** | §5.5 |
| I11 | Exact route-collector cardinality threshold/instrumentation mechanism, provided retained routes quiesce by default and aggregate live collector count does not grow with route depth. | **INTENTIONALLY IMPLEMENTATION-LEVEL** | §6.12 |
| I12 | Exact Home Search indexing/FTS/windowing/query technology, provided the physical scaling/query-plan contract is met. | **INTENTIONALLY IMPLEMENTATION-LEVEL** | §12.7.1 |
| I13 | Exact dispatchers/executors/admission implementation and artwork coalescing mechanism, provided execution-owner, fairness and same-key duplicate-work contracts hold. | **INTENTIONALLY IMPLEMENTATION-LEVEL** | §17.5, §21.7–21.8 |
| I14 | Exact numeric route-entry/history bound and deterministic trim/collapse policy, provided aggregate route metadata/saved state cannot grow without bound and root/current history invariants are preserved. | **INTENTIONALLY IMPLEMENTATION-LEVEL** | §6.11.1 |
| I15 | Exact foreground resume policy for previously cancelled transient work where the product contract does not already force explicit resubmit, provided RETAINED routes do zero transient expensive work and Search/Detect explicit-submit semantics are preserved. | **INTENTIONALLY IMPLEMENTATION-LEVEL** | §6.4, §6.10 |
| I16 | Exact inactive-root UI disposal/saveable-state mechanism (NavHost/SaveableStateHolder/etc.), provided RETAINED does not mean persistently full active composition. | **INTENTIONALLY IMPLEMENTATION-LEVEL** | §6.4, §6.6 |
| I17 | Exact recent-continuation/no-progress pagination guard thresholds, provided continuation cycles/non-advancing cursors and unbounded zero-delta walking are impossible. | **INTENTIONALLY IMPLEMENTATION-LEVEL** | §21.5.1 |
| I18 | Exact bounded transport retry count and source-specific credential/redirect propagation details, provided no unbounded retry or implicit cross-origin credential leak is possible. | **INTENTIONALLY IMPLEMENTATION-LEVEL** | §7.10 |
| I19 | Exact Catalog-retention SQL/index/batch/locking mechanism, provided point acquisition/eviction work remains bounded and multi-authority active pins stay safe. | **INTENTIONALLY IMPLEMENTATION-LEVEL** | §7.7 |

## 5. Explicit milestone deferrals

| ID | Deferred capability | Status | Authority |
|---|---|---|---|
| D01 | Chapters/releases/Reader/progress/download consumption. | **DEFERRED BY MILESTONE** | §2.3 |
| D02 | Custom Catalog/Reading Source installation, repositories, enable/disable/update lifecycle, generic plugin settings. | **DEFERRED BY MILESTONE** | §2.4, §24 |
| D03 | Personalized recommendations / real Recommended-for-You authority. | **DEFERRED BY MILESTONE** | §8.6, §24 |
| D04 | Full public deep-link/share-link subsystem unless separately admitted. | **DEFERRED BY MILESTONE** | §11.12, §20.10 |
| D05 | WEB_NOVEL product admission into Light Novel root. | **DEFERRED BY MILESTONE** | §2.5 |

## 6. Final structural / contradiction self-review

- Duplicate headings: **PASS — none**
- Placeholder-token scan: **PASS — none present in the reviewed spec**
- R0 acceptance traceability: **PASS — 106/106 accounted for**
- Post-R0 audit decisions: **PASS — 87/87 accounted for (56 prior + 12 R1.4 repository-realizability decisions + 19 R1.5 performance-hardening decisions)**
- Implementation-level choices: **PASS — 19/19 explicitly classified**
- Milestone deferrals: **PASS — 5/5 explicitly classified**
- Step 1 startup handoff vs Home-default wording: **PASS** — Home is explicitly post-Ready; FirstRun is preserved.
- logical-store vs physical-DB lifetime: **PASS** — shared physical persistence can no longer be closed by one logical Catalog owner.
- per-route vs process-wide retention: **PASS** — aggregate retained payload is explicitly bounded/compactable.
- production-network admission vs zero-work startup: **PASS** — permission/transport admission is separated from activation.
- per-capability single-flight vs aggregate resource pressure: **PASS** — process-wide expensive-work admission is explicit without becoming a semantic coordinator.
- Home artwork vs Catalog activation: **PASS** — policy lookup is cheap control-plane data and artwork runtime is shared/bounded.
- retained payload bound vs live collector bound: **PASS** — R1.5 distinguishes materialized retained payload from route-scoped observation and bounds both independently.
- retained payload bound vs route-entry metadata bound: **PASS** — deep history cannot remain unbounded merely because route entries are individually cheap; aggregate route metadata/saved-state footprint is separately bounded.
- same-process Back restoration vs collector quiescence: **PASS** — retained UI context may restore immediately; ACTIVE re-entry reconciles bounded local truth without hidden remote refetch.
- latest-wins vs physical Home Search cost: **PASS** — correctness cancellation no longer substitutes for query-plan/scaling evidence.
- Main-thread publication vs heavy transformation: **PASS** — Main owns small UI publication only; collection-scale CPU and blocking I/O/network have explicit non-Main owners.
- accepted Step 2 red debt vs Step 3 expansion: **PASS** — debt is no-growth and final graph/profile validation is mandatory.
- shared artwork runtime vs duplicate same-key work: **PASS** — equivalent in-flight work is coalesced/bounded with multi-consumer cancellation semantics.
- process admission ceiling vs foreground starvation: **PASS** — fairness/non-starvation is now part of the normative and verification contract.
- active-concurrency bound vs pending-queue growth: **PASS** — waiting work is separately bounded; suspended callers/queued artwork cannot accumulate without limit.
- retained-route restoration vs in-flight transient work: **PASS** — route context is retained, but route-owned Search/Listing/Similar/Detect work is cancelled/invalidated and late results cannot re-inflate inactive state.
- local snapshot enrichment vs Room invalidation amplification: **PASS** — semantically identical writes are explicitly rejected as performance churn.
- shared semantic DB ownership vs physical startup coupling: **PASS** — a shared Room DB cannot make Home cold-open/migration work primarily scale with unrelated aged Catalog cache.
- Library idempotency vs ordering churn: **PASS** — repeated Add and metadata-only enrichment cannot bump `savedAt` or reorder Home.
- immediate Settings apply vs same-value churn: **PASS** — selecting the already committed value is explicitly a no-op, including avoiding unnecessary locale/theme recreation.
- route history retention vs persistent Compose-tree retention: **PASS** — R1.5 treats RETAINED as logical state; offscreen full composition/effects are not restoration authority.
- incremental pagination vs malicious/broken continuation: **PASS** — non-advancing/cyclic cursors and unbounded zero-delta page walking are explicitly bounded/stopped.
- bounded redirect vs auth/header leakage/retry amplification: **PASS** — cross-origin sensitive-header propagation requires explicit policy and transport retries are bounded.
- one committed Reading binding vs multiple retained Story routes: **PASS** — binding/clear mutation serialization lives at the Reading Source owner per Story, not in route-local locks; identical commits are no-op.
- multi-authority shared retention vs V1-style global cache accounting: **PASS** — eviction candidate work is explicitly bounded/indexed by batch and cannot reaggregate full Catalog history per point acquisition.

### 6.1 Active-contradiction scan

- dead `Manage Reading Sources` CTA: **PASS** — the phrase appears only in an explicit prohibition/traceability note; no actionable CTA is admitted.
- unconditional Similar functional phrase: **PASS** — Similar is capability-gated.
- Plugin enable/disable treated as a Step 3 function: **PASS** — enable/disable appears only as later-wave/deferred lifecycle language.
- `WEB_NOVEL` admitted into Light Novel root: **PASS** — the only normative occurrence explicitly defers/rejects it for Step 3.
- Home default root bypasses FirstRun: **PASS** — explicitly forbidden by §1.2.1.
- Home artwork resolves policy through full Catalog activation: **PASS** — forbidden by §17.5/§24.
- closing a Catalog session closes a shared physical DB: **PASS** — forbidden by §5.5/§24.
- deep navigation retains unlimited transient payload because each route is individually bounded: **PASS** — §6.11 adds aggregate budget/compaction.
- Android `INTERNET` permission is treated as eager-runtime admission: **PASS** — §7.10 separates permission from activation.
- Catalog/Detect/artwork each create independent unbounded concurrency: **PASS** — §21.7 requires shared admission.
- mapped Story has no path back to default/unmapped state: **PASS** — §13.6.1 adds explicit clear/use-default semantics.
- default Catalog change mutates existing retained result authority: **PASS** — §7.11 forbids hot-swap.
- transient Reading Source failure is mislabeled as source absence: **PASS** — §13.13 distinguishes presence from operation failure.
- unconditional Catalog-language preference claimed by traceability: **PASS** — R0 #56 is now clarified/capability-gated.
- cheap route identity is allowed to accumulate without limit after payload compaction: **PASS** — §6.11.1 separately bounds route-entry/history metadata and saved-state growth.
- retained Story/Home routes keep one live local observer each forever: **PASS** — route-scoped collectors quiesce by default; exceptions require aggregate-bounded evidence.
- Home Search uses latest-wins but still scans/sorts the entire durable corpus on every keystroke: **PASS** — explicitly forbidden without hard corpus bound + accepted evidence.
- heavy validator/dedupe/projection work silently executes on Main because the repository API is suspend/reactive: **PASS** — execution-owner contract is explicit and thread ownership is verified.
- Step 3 moves the Step 2 performance baseline to hide slower Story transitions: **PASS** — red debt is no-growth; worsening requires explicit new debt review.
- multiple visible consumers multiply the same artwork fetch/decode: **PASS** — equivalent work must be coalesced/bounded.
- global concurrency semaphore permits artwork backlog to starve foreground user commands: **PASS** — fairness/non-starvation is required.
- a bounded semaphore still permits an unbounded queue of suspended expensive work: **PASS** — pending backlog is explicitly bounded and route cancellation invalidates queued work.
- switching roots preserves Search/Similar/Detect network work in the background because the route still exists: **PASS** — route-owned transient expensive work is cancelled on RETAINED; only materialized context remains.
- lifecycle cancellation from root/tab switching is surfaced as provider/offline Failure: **PASS** — §18.4 classifies retention cancellation separately and forbids false error UI.
- Story metadata enrichment repeatedly writes identical Library snapshots and wakes Home: **PASS** — no-op snapshot writes are rejected.
- sharing one physical Room database makes Home startup pay for aged Catalog migration/history scans: **PASS** — shared topology requires aged-fixture startup proof or must be split.
- idempotent Add still refreshes `savedAt` and silently changes Library order: **PASS** — explicitly forbidden.
- tapping the already-selected Theme/App language/default rewrites storage or recreates UI: **PASS** — same-value selection is a semantic no-op.
- independent root histories are implemented by leaving all three NavHosts/composition trees fully active forever: **PASS** — explicitly forbidden; logical history is separate from active composition.
- broken provider continuation returns the same/cyclic cursor or endless duplicate pages and Hikari keeps fetching: **PASS** — continuation/no-progress guards stop unbounded walking.
- HTTP redirect forwards source auth to a new origin or automatic retry silently multiplies requests: **PASS** — explicit policy/bounded retry is required.
- two routes for the same Story race independent binding writes or repeatedly rewrite the same binding timestamp: **PASS** — owner-level per-Story serialization/idempotency is explicit.
- every Catalog insert/Story open scans/reaggregates the entire historical Catalog to compute retention: **PASS** — §7.7 forbids this and requires bounded/indexed eviction work.

## 7. Final findings from the traceability pass

The earlier traceability pass found decisions that were semantically present only indirectly or had been compressed too far in R1.2. R1.3 restored them explicitly, and R1.4 preserves those restorations:

- Story Hero Back contrast/scrim, safe-area/touch semantics, and the intentionally empty opposite corner.
- Listing → Story → Back pages/scroll restoration and Listing → Discover position restoration.
- Library/StoryReadingBinding protection from destructive Catalog-cache cascade semantics.
- Point-scoped StoryReadingBinding observation rather than an observe-all mapping corpus.
- Reading Source picker card contents/current-default-unavailable markers and the committed source staying visually stable while exploring alternatives.
- Explicit Detect Empty vs Failure/offline semantics and alternate-title suggestions as input-only shortcuts.
- Stable Settings IA as a Wave 11 extension seam plus the complete deferred-Settings inventory.
- Poster-skeleton geometry matching the shared poster card/frame.
- Targeted retirement/renaming of Step 2 global-nav and `StoryPreview*` presentation seams once they own real Step 3 behavior.
- Explicit no-speculative-prefetch policy for Search/Listing/Similar/Detect in Step 3.
- Explicit exclusion of V1 manual URL mapping / mapping policy-version machinery from Reading Source setup.

No new user-facing product subsystem was added by these fixes; they restore already agreed semantics and make hidden implementation assumptions explicit.

### 7.1 R1.4 repository-realizability findings

The final repository pass additionally compared the design against current Step 2 implementation seams rather than checking decision coverage alone. It found and closed six architecture blockers:

1. Step 1 startup-gate handoff vs Home-default wording;
2. Home/artwork policy depending on full Catalog runtime activation;
3. logical Catalog-session close being able to close its opened physical Room database;
4. individually bounded routes still allowing unbounded aggregate retained payload;
5. Step 3 requiring production remote transport while Step 1/2 static policy deliberately forbids production `INTERNET`;
6. per-capability single-flight not bounding aggregate Catalog/Reading/artwork pressure.

It also closes the smaller product/transition gaps for clearing a Story Reading binding, configurable-default-Catalog transition semantics, app-level Settings/About routing, Reading Source presence-vs-health vocabulary, intentional build-policy evolution, and R0 Catalog-language traceability.

### 7.2 Adversarial consistency pass after the fixes

- Startup: no new path bypasses `Unknown -> FirstRun -> Ready`.
- Activation: permission, construction, descriptor lookup and route retention are all explicitly non-equivalent to remote activation.
- Persistence: semantic ownership remains independent even if one physical DB is shared.
- Navigation: deep history may remain logically intact without retaining unbounded payload/runtime.
- Images: artwork stays outside Design System/domain truth and no longer needs Catalog payload activation for policy lookup.
- Network: security/boundedness is admitted without re-admitting generic plugin/runtime topology.
- Resource governance: process-wide admission coordinates cost only; it does not own Search/Detect/Story business policy.
- Reading Source: clear/remap/default intents are now all explicit and authority-preserving.
- Catalog defaults: existing route/provenance authority is frozen; future default affects a new discovery context only.
- Language: App/Catalog/Reading domains remain separate without inventing unsupported Catalog settings.

R1.4-stage result: **PASS — the six repository-realizability blockers were closed.** The audit then continued into physical-work/performance hardening rather than treating that intermediate result as final.

### 7.3 R1.5 performance-hardening findings

The R1.5 adversarial pass focused on physical work and lifetime growth that could still occur even when semantic ownership was correct. It found and closed nineteen remaining loopholes:

1. per-route point observers could still grow linearly with deep retained navigation;
2. Home Search latest-wins correctness did not itself prevent O(total Library history) physical work per keystroke;
3. new Step 3 validation/mapping/query work lacked an explicit inherited CPU/I/O/network execution-owner contract in this spec;
4. accepted Step 2 red performance debt was visible but not explicitly frozen against regression by Step 3 expansion;
5. opportunistic Library snapshot enrichment could create no-op Room invalidation/recomposition churn;
6. shared artwork infrastructure bounded concurrency but did not explicitly require same-key in-flight coalescing/shared cancellation;
7. process-wide admission bounded concurrency but did not yet prove foreground fairness under noncritical artwork pressure;
8. compacting route payload still left cheap route-entry metadata/saved-state size theoretically unbounded under extreme nested navigation;
9. retained routes could still allow already-started transient Search/Listing/Similar/Detect work to continue consuming resources and late-complete into inactive state;
10. bounding active concurrency alone could still leave an unbounded queue of suspended/pending expensive work;
11. aggressive RETAINED cancellation could be misclassified as provider/offline Failure and create false error UX;
12. a semantically shared-but-physically-shared Room DB could still couple Home cold-open/migration cost to unrelated aged Catalog history;
13. repeated Add/metadata enrichment could churn `savedAt`, reorder Home and trigger avoidable invalidation;
14. immediate Settings apply could redundantly persist/recreate/reconfigure when the chosen value was already committed;
15. independent root/history retention could be implemented as permanently composed/resumed offscreen UI trees, keeping effects/images/collectors alive despite logical quiescence rules;
16. opaque pagination continuation could cycle/non-advance or return endless zero-delta pages and create an unbounded request loop;
17. redirects/automatic transport retries could amplify work or leak sensitive source credentials across origins without an explicit bound/policy;
18. the same Story opened in multiple retained/root routes could race route-local Reading-binding commits or repeatedly rewrite an identical binding;
19. multi-authority shared Catalog retention could regress into V1-style full historical cache accounting on every point materialization/eviction pass.

R1.5 converts each into a normative rule plus deterministic verification evidence. No user-facing product subsystem or Step 4 capability was added.

### 7.4 Final adversarial conflict scan after R1.5

- **Route-entry bounding does not redefine normal Back behavior:** ordinary history remains intact within the admitted bound; only extreme deep-history pressure may deterministically trim/collapse oldest reconstructible ancestors while preserving every root identity, each root's last-selected/top route, the current immediate Back parent, and durable truth.
- **Collector quiescence does not break Back restoration:** route identity, scroll/query/tab and retained payload may remain; only route-scoped live observation is quiesced. On ACTIVE re-entry, bounded local truth reconciles without remote refetch.
- **Collector quiescence does not break Home mutation truth:** Add/Remove persists locally; Home can re-query/reobserve local Library on reactivation while preserving scroll/filter state. This is local truth reconciliation, not Catalog refresh.
- **Home Search scaling rule does not over-specify storage technology:** FTS/index/windowing remains implementation-level; only the physical-work bound/evidence is normative.
- **Execution-owner rule does not forbid all Main work:** small constant/bounded Compose-facing reduction is allowed; only blocking I/O, large decode and collection-scale CPU are prohibited on Main.
- **No-growth debt does not permanently freeze optimization:** debt may be improved/retired with equivalent-or-stronger evidence; only silent worsening/rebaselining is forbidden.
- **Artwork coalescing does not merge security identities:** requests coalesce only when validated artwork identity, provenance/security policy, transform and request-varying headers/auth/cache scope are equivalent.
- **Transient cancellation does not discard durable truth:** only route-owned transient work is cancelled on RETAINED; separately owned durable Catalog acquisition may finish only under its explicit safe lifetime policy.
- **Transient cancellation does not violate explicit-submit UX:** returning to Search/Detect does not silently replay a query; other capability-specific foreground resume behavior may be chosen only where the existing demand contract permits it.
- **Lifecycle cancellation does not become an error state:** ACTIVE -> RETAINED cancellation is neutral lifecycle control; only a real captured provider/transport failure remains Failure.
- **Bounded pending work does not require a global business queue:** admission may reject/defer noncritical work at the infrastructure layer while semantic retry/demand remains with each capability owner.
- **Fair admission does not become business orchestration:** the shared primitive schedules cost classes/permits only; Search/Detect/Catalog authority and request semantics remain with their owners.
- **No-op write avoidance does not suppress real freshness changes:** a semantically changed snapshot may still point-update; only identical presentation state is not deliberately rewritten.
- **Shared DB permission does not waive boot isolation:** physical sharing remains an implementation choice only while aged unrelated data cannot dominate Home open/migration cost.
- **Stable `savedAt` does not block real re-add ordering:** removing then later adding a Story creates a new membership/save time; only repeated Add while already saved and metadata-only enrichment stay timestamp-stable.
- **Same-value Settings no-op does not prevent real immediate apply:** a genuinely different committed value still persists/applies immediately; only identical selection avoids redundant work.
- **Logical retention does not require UI disposal by one specific navigation library:** exact Compose/NavHost mechanism remains implementation-level; only persistent inactive side effects/full active composition are forbidden.
- **Continuation guards do not invent cross-source merge semantics:** they compare opaque continuation/request progress and exact source-local identities only.
- **Transport hardening does not ban all retries/redirects:** bounded source-policy-approved behavior remains allowed; only implicit credential propagation/unbounded retry is forbidden.
- **Owner-level binding serialization does not globalize Reading Source state:** the lock/order is keyed per Story provenance; different Stories remain independent and can proceed concurrently.
- **Bounded Catalog retention does not forbid global safety awareness:** the shared retention domain may see the small cross-authority active-pin set; only full historical reaggregation per point operation is forbidden.

Result: **PASS — no unresolved architecture/performance contradiction found after the R1.5 hardening pass.**

## 8. Final recommendation

**R1.5 is a freeze candidate, not an implementation plan.**

After the repository-realizability and performance-hardening passes, the identified ownership, lifetime and physical-work loopholes are represented as explicit normative contracts and verification gates rather than left for implementation to invent. Remaining uncertainty is implementation-level (exact navigation mechanism, module split, DB topology, cache/budget numbers, concurrency primitive) or capability-gated (real Similar/Listing/Catalog-language support).

Do not begin Step 3 implementation until the repository's current Step 2 final acceptance/freeze checkpoint is verified and the user approves this R1.5 freeze candidate.
