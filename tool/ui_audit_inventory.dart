import 'dart:convert';
import 'dart:io';

/// Lexical inventory, not a Dart name/type resolver. Interpolation, aliases and
/// dynamic route builders need source review; matches are navigation evidence.
List<Map<String, Object>> inventorySources(Map<String, String> sources) {
  final rows = <Map<String, Object>>[];
  for (final entry in sources.entries) {
    final path = entry.key.replaceAll('\\', '/');
    void add(
      String kind,
      String symbol,
      int line,
      int offset, {
      String? status,
    }) {
      rows.add({
        'id': '$path:${offset.toString().padLeft(8, '0')}:$kind:$symbol',
        'path': path,
        'line': line,
        'kind': kind,
        'symbol': symbol,
        'status':
            status ??
            (kind == 'route-constructor' || kind == 'dynamic-navigation'
                ? 'manual-audit-required'
                : 'legacy-pending'),
      });
    }

    if (path.startsWith('lib/features/') && path.endsWith('_page.dart')) {
      add('page-file', path.split('/').last, 1, 0);
    }
    if (path.startsWith('lib/core/ui/')) {
      add('shared-file', path.split('/').last, 1, 0);
    }
    if (path.startsWith('lib/app/navigation/')) {
      add('navigation-owner', path.split('/').last, 1, 0);
    }
    final tokens = dartTokens(entry.value);
    for (var i = 0; i < tokens.length; i++) {
      final token = tokens[i];
      if (token.text == 'import' || token.text == 'export') {
        for (var j = i + 1; j < tokens.length && tokens[j].text != ';'; j++) {
          if (!tokens[j].isString) continue;
          final uri = tokens[j].text;
          final target = internalLibPath(path, uri);
          if (target != null &&
              (target.startsWith('lib/core/ui/') ||
                  target.startsWith('lib/app/theme/') ||
                  path.startsWith('lib/core/ui/') &&
                      target.startsWith('lib/domain/'))) {
            add(
              'legacy-${token.text}',
              uri,
              tokens[j].line,
              tokens[j].offset,
              status:
                  path.startsWith('lib/features/') &&
                      _isCanonicalDependency(target)
                  ? 'canonical'
                  : path.startsWith('lib/features/') &&
                        _isNonVisualHelper(target)
                  ? 'nonvisual-helper'
                  : null,
            );
          }
        }
      }
      if (token.isString) continue;
      final kind = _symbols[token.text];
      if (kind == null) continue;
      final next = i + 1 < tokens.length ? tokens[i + 1].text : '';
      if (next == '(' || next == '<' || next == '.' || next == ':') {
        add(kind, token.text, token.line, token.offset);
      }
    }
  }
  rows.sort((a, b) => (a['id']! as String).compareTo(b['id']! as String));
  return rows;
}

// `legacy-import` is a historical lexical finding kind; it does not imply
// the target is legacy. Only explicit core UI directories exported by the
// Preview API (or their internal implementations) are canonical dependencies.
bool _isCanonicalDependency(String path) =>
    path == 'lib/core/ui/hikari_ui.dart' ||
    const [
      'foundation',
      'controls',
      'content',
      'feedback',
      'layout',
      'media',
      'navigation',
      'surfaces',
    ].any((dir) => path.startsWith('lib/core/ui/$dir/'));

bool _isNonVisualHelper(String path) => const {
  'lib/core/ui/patterns/chapter_list_filter.dart',
  'lib/core/ui/patterns/media_artwork_decode.dart',
}.contains(path);

