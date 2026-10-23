import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tool/ui_audit_inventory.dart';

void main() {
  test('records Back interception and explicit route exit sites', () {
    final rows = inventorySources({
      'lib/features/example/view.dart':
          'PopScope(canPop: false); Navigator.of(context).pop();',
    });
    expect(rows.map((row) => row['symbol']), containsAll(['PopScope', 'pop']));
  });
  test('inventories domain dependencies of legacy shared patterns', () {
    final rows = inventorySources({
      'lib/core/ui/patterns/chapter_row.dart':
          "import 'package:hikari/domain/media/manga.dart';",
    });
    expect(rows.where((row) => row['kind'] == 'legacy-import'), hasLength(1));
  });
  test(
    'matrix gives each finding owner target task coverage and exception',
    () {
      final rows = inventorySources({
        'lib/features/catalog/catalog_detail_page.dart':
            'void f() { showDialog(); }',
      });
      final markdown = renderMigrationMatrix(
        rows,
        snapshot: const UiMigrationSnapshot(
          date: '2026-10-22',
          branch: 'baseline',
          head: '0123456789abcdef',
          targetStatus: 'PROPOSED',
        ),
      );
      expect(markdown, contains('Current owner / behavior'));
      expect(markdown, contains('Target owner'));
      expect(markdown, contains('02/08'));
      expect(
        markdown,
        contains('test/features/catalog/catalog_detail_page_test.dart'),
      );
      for (final row in rows) {
        expect(markdown, contains(row['id']! as String));
      }
      expect(markdown, contains('manual-audit-required'));
    },
  );
  test('snapshot metadata is supplied and can change without code edits', () {
    const updated = UiMigrationSnapshot(
      date: '2026-11-01',
      branch: 'feat/next-ui-wave',
      head: 'abcdef0123456789',
      targetStatus: 'ACCEPTED',
    );
    final markdown = renderMigrationMatrix(const [], snapshot: updated);
    expect(markdown, contains('Snapshot: 2026-11-01'));
    expect(markdown, contains('target ACCEPTED'));
    final roundTrip = UiMigrationSnapshot.fromMarkdown(markdown);
    expect(roundTrip.branch, updated.branch);
    expect(roundTrip.head, updated.head);
    expect(roundTrip.targetStatus, updated.targetStatus);
    expect(
      () => UiMigrationSnapshot.fromMarkdown('No recorded baseline'),
      throwsFormatException,
    );
  });

  test('shared components have specific primary migration owners', () {
    final rows = inventorySources({
      'lib/core/ui/patterns/media_poster.dart': 'class MediaPoster {}',
      'lib/core/ui/patterns/chapter_row.dart': 'class ChapterRow {}',
      'lib/core/ui/components/hikari_confirmation_dialog.dart':
          'void showHikariConfirmation() {}',
    });
    final markdown = renderMigrationMatrix(
      rows,
      snapshot: const UiMigrationSnapshot(
        date: '2026-10-22',
        branch: 'baseline',
        head: '0123456789abcdef',
        targetStatus: 'PROPOSED',
      ),
    );
    expect(markdown, contains('03/02 (primary); 07/01 (legacy cleanup)'));
    expect(markdown, contains('03/06 (primary); 07/01 (legacy cleanup)'));
    expect(markdown, contains('02/05 (primary); 07/01 (legacy cleanup)'));
    expect(markdown, isNot(contains('01/01–05; 02/02–04; 03/01–06')));
  });

  test('canonical geometry and temporary forwarders have exact owners', () {
    final rows = inventorySources({
      'lib/core/ui/foundation/hikari_spacing.dart': 'class HikariSpacing {}',
      'lib/app/theme/hikari_spacing.dart':
          "export 'package:hikari/core/ui/foundation/hikari_spacing.dart';",
    });
    final markdown = renderMigrationMatrix(
      rows,
      snapshot: const UiMigrationSnapshot(
        date: '2026-10-22',
        branch: 'baseline',
        head: '0123456789abcdef',
        targetStatus: 'ACCEPTED',
      ),
    );
    expect(markdown, contains('01/01 (canonical foundation)'));
    expect(markdown, contains('01/07 (remove transitional token export)'));
    expect(markdown, isNot(contains('unmapped shared file')));
  });

  test('canonical public controls are Preview without legacy cleanup debt', () {
    final rows = inventorySources({
      'lib/core/ui/controls/hikari_search_field.dart':
          'class HikariSearchField {}',
      'lib/core/ui/feedback/hikari_async_state_view.dart':
          'class HikariAsyncStateView {}',
      'lib/core/ui/content/hikari_list_row.dart': 'class HikariListRow {}',
      'lib/core/ui/hikari_ui.dart':
          "export 'controls/hikari_search_field.dart';",
    });
    final markdown = renderMigrationMatrix(
      rows,
      snapshot: const UiMigrationSnapshot(
        date: '2026-10-23',
        branch: 'baseline',
        head: '0123456789abcdef',
        targetStatus: 'ACCEPTED',
      ),
    );
    expect(markdown, contains('preview'));
    expect(markdown, isNot(contains('legacy-pending')));
    expect(markdown, isNot(contains('07/01 (legacy cleanup)')));
    expect(markdown, contains('canonical Preview API; no legacy exemption'));
  });

  test(
    'Phase 02 mechanics have exact Preview owners and current route audit',
    () {
      final rows = inventorySources({
        'lib/core/ui/surfaces/dialog/hikari_dialog.dart':
            'class HikariDialog {}',
        'lib/core/ui/surfaces/bottom_sheet/hikari_bottom_sheet.dart':
            'class HikariBottomSheet {}',
        'lib/core/ui/surfaces/adaptive/show_hikari_adaptive.dart':
            'void showHikariAdaptive() {}',
        'lib/core/ui/navigation/hikari_navigation_shell.dart':
            'class HikariNavigationShell {}',
        'lib/core/ui/layout/hikari_page_scaffold.dart':
            'class HikariPageScaffold {}',
      });
      final markdown = renderMigrationMatrix(
        rows,
        snapshot: const UiMigrationSnapshot(
          date: '2026-10-23',
          branch: 'baseline',
          head: '0123456789abcdef',
          targetStatus: 'ACCEPTED',
        ),
      );
      for (final task in ['02/02', '02/03', '02/04', '02/06', '02/07']) {
        expect(markdown, contains('$task (canonical Preview API)'));
      }
      expect(markdown, isNot(contains('unmapped shared file')));
      expect(markdown, contains('showHikariAdaptive<CatalogSourceSelection>'));
      expect(
        markdown,
        isNot(contains('picker\n  additionally pops its own route')),
      );
    },
  );

  test('finds all 19 baseline page files plus added live checkout pages', () {
    final sources = readUiSources(Directory.current);
    final pages = inventorySources(sources)
        .where((row) => row['kind'] == 'page-file')
        .map((row) => row['path'])
        .toSet();
    final actual = sources.keys
        .where(
          (path) =>
              path.startsWith('lib/features/') && path.endsWith('_page.dart'),
        )
        .toSet();
    expect(actual.length, greaterThanOrEqualTo(19));
    expect(pages, actual);
    expect(
      inventorySources({
        'lib/features/example/new_page.dart': 'class NewPage {}',
      }).single['kind'],
      'page-file',
    );
  });

  test('locates modal helpers routes overlays drawers and legacy media', () {
    final rows = inventorySources({
      'lib/features/example/example_page.dart': '''
import 'package:hikari/core/ui/patterns/media_poster.dart';
export '../../core/ui/patterns/chapter_row.dart';
void build() {
  showDialog<void>(context: context, builder: build);
  showModalBottomSheet(context: context, builder: build);
  showHikariModalSheet(context: context, builder: build);
  showHikariConfirmation(context: context);
  MaterialPageRoute<void>(builder: (_) => page);
  PageRouteBuilder(pageBuilder: build);
  OverlayEntry(builder: build);
  ModalRoute.of(context);
  Navigator(onGenerateRoute: build);
  Scaffold(drawer: Drawer());
  MenuAnchor(menuChildren: []);
}
''',
    });
    final symbols = rows.map((row) => row['symbol']).toSet();
    expect(
      symbols,
      containsAll([
        'showDialog',
        'showModalBottomSheet',
        'showHikariModalSheet',
        'showHikariConfirmation',
        'MaterialPageRoute',
        'PageRouteBuilder',
        'OverlayEntry',
        'ModalRoute',
        'Navigator',
        'drawer',
        'MenuAnchor',
        'package:hikari/core/ui/patterns/media_poster.dart',
        '../../core/ui/patterns/chapter_row.dart',
      ]),
    );
    expect(
      rows.where((row) => row['kind'] == 'route-constructor').first['status'],
      'manual-audit-required',
    );
  });

  test('ignores nested comments raw triple strings and escaped quotes', () {
    final rows = inventorySources({
      'lib/features/example/view.dart': r"""
/* showDialog(); /* OverlayEntry(); */ showModalBottomSheet(); */
// Navigator();
const message = 'showDialog(\'not code\')';
const raw = r'showHikariModalSheet()';
const triple = '''Drawer();
MenuAnchor();''';
void build() { showDialog(context: context, builder: build); }
""",
    });
    expect(rows, hasLength(1));
    expect(rows.single['symbol'], 'showDialog');
    expect(rows.single['line'], 7);
  });

  test('captures multiline conditional imports and same-line exports', () {
    final rows = inventorySources({
      'lib/features/example/view.dart': '''
import /* comment */ 'package:hikari/app/theme/theme.dart'
  if (dart.library.io) '../../core/ui/io.dart'; export '../../core/ui/web.dart';
''',
    });
    expect(rows.where((row) => row['kind'] == 'legacy-import'), hasLength(2));
    expect(rows.where((row) => row['kind'] == 'legacy-export'), hasLength(1));
  });

  test(
    'inventory has deterministic sorted unique keys including repeated calls',
    () {
      final sources = {
        'lib/features/z/view.dart': 'void f() { showDialog(); showDialog(); }',
        'lib/features/a/view.dart': 'void f() { OverlayEntry(); }',
      };
      final rows = inventorySources(sources);
      expect(rows, hasLength(3));
      expect(
        inventorySources(Map.fromEntries(sources.entries.toList().reversed)),
        rows,
      );
      final keys = rows.map((row) => row['id']! as String).toList();
      expect(keys, keys.toList()..sort());
      expect(keys.toSet(), hasLength(keys.length));
    },
  );
}
