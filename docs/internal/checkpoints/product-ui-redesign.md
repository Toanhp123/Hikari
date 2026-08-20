# Product UI Redesign Checkpoint

Date: 2026-08-14
Status: **ACCEPTED**

## Environment

- Branch: `feature/update-ui-design`
- Verified base HEAD: `71111f99f6f8b518bc547fb3dc4fe344018e445c`
- The final Task 16 gate fixes were verified from the working tree before this acceptance commit.
- Operating system: Windows 11 10.0 amd64
- JDK: Eclipse Temurin 17.0.20
- Gradle: 9.5.0
- Android application identity: `app.openstory`

## Accepted Product Boundary

- The only top-level destinations are `Discover`, `Home`, and `Library`.
- Discover, personal Home, Library, Search, Story Overview/Chapters/Sources, mapping,
  Downloads, Updates, and Reader use the accepted responsive product UI.
- Downloads and Updates are the current quick-access utility routes.
- Settings remains owned by Wave 10 and Plugin management remains owned by Wave 11.
  Their target-pack images are future visual references, not current application routes.
- Stable story, chapter, release, plugin, and source identities remain intact across UI
  actions. No implicit remapping was introduced.
- The repository contains no tracked ReDantotsu code or assets outside the approved
  documentation and independently rendered target references.

## Verification Evidence

| Command | Result | Evidence |
|---|---|---|
| `bash scripts/check-module-dependencies.sh` | PASS | Application identity and module boundaries verified; architecture remains at 14 modules. |
| `bash scripts/verify-current-architecture.sh` | PASS | Current architecture verified at 14 modules; Room schemas 1 through 6 remain stable. |
| `./gradlew detekt --stacktrace` | PASS | Analysis completed without an active issue or added suppression/baseline. Existing structural review warnings remain review candidates. |
| `./gradlew verifyRoborazziDebug --stacktrace` | PASS | All current compact, large-phone, medium, dark/light, and UX-state screenshot baselines matched. One transient 186-pixel font-raster comparison in the loaded artwork monogram passed unchanged on the immediate fresh rerun; geometry and production sources were unchanged. |
| `bash scripts/verify.sh` | PASS | Shell contracts, package/source boundaries, architecture, unit tests, lint, Detekt, debug assembly, and Room schema stability completed with Gradle `BUILD SUCCESSFUL` (623 actionable tasks). |
| `git diff --check` | PASS | No whitespace errors. Git only reported the repository's expected Windows line-ending notices. |
| `powershell -ExecutionPolicy Bypass -File scripts/tests/ui-target-pack-test.ps1` | PASS | 34 deterministic PNGs rendered at the required 2x dimensions with non-uniform visual content. |
| `Get-FileHash 'E:\Downloads\Hikari-UI-Target-Pack.zip' -Algorithm SHA256` | PASS | `37E5B60D866E109C2B9B0C71B3E2AD265B7ED57EC2C76D918070BB6C164632DB`. |

## Instrumentation Evidence

Instrumentation was run sequentially to avoid UTP/ADB contention.

| Device | Suite | Result |
|---|---|---|
| `Pixel(AVD) - 8.0.0`, API 26, `emulator-5556` | `:core:designsystem:connectedDebugAndroidTest` | PASS, 10/10. |
| `Pixel(AVD) - 8.0.0`, API 26, `emulator-5556` | `:feature:catalog:connectedDebugAndroidTest` | PASS, original 34/34 suite plus 3/3 focused final Search and medium Story regressions. |
| `Pixel(AVD) - 8.0.0`, API 26, `emulator-5556` | `:feature:reader:connectedDebugAndroidTest` | PASS, 9/9. |
| `Pixel(AVD) - 8.0.0`, API 26, `emulator-5556` | `:app:connectedDebugAndroidTest` | PASS, original 14 scheduled with 0 failed and 4 environment-conditioned live-network skips, plus 1/1 focused final Home focus regression. |
| `Pixel_10_Pro(AVD) - 17`, API 37, `emulator-5554` | `:core:designsystem:connectedDebugAndroidTest` | PASS, 10/10. |
| `Pixel_10_Pro(AVD) - 17`, API 37, `emulator-5554` | `:feature:catalog:connectedDebugAndroidTest` | PASS, 37/37 after the final Search and medium Story regression coverage. |
| `Pixel_10_Pro(AVD) - 17`, API 37, `emulator-5554` | `:feature:reader:connectedDebugAndroidTest` | PASS, 9/9. |
| `Pixel_10_Pro(AVD) - 17`, API 37, `emulator-5554` | `:app:connectedDebugAndroidTest` | PASS, 15 scheduled, 0 failed; 2 environment-conditioned live-network tests skipped. |

