import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/core/ui/hikari_ui.dart';

import '../../../tool/ui_gallery/main.dart';

void main() {
  test('Phase 02 Preview API exposes mechanics without internal session', () {
    const HikariPresentationResult<String?> result = HikariCompleted(null);
    expect(result, isNot(isA<HikariCancelled<String?>>()));
    expect(resolveNavigationMode(375, 6), HikariNavigationMode.drawer);
    const header = HikariPageHeader(title: 'Preview');
    expect(header.title, 'Preview');
    final barrel = File('lib/core/ui/hikari_ui.dart').readAsStringSync();
    expect(barrel, isNot(contains('hikari_presentation_session.dart')));
  });
  testWidgets('gallery renders public API and switches OLED and loading', (
    tester,
  ) async {
    await tester.pumpWidget(const HikariGallery());
    expect(find.byType(HikariButton), findsWidgets);
    final context = tester.element(find.text('OLED'));
    expect(Theme.of(context).colorScheme.surface, isNot(Colors.black));
    await tester.tap(find.text('OLED'));
    await tester.pumpAndSettle();
    expect(
      Theme.of(tester.element(find.text('OLED'))).colorScheme.surface,
      Colors.black,
    );
    await tester.tap(find.text('Loading'));
    await tester.pump();
    expect(
      tester
          .widgetList<HikariButton>(find.byType(HikariButton))
          .every((b) => b.isLoading),
      isTrue,
    );
    await tester.pumpWidget(const SizedBox());
  });

  testWidgets(
    'gallery stress labels reflow across scales local widths and RTL',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(1440, 1000));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.pumpWidget(const HikariGallery());
      await tester.tap(find.text('Stress text'));
      await tester.tap(find.text('RTL'));
      await tester.tap(find.text('Reduced motion'));
      final menus = tester
          .widgetList<HikariChoiceMenu<double>>(
            find.byType(HikariChoiceMenu<double>),
          )
          .toList();
      final setScale = menus
          .firstWhere((w) => w.label == 'Text scale')
          .onChanged!;
      final setWidth = menus
          .firstWhere((w) => w.label == 'Local width')
          .onChanged!;
      for (final scale in [1.0, 2.0, 3.0]) {
        setScale(scale);
        await tester.pump();
        for (final width in [
          220.0,
          375.0,
          599.0,
          600.0,
          601.0,
          839.0,
          840.0,
          841.0,
          1200.0,
          1440.0,
        ]) {
          setWidth(width);
          await tester.pump();
          await tester.scrollUntilVisible(
            find.byType(HikariSegmentedControl<int>),
            150,
            scrollable: find.byType(Scrollable).first,
          );
          await tester.pump();
          expect(
            tester.takeException(),
            isNull,
            reason: '$width dp, ${scale}x, RTL',
          );
          await tester.drag(
            find.byType(Scrollable).first,
            const Offset(0, 10000),
          );
          await tester.pump();
        }
      }
      await tester.pumpWidget(const SizedBox());
    },
  );

  test(
    'component docs declare ownership lifecycle and controller contract',
    () {
      final docs = File('docs/ui/components/controls.md').readAsStringSync();
      expect(docs, contains('Owner:'));
      expect(docs, contains('Preview'));
      expect(docs, contains('controlled'));
      final contributing = File('docs/ui/CONTRIBUTING.md').readAsStringSync();
      for (final stage in ['Draft', 'Preview', 'Stable', 'Deprecated']) {
        expect(contributing, contains(stage));
      }
    },
  );
  test('public UI API gallery and contribution contracts exist', () {
    for (final path in [
      'lib/core/ui/hikari_ui.dart',
      'tool/ui_gallery/main.dart',
      'docs/ui/CONTRIBUTING.md',
      'docs/ui/components/controls.md',
    ]) {
      expect(File(path).existsSync(), isTrue, reason: path);
    }
  });

  test('production never imports standalone gallery', () {
    for (final file
        in Directory('lib')
            .listSync(recursive: true)
            .whereType<File>()
            .where((file) => file.path.endsWith('.dart'))) {
      expect(
        file.readAsStringSync(),
        isNot(contains('ui_gallery')),
        reason: file.path,
      );
    }
  });
}
