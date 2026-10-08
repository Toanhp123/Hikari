import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/continue_reading_item.dart';
import 'package:hikari/features/home/widgets/continue_shelf.dart';

void main() {
  testWidgets(
    'Continue cards grow with text and expose the full resume label',
    (tester) async {
      tester.view.physicalSize = const Size(375, 800);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      const media = Media(
        title: 'An unusually long reading title',
        type: MediaType.manga,
        source: SourceMediaRef(sourceId: SourceId.local, itemId: 'long'),
      );
      var opened = false;
      final semantics = tester.ensureSemantics();
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(),
          home: MediaQuery(
            data: const MediaQueryData(textScaler: TextScaler.linear(3)),
            child: Scaffold(
              body: ContinueShelf(
                items: const [ContinueReadingItem(media: media, progress: 0.5)],
                onOpenMedia: (_, _) => opened = true,
              ),
            ),
          ),
        ),
      );
      final shelfList = find.descendant(
        of: find.byType(ContinueShelf),
        matching: find.byType(ListView),
      );
      expect(tester.getSize(shelfList).height, greaterThan(142));
      expect(
        find.bySemanticsLabel(
          'Continue An unusually long reading title, Resume',
        ),
        findsOneWidget,
      );
      await tester.tap(find.text('An unusually long reading title'));
      expect(opened, isTrue);
      expect(tester.takeException(), isNull);
      semantics.dispose();
    },
  );
}
