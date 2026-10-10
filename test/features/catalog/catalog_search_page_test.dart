import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/catalog/search_catalog.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/catalog/catalog_search_page.dart';

void main() {
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

    await tester.tap(find.byTooltip('Search catalog'));
    await tester.pumpAndSettle();
    expect(provider.calls.last, ('Bleach', MediaType.manga));

    await tester.tapAt(tester.getCenter(find.text('Manga result')));
    expect(opened?.title, 'Manga result');
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
