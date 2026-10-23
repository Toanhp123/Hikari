import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/core/ui/hikari_ui.dart';

import '../../../../tool/ui_gallery/media_preview.dart';

void main() {
  testWidgets(
    'media Gallery renders stress states at 3x RTL and local widths',
    (tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(1440, 900);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);
      for (final width in [375.0, 600.0, 840.0, 1440.0]) {
        await tester.pumpWidget(
          MaterialApp(
            home: MediaQuery(
              data: const MediaQueryData(textScaler: TextScaler.linear(3)),
              child: Directionality(
                textDirection: TextDirection.rtl,
                child: Scaffold(
                  body: SingleChildScrollView(
                    child: MediaPreview(
                      width: width,
                      stress: true,
                      enabled: false,
                    ),
                  ),
                ),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(
          find.byType(HikariPosterCard).evaluate().length,
          inExclusiveRange(0, 100),
        );
        expect(find.byIcon(Icons.image_not_supported_outlined), findsWidgets);
        expect(find.byType(HikariLandscapeCard), findsOneWidget);
        expect(find.byType(HikariChapterRow), findsOneWidget);
        expect(
          tester
              .widget<HikariHorizontalShelf>(find.byType(HikariHorizontalShelf))
              .itemCount,
          5000,
        );
        expect(
          tester
              .widget<HikariAdaptiveGrid>(find.byType(HikariAdaptiveGrid))
              .itemCount,
          5000,
        );
        expect(tester.takeException(), isNull);
      }
    },
  );
}
