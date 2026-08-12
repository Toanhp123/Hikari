# Product UI Task 02 — Reproducible Target-Pack Checkpoint

Date: 2026-08-12
Status: **VERIFIED**
Scope: ReDantotsu-inspired Product UI Task 2 — production-code-independent, reproducible UI target rendering and ZIP packaging.

## Accepted boundary

- No production module, Room schema, plugin contract, or domain behavior is changed.
- `tools/ui-target/src/` owns one query-driven HTML/CSS/JavaScript renderer with deterministic mock data and deterministic inline SVG artwork generated from stable story IDs; it does not fetch remote artwork or embed ReDantotsu screenshots.
- `tools/ui-target/render-ui-target.ps1` renders the approved 34-file matrix through Microsoft Edge headless at 2x output density.
- Compact `360x800dp`, large-phone `412x892dp`, and medium `600x960dp` targets render to `720x1600`, `824x1784`, and `1200x1920` pixels respectively; the overview renders at `2400x1800`.
- Dark, light, loading, empty, error, partial-failure, and offline targets are included.
- Plugin Manager and Settings remain visual targets for their later Wave 11 and Wave 10 owners rather than new current routes.
- Generated output under `tools/ui-target/build/` is ignored; renderer source and scripts remain tracked.

## Regression corrections verified during acceptance

The first Windows visual inspection exposed a renderer bug that the original dimension-only gate could not detect: Edge headless can report a CSS viewport different from the requested `--window-size`, so an exact `window.innerWidth`/`window.innerHeight` assertion aborted JavaScript rendering while CSS still produced a correctly sized dark PNG. The accepted renderer therefore uses a fixed target canvas from query parameters and keys responsive behavior from the target width instead of trusting Edge's reported viewport.

A follow-up gate initially rejected the valid light Settings target because a sampled-color threshold required at least eight colors. The accepted guard now checks for truly uniform/blank output rather than requiring an arbitrary palette size. This keeps the original black/blank regression detectable while allowing intentionally low-color screens.

## Verification evidence

| Gate | Result | Evidence |
|---|---|---|
| Target-pack PowerShell contract | PASS | Windows PowerShell rerun of `scripts/tests/ui-target-pack-test.ps1` after both renderer corrections; all 34 required targets rendered with deterministic 2x dimensions and the non-uniform-content guard accepted the final matrix |
| Compact dimensions | PASS | `360x800dp` targets rendered at `720x1600` |
| Large-phone dimensions | PASS | `412x892dp` targets rendered at `824x1784` |
| Medium dimensions | PASS | `600x960dp` targets rendered at `1200x1920` |
| Overview dimensions | PASS | overview rendered at `2400x1800` |
| Visual regression: blank output | PASS | Initial dark/blank captures were reproduced, root-caused to Edge viewport mismatch, corrected with a fixed target canvas, and the final rerun was visually confirmed usable on the Windows development host |
| Visual regression: low-color Settings | PASS | Light Settings produced seven sampled colors; the false-positive threshold was replaced by a uniform-image guard and the Windows rerun completed successfully |
| ZIP packaging path | PASS | `tools/ui-target/package-ui-target.ps1` successfully created `Hikari-UI-Target-Pack.zip` on the Windows host; packaging consumes the same renderer and source matrix |

## Result

Product UI Task 2 is complete. Product UI Task 3 — shared artwork state and stable fallbacks in `:core:designsystem` — is the next implementation task. The target pack remains a design/acceptance artifact; it does not itself change Android production screens.
