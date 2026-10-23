# UI component ownership

Status: **Phase 01–02 Preview implementation; G1/G2 OWNER-ACCEPTED (2026-10-23) for continuation.**
Original incomplete hardware evidence is retained in the execution reports;
see [owner decision](../roadmap/UI_FOUNDATION_OWNER_ACCEPTANCE_2026-10-23.md). Controls, surfaces, navigation and page composition remain Preview;
no Stable promotion. Evidence: [Phase 01](../roadmap/UI_FOUNDATION_PHASE_01.md) and
[Phase 02](../roadmap/UI_FOUNDATION_PHASE_02.md). Interaction/lifecycle contracts:
[patterns](PATTERNS.md).

Task 03 (2026-10-22): canonical button, icon action, filter chip and chip row
live in `lib/core/ui/controls`. Native Material owns activation and interaction
states. Button labels reflow; loading disables activation. Geometry/density
preserved; chip row retains 8dp gaps and horizontal scrolling. Task 07 removed
button/icon/choice/section forwarders and migrated filter callers directly to
HikariFilterChip; no display/filter compatibility adapter remains.
Three removed core-to-app imports had their exact exemptions deleted after
source review; no new exemptions admitted.

Evidence: ownership and label-reflow assertions observed RED, then 26 focused
and existing component tests passed; analyzer reported no issues. Narrow 375dp
and 1x/2x/3x text tested in scrollable host. Physical keyboard/TalkBack/Android
runtime remain unverified; widget tests do not imply platform PASS.
Task 04 (2026-10-22): controlled search, choice menu and segmented control are
Preview. Search owns only presentation/controller listeners; external controller
and focus node remain caller-owned. Typing emits edits, only explicit submit
emits submission. No debounce/request policy in canonical field. Existing search
adapter was removed in Task 07. Catalog/Source route owners preserve the 300ms
edit debounce and explicit submission; local plugin filtering remains immediate.
Choice has one canonical implementation, preserving nullable/empty selection,
MenuAnchor keyboard and focus restoration. Segments use local measured labels,
vertical layout when needed, and bounded wrapping at 3x text.

Evidence: canonical ownership RED then GREEN; segment long-label hit testing
caught overflow and passed after bounding labels. 20 input/search/menu tests
passed; analyzer clean. Windows interactive keyboard and physical IME NOT RUN.

Task 05 (2026-10-22): Preview async state view distinguishes loading, loaded,
true empty, refreshing, recoverable error and fatal error. Features normalize
state; retained content keeps its element position across refresh/error. Notice
uses semantic pairs plus icon/text, retry stays callback-owned. Section actions
wrap; domain-free list row grows with text and disables native interaction.
Legacy AsyncStateView remains staged for later feature migration (07/01), not
part of curated public API. Section export was removed in 01/07.

Evidence: missing ownership RED, then retained editor identity, retry, true-empty
and 3x text widget tests GREEN. Physical semantics/IME remain NOT RUN.

Task 06 (2026-10-22): [curated controls API](components/controls.md) and
[contribution/promotion contract](CONTRIBUTING.md) now available. Standalone
`tool/ui_gallery/main.dart` exercises dark/OLED, six seeds, enabled/loading,
stress labels, RTL, reduced motion, local widths, scales and async states.
Four public API/gallery/isolation tests passed; analyzer clean. Runtime/build
checks remain separate G1 evidence. No component promoted to Stable.

Approval and supersession: [ADR-026](../decisions/ADR-026-ui-foundation-rebuild.md).
Current callers and staged migration: [matrix](MIGRATION_MATRIX.md).

| Family | Target owner / mechanics | Feature responsibility | Phase/task |
|---|---|---|---|
| Tokens / ThemeExtensions | core/ui/foundation | consume semantic roles | 01/01–02 |
| Controls / choice / status / HikariListRow | core/ui controls/content | labels, controlled values, validation, domain mapping | 01/03–05 |
| HikariDialog | independent core/ui/surfaces/dialog family | content, authorization, VM and identity | 02/02 |
| HikariBottomSheet | independent core/ui/surfaces/bottom_sheet family | content, validation, workflow state | 02/03 |
| showHikariAdaptive | purpose/constraints policy; dialog or bottomSheet only | useRootNavigator, typed outcome, stale permission check | 02/01, 02/04 |
| HikariDrawer / shell / header | core/ui/navigation/layout mechanics | app destinations/routing/selected tab | 02/06–07 |
| Source Picker | shared host + feature workflow body | language Back, source identity, manual search, persisted confirmed choice | 02/08–09 |
| Artwork / poster / grid / shelf | core/ui media/layout | source-owned artwork identity and loading boundary | 03/01–05 |
| Chapter row / reading CTA | domain-free core/ui/content | Manga/Novel mapping, progress and authorization | 03/06 |
| Plugin dialogs/forms | shared transient shell | exact identity/origin consent; application/native decision | 05/02–05 |
| Reader / player canvas | feature specialist rendering | progress, gestures, engine/session lifecycle | 06/01–05 |

Common hosts settle exactly once; typed completion (including null values) is
separate from cancellation. Back/barrier/drag are precise only if observed;
otherwise routeDismissed. Caller always chooses root/nested Navigator explicitly.
Phase 02 compiler/widget tests freeze public signatures. No sidePanel presenter
mode, generic workflow engine or shared business authorization is introduced.

Current components/patterns remain until their owner task. Framework MenuAnchor,
Tooltip, SnackBar and native OS consent are explicit mechanisms, not duplicate
result hosts. Reader prose palettes and video black remain specialist exceptions.
Component promotion follows Draft/Preview/Stable/Deprecated governance from the
[v4.2 spec](../superpowers/specs/2026-10-22-hikari-ui-foundation-rebuild-design.md);
Phase 00 creates no gallery or new foundation implementation.

See [foundations](FOUNDATIONS.md), [responsive](RESPONSIVE.md) and
[baseline runs](BASELINE_RUNS.md).
