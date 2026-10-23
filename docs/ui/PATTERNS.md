# Presentation patterns

Status: **Preview**. Phase 02 mechanics are implemented; physical/runtime G2
acceptance is separate. See [execution evidence](../roadmap/UI_FOUNDATION_PHASE_02.md).

## Typed modal interaction

Use `showHikariDialog`, `showHikariBottomSheet` or `showHikariAdaptive` with an
explicit `useRootNavigator`. Dialog and sheet remain independent families;
Drawer is navigation only. Adaptive policy snapshots purpose/window/IME once:
selection uses a sheet below 840dp when usable height is at least 360dp;
confirmation/form and shorter windows use dialog. An override must satisfy
supported modes; confirmation cannot become a sheet. Active routes reflow without
switching family, popping/reopening, recreating content or resetting feature state.

`HikariCompleted<T>(value)` is distinct from `HikariCancelled<T>(reason)`, even
when value is null. Controller accepts one settlement; later commands are ignored.
Public helpers return after the owned native route finishes teardown, so subsequent
feature navigation cannot race that route. If another route is above the modal,
settlement removes only the owned modal. Disposal settles pending interaction;
unknown framework exits use `routeDismissed`, never a guessed reason.

Result means **interaction only**. It never proves native trust, permission,
installation, persistence or business success. Revalidate current feature identity,
generation, authorization and operation state after every await before side effects.

## Dismissal and focus ownership

Host owns Navigator selection, route/controller lifetime, barrier/drag mechanics,
Escape/Back handling, focus traversal and route focus return. Feature owns its VM,
validation, nested steps and disposal in `finally`. Keep `canDismiss` synchronized
before pop; `onPopInvokedWithResult(didPop: true)` cannot reverse predictive Back.
A synchronous guard can consume Back to leave a nested step, then permit a later
Back. Do not put domain-specific guard logic in core UI. Protected dialogs remove
barrier dismissal semantics. Protected sheets hide the native handle as well as
disabling drag: Flutter's handle has an independent drag detector otherwise.

Nested keyboard dismissal priority needs an explicit regression: with the
Source Picker **Language MenuAnchor open**, the first Escape closes only the
menu, keeps the picker/selection session alive and saves nothing; a second
Escape may cancel the picker. In the picker's `choosingLanguage` step, the first
Escape returns to the source list and the next dismisses the picker. Check both
sheet and dialog under the same explicit Navigator. A Phase 02 follow-up test
was added on 2026-10-23; its new run result must be recorded before Phase 03.

Dialog provides closed-loop focus traversal. Confirmation focuses safe Cancel,
not destructive confirm. Label close/actions; retain visible native focus indicators.
Native route focus return and widget tests do not certify TalkBack or Windows
screen-reader behavior. See [responsive contracts](RESPONSIVE.md).

## Scroll and inset composition

Canonical chrome supports scrolling header/body/actions together for short windows
and 3x text, or `scrollable: false` when a feature list owns its viewport. Adaptive
raw content owns its internal list and responsive header fallback; host owns IME
and safe-bottom treatment. Do not add another route SafeArea or keyboard padding
inside picker content. Use bounded constraints, not a second nested list viewport.
`HikariContentRegion.sliver` preserves lazy slivers; shared page scaffold is not
mandatory for reader canvases or NestedScrollView pages with specialist edge rules.

## Catalog Source Picker pilot

Production coordinator opens exactly one
`showHikariAdaptive<CatalogSourceSelection>` on the root Navigator, including
under a nested page Navigator. Legacy dialog defaulted root while legacy sheet
defaulted nested; pilot deliberately unifies explicit ownership. One `CatalogSourcePicker`
content tree serves both families. Feature variants are `CatalogCandidateSelection`
and `CatalogManualSearchSelection`; no nullable record can represent conflicting
candidate/manual payloads. Manual payload freezes visible source IDs and language
before resetting VM state.

