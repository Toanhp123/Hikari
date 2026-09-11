# Hikari V2 Step 2 - Task 14 Visual Acceptance

Date: 2026-09-11
Status: **COMPLETED/ACCEPTED**

## Authority And Reference Intake

- Canonical design: `docs/superpowers/specs/2026-09-08-hikari-v2-step-2-discover-story-foundation-design-R2.8.md`
- Implementation plan: `docs/superpowers/plans/2026-09-08-hikari-v2-step-2-discover-story-foundation-implementation-plan.md`
- User Visual Direction: User-provided mockups (2026-09-11):
    - Image 2 / Direction 3 ("Magazine Style Grid") for Discover screen.
    - Image 1 / Direction 3 ("Whispers in the Rain") for Story Detail screen.
- Written blueprint: `docs/ui/references/product-ui/task14-dantotsu-inspired-blueprint-r1.md`
- Companion image: `docs/ui/references/product-ui/task14-dantotsu-inspired-blueprint-r1.png`
- The written blueprint's reviewed LF-normalized SHA-256 is `90d29a254c76632447d79b8053518e9e6567e52661ca00616897420dd8586c52`; its Windows worktree bytes use CRLF and therefore have raw SHA-256 `30de470f4e552ab6bcb512484f23efa6c21b85cead28b4aeb4249c3117225fd9`.
- Companion image SHA-256: `872ac472b05caa3de63b39c491360f4a386ac6fc58f0c541ed38258043f5fff1`.

## Image -> Contract Extraction (Direction 3 Alignment)

| Visual area           | Accepted Direction 3 cue                                                                                                      | Required Hikari implementation                      | Step 2 bypass / disabled stub                                |
| --------------------- | ----------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------- | ------------------------------------------------------------ |
| Global visual mood    | Warm-paper surfaces (`#FFF9F7`/`#F6F0EC`), deep charcoal dark (`#131318`/`#181820`), coral rose (`#F4515B`), teal (`#2A9D8F`) | `HikariPalette.kt` theme tokens                     | None; full theme integration                                 |
| Discover Header       | "Discover" title + search icon                                                                                                | Top app bar with circular `SearchIcon` button       | Search icon is disabled stub (`enabled = false`)             |
| Discover Hero         | Editorial banner "Stories for a Brighter You" with pagination dots                                                            | Full-width rounded hero card (180.dp)               | Static editorial card; no network carousel                   |
| Discover Rails        | "Trending Now" & "Recommended for You" poster rails with "See All"                                                            | Horizontal rails (136x192 & 104x150 dp covers)      | "See All" text buttons are disabled stubs                    |
| Discover Quote        | Editorial Quote card "A good story stays with you."                                                                           | Styled quote card with italic serif and attribution | Static presentation element                                  |
| Discover Top Rated    | Ranked list with position numbers                                                                                             | Vertical ranked items with clean single-digit rank  | Driven by top rated catalog projection                       |
| Discover Floating Nav | Floating pill navbar: Manga, Home, Light Novel                                                                                | Floating `CircleShape` pill with Home in center     | Filter switches media type; Home scrolls to top              |
| Discover Bottom Nav   | Removed per user request                                                                                                      | Floating pill is sole bottom navigation bar         | None (bottom bar removed)                                    |
| Story Hero            | Large cover artwork banner with bottom gradient fade                                                                          | 280.dp banner with vertical scrim fade into surface | Displays story cover and title                               |
| Story Rating & Badges | Star rating (★ 9.5) and status/type pill chips                                                                                | Formatted rating row with colored badges            | Displays rating & projection status/media                    |
| Story Actions         | Primary CTA "Read from Chapter 1", "Add to Library", action icons                                                             | Primary button, outline + `BookmarkIcon`, icon row  | Stubs (`enabled = false`); no reader or library side-effects |
| Story Top Bar         | Back arrow & Favorite circular icon buttons                                                                                   | Circular buttons with `BackArrowIcon` & `HeartIcon` | Stubs (`enabled = false`)                                    |
| Story Metadata        | 2-column key-value grid (Authors, Artists, Status, Language)                                                                  | Structured metadata card layout                     | Bounded to story projection metadata                         |
| Story Genres          | Rounded genre pill chips                                                                                                      | Horizontal flow of genre chips                      | Displays genre list from projection                          |
| Story Tabs            | Synopsis, Chapters, Similar tab bar                                                                                           | Tab row with active indicator                       | Stubs (`enabled = false`)                                    |
| Story Synopsis        | Expandable "About" description with "Read more / Read less"                                                                   | Toggleable expanded state with threshold            | Inline UI state only                                         |
| Story Recommendations | "You May Also Like" shelf                                                                                                     | Horizontal recommendation poster rail               | Presentation placeholder                                     |

