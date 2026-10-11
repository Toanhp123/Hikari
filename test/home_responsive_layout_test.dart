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

  testWidgets('compact hero exposes reachable previous and next actions', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(375, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final entries = [
      for (final value in ['first', 'second'])
        CatalogEntry(
          id: CatalogEntryId(provider: 'test', value: value),
          title: value,
          type: MediaType.anime,
        ),
    ];
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: Scaffold(
          body: HeroCarousel(entries: entries, openDetail: (_) {}),
        ),
      ),
    );
    final previous = find.ancestor(
      of: find.byTooltip('Previous featured item'),
      matching: find.byType(IconButton),
    );
    final next = find.ancestor(
      of: find.byTooltip('Next featured item'),
      matching: find.byType(IconButton),
    );
    expect(previous, findsOneWidget);
    expect(next, findsOneWidget);
    expect(tester.widget<IconButton>(previous).onPressed, isNull);
    await tester.tap(next);
    await tester.pumpAndSettle();
    expect(tester.widget<IconButton>(previous).onPressed, isNotNull);
    expect(tester.widget<IconButton>(next).onPressed, isNull);
  });

  testWidgets('hero increases height to accommodate scaled content', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(375, 800);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final entry = CatalogEntry(
      id: const CatalogEntryId(provider: 'test', value: 'long'),
      title: 'A very long featured story title needing two full lines',
      type: MediaType.manga,
    );
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: MediaQuery(
          data: const MediaQueryData(textScaler: TextScaler.linear(2)),
          child: Scaffold(
            body: SingleChildScrollView(
              child: HeroCarousel(entries: [entry], openDetail: (_) {}),
            ),
          ),
        ),
      ),
    );
    final aspect = find
        .descendant(
          of: find.byType(HeroCarousel),
          matching: find.byType(AspectRatio),
        )
        .first;
    expect(tester.getSize(aspect).height, greaterThan((375 - 32) / (16 / 10)));
    expect(find.byType(FilledButton), findsOneWidget);
    expect(tester.takeException(), isNull);
  });
}