Picker VM owns source groups, language filter, pending resolver revision,
candidates, empty/error/retry and manual recovery. Content synchronously updates
controller dismissal permission when `choosingLanguage` changes: first Back leaves
the language group; second Back dismisses. Close explicitly cancels. Controller,
not a content-local flag, rejects duplicate completion. Resize preserves route,
VM, language and current source/candidates. Late resolver completion after
cancellation/disposal cannot initiate navigation.

Coordinator owns picker-session concurrency, `finally` disposal, mounted/series/
resolver generation checks, cancellation restore, exact media/source/cover
validation and serialized preference writes. After manual-search await it checks
generation again. Selection does not autoplay Reader; existing chapter/continuation
logic retains exact source identity and its own `canNavigate` checks. Error/empty
state exposes retry, change source and manual search without inventing a diagnosis.

## Phase 04: Continue Reading chrome and media adapter ownership

`ContinueReadingPage` owns an immediate pending route. Loading/error/retry use
`HikariPageScaffold` + `HikariContentRegion` + `HikariAsyncStateView` with a
feature-owned retry callback. Once `ReaderLaunch.reader` resolves, return that
widget directly: do **not** nest its canvas in another browse scaffold. The
existing `_generation`/`mounted` rejection and `ready.abandon()` handling remain
with the reader launch owner. UI migration cannot infer offline cache success.

Domain-bound source metadata and media-type badges now belong to
`features/media/widgets/`, not `core/ui/patterns/`. Canonical core UI must not
import domain, app theme, application workflows or native authorization. Wave
A/B disallows retired Scaffold, MediaPoster, ChapterRow and AsyncStateView
adapters in migrated features. `ChapterListControls` is a shared **feature-owned** chapter-search/order
composition at `features/reading/widgets/` for Catalog Detail and native
Manga/Novel detail. It is not a universal core UI primitive: the source-order
switch and the feature-provided query lifecycle stay outside core. Its sort
and search actions use `HikariIconButton`, while each feature composes the
start/continue CTA directly with `HikariButton` and feature-owned eligibility
checks. None of these controls resolves a chapter or changes Reader-first policy.

`MediaPoster` and `AsyncStateView` are retired after confirming zero production
imports; their responsive, input, focus, and feedback contracts are covered in
canonical media/feedback tests. `PrimaryReadingCta` and the core-owned
`ChapterControlBar` are also retired. The two remaining imports from
`core/ui/patterns/` in Wave A/B are *nonvisual* helpers:
`chapter_list_filter.dart` and `media_artwork_decode.dart`. They are not
canonical UI components and remain subject to 07/01 ownership review.
`HikariRefreshAction` remains a temporary shared component because Reader and
Plugin Manager still consume it. Remaining legacy presentation for Settings,
Plugin Manager and Reader routes retains its scheduled owner and test coverage;
only verified zero-consumer code may be removed. Inventory is lexical evidence,
not a replacement for caller review or runnable regression tests.

## Restoration and migration

No automatic modal restoration registration exists. On process death, reopen only
from validated feature state; never replay a completed interaction or native action.
Legacy `showHikariConfirmation` forwards to canonical typed dialog and maps only
explicit true to true, retaining historical root ownership. Compatibility expires
at cleanup Task 07/01, not a permanent facade. Legacy `HikariScaffold` likewise
forwards to `HikariPageScaffold`, retaining ambient-on default until 07/01; canonical
scaffold defaults ambient off. Other feature dialogs/sheets remain
under their scheduled owners in [migration matrix](MIGRATION_MATRIX.md); pilot
success does not authorize mass replacement.

Debug Gallery has compact/expanded picker family previews using the real content
with empty fake source inventory and explicit preview policy overrides. It neither
performs native installation nor represents physical-device accessibility evidence.

## Gallery composition references (post-G4)

The standalone [UI Gallery](GALLERY.md) demonstrates typed modal result hosts
and bounded page/navigation composition without feature models, real routing or
application authorization. These are Preview examples, not a new canonical
wrapper or a change to the G4 acceptance record.
