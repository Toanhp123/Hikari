# Correctness hardening (2026-10)

Scope: application/search/UI correctness and preferences. The two existing glass blur effects were deliberately preserved; no new real-device profiling is claimed.

- Source Search now distinguishes edited query from submitted query. Press Search or Enter to request; editing invalidates in-flight results. A filter change reruns only an already submitted, unchanged query.
- Source fan-out starts at most four awaited searches at once, applies a 12-second per-source UI wait timeout and publishes partial result batches. **Timeout does not cancel source execution**; an extension gateway may still have a late physical request. Generation guards reject stale publishes and prevent new queued operations for that generation.
- Catalog resolution collects up to four distinct titles/aliases before deciding if its normalized exact match is unambiguous. This can generate more searches than the earlier first-match fast path.
- Appearance is stored in the application-support directory as a small JSON document; loading happens before `runApp` to avoid default-theme flashes. Saves are serialized by the root app; errors are reported while the current session retains the selection.
- Poster title and navigation label text metrics are reused under the same style, text scaler and direction. Their cache key changes for theme / accessibility text scaling.
- No new dependencies; existing route structure, screen designs and graphics blur remain unchanged.

Verification on a Flutter 3.47.5 workstation:

```powershell
fvm dart format lib test
fvm flutter analyze
fvm flutter test test/source_search_view_model_test.dart test/source_search_page_test.dart test/application/catalog/resolve_catalog_source_test.dart test/appearance_settings_repository_test.dart test/navigation_shell_test.dart test/media_poster_test.dart
fvm flutter test
fvm flutter build apk --debug
```

Do not interpret source-level inspection as a successful runtime test.
