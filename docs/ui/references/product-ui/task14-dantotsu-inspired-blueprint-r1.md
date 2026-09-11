# Hikari V2 Step 2 — Task 14 Dantotsu-Inspired Visual Blueprint R1

Date: 2026-09-11
Status: **APPROVED VISUAL BLUEPRINT FOR TASK 14 — R2.8 SYNCHRONIZED**
Companion image: `task14-dantotsu-inspired-blueprint-r1.png`
Companion image SHA-256: `872ac472b05caa3de63b39c491360f4a386ac6fc58f0c541ed38258043f5fff1`

## 1. Authority

This document freezes Task 14's presentation geometry, density, hierarchy, component silhouette, and Manga/Light Novel navigation direction. It is inspired by Dantotsu/ReDantotsu but does not copy their architecture, effects, assets, or unsupported product actions.

Authority order:

1. canonical Step 2 R2.8 design/spec for product, data, architecture, exact root palette, and exact typography roles;
2. this blueprint for Task 14 geometry/composition;
3. the companion PNG for visual mood, hierarchy, proportions, density, and silhouette;
4. bundled Hikari V1 screenshots as a visual quality floor only;
5. Dantotsu/ReDantotsu as external inspiration only.

The PNG's illustrated token sidebar is **not** a literal token sheet. Do not sample its colors or font values. The following concept-only elements are outside Step 2 unless already backed by an admitted contract: `Read`, `Add to Library`, `Chapters`, `See all`, `Year`, `R15+`, Search, bookmark, Library/Reader destinations, and any invented metadata.

Story media type and latest-update copy are allowed only because the accepted Story summary projection already contains `contentType` and `latestUpdateEpochMs`.

## 2. Root Design System relationship

Task 14 preserves Task 13's Design-System architecture but intentionally performs the exact R2.8 visual-token migration defined in the canonical spec.

Locked values/ownership:

```text
Spacing: 4 / 8 / 12 / 16 / 20 / 24 / 32 dp
Shapes : 8 / 12 / 20 / 28 / 36 dp
Root background: light = White, dark = Black
Font ownership: platform Serif + SansSerif, zero runtime/resource/downloadable-font I/O
Theme ownership: one root HikariTheme / MaterialTheme
```

Intentionally migrated by R2.8:

```text
HikariPalette.kt     exact canonical R2.8 palette values
HikariTypography.kt  exact canonical R2.8 Material3 role values
HikariErrorState     visual hierarchy only; behavior/state ownership unchanged
HikariInlineFeedback visual hierarchy only; behavior/state ownership unchanged
```

Do not introduce a second spacing scale, semantic-color hierarchy, public `HikariDimensions`, global breakpoint system, generic Catalog navigation primitive, blur/glass/backdrop system, shimmer, infinite animation, image/network owner, or new runtime dependency.

## 3. Reference geometry

```text
Compact reference width                  360 dp
Wide Catalog threshold                   600 dp
Compact horizontal inset                  20 dp = space20
Wide horizontal inset                     32 dp = space32
Major section gap                         32 dp = space32
Section title -> content gap               12 dp = space12
Horizontal shelf gap                       12 dp = space12
Portrait artwork ratio                      2:3
Minimum interactive target                 48 dp
```

Feature-specific dimensions remain internal to `:feature:catalog`; values that match generic spacing/shape roles must consume `MaterialTheme.hikariSpacing` / `MaterialTheme.shapes` rather than duplicate constants.

## 4. Floating Manga / Light Novel navigation

Manga and Light Novel are top-level **Catalog media destinations**, not a form/filter segmented field. The navigation is a floating bottom pill on Discover only.

```text
Navigation height                          64 dp
Maximum navigation width                  400 dp
Outer padding                               4 dp = space4
Selected destination height                56 dp
Outer radius                               36 dp = shapes.extraLarge
Selected radius                            28 dp = shapes.large
Bottom product gap above nav safe inset    16 dp = space16
Content breathing room above nav            24 dp = space24
Discover bottom clearance                 104 dp + navigation-bar bottom inset
```

Required behavior:

- root is one full-size `Box`;
- `HikariPullToRefresh` owns the caller-sized Discover scroller branch;
- floating nav is a sibling overlay aligned `BottomCenter`;
- navigation-bar inset is consumed by an outer wrapper, not inside the fixed 64.dp pill;
- feature-local `selectableGroup` / `Role.Tab` semantics;
- reselecting active media is a no-op;
- switching media dispatches the existing media-selection intent and resets the newly selected feed to item 0;
- Discover -> Story -> Back preserves selected media and existing Discover list position;
- Story Detail never renders this media nav.

## 5. Discover composition

The selected media name (`Manga` or `Light Novel`) is the page identity. Generic developer copy is forbidden. Optional product support copy is `Discover extraordinary stories.`. No fake Search box or `See all` action.

### 5.1 Popular — artwork-led hero rail

```text
Card                               296 x 184 dp
Cover                              104 x 156 dp
Card radius                             20 dp = shapes.medium
Cover radius                            12 dp = shapes.small
Cover -> copy gap                       16 dp = space16
Title role                         headlineSmall, max 2 lines
Items                                <=5
```