const _symbols = {
  'showDialog': 'modal-entrypoint',
  'showGeneralDialog': 'modal-entrypoint',
  'showModalBottomSheet': 'modal-entrypoint',
  'showBottomSheet': 'modal-entrypoint',
  'showHikariModalSheet': 'modal-entrypoint',
  'showHikariConfirmation': 'modal-entrypoint',
  'showMenu': 'overlay',
  'showSearch': 'overlay',
  'showDatePicker': 'modal-entrypoint',
  'showTimePicker': 'modal-entrypoint',
  'OverlayEntry': 'overlay',
  'OverlayPortal': 'overlay',
  'MenuAnchor': 'overlay',
  'PopupMenuButton': 'overlay',
  'Tooltip': 'overlay',
  'SnackBar': 'overlay',
  'showSnackBar': 'overlay',
  'MaterialPageRoute': 'route-constructor',
  'PageRouteBuilder': 'route-constructor',
  'DialogRoute': 'route-constructor',
  'RawDialogRoute': 'route-constructor',
  'ModalBottomSheetRoute': 'route-constructor',
  'ModalRoute': 'route-observation',
  'PopScope': 'route-exit-policy',
  'pop': 'route-exit-reference',
  'maybePop': 'route-exit-reference',
  'Navigator': 'navigator-reference',
  'pushNamed': 'dynamic-navigation',
  'onGenerateRoute': 'dynamic-navigation',
  'routes': 'dynamic-navigation',
  'drawer': 'navigation-surface',
  'endDrawer': 'navigation-surface',
  'Drawer': 'navigation-surface',
  'NavigationDrawer': 'navigation-surface',
  'AppNavigationShell': 'navigation-shell',
  'IndexedStack': 'navigation-retention',
};

String? internalLibPath(String sourcePath, String rawUri) {
  final uri = Uri.tryParse(rawUri);
  if (uri == null) return null;
  if (uri.scheme == 'package') {
    return uri.pathSegments.firstOrNull == 'hikari'
        ? 'lib/${uri.pathSegments.skip(1).join('/')}'
        : null;
  }
  if (uri.scheme.isNotEmpty) return null;
  return Uri(path: sourcePath).resolveUri(uri).path;
}

class DartToken {
  const DartToken(this.text, this.offset, this.line, {this.isString = false});
  final String text;
  final int offset;
  final int line;
  final bool isString;
}

/// Handles nested comments, escapes and raw/triple strings. String interpolation
/// is deliberately opaque: it cannot establish absence of calls inside `${...}`.
List<DartToken> dartTokens(String source) {
  final tokens = <DartToken>[];
  var i = 0;
  var line = 1;
  bool at(String text) => source.startsWith(text, i);
  void advance(int count) {
    final end = i + count;
    while (i < end && i < source.length) {
      if (source[i] == '\n') line++;
      i++;
    }
  }

  while (i < source.length) {
    if (RegExp(r'\s').hasMatch(source[i])) {
      advance(1);
      continue;
    }
    if (at('//')) {
      while (i < source.length && source[i] != '\n') {
        advance(1);
      }
      continue;
    }
    if (at('/*')) {
      var depth = 1;
      advance(2);
      while (i < source.length && depth > 0) {
        if (at('/*')) {
          depth++;
          advance(2);
        } else if (at('*/')) {
          depth--;
          advance(2);
        } else {
          advance(1);
        }
      }
      continue;
    }
    final start = i;
    final startLine = line;
    final raw =
        source[i] == 'r' &&
        i + 1 < source.length &&
        (source[i + 1] == "'" || source[i + 1] == '"');
    if (raw || source[i] == "'" || source[i] == '"') {
      if (raw) advance(1);
      final quote = source[i];
      final delimiter = at(quote * 3) ? quote * 3 : quote;
      advance(delimiter.length);
      final content = i;
      while (i < source.length && !at(delimiter)) {
        if (!raw && at('\\')) {
          advance(i + 1 < source.length ? 2 : 1);
        } else {
          advance(1);
        }
      }
      final text = source.substring(content, i);
      if (i < source.length) advance(delimiter.length);
      tokens.add(DartToken(text, start, startLine, isString: true));
      continue;
    }
    if (RegExp(r'[A-Za-z_$]').hasMatch(source[i])) {
      advance(1);
      while (i < source.length &&
          RegExp(r'[A-Za-z0-9_$]').hasMatch(source[i])) {
        advance(1);
      }
    } else {
      advance(1);
    }
    tokens.add(DartToken(source.substring(start, i), start, startLine));
  }
  return tokens;
}

