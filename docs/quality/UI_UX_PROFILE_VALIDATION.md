# UI/UX hardening — device validation and performance gate

Scope: `feat/ui-ux-hardening`, after the canonical Hikari theme consolidation and responsive/accessibility fixes. This is a **test plan**, not evidence that performance or assistive-technology checks passed.

## Automated gate (Windows checkout)

```powershell
fvm dart format lib test
.\tool\check.ps1
fvm flutter build apk --debug
git diff --check
```

Run focused tests first when diagnosing failures:

```powershell
fvm flutter test test/components_test.dart test/navigation_shell_test.dart test/home_responsive_layout_test.dart test/features/home/home_header_test.dart test/features/home/continue_shelf_accessibility_test.dart
```

## Manual accessibility gate

- At 320/375/599/600/601/840 logical px, check selected tab, last-item reachability, focused actions, scrolling and live resize without tab-state reset.
- With 100% and 200% Android text size, and Android nonlinear scaling, check HomeHeader, Hero (title and **View details**), Continue cards, navbar labels, retry/Settings buttons; repeat at short landscape height. Stress 300% in widget tests without treating that as a universal device requirement.
- Use TalkBack (Android) and keyboard-only navigation (Windows): tab/Shift-Tab, Enter/Space on action buttons, accessible names on previous/next carousel controls, selected navigation destination, and a single complete spoken label per Continue card.
- Turn on Android **Remove animations** and check that Hikari-authored decorative transitions are disabled; normal loading/progress behavior is a separate contract.

## Profile gate — collect measurements before changing effects

1. Build/run **profile mode** on a representative low/mid-range Android phone (`fvm flutter run --profile`) and on Windows if supported. Do not use debug-mode jank to justify raster changes.
2. Open Flutter DevTools **Performance** and **Memory**. Record device model, display refresh rate, Flutter SDK revision, app revision, and dataset/artwork conditions.
3. Capture a cold Home load and a warm Home load, then scroll through the pinned header, Hero, Continue/discovery shelves, bottom-bar region and back to the top. Repeat enough times to separate first-load effects from steady scrolling.
4. Switch through all tabs, return to Home, navigate Hero pages several times, and compare memory retention/image allocations. Inspect UI-thread vs raster-thread frame times and allocation growth.
5. If blur is a suspect, compare **one** isolated temporary opaque-header/bar variant at a time against the baseline on the same device and dataset. Do not remove both effects simultaneously or interpret a single trace as a result.
6. If hero image decoding is a suspect, compare source image pixel dimensions, rendered device pixels, decode allocations and visual sharpness before adding `cacheWidth/cacheHeight`. Don't hardcode a global decode width without measurements.

At 60 Hz a frame period is ~16.7 ms; at 120 Hz ~8.3 ms. Log trace evidence and whether any change improves meaningful, reproducible frame time or memory use. If performance is acceptable, **retain** the current appearance without speculative cache dependencies, repaint boundaries or effect removal.

## Deferred product decision

Keep the current custom navigation during this correctness pass. A separate **native NavigationBar/NavigationRail comparison prototype** may be made later; compare semantics, keyboard support, large-text behavior, visual fidelity and maintenance costs before deciding. Do not equate native migration with a proven performance or accessibility fix.
