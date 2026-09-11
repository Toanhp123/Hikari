# Hikari V2 Step 2 - Task 14 Visual Acceptance

Date: 2026-09-11
Status: **READY FOR USER VISUAL ACCEPTANCE**

## Authority And Reference Intake

- Canonical design: `docs/superpowers/specs/2026-09-08-hikari-v2-step-2-discover-story-foundation-design-R2.8.md`
- Written blueprint: `docs/ui/references/product-ui/task14-dantotsu-inspired-blueprint-r1.md`
- Companion image: `docs/ui/references/product-ui/task14-dantotsu-inspired-blueprint-r1.png`
- The written blueprint's reviewed LF-normalized SHA-256 is `90d29a254c76632447d79b8053518e9e6567e52661ca00616897420dd8586c52`; its Windows worktree bytes use CRLF and therefore have raw SHA-256 `30de470f4e552ab6bcb512484f23efa6c21b85cead28b4aeb4249c3117225fd9`.
- Companion image SHA-256: `872ac472b05caa3de63b39c491360f4a386ac6fc58f0c541ed38258043f5fff1`.
- All six bundled V1 quality-floor images match the reviewed hashes recorded in the blueprint.

## Image -> Contract Extraction

| Visual area | Accepted cue | Required Hikari implementation | Rejected concept-only cue |
|---|---|---|---|
| Global visual mood | neutral charcoal/warm-paper surfaces + coral/teal accents | exact written R2.8 palette, not sampled image values | literal blueprint hex/token panel |
| Typography | editorial identity + quiet metadata | exact written R2.8 Serif/Sans role hierarchy | runtime/custom/downloadable font |
| Header | media name is page identity | Manga / Light Novel title | fake Search/action |
| Popular | asymmetric portrait hero | 296x184 hero; 104x156 cover; 12dp next-card peek at 360dp | invented detail metadata |
| Latest | dense poster shelf | horizontal 92x138 poster rail | full-width rows |
| Top Rated | rank-led list | 36dp rank + 48x72 cover | generic third card rail |
| Media nav | floating two-destination pill | Discover-only Manga/LN tabs | global nav framework |
| Story hero | portrait + authoritative identity | 112x168 cover + contentType/title/rating/status | year/R15+/library/bookmark |
| Story body | grouped metadata | About/Authors/Artists/Genres/Status/Language | Read/Library/Chapters |
| Loading | final-geometry static skeletons | no layout jump/fake title | shimmer/infinite motion |

## Locked Root Contracts

- Spacing remains exactly `4/8/12/16/20/24/32.dp`.
- Material shapes remain exactly `8/12/20/28/36.dp`.
- Root backgrounds remain `Color.White` in light mode and `Color.Black` in dark mode.
- Font ownership remains platform `FontFamily.Serif` and `FontFamily.SansSerif` with zero runtime/resource/downloadable-font I/O.

## Verification Status

- Agent-owned focused correctness: `PASS`.
  - Design System production/androidTest compile and feature production/androidTest compile: PASS.
  - Focused Discover refresh/ViewModel, Story Detail ViewModel, and cover-failure host tests: PASS.
  - Returned-failure repair cone (`:feature:catalog` production/androidTest compile,
    `CatalogRouteTest`, and `verifyProductionPackageStructure`): PASS in 22s, 61 actionable tasks.
  - `scripts/tests/v2-step2-designsystem-slice-test.sh`: PASS.
  - `git diff --check`: PASS.
- User-owned connected Design System contracts: `PASS` on Redmi Note 9S / API 35
  (`7` tests, `BUILD SUCCESSFUL` in 27s).
- User-owned connected Discover/Story contracts: `PASS`; the user reported `BUILD SUCCESSFUL` on
  2026-09-11 after the three Task 14 regressions were repaired.
- User-owned architecture/Detekt gate: `PASS`; the user reported `BUILD SUCCESSFUL` on 2026-09-11
  after the feature package SCC and both Task 14 `MagicNumber` findings were repaired.
- User-owned visual acceptance: `NOT RUN`.

## User Visual Acceptance Checklist

- [ ] Discover Manga - Ready / compact dark.
- [ ] Discover Light Novel - Ready / compact dark; selected pill remains obvious without color alone.
- [ ] Discover - representative light theme.
- [ ] Discover - Loading; skeleton hierarchy matches final geometry.
- [ ] Discover - Empty; copy hierarchy and readability are coherent.
- [ ] Discover - retryable Error and retained refresh failure; Retry/feedback remain subordinate.
- [ ] Discover - pull-to-refresh idle, refreshing, and settled states fit the new theme.
- [ ] Story - Ready; portrait hero and content type/title/rating/status/update scan correctly.
- [ ] Story - summary-null and rich-detail loading; no layout jump or fake copy.
- [ ] Story - retryable/retained failure plus representative light or wide composition.
- [ ] FirstRun after clear-data when practical; the root palette/type migration remains coherent.

All Task 14 correctness gates are closed. Task 14 cannot be accepted until the user explicitly
reports visual PASS for this checklist or returns concrete visual rejections for repair.