Map<String, String> readUiSources(Directory root) => {
  for (final file
      in Directory('${root.path}/lib')
          .listSync(recursive: true, followLinks: false)
          .whereType<File>()
          .where((file) => file.path.endsWith('.dart')))
    file.path.substring(root.path.length + 1).replaceAll('\\', '/'): file
        .readAsStringSync(),
};

/// Recorded metadata is supplied explicitly so a previous baseline cannot be
/// presented as a fresh checkout after later migrations.
class UiMigrationSnapshot {
  const UiMigrationSnapshot({
    required this.date,
    required this.branch,
    required this.head,
    required this.targetStatus,
  });

  final String date;
  final String branch;
  final String head;
  final String targetStatus;

  factory UiMigrationSnapshot.fromMarkdown(String markdown) {
    final header = RegExp(
      r'Snapshot: (\d{4}-\d{2}-\d{2}), branch `([^`]+)`, HEAD\s*`([^`]+)`\.',
    ).firstMatch(markdown);
    final status = RegExp(
      r'^Status: [^\n]*target (PROPOSED|ACCEPTED)\b',
      multiLine: true,
    ).firstMatch(markdown);
    if (header == null || status == null) {
      throw const FormatException('Missing UI migration snapshot metadata');
    }
    return UiMigrationSnapshot(
      date: header[1]!,
      branch: header[2]!,
      head: header[3]!,
      targetStatus: status[1]!,
    );
  }

  /// Used by the CLI for a new snapshot. Tests use the recorded metadata
  /// instead, keeping the historical Phase 00 matrix reproducible.
  static UiMigrationSnapshot current(Directory root) {
    String git(List<String> args) {
      final result = Process.runSync('git', args, workingDirectory: root.path);
      if (result.exitCode != 0) {
        throw StateError(
          'Cannot record UI inventory Git metadata: ${result.stderr}',
        );
      }
      return (result.stdout as String).trim();
    }

    final adr = File(
      '${root.path}/docs/decisions/ADR-026-ui-foundation-rebuild.md',
    ).readAsStringSync();
    final decision = RegExp(
      r'^Status: \*\*(Proposed|Accepted)\b',
      multiLine: true,
    ).firstMatch(adr);
    if (decision == null) {
      throw const FormatException('ADR-026 must be Proposed or Accepted');
    }
    final now = DateTime.now().toIso8601String().substring(0, 10);
    final branch = git(['branch', '--show-current']);
    final revision = git(['rev-parse', 'HEAD']);
    final dirty = git(['status', '--porcelain', '--untracked-files=all'])
        .isNotEmpty;
    return UiMigrationSnapshot(
      date: now,
      branch: branch.isEmpty ? 'detached' : branch,
      head: dirty ? '$revision (dirty)' : revision,
      targetStatus: decision[1]!.toUpperCase(),
    );
  }
}