At 360.dp: content width is 320.dp; a 296.dp card plus 12.dp item gap leaves a deliberate 12.dp peek of the next card. The cover is vertically centered inside the 184.dp card; the resulting 14.dp top/bottom breathing room is **derived geometry**, not a new spacing token.

### 5.2 Latest Updates — dense poster rail

```text
Poster                              92 x 138 dp
Poster radius                            12 dp = shapes.small
Poster -> title gap                       8 dp = space8
Tile gap                                 12 dp = space12
Title role                         titleSmall, max 2 lines
Support role                        bodySmall, max 1 line
Items                                <=9
```

Use a horizontal poster-first rail. Do not turn Latest back into full-width vertical rows and do not wrap every tile in a filled/elevated card.

### 5.3 Top Rated — rank-led vertical list

```text
Minimum row height                        88 dp
Rank column width                         36 dp
Cover                                  48 x 72 dp
Rank -> cover / cover -> copy gap         12 dp = space12
Items                                <=5
```

Top Rated is not a third horizontal card rail. Prefer typography, spacing, and restrained surface/divider treatment over large repeated elevated cards.

### 5.4 Discover spacing owner

Remove the Task 13 root `LazyColumn(verticalArrangement = spacedBy(20.dp))`. Task 14 owns explicit rhythm:

```text
header -> first section              space32
section title -> section content     space12
section -> next section              space32
```

The final Top Rated row must be scrollable completely above the floating nav; bounds evidence is required.

## 6. Story Detail composition

### 6.1 Compact identity hero

```text
Screen horizontal inset                  20 dp = space20
Cover                               112 x 168 dp
Cover radius                             12 dp = shapes.small
Cover -> identity gap                    16 dp = space16
Title role                         headlineMedium, max 3 lines
```

The identity cluster may render only trusted accepted fields: `contentType`, title, cover, rating, publication status, and a derived absolute latest-update label when present.

`contentType` becomes the `MANGA` / `LIGHT NOVEL` eyebrow. `latestUpdateEpochMs` becomes a deterministic `Updated MMM d, uuuu` label using UTC + `Locale.ENGLISH`. Raw epoch, `sourceVersion`, `detailProvenance`, raw route/ref identity, and acquisition internals stay hidden.

### 6.2 Detail body

Order:

```text
About / description
Authors
Artists
Genres
Status + Language
```

Genres may use a bounded wrapping chip flow. Summary/cover remain visible while rich detail loads or fails. No Chapter/Reader/Library/year/age-rating/source/debug fields are added.

### 6.3 Summary-null loading

A Story can initially have `summary == null`. Loading must reserve the final compact hero geometry rather than inventing a fake title/metadata. Cover continuity may use the already-owned route/image path when available. Transition from skeleton to summary must not change the hero's outer geometry.

### 6.4 Wide reflow >=600.dp

```text
Wide screen inset                        32 dp = space32
Story cover                         144 x 216 dp
Identity gap                              24 dp = space24
```

Recompose; do not proportionally scale every compact dimension. Discover keeps artwork geometry and gains visible density/space. Story can use the additional horizontal room for identity/body grouping while retaining bounded readable line lengths.

## 7. Shared state surfaces

The R2.8 visual-token migration affects all shared states. Task 14 therefore verifies, without changing ownership semantics:

- Ready Manga and Light Novel;
- Loading/static skeletons;
- Empty;
- Error + Retry;
- retained refresh failure / inline feedback;
- pull-to-refresh;
- Story Ready;
- Story summary-null/loading;
- Story detail failure;
- representative light and dark themes;
- compact and wide layouts.

Automation/connected tests own correctness. The implementing agent may prepare captures and evidence but may only stop at `READY FOR USER VISUAL ACCEPTANCE`; the user owns the final visual PASS/FAIL.

## 8. Bundled V1 quality-floor references

These images are evidence only; they do not restore V1 IA or implementation ownership.

| Reference | SHA-256 |
|---|---|
| `task14-v1-quality-floor/discover-compact-dark.png` | `496d10de5c210cefa61d1481528a422b81356b880de6bab81db7c79251f39878` |
| `task14-v1-quality-floor/discover-compact-light.png` | `0bf0d449c2ad58a3aa271d1dba8323cad54e65654cdbd30ee57d0d7cd30deb2c` |
| `task14-v1-quality-floor/discover-medium-dark.png` | `ae5648f23f961afdb23d59ddbcd922fbd7181a39271ab31776cc27c030024e0f` |
| `task14-v1-quality-floor/story-compact-overview.png` | `aacffefac4b4306531747a21f96bfb98617d5b0144d05f4e8837d1638352fa04` |
| `task14-v1-quality-floor/story-large-phone.png` | `8c241ba9140d20a6dcef480a526bdc4f60179eb325309d307f0f7c9499610fb3` |
| `task14-v1-quality-floor/story-medium-two-pane.png` | `adef51e94d5e28f8ef05aa50abbd080cc8828f0344a25416ff5378994a47dfb1` |

If the user attaches the approved concept directly to Codex, inspect it before production edits. Record SHA-256 only when a filesystem path is actually available; otherwise record `ATTACHMENT_ONLY — HASH UNAVAILABLE`. Never invent a hash.
