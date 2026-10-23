import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/features/catalog/widgets/catalog_source_picker.dart';

import '../../../../tool/ui_gallery/main.dart';

void main() {
  for (final sample in ['Picker compact', 'Picker expanded']) {
    testWidgets('Gallery $sample opens production picker content', (
      tester,
    ) async {
      await tester.pumpWidget(const HikariGallery());
      final button = find.widgetWithText(TextButton, sample);
      await tester.scrollUntilVisible(
        button,
        400,
        scrollable: find.byType(Scrollable).first,
      );
      await tester.tap(button);
      await tester.pumpAndSettle();
      expect(find.byType(CatalogSourcePicker), findsOneWidget);
      expect(
        find.byType(sample.endsWith('compact') ? BottomSheet : Dialog),
        findsOneWidget,
      );
      await tester.tap(find.byTooltip('Close'));
      await tester.pumpAndSettle();
      expect(find.byType(CatalogSourcePicker), findsNothing);
    });
  }
}