/// A primary migration task, not a promise that every legacy caller is removed
/// at that task. Late consumer cleanup remains owned by 07/01.
const _sharedPrimaryTask = <String, String>{
  'lib/core/ui/hikari_ui.dart': '01/06',
  'lib/core/ui/controls/hikari_button.dart': '01/03',
  'lib/core/ui/controls/hikari_icon_button.dart': '01/03',
  'lib/core/ui/controls/hikari_filter_chip.dart': '01/03',
  'lib/core/ui/controls/hikari_chip_row.dart': '01/03',
  'lib/core/ui/components/hikari_button.dart': '01/07',
  'lib/core/ui/components/hikari_icon_button.dart': '01/07',
  'lib/core/ui/components/hikari_chip.dart': '01/07',
  'lib/core/ui/components/hikari_refresh_action.dart': '01/03',
  'lib/core/ui/controls/hikari_search_field.dart': '01/04',
  'lib/core/ui/controls/hikari_choice_menu.dart': '01/04',
  'lib/core/ui/controls/hikari_segmented_control.dart': '01/04',
  'lib/core/ui/components/hikari_choice_menu.dart': '01/07',
  'lib/core/ui/components/hikari_search_bar.dart': '01/04',
  'lib/core/ui/components/hikari_confirmation_dialog.dart': '02/05',
  'lib/core/ui/components/hikari_modal_sheet.dart': '02/03',
  'lib/core/ui/components/hikari_scaffold.dart': '02/07',
  'lib/core/ui/layout/hikari_page_scaffold.dart': '02/07',
  'lib/core/ui/layout/hikari_page_header.dart': '02/07',
  'lib/core/ui/layout/hikari_content_region.dart': '02/07',
  'lib/core/ui/navigation/hikari_navigation_destination.dart': '02/06',
  'lib/core/ui/navigation/hikari_navigation_policy.dart': '02/06',
  'lib/core/ui/navigation/hikari_navigation_shell.dart': '02/06',
  'lib/core/ui/surfaces/drawer/hikari_drawer.dart': '02/06',
  'lib/core/ui/surfaces/dialog/hikari_dialog.dart': '02/02',
  'lib/core/ui/surfaces/dialog/hikari_confirmation_dialog.dart': '02/05',
  'lib/core/ui/surfaces/bottom_sheet/hikari_bottom_sheet.dart': '02/03',
  'lib/core/ui/surfaces/adaptive/hikari_presentation_models.dart': '02/01',
  'lib/core/ui/surfaces/adaptive/hikari_presentation_policy.dart': '02/01',
  'lib/core/ui/surfaces/adaptive/hikari_presentation_controller.dart': '02/01',
  'lib/core/ui/surfaces/adaptive/show_hikari_adaptive.dart': '02/04',
  'lib/core/ui/surfaces/adaptive/hikari_presentation_session.dart': '02/04',
  'lib/core/ui/feedback/hikari_async_state_view.dart': '01/05',
  'lib/core/ui/feedback/hikari_notice_banner.dart': '01/05',
  'lib/core/ui/content/hikari_section_header.dart': '01/05',
  'lib/core/ui/content/hikari_list_row.dart': '01/05',
  'lib/core/ui/content/hikari_chapter_row.dart': '03/06',
  'lib/core/ui/layout/hikari_adaptive_grid.dart': '03/04',
  'lib/core/ui/layout/hikari_horizontal_shelf.dart': '03/05',
  'lib/core/ui/media/hikari_artwork.dart': '03/01',
  'lib/core/ui/media/hikari_artwork_decode_policy.dart': '03/01',
  'lib/core/ui/media/hikari_poster_card.dart': '03/02',
  'lib/core/ui/media/hikari_landscape_card.dart': '03/02',
  'lib/core/ui/media/hikari_media_progress.dart': '03/02',
  'lib/core/ui/media/hikari_media_card_body.dart': '03/02',
  'lib/core/ui/media/hikari_card_metrics.dart': '03/03',
  'lib/core/ui/patterns/hikari_section_header.dart': '01/07',
  'lib/core/ui/patterns/chapter_list_filter.dart': '04/05',
  'lib/core/ui/patterns/media_artwork_decode.dart': '03/01',
  'lib/core/ui/patterns/media_progress_bar.dart': '03/02',
};

const _geometryTokens = ['spacing', 'radius', 'motion', 'size', 'breakpoints'];
const _foundationTasks = {
  'spacing': '01/01',
  'radius': '01/01',
  'motion': '01/01',
  'size': '01/01',
  'breakpoints': '01/01',
  'color_roles': '01/02',
  'typography': '01/02',
  'aspect_ratios': '03/03',
};

bool _isInternalMedia(String path) =>
    path == 'lib/core/ui/media/hikari_media_card_body.dart';