## Locked Root Contracts

- Spacing remains exactly `4/8/12/16/20/24/32.dp`.
- Material shapes remain exactly `8/12/20/28/36.dp` plus `CircleShape` for floating pills.
- Root backgrounds remain `Color.White` in light mode and `Color.Black` in dark mode.
- Font ownership remains platform `FontFamily.Serif` and `FontFamily.SansSerif` with zero runtime/resource/downloadable-font I/O.
- Non-Step-2 actions are strictly non-functional stubs (`enabled = false`) without domain or acquisition side-effects.

## Verification Status

- Agent-owned focused correctness: `PASS`.
    - Design System production/androidTest compile: PASS.
    - Feature production/androidTest compile: PASS.
    - `:feature:catalog:testDebugUnitTest` + `:catalog:domain:test`: PASS.
    - `scripts/tests/v2-step2-designsystem-slice-test.sh`: PASS.
    - `./gradlew verifyProductionPackageStructure`: PASS.
    - `./gradlew detekt --no-daemon`: PASS (0 issues in modified files).
    - `git diff --check`: PASS (0 whitespace errors).
    - `./gradlew :app:assembleDebug`: PASS.
- Follow-up Direction 3 cleanup: `PASS`.
    - Stateless navigation RED/GREEN unit regression: PASS.
    - Design System + Catalog production/androidTest compilation, focused Discover/Story/Nav unit
      tests, and `verifyProductionPackageStructure`: final post-review `BUILD SUCCESSFUL` in 10s
      (63 tasks).
    - Design System slice script via Git Bash: PASS.
    - `git diff --check`: PASS.
- User-owned connected Design System contracts: `PASS` on Redmi Note 9S / API 35 (`7` tests, `BUILD SUCCESSFUL` in 27s).
- Earlier user-owned connected Discover/Story and broad architecture/Detekt evidence was PASS, but
  is superseded for the affected Catalog surface by the follow-up production refactor.
- First returned rerun after cleanup:
    - Broad `:app:verifyFoundation verifyArchitecture detekt`: PASS in 14s (51 tasks). The changed
      cone reported three `LongMethod` warnings, now addressed by extraction pending the rerun.
    - Connected Catalog: FAIL, 20/23 passed. Two tests asserted lazy Story content before scrolling;
      one route test still searched for removed Back text.
- Returned-failure repair: Story root/back semantics and the three test selectors are aligned with
  the real detail/icon/LazyColumn UI; focused production/androidTest compile, unit tests, and package
  verification pass (final `BUILD SUCCESSFUL` in 9s, 63 tasks).
- Final user rerun after the returned-failure repair:
    - Connected Catalog: PASS, reported on 2026-09-11.
    - Broad `:app:verifyFoundation verifyArchitecture detekt`: PASS, reported on 2026-09-11.
- Final acceptance-tree focused compile/unit/package gate: `BUILD SUCCESSFUL` in 21s,
  69 actionable tasks; the Design System slice script and `git diff --check` also pass.
- Accepted commands:

```bash
./gradlew :feature:catalog:connectedDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.feature.discover.DiscoverScreenInstrumentedTest,app.openstory.catalog.feature.story.StoryDetailScreenInstrumentedTest,app.openstory.catalog.feature.story.StoryRouteRestorationInstrumentedTest' \
  --no-daemon

./gradlew :app:verifyFoundation verifyArchitecture detekt --no-daemon
```

- User-owned visual acceptance: `PASS` (the user explicitly accepted the complete checklist on
  2026-09-11).

## User Visual Acceptance Checklist

- [x] Discover Manga - Ready / compact dark.
- [x] Discover Light Novel - Ready / compact dark; selected pill remains obvious without color alone.
- [x] Discover - representative light theme.
- [x] Discover - Loading; skeleton hierarchy matches final geometry.
- [x] Discover - Empty; copy hierarchy and readability are coherent.
- [x] Discover - retryable Error and retained refresh failure; Retry/feedback remain subordinate.
- [x] Discover - pull-to-refresh idle, refreshing, and settled states fit the new theme.
- [x] Story - Ready; portrait hero and content type/title/rating/status/update scan correctly.
- [x] Story - summary-null and rich-detail loading; no layout jump or fake copy.
- [x] Story - retryable/retained failure plus representative light or wide composition.
- [x] FirstRun after clear-data when practical; the root palette/type migration remains coherent.

Task 14 correctness verification and user-owned visual acceptance are complete. Task 14 is
completed/accepted; Task 15 remains `NOT RUN` and was not started in this acceptance turn.
