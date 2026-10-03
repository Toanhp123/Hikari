import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/search/unified_search_page.dart';

void main() {
  testWidgets(
    'UnifiedSearchPage shows initial empty state and renders query results',
    (tester) async {
      const item1 = Media(
        title: 'Solo Leveling',
        type: MediaType.manga,
        source: SourceMediaRef(sourceId: SourceId.local, itemId: 'sl'),
      );

      Media? tappedMedia;
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(),
          home: UnifiedSearchPage(
            scanLocalMedia: () async => [item1],
            openMedia: (_, item) => tappedMedia = item,
          ),
        ),
      );

      expect(find.text('Explore & Search'), findsOneWidget);

      // Enter search query
      await tester.enterText(find.byType(TextField), 'Solo');
      await tester.pump(const Duration(milliseconds: 350));
      await tester.pumpAndSettle();

      expect(find.text('Solo Leveling'), findsOneWidget);

      await tester.tap(find.text('Solo Leveling'));
      await tester.pumpAndSettle();
      expect(tappedMedia, item1);
    },
  );
}