bool _isPreviewApi(String path) =>
    _sharedPrimaryTask.containsKey(path) &&
    !_isInternalMedia(path) &&
    (path == 'lib/core/ui/hikari_ui.dart' ||
        path.startsWith('lib/core/ui/media/') ||
        path.startsWith('lib/core/ui/controls/') ||
        path.startsWith('lib/core/ui/content/') ||
        path.startsWith('lib/core/ui/feedback/') ||
        path.startsWith('lib/core/ui/layout/') ||
        path.startsWith('lib/core/ui/navigation/') ||
        (path.startsWith('lib/core/ui/surfaces/') &&
            !path.endsWith('hikari_presentation_session.dart')));

String _sharedMigrationTasks(String path, String kind, String symbol) {
  for (final entry in _foundationTasks.entries) {
    if (path == 'lib/core/ui/foundation/hikari_${entry.key}.dart') {
      return '${entry.value} (canonical foundation)';
    }
  }
  final primary = _sharedPrimaryTask[path];
  if (primary == null) return 'manual-audit-required (unmapped shared file)';
  if (_isPreviewApi(path)) return '$primary (canonical Preview API)';
  if (_isInternalMedia(path)) return '$primary (internal media composition)';
  if (path.endsWith('/hikari_presentation_session.dart')) {
    return '$primary (internal route lifecycle)';
  }
  if (kind == 'legacy-import' || kind == 'legacy-export') {
    if (symbol.contains('/app/theme/')) {
      return '01/07 (token import); $primary (component migration)';
    }
  }
  return '$primary (primary); 07/01 (legacy cleanup)';
}

