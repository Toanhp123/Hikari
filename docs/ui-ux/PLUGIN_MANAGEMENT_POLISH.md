# Plugin Management UI polish (2026-10-20)

## Scope

The APK install/removal authority, journal, repository ownership, source activation,
and Novel trust/admission are unchanged. This is a presentation-only pass over the
Plugin Manager, APK actions, and Novel actions. Existing native consent confirmation
and foreground/epoch guards remain route-owned.

## Sources and decisions

- [Flutter: theming and Material components](https://docs.flutter.dev/ui/design/material):
  use `ThemeData`, Material buttons, cards and form fields, not an alternate palette.
- [Flutter: architecture](https://docs.flutter.dev/app-architecture/guide):
  leave state and operation ownership in the existing ViewModels/application.
- [Android accessibility: touch targets](https://support.google.com/accessibility/android/answer/7101858):
  prefer native Material touch controls with a target of at least 48dp.
- [NN/g progressive disclosure](https://www.nngroup.com/articles/progressive-disclosure/):
  keep primary tasks and unknown-outcome actions in view; collapse nonessential
  diagnostic evidence without hiding the evidence itself.
- `docs/architecture/DESIGN_SYSTEM.md`, `UI_ARCHITECTURE.md`, and ADR-017 are
  authoritative for Hikari typography, color, spacing, page header, and reuse.

## Component audit of existing features

| Area | Existing presentation pattern | Reuse decision |
| --- | --- | --- |
| Home | Artwork-first hero, scroll-bound branded header | Keep feature-owned, not a settings/management header |
| Library and Local | `HikariChipRow` + `HikariChip` | Reuse for Available / Installed / Updates selection |
| Catalog / Source Search | Search submission and remote results | Do **not** reuse search flow; plugin filtering is local, immediate |
| Settings | Compact semantic `_SectionHeader` | Promote to `core/ui/patterns/hikari_section_header.dart` and reuse in Plugin Manager |
| Home / Local / Detail | Feature-specific async messages, MaterialBanner | Use plugin-owned inline notice; no premature app-wide banner abstraction |
| Reader / Player | Immersive controls and media-specific palettes | Not suitable for plugin app chrome |
| Existing root theme | Material3 Card, buttons, inputs, spacing, radii | Continue using unchanged |

Only one small visual component is promoted to `core/ui`: `HikariSectionHeader`,
replacing the private Settings variant with the same role and reusable optional
supporting text. `PluginNotice`, `PluginActionSection`, and `PluginListTile`
remain in `features/plugins/widgets` because their semantics and usage are
presently specific to Plugin Management. Avoid generic `CardSection` APIs with
many configuration booleans or cross-feature design rewrites.

## Information hierarchy

1. Manager: task heading, add repository / APK operations / inventory refresh;
   Available–Installed–Updates chips; local search and adaptive two-facet row;
   plugin list; repository maintenance.
2. APK: explain source activation and preserved reading state before actions;
   installed revision, download/inspection, staged consent, native operation journal.
3. Novel: show origin/consent explanation before actions; stored bundle,
   publication failure lock, staging, runtime evidence.
4. Native journal: operation status and recovery controls stay visible.
   Expanding *Technical evidence* reveals signer and operation identifier.
5. All pages: native buttons/fields with their focus, disabled, and keyboard
   semantics; narrow layouts stack controls and content width is bounded.

## Regression gates

Run `fvm dart format lib test`, `fvm flutter analyze`, `fvm flutter test`,
and a release build. Widget tests should cover 320dp / large text, tab changes,
repository CRUD, native unknown outcomes, consent boundaries, and expansion of
journal evidence. On Redmi Note 9S manually verify keyboard dismissal,
TalkBack order, long signer/URL wrapping and navigation after package changes.
