import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/source_search/source_search_page.dart';

void main() {
  testWidgets(
    'SourceSearchPage shows initial empty state and renders query results',
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
          home: SourceSearchPage(
            scanLocalMedia: () async => [item1],
            openMedia: (_, item) => tappedMedia = item,
          ),
        ),
      );

      expect(find.text('Find a source'), findsOneWidget);

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
