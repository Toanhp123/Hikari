import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/foundation/hikari_breakpoints.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/application/catalog/search_catalog.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/catalog/catalog_search_page.dart';

void main() {
  testWidgets('catalog edits debounce empty text but clear is immediate', (
    tester,
  ) async {
    final provider = _Provider();
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: CatalogSearchPage(
          searchCatalog: SearchCatalog(provider),
          openDetail: (_, _) {},
          openSourceSearch: (_) {},
        ),
      ),
    );
    await tester.enterText(find.byType(SearchBar), 'Frieren');
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();
    await tester.enterText(find.byType(SearchBar), '');
    await tester.pump(const Duration(milliseconds: 299));
    expect(find.text('All result'), findsOneWidget);
    await tester.pump(const Duration(milliseconds: 1));
    expect(find.text('Explore & Search'), findsOneWidget);
    await tester.enterText(find.byType(SearchBar), 'Frieren');
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Clear search'));
    await tester.pump();
    expect(find.text('Explore & Search'), findsOneWidget);
    expect(provider.calls, [('Frieren', null), ('Frieren', null)]);
  });

  testWidgets(
    'catalog selection preserves edit timer and external edits cancel it',
    (tester) async {
      final provider = _Provider();
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(),
          home: CatalogSearchPage(
            searchCatalog: SearchCatalog(provider),
            openDetail: (_, _) {},
            openSourceSearch: (_) {},
          ),
        ),
      );
      await tester.enterText(find.byType(SearchBar), 'Frieren');
      await tester.testTextInput.receiveAction(TextInputAction.search);
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(SearchBar), 'Bleach');
      final controller = tester
          .widget<SearchBar>(find.byType(SearchBar))
          .controller!;
      controller.selection = const TextSelection.collapsed(offset: 1);
      await tester.pump(const Duration(milliseconds: 299));
      expect(find.text('All result'), findsOneWidget);
      await tester.pump(const Duration(milliseconds: 1));
      expect(find.text('Explore & Search'), findsOneWidget);
      await tester.testTextInput.receiveAction(TextInputAction.search);
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(SearchBar), 'pending');
      controller.text = 'external';
      await tester.pump(const Duration(milliseconds: 301));
      expect(find.text('All result'), findsOneWidget);
      expect(provider.calls, [('Frieren', null), ('Bleach', null)]);
      await tester.enterText(find.byType(SearchBar), 'dispose pending');
      await tester.pumpWidget(const SizedBox());
      await tester.pump(const Duration(milliseconds: 301));
      expect(tester.takeException(), isNull);
    },
  );

  testWidgets('catalog search queries metadata and opens selected detail', (
    tester,
  ) async {
    final provider = _Provider();
    CatalogEntry? opened;

    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: CatalogSearchPage(
          searchCatalog: SearchCatalog(provider),
          openDetail: (_, entry) => opened = entry,
          openSourceSearch: (_) {},
        ),
      ),
    );

    expect(find.text('Explore & Search'), findsOneWidget);
    await tester.enterText(find.byType(SearchBar), '  Frieren  ');
    await tester.pump(const Duration(milliseconds: 301));
    expect(provider.calls, isEmpty);

    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();

    expect(provider.calls, [('Frieren', null)]);
    expect(find.text('All result'), findsOneWidget);

    await tester.tap(find.text('Manga'));
    await tester.pumpAndSettle();
    expect(provider.calls.last, ('Frieren', MediaType.manga));
    expect(find.text('Manga result'), findsOneWidget);

    await tester.enterText(find.byType(SearchBar), 'Bleach');
    await tester.pump(const Duration(milliseconds: 301));
    expect(provider.calls.length, 2);
    expect(find.text('Explore & Search'), findsOneWidget);

    expect(find.byTooltip('Search catalog'), findsNothing);
    expect(find.byTooltip('Submit search'), findsOneWidget);
    await tester.tap(find.byTooltip('Submit search'));
    await tester.pumpAndSettle();
    expect(provider.calls, [
      ('Frieren', null),
      ('Frieren', MediaType.manga),
      ('Bleach', MediaType.manga),
    ]);

    await tester.tapAt(tester.getCenter(find.text('Manga result')));
    expect(opened?.title, 'Manga result');
  });

  testWidgets('catalog search has one submit and singular filters', (
    tester,
  ) async {
    final provider = _Provider();
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: CatalogSearchPage(
          searchCatalog: SearchCatalog(provider),
          openDetail: (_, _) {},
          openSourceSearch: (_) {},
        ),
      ),
    );

    expect(find.text('All'), findsOneWidget);
    expect(find.text('Light Novel'), findsOneWidget);
    expect(find.text('Light Novels'), findsNothing);
    expect(find.byTooltip('Search catalog'), findsNothing);
    await tester.enterText(find.byType(SearchBar), 'Novel');
    await tester.pump();
    expect(find.byTooltip('Submit search'), findsOneWidget);
    await tester.tap(find.byTooltip('Submit search'));
    await tester.pumpAndSettle();
    expect(provider.calls, [('Novel', null)]);

    await tester.tap(find.widgetWithText(FilterChip, 'Light Novel'));
    await tester.pumpAndSettle();
    expect(provider.calls.last, ('Novel', MediaType.lightNovel));
    final count = provider.calls.length;
    await tester.tap(find.widgetWithText(FilterChip, 'Light Novel'));
    await tester.pumpAndSettle();
    expect(provider.calls.length, count);
    await tester.tap(find.text('All'));
    await tester.pumpAndSettle();
    expect(provider.calls.last, ('Novel', null));
  });

  testWidgets('source search action is available without catalog results', (
    tester,
  ) async {
    var opened = 0;
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: CatalogSearchPage(
          searchCatalog: SearchCatalog(_Provider()),
          openDetail: (_, _) {},
          openSourceSearch: (_) => opened++,
        ),
      ),
    );

    expect(find.text('Search sources'), findsOneWidget);
    await tester.tap(find.text('Search sources'));
    expect(opened, 1);
    expect(find.text('Explore & Search'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('catalog search bounds content on resize', (tester) async {
    tester.view.devicePixelRatio = 1;
    tester.view.physicalSize = const Size(375, 900);
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: CatalogSearchPage(
          searchCatalog: SearchCatalog(_Provider()),
          openDetail: (_, _) {},
          openSourceSearch: (_) {},
        ),
      ),
    );
    await tester.enterText(find.byType(SearchBar), 'Frieren');
    await tester.testTextInput.receiveAction(TextInputAction.search);
    await tester.pumpAndSettle();

    for (final width in [320.0, 375.0, 600.0, 840.0, 1200.0, 1800.0]) {
      tester.view.physicalSize = Size(width, 900);
      await tester.pumpAndSettle();
      final contentWidth = width < HikariBreakpoints.maxContentWidth
          ? width
          : HikariBreakpoints.maxContentWidth;
      expect(
        tester.getSize(find.byType(SearchBar)).width,
        moreOrLessEquals(contentWidth - HikariSpacing.lg * 2),
      );
      expect(
        tester.getSize(find.byType(GridView)).width,
        moreOrLessEquals(contentWidth),
      );
      expect(
        tester.getCenter(find.byType(SearchBar)).dx,
        closeTo(width / 2, 1),
      );
      expect(tester.takeException(), isNull);
    }
  });
}

final class _Provider implements CatalogProvider {
  final calls = <(String, MediaType?)>[];

  @override
  String get id => 'test';

  @override
  Future<CatalogDiscovery> discover() async =>
      CatalogDiscovery(sections: const {});

  @override
  Future<List<CatalogEntry>> search(String query, {MediaType? type}) async {
    calls.add((query, type));
    return [
      CatalogEntry(
        id: CatalogEntryId(provider: id, value: '${calls.length}'),
        title: type == MediaType.manga ? 'Manga result' : 'All result',
        type: type ?? MediaType.anime,
      ),
    ];
  }

  @override
  Future<CatalogEntryDetails?> loadDetails(CatalogEntryId id) async => null;

  @override
  Future<void> close() async {}
}