String renderMigrationMatrix(
  List<Map<String, Object>> rows, {
  required UiMigrationSnapshot snapshot,
}) {
  final out = StringBuffer('''# UI migration matrix

Snapshot: ${snapshot.date}, branch `${snapshot.branch}`, HEAD
`${snapshot.head}`. Reproduce with
`fvm dart run tool/ui_audit_inventory.dart --markdown`.
Use `--preserve-snapshot` to reproduce recorded metadata instead of
capturing the current checkout.

Status: current implementation inventoried; target ${snapshot.targetStatus};
per-row migration status is listed below.
See [baseline evidence](BASELINE_RUNS.md), [foundations](FOUNDATIONS.md) and
[ADR-026](../decisions/ADR-026-ui-foundation-rebuild.md).

## Scope and limits

Lexical findings, not resolved Dart call graph. Definitions/references can occur
alongside invocations; source locations distinguish them. `legacy-import` is
a historical lexical kind, not a claim of legacy ownership: canonical imports
are labeled `canonical`, with two nonvisual helpers labeled separately.
Route constructors remain `manual-audit-required`: variable builders are not silently resolved.
Opaque string interpolation, aliases, tear-offs, native surfaces and callbacks
need manual source review. No named-route or nested-Navigator absence is proven
by this scanner. Never equate page-file count with registered routes.

## Manual route and ownership audit

- `lib/app/app.dart`: MaterialApp root Navigator, no named route table. Five
  shell destinations: Home, Catalog Search, Local Media, Library, Settings.
- `_openCatalogDetail`: CatalogDetailPage; `_openStandaloneSourceSearch` and
  `_openCatalogSourceSearch`: two SourceSearchPage contexts (normal/selectOnly).
- `_pushMediaTarget`: dynamic `page` resolves video PlayerPage, MangaSeriesPage,
  MangaReaderPage, NovelReaderPage, NovelSeriesPage, PublicationReaderPage.
- `_continueReading`: ContinueReadingPage directly opens exact saved chapter;
  `_openMangaChapter` / `_openNovelChapter` push dynamic `ready.reader` resolving
  MangaChapterReaderPage / NovelChapterReaderPage. Source leases settle before
  release. Preserve ADR-009/023/024; never insert Detail prerequisite.
- Settings opens PluginManagerPage; manager imperatively pushes ApkActionsPage
  and NovelActionsPage. Returning from route refreshes evidence, not install
  success. Android installer/permission/removal and Novel runtime consent remain
  application/native-owned (ADR-025), outside shared UI authorization.
- AppNavigationShell owns selected destination/controller and lazy IndexedStack;
  HikariNavigationShell renders compact bar <600, rail >=600 and compact Drawer
  for >5 destinations. Production keeps five destinations; no child Navigator.
- CatalogDetailPage calls showHikariAdaptive<CatalogSourceSelection> with explicit
  root ownership for both families. Picker owns language-step policy, not route
  pops. Coordinator revalidates source/generation before persistence/navigation.
  Legacy sheet defaulted nested, dialog root; pilot now uses one explicit owner.
- MenuAnchor is framework-owned overlay in HikariChoiceMenu; tooltips/snackbars
  are explicit transient exceptions, not presenter result workflows.
- Reader sheets own outline/settings content. Player/reader canvases and platform
  system UI remain specialist exceptions, not generic modal replacements.
- Home Continue precedes Featured since 04/01; physical acceptance remains pending.

## Findings

Guard exemptions are frozen separately in
`test/fixtures/ui_architecture/legacy_sites.txt`; regenerating this matrix cannot
allow a new violation. Remove sites at their listed owner task, no later than
07/01. Updating offsets requires deliberate source/diff review, not bulk refresh.
The staged lexer checks import/export/part boundaries and raw feature modals;
it does not resolve aliases, symbol types or calls inside string interpolation.

Every exemption is temporary until listed task; coverage names are entrypoints,
not claims each line has a dedicated assertion. `manual-audit-required` means
source review remains required whenever migrating that row. Shared UI findings
list a primary migration owner and any earlier token-import/late cleanup task;
these are not blanket migration windows.

| Finding ID | Path:line | Kind / symbol | Current owner / behavior | Target owner | Phase/task | Regression coverage | Status | Legacy/manual exception |
|---|---|---|---|---|---|---|---|---|
''');
  for (final row in rows) {
    final path = row['path']! as String;
    final feature = path.startsWith('lib/features/') ? path.split('/')[2] : '';
    final (task, coverage) = switch (feature) {
      'catalog' => (
        '02/08–09; 04/04–05',
        'test/features/catalog/catalog_detail_page_test.dart; test/features/catalog/catalog_source_picker_view_model_test.dart',
      ),
      'home' => ('04/01', 'test/home_page_test.dart'),
      'library' => ('03/07; 04/02', 'test/library_page_test.dart'),
      'local_media' => ('04/03', 'test/library_page_test.dart'),
      'source_search' => ('04/04', 'test/source_search_page_test.dart'),
      'remote_manga' => (
        '04/06; 06/02',
        'test/chapter_reader_routes_test.dart',
      ),
      'remote_novel' => (
        '04/06; 06/04',
        'test/chapter_reader_routes_test.dart',
      ),
      'reading' => (
        path.contains('/widgets/') ? '04/08' : '04/07',
        path.contains('/widgets/')
            ? 'test/features/reading/chapter_list_controls_test.dart'
            : 'test/reader_first_sequence_test.dart',
      ),
      'media' => (
        '04/08',
        'test/features/media/media_metadata_view_test.dart; test/media_type_badge_theme_test.dart; test/read_source_artwork_test.dart',
      ),
      'settings' => ('05/01', 'test/theme_test.dart'),
      'plugins' => (
        '05/02–05',
        'test/plugins/apk_actions_page_test.dart; test/plugins/novel_actions_page_test.dart',
      ),
      'manga_reader' => ('06/01', 'test/chapter_reader_routes_test.dart'),
      'novel_reader' => ('06/03–04', 'test/chapter_reader_routes_test.dart'),
      'player' => ('06/05', 'manual platform playback NOT RUN'),
      _ when path.startsWith('lib/app/') => (
        '01/01; 02/06–07',
        'test/navigation_shell_test.dart; test/theme_test.dart',
      ),
      _ => (
        '01/01–05; 02/02–04; 03/01–06; 07/01',
        'test/components_test.dart; test/architecture_guard_test.dart',
      ),
    };
    final kind = row['kind']! as String;
    final tokenForwarder =
        kind == 'legacy-export' &&
        [
          ..._geometryTokens,
          'status_colors',
          'typography',
        ].any((name) => path == 'lib/app/theme/hikari_$name.dart');
    final canonicalGeometry = _foundationTasks.keys.any(
      (name) => path == 'lib/core/ui/foundation/hikari_$name.dart',
    );
    final assignedTask = tokenForwarder
        ? '01/07 (remove transitional token export)'
        : path.startsWith('lib/core/ui/')
        ? _sharedMigrationTasks(path, kind, row['symbol']! as String)
        : task;
    final target = switch (kind) {
      'modal-entrypoint' => 'core/ui/surfaces; feature content/authorization',
      'navigation-owner' ||
      'navigation-shell' ||
      'navigation-retention' ||
      'navigator-reference' ||
      'route-constructor' ||
      'dynamic-navigation' => 'app routing + core/ui/navigation mechanics',
      'legacy-import' || 'legacy-export'
          when (row['symbol']! as String).contains('/domain/') =>
        'feature domain-to-presentation mapping; domain-free shared content',
      'legacy-import' ||
      'legacy-export' => 'core/ui/foundation or canonical component',
      'shared-file' => 'core/ui controls/content/media/layout/surfaces',
      _ =>
        feature.isEmpty ? 'shared presentation mechanics' : 'feature $feature',
    };
    final previewApi = _isPreviewApi(path);
    final internalSession = path.endsWith('/hikari_presentation_session.dart');
    final internalMedia = _isInternalMedia(path);
    final status = row['status']! as String;
    final exception = status == 'canonical'
        ? 'canonical dependency; no legacy exemption'
        : status == 'nonvisual-helper'
        ? 'scoped nonvisual helper; reassess ownership at 07/01'
        : canonicalGeometry
        ? 'canonical token authority; no legacy exemption'
        : previewApi
        ? 'canonical Preview API; no legacy exemption'
        : internalMedia
        ? 'internal media composition; not public API or legacy exemption'
        : internalSession
        ? 'internal lifecycle; not public API or legacy exemption'
        : switch (kind) {
            'route-constructor' || 'dynamic-navigation' =>
              'manual-audit-required; resolve builder at migration',
            'route-observation' =>
              'visibility/lifecycle observation, not modal creation',
            'overlay' =>
              'framework tooltip/menu/snackbar mechanics; review at owner task',
            _ =>
              'legacy owner retained until listed task; no permanent exemption',
          };
    out.writeln(
      '| `${row['id']}` | `$path:${row['line']}` | $kind / `${row['symbol']}` | ${feature.isEmpty ? path : feature} | ${canonicalGeometry ? 'core/ui/foundation' : target} | $assignedTask | ${canonicalGeometry
          ? 'test/theme_architecture_test.dart; test/theme_test.dart'
          : previewApi
          ? 'test/core/ui/; test/architecture_guard_test.dart'
          : internalMedia
          ? 'test/core/ui/media/'
          : internalSession
          ? 'test/core/ui/surfaces/'
          : coverage} | ${canonicalGeometry
          ? 'canonical'
          : previewApi
          ? 'preview'
          : internalSession || internalMedia
          ? 'internal'
          : status} | $exception |',
    );
  }
  return out.toString();
}

void main(List<String> arguments) {
  final root = Directory.current;
  final rows = inventorySources(readUiSources(root));
  if (!arguments.contains('--markdown')) {
    stdout.writeln(const JsonEncoder.withIndent('  ').convert(rows));
    return;
  }
  // Reproduction is opt-in. Normal regeneration records the current branch,
  // revision, date and actual ADR status rather than Phase 00 literals.
  final snapshot = arguments.contains('--preserve-snapshot')
      ? UiMigrationSnapshot.fromMarkdown(
          File('docs/ui/MIGRATION_MATRIX.md').readAsStringSync(),
        )
      : UiMigrationSnapshot.current(root);
  stdout.write(renderMigrationMatrix(rows, snapshot: snapshot));
}
