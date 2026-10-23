import 'dart:io';

import 'package:flutter_test/flutter_test.dart';

import '../tool/ui_audit_inventory.dart';

const _canonical = [
  'docs/decisions/ADR-026-ui-foundation-rebuild.md',
  'docs/ui/FOUNDATIONS.md',
  'docs/ui/RESPONSIVE.md',
  'docs/ui/COMPONENTS.md',
  'docs/ui/MIGRATION_MATRIX.md',
  'docs/ui/BASELINE_RUNS.md',
  'docs/roadmap/UI_FOUNDATION_PHASE_01.md',
  'docs/ui/components/controls.md',
  'docs/ui/components/media.md',
  'docs/roadmap/UI_FOUNDATION_PHASE_03.md',
  'docs/roadmap/UI_FOUNDATION_PHASE_04.md',
  'docs/roadmap/UI_FOUNDATION_G3_OWNER_ACCEPTANCE_2026-10-23.md',
  'docs/ui/CONTRIBUTING.md',
  'docs/superpowers/handoff/README_GRAVITY.md',
  'docs/superpowers/handoff/AUDIT_REPORT.md',
  'docs/superpowers/handoff/VALIDATION_RESULTS.md',
  'docs/superpowers/plans/2026-10-22-hikari-ui-rebuild-master.md',
  'docs/superpowers/plans/2026-10-22-hikari-ui-00-baseline-and-adr.md',
  'docs/superpowers/plans/BASELINE_ROUTE_INVENTORY.md',
  'docs/superpowers/specs/2026-10-22-hikari-ui-foundation-rebuild-design.md',
];

void main() {
  test('canonical UI documents exist and relative Markdown links resolve', () {
    for (final path in _canonical) {
      final file = File(path);
      expect(file.existsSync(), isTrue, reason: path);
      final source = file.readAsStringSync();
      for (final match in RegExp(r'\[[^\]]*\]\(([^)]+)\)').allMatches(source)) {
        final target = match[1]!.split('#').first;
        if (target.isEmpty || Uri.parse(target).hasScheme) continue;
        final resolved = file.absolute.uri.resolve(target).toFilePath();
        expect(
          File(resolved).existsSync() || Directory(resolved).existsSync(),
          isTrue,
          reason: '$path links to $target',
        );
      }
    }
  });

  test('matrix covers every live page and deterministic inventory finding', () {
    final matrix = File('docs/ui/MIGRATION_MATRIX.md').readAsStringSync();
    final rows = inventorySources(readUiSources(Directory.current));
    final snapshot = UiMigrationSnapshot.fromMarkdown(matrix);
    expect(
      matrix.replaceAll('\r\n', '\n').trim(),
      renderMigrationMatrix(rows, snapshot: snapshot).trim(),
    );
    for (final row in rows) {
      expect(matrix, contains(row['id']! as String));
      if (row['kind'] == 'page-file') {
        expect(matrix, contains(row['path']! as String));
      }
    }
    for (final heading in [
      'Current owner / behavior',
      'Target owner',
      'Phase/task',
      'Regression coverage',
      'Status',
      'Legacy/manual exception',
    ]) {
      expect(matrix, contains(heading));
    }
  });

  test(
    'Phase 04 matrix tracks Continue Reading and retires stale exemptions',
    () {
      final rows = inventorySources(readUiSources(Directory.current));
      final matrix = File('docs/ui/MIGRATION_MATRIX.md').readAsStringSync();
      final ids = rows.map((row) => row['id']).toSet();
      final exceptions = File('test/fixtures/ui_architecture/legacy_sites.txt')
          .readAsLinesSync()
          .where((line) => line.isNotEmpty);
      expect(exceptions.every(ids.contains), isTrue);
      for (final symbol in [
        'hikari_async_state_view.dart',
        'hikari_content_region.dart',
        'hikari_page_scaffold.dart',
      ]) {
        expect(
          rows.where(
            (row) =>
                row['path'] ==
                    'lib/features/reading/continue_reading_page.dart' &&
                row['symbol'].toString().endsWith(symbol),
          ),
          hasLength(1),
        );
        expect(matrix, contains(symbol));
      }
    },
  );

  test('G4 report reflects owner acceptance after executable gates', () {
    final report = File('docs/roadmap/UI_FOUNDATION_PHASE_04.md')
        .readAsStringSync();
    expect(report, contains('G4 OWNER-ACCEPTED'));
    expect(report, contains('ChapterListControls'));
    expect(report, contains('1253 passed'));
  });

  test('ADR has an explicit approval state and preserves non-UI decisions', () {
    final adr = File(_canonical.first).readAsStringSync();
    final decision = RegExp(
      r'^Status: \*\*(Proposed|Accepted)\b',
      multiLine: true,
    ).firstMatch(adr);
    expect(decision, isNotNull, reason: 'ADR-026 status must be explicit');
    if (decision![1] == 'Accepted') {
      expect(
        RegExp(r'^Approved by: \S.*$', multiLine: true).hasMatch(adr),
        isTrue,
        reason: 'Accepted ADR requires the approving owner',
      );
      expect(
        RegExp(
          r'^Approval date: \d{4}-\d{2}-\d{2}$',
          multiLine: true,
        ).hasMatch(adr),
        isTrue,
        reason: 'Accepted ADR requires a dated approval record',
      );
    }
    for (final protected in ['ADR-009', 'ADR-023', 'ADR-024', 'ADR-025']) {
      expect(adr, contains(protected));
    }
    expect(adr, contains('useRootNavigator'));
    expect(adr, contains('routeDismissed'));
  });
}
