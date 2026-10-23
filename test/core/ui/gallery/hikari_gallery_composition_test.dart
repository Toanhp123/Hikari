import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/hikari_ui.dart';

import '../../../../tool/ui_gallery/foundation_preview.dart';
import '../../../../tool/ui_gallery/main.dart';
import '../../../../tool/ui_gallery/page_navigation_preview.dart';
import '../../../../tool/ui_gallery/surface_preview.dart';

void main() {
  testWidgets(
    'gallery exposes composition and surfaces without losing controls',
    (tester) async {
      await tester.binding.setSurfaceSize(const Size(1440, 1000));
      addTearDown(() => tester.binding.setSurfaceSize(null));
      await tester.pumpWidget(const HikariGallery());
      expect(find.byType(HikariButton), findsWidgets);

      Future<void> choose(String section) async {
        await tester.scrollUntilVisible(
          find.byKey(const ValueKey('gallery-showcase')),
          -200,
          scrollable: find.byType(Scrollable).first,
        );
        final menu = tester.widget<HikariChoiceMenu<String>>(
          find.byKey(const ValueKey('gallery-showcase')),
        );
        menu.onChanged!(section);
        await tester.pump();
      }

      await choose('Foundations');
      await tester.scrollUntilVisible(
        find.byType(FoundationPreview),
        200,
        scrollable: find.byType(Scrollable).first,
      );
      expect(find.text('Headline medium'), findsOneWidget);

      await choose('Page & navigation');
      await tester.scrollUntilVisible(
        find.byType(PageNavigationPreview),
        200,
        scrollable: find.byType(Scrollable).first,
      );
      expect(find.byType(HikariPageScaffold), findsOneWidget);
      expect(find.byType(HikariContentRegion), findsOneWidget);
      expect(find.byType(HikariNavigationShell), findsOneWidget);

      await choose('Surfaces');
      await tester.scrollUntilVisible(
        find.byType(SurfacePreview),
        200,
        scrollable: find.byType(Scrollable).first,
      );
      expect(find.text('Dialog preview'), findsOneWidget);

      await choose('Controls & feedback');
      await tester.scrollUntilVisible(
        find.byType(HikariSegmentedControl<int>),
        200,
        scrollable: find.byType(Scrollable).first,
      );
      expect(find.byType(HikariAsyncStateView), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('gallery root dialogs inherit stress accessibility settings', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(1200, 1000));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(const HikariGallery());

    await tester.tap(find.text('RTL'));
    await tester.pump();
    await tester.tap(find.text('Reduced motion'));
    await tester.pump();
    final scaleMenu = tester
        .widgetList<HikariChoiceMenu<double>>(
          find.byType(HikariChoiceMenu<double>),
        )
        .firstWhere((menu) => menu.label == 'Text scale');
    scaleMenu.onChanged!(3.0);
    final showcase = tester.widget<HikariChoiceMenu<String>>(
      find.byKey(const ValueKey('gallery-showcase')),
    );
    showcase.onChanged!('Surfaces');
    await tester.pump();

    await tester.scrollUntilVisible(
      find.byType(SurfacePreview),
      200,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(find.text('Dialog preview'));
    await tester.pumpAndSettle();
    final dialogContext = tester.element(find.byType(HikariDialog));
    expect(Directionality.of(dialogContext), TextDirection.rtl);
    expect(MediaQuery.textScalerOf(dialogContext).scale(10), 30);
    expect(MediaQuery.of(dialogContext).disableAnimations, isTrue);
    expect(tester.takeException(), isNull);
  });

  testWidgets('navigation preview demonstrates bottom bar rail and drawer', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: Scaffold(
          body: SingleChildScrollView(
            child: PageNavigationPreview(width: 375, stress: false),
          ),
        ),
      ),
    );
    expect(find.byType(HikariNavigationShell), findsOneWidget);
    expect(find.byTooltip('Open navigation menu'), findsNothing);
    await tester.tap(find.text('Six destinations'));
    await tester.pumpAndSettle();
    expect(find.byTooltip('Open navigation menu'), findsOneWidget);
    await tester.tap(find.byTooltip('Open navigation menu'));
    await tester.pumpAndSettle();
    expect(find.byType(HikariDrawer), findsOneWidget);
    await tester.tap(find.text('Extra'));
    await tester.pumpAndSettle();
    expect(
      find.text('Selected destination: extra · Actions: 0'),
      findsOneWidget,
    );
    await tester.tap(find.text('Six destinations'));
    await tester.pumpAndSettle();
    expect(
      find.text('Selected destination: home · Actions: 0'),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('surface preview uses typed canonical dialog and sheet', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: const Scaffold(body: SurfacePreview()),
      ),
    );
    await tester.tap(find.text('Dialog preview'));
    await tester.pumpAndSettle();
    expect(find.byType(HikariDialog), findsOneWidget);
    await tester.tap(find.text('Complete preview'));
    await tester.pumpAndSettle();
    expect(find.text('Last interaction: completed'), findsOneWidget);

    await tester.tap(find.text('Sheet preview'));
    await tester.pumpAndSettle();
    expect(find.byType(HikariBottomSheet), findsOneWidget);
    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();
    expect(find.text('Last interaction: cancelled'), findsOneWidget);

    await tester.tap(find.text('Confirmation preview'));
    await tester.pumpAndSettle();
    expect(find.byType(HikariConfirmationDialog), findsOneWidget);
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(find.text('Last interaction: cancelled'), findsOneWidget);

    await tester.tap(find.text('Adaptive preview'));
    await tester.pumpAndSettle();
    expect(find.text('Adaptive selection preview'), findsOneWidget);
    await tester.tap(find.text('Choose sample'));
    await tester.pumpAndSettle();
    expect(find.text('Last interaction: completed'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