The live-network skips are external connectivity conditions and are not UI failures.
API 26 confirms the translucent glass fallback and responsive critical flows. API 37
confirms the current blur path and includes the final Home quick-access keyboard/D-pad
focus regression.

## Deep Product Review

- Discover density, category/catalog navigation, combined partial results, and partial
  failure presentation remain readable across compact, large-phone, and medium layouts.
- Home preserves personal reading identity, empty setup guidance, resume behavior, and
  now moves keyboard/D-pad focus from quick access to the first attached Home action.
- Library status/source/search/sort controls, grid/list presentation, and long-content
  scrolling remain usable without clipping.
- Search keeps successful multi-source results beside source failures and now presents a
  distinct `No matches found` state only after a completed nonblank search.
- Story preserves explicit Overview/Chapters/Sources selection. Medium Overview no longer
  duplicates its description; Chapters and Sources remain in the content pane.
- Mapping confirmation remains explicit, Downloads retains retry/cancel/remove behavior,
  and Updates remains ordered by reading relevance.
- Reader chrome, progress, settings, reduce-motion behavior, and floating-navigation
  visibility remain consistent with the accepted baselines.
- TalkBack labels, 48dp targets, keyboard/D-pad ordering, 200% font scaling, dark/light
  themes, and API 26 fallback were reviewed from instrumentation and screenshot evidence.
- Representative target images reviewed included overview, compact dark Discover,
  medium Story, partial failure, light Library, and Reader; no clipping, overlap, or
  hierarchy defect was observed.

## Post-Checkpoint Refresh UX Follow-up — 2026-08-15

Status: **IMPLEMENTED; FINAL VISUAL RE-RECORD PENDING**

- Discover, Story Overview, and Story Sources use the shared `HikariPullToRefresh`; Chapters remains intentionally non-refreshable.
- Edge-to-edge Discover now passes the shell safe top inset into the refresh owner and removes that inset from the list padding, preventing duplicate inset consumption while keeping the indicator below system chrome.
- Regression coverage now includes real `performTouchInput { swipeDown() }` gestures in the shared primitive and Discover/Story integrations, in addition to the accessibility `Refresh` action.
- Source-detail refresh failures are scoped to Overview/Sources and no longer appear above Chapters.
- Overview, Chapters, and Sources consume one shared Story section-padding owner; nested Sources/Mapping content no longer owns a competing horizontal outer inset.
- The obsolete manual-refresh glyph geometry and refresh-action typography token were removed together with the stale policy requirement that preserved them.
- The original pull-to-refresh implementation plan is marked complete except for the final developer-machine visual/full verification step. The Story/Discover Roborazzi baselines must be reviewed, recorded, and verified after this follow-up before this addendum can be promoted to accepted evidence.

## Decision

The product UI redesign checkpoint is accepted. The current product graph is ready to
continue without claiming Wave 10 Settings or Wave 11 Plugin management as implemented.

## Superseding Discover Follow-up — 2026-08-20

This checkpoint remains accepted evidence for the broader Product UI shell and presentation
baseline. Its Discover-specific source/category composition is no longer the current product
contract. The accepted 2026-08-19 semantic-feed redesign supersedes only that Discover slice,
advances Room from schema 6 to schema 7, and is recorded separately in
`discover-semantic-feed-redesign.md`.

Do not reinterpret the original screenshots or Task-16 evidence as requiring catalog selectors,
quick-category cards, provider-named primary shelves, or the old single-featured-story composition
in current Discover.
