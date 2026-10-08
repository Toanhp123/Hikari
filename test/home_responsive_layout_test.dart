import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/home/widgets/hero_carousel.dart';

void main() {
  testWidgets('hero responds to local width rather than whole window', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(1000, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final entry = CatalogEntry(
      id: const CatalogEntryId(provider: 'test', value: 'hero'),
      title: 'Responsive Hero',
      type: MediaType.anime,
    );
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: Scaffold(
          body: Align(
            alignment: Alignment.topLeft,
            child: SizedBox(
              width: 512,
              child: HeroCarousel(entries: [entry], openDetail: (_) {}),
            ),
          ),
        ),
      ),
    );
    final aspect = tester.widget<AspectRatio>(
      find
          .descendant(
            of: find.byType(HeroCarousel),
            matching: find.byType(AspectRatio),
          )
          .first,
    );
    expect(aspect.aspectRatio, 16 / 10);
    expect(tester.takeException(), isNull);
  });
}
