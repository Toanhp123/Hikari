import 'dart:async';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/catalog/load_catalog_entry_details.dart';
import 'package:hikari/application/catalog/resolve_catalog_source.dart';
import 'package:hikari/application/search/search_manga.dart';
import 'package:hikari/application/search/search_novels.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/source.dart';
import 'package:hikari/features/catalog/catalog_detail_page.dart';
import 'package:hikari/features/catalog/widgets/catalog_source_picker.dart';

void main() {
  testWidgets(
    'same-name grouped sources stay separate without exposing opaque IDs',
    (tester) async {
      await tester.pumpWidget(
        _app(
          CatalogDetailPage(
            initialEntry: _related,
            loadDetails: LoadCatalogEntryDetails(_Provider()),
            resolveCatalogSource: _resolver([
              for (final package in ['package:a', 'package:b'])
                for (final language in ['en', 'fr'])
                  _PickerMangaSource(
                    id: SourceId('$package:$language'),
                    displayName: 'MangaDex',
                    languageCode: language,
                    presentationGroupId: package,
                  ),
            ]),
            openMedia: (_, _) async {},
            openRelated: (_) {},
            openSourceSearch: (_, _, _, _, _) {},
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Read'));
      await tester.pumpAndSettle();
      expect(find.text('MangaDex'), findsNWidgets(2));
      expect(find.textContaining('package:a'), findsNothing);
      expect(find.textContaining('package:b'), findsNothing);
      final sourceTiles = find.descendant(
        of: find.byType(CatalogSourcePicker),
        matching: find.byType(ListTile),
      );
      expect(sourceTiles, findsNWidgets(2));
      await tester.tap(sourceTiles.at(1));
      await tester.pumpAndSettle();
      expect(find.text('Choose language'), findsOneWidget);
      expect(find.text('MangaDex'), findsNWidgets(3));
      expect(find.text('EN'), findsOneWidget);
      expect(find.text('FR'), findsOneWidget);
      expect(find.textContaining('package:a'), findsNothing);
      expect(find.textContaining('package:b'), findsNothing);
    },
  );

  testWidgets(
    'ambiguous languages remain separate without exposing opaque IDs',
    (tester) async {
      final manga = CatalogEntry(
        id: _related.id,
        title: 'Book A',
        type: MediaType.manga,
      );
      await tester.pumpWidget(
        _app(
          CatalogDetailPage(
            initialEntry: manga,
            loadDetails: LoadCatalogEntryDetails(
              _Provider(
                onLoadDetails: (_) async => CatalogEntryDetails(entry: manga),
              ),
            ),
            resolveCatalogSource: _resolver([
              for (final (id, language) in [
                ('a', 'en'),
                ('b', 'en'),
                ('c', 'all'),
                ('d', null),
              ])
                _PickerMangaSource(
                  id: SourceId(id),
                  displayName: 'Same',
                  languageCode: language,
                  presentationGroupId: 'one',
                ),
            ]),
            openMedia: (_, _) async {},
            openRelated: (_) {},
            openSourceSearch: (_, _, _, _, _) {},
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Read'));
      await tester.pumpAndSettle();
      expect(find.text('Same'), findsNWidgets(4));
      expect(find.text('EN'), findsNWidgets(2));
      expect(find.text('Multiple languages'), findsOneWidget);
      expect(find.text('Unspecified'), findsOneWidget);
      expect(find.byType(MenuAnchor), findsNothing);
    },
  );

  testWidgets(
    'thirty languages stay in a bounded scrollable menu with selection state',
    (tester) async {
      tester.view.physicalSize = const Size(390, 844);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      final manga = CatalogEntry(
        id: _related.id,
        title: 'Book A',
        type: MediaType.manga,
      );
      await tester.pumpWidget(
        _app(
          CatalogDetailPage(
            initialEntry: manga,
            loadDetails: LoadCatalogEntryDetails(
              _Provider(
                onLoadDetails: (_) async => CatalogEntryDetails(entry: manga),
              ),
            ),
            resolveCatalogSource: _resolver([
              for (var i = 0; i < 30; i++)
                _PickerMangaSource(
                  id: SourceId('test:$i'),
                  displayName: 'MangaDex',
                  languageCode: 'l${i.toString().padLeft(2, '0')}',
                  presentationGroupId: 'package:dex',
                ),
            ]),
            openMedia: (_, _) async {},
            openRelated: (_) {},
            openSourceSearch: (_, _, _, _, _) {},
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Read'));
      await tester.pumpAndSettle();
      expect(find.text('MangaDex'), findsOneWidget);
      expect(find.byType(MenuAnchor), findsOneWidget);
      final languageMenu = tester.widget<MenuAnchor>(find.byType(MenuAnchor));
      final maximumSize = languageMenu.style?.maximumSize?.resolve({});
      expect(maximumSize?.height, 320);

      await tester.tap(find.text('Language: All'));
      await tester.pumpAndSettle();
      final allLanguagesItem = find.ancestor(
        of: find.text('All languages'),
        matching: find.byType(MenuItemButton),
      );
      expect(
        find.descendant(
          of: allLanguagesItem,
          matching: find.byIcon(Icons.check_rounded),
        ),
        findsOneWidget,
      );
      await tester.ensureVisible(find.widgetWithText(MenuItemButton, 'L29'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(MenuItemButton, 'L29'));
      await tester.pumpAndSettle();
      expect(find.text('Language: L29'), findsOneWidget);

      await tester.tap(find.text('Language: L29'));
      await tester.pumpAndSettle();
      final selectedLanguageItem = find.widgetWithText(MenuItemButton, 'L29');
      expect(
        find.descendant(
          of: selectedLanguageItem,
          matching: find.byIcon(Icons.check_rounded),
        ),
        findsOneWidget,
      );
      await tester.tap(find.text('Language: L29'));
      await tester.pumpAndSettle();
      expect(find.text('Language: L29'), findsOneWidget);
      expect(find.text('Search L29 sources'), findsOneWidget);
      await tester.tap(find.text('Show all languages'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('MangaDex'));
      await tester.pumpAndSettle();
      expect(find.text('Choose language'), findsOneWidget);
      await tester.binding.handlePopRoute();
      await tester.pumpAndSettle();
      expect(find.text('Choose language'), findsNothing);
      expect(find.text('Read from'), findsOneWidget);
      await tester.tap(find.text('MangaDex'));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Back'));
      await tester.pumpAndSettle();
      expect(find.text('MangaDex'), findsOneWidget);
      await tester.tap(find.text('MangaDex'));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Close'));
      await tester.pumpAndSettle();
      expect(find.text('Read from'), findsNothing);
    },
  );

  testWidgets('detail keeps identity and source action while loading', (
    tester,
  ) async {
    final pending = Completer<CatalogEntryDetails?>();
    CatalogEntry? searched;

    await tester.pumpWidget(
      _app(
        CatalogDetailPage(
          initialEntry: _anime,
          loadDetails: LoadCatalogEntryDetails(
            _Provider(onLoadDetails: (_) => pending.future),
          ),
          resolveCatalogSource: _resolver(),
          openMedia: (_, _) async {},
          openRelated: (_) {},
          openSourceSearch: (entry, _, _, _, _) => searched = entry,
        ),
      ),
    );
    await tester.pump();

    expect(find.text('Anime A'), findsWidgets);
    expect(find.byKey(const Key('catalog-detail-loading')), findsOneWidget);
    expect(find.text('Watch'), findsOneWidget);
    expect(find.text('Choose a source to continue'), findsOneWidget);

    await tester.tap(find.text('Watch'));
    expect(searched, _anime);

    pending.complete(CatalogEntryDetails(entry: _anime));
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('catalog-detail-loading')), findsNothing);
  });

  testWidgets('detail renders grouped metadata and routes relations', (
    tester,
  ) async {
    CatalogEntry? related;
    final description = List.filled(
      10,
      'A long catalog synopsis that should remain readable and scannable.',
    ).join(' ');
    final detail = CatalogEntryDetails(
      entry: _anime,
      description: description,
      alternateTitles: ['Japanese title'],
      synonyms: ['Alternate'],
      averageScore: 91,
      popularity: 15500,
      format: CatalogFormat.tv,
      status: CatalogStatus.finished,
      season: CatalogSeason.summer,
      year: 2025,
      episodes: 12,
      studios: ['Example Studio'],
      staff: ['Writer'],
      warnings: ['Some fields unavailable'],
      relations: [
        CatalogRelatedEntry(
          relation: CatalogRelation.sideStory,
          entry: _related,
        ),
      ],
    );

    await tester.pumpWidget(
      _app(
        CatalogDetailPage(
          initialEntry: _anime,
          loadDetails: LoadCatalogEntryDetails(
            _Provider(onLoadDetails: (_) async => detail),
          ),
          resolveCatalogSource: _resolver(),
          openMedia: (_, _) async {},
          openRelated: (entry) => related = entry,
          openSourceSearch: (_, _, _, _, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();

    for (final value in [
      'At a glance',
      '91%',
      '16K',
      'Finished',
      'TV',
      'Some fields unavailable',
      'Genres',
      'Action',
      'Synopsis',
      'Titles',
      'Japanese title',
      'Alternate',
    ]) {
      await _scrollTo(tester, find.text(value));
      expect(find.text(value), findsWidgets, reason: value);
    }

    await _scrollTo(tester, find.text('Related series'));
    expect(find.text('Side story'), findsOneWidget);
    await tester.tap(find.text('Related series'));
    expect(related, _related);

    await _scrollTo(tester, find.text('Details'));
    expect(find.text('Summer 2025'), findsOneWidget);
    expect(find.text('12'), findsOneWidget);

    await _scrollTo(tester, find.text('Credits'));
    expect(find.text('Example Studio'), findsOneWidget);
    expect(find.text('Writer'), findsOneWidget);
  });

  testWidgets('description expands and collapses without replacing content', (
    tester,
  ) async {
    final description = List.filled(
      12,
      'Long description text for catalog detail readability.',
    ).join(' ');
    await tester.pumpWidget(
      _app(
        CatalogDetailPage(
          initialEntry: _anime,
          loadDetails: LoadCatalogEntryDetails(
            _Provider(
              onLoadDetails: (_) async =>
                  CatalogEntryDetails(entry: _anime, description: description),
            ),
          ),
          resolveCatalogSource: _resolver(),
          openMedia: (_, _) async {},
          openRelated: (_) {},
          openSourceSearch: (_, _, _, _, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();

    await _scrollTo(tester, find.text('Show more'));
    var body = tester.widget<Text>(
      find.byKey(const Key('catalog-detail-description')),
    );
    expect(body.maxLines, 6);

    await tester.tap(find.text('Show more'));
    await tester.pump();
    body = tester.widget<Text>(
      find.byKey(const Key('catalog-detail-description')),
    );
    expect(body.maxLines, isNull);
    expect(find.text('Show less'), findsOneWidget);
  });

  testWidgets('failed and missing metadata keep source search usable', (
    tester,
  ) async {
    var calls = 0;
    CatalogEntry? searched;
    final provider = _Provider(
      onLoadDetails: (_) {
        calls++;
        if (calls == 1) return Future<CatalogEntryDetails?>.value(null);
        if (calls == 2) {
          return Future<CatalogEntryDetails?>.error(StateError('offline'));
        }
        return Future.value(CatalogEntryDetails(entry: _anime));
      },
    );

    await tester.pumpWidget(
      _app(
        CatalogDetailPage(
          initialEntry: _anime,
          loadDetails: LoadCatalogEntryDetails(provider),
          resolveCatalogSource: _resolver(),
          openMedia: (_, _) async {},
          openRelated: (_) {},
          openSourceSearch: (entry, _, _, _, _) => searched = entry,
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Catalog entry unavailable'), findsOneWidget);
    expect(find.text('Watch'), findsOneWidget);
    await tester.tap(find.text('Watch'));
    expect(searched, _anime);

    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(find.text('More details could not load'), findsOneWidget);
    expect(find.text('Watch'), findsOneWidget);

    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();
    expect(calls, 3);
    expect(find.text('More details could not load'), findsNothing);
  });

  testWidgets('detail refresh action reloads metadata without navigation', (
    tester,
  ) async {
    var calls = 0;
    final provider = _Provider(
      onLoadDetails: (_) async {
        calls++;
        return CatalogEntryDetails(entry: _anime);
      },
    );

    await tester.pumpWidget(
      _app(
        CatalogDetailPage(
          initialEntry: _anime,
          loadDetails: LoadCatalogEntryDetails(provider),
          resolveCatalogSource: _resolver(),
          openMedia: (_, _) async {},
          openRelated: (_) {},
          openSourceSearch: (_, _, _, _, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(calls, 1);

    await tester.tap(find.byTooltip('Refresh'));
    await tester.pumpAndSettle();
    expect(calls, 2);
  });

  testWidgets('failed refresh keeps the last usable details visible', (
    tester,
  ) async {
    var calls = 0;
    final provider = _Provider(
      onLoadDetails: (_) {
        if (++calls == 1) {
          return Future.value(
            CatalogEntryDetails(
              entry: _anime,
              description: 'Cached description',
              warnings: ['Partial metadata'],
            ),
          );
        }
        return Future<CatalogEntryDetails?>.error(StateError('offline'));
      },
    );

    await tester.pumpWidget(
      _app(
        CatalogDetailPage(
          initialEntry: _anime,
          loadDetails: LoadCatalogEntryDetails(provider),
          resolveCatalogSource: _resolver(),
          openMedia: (_, _) async {},
          openRelated: (_) {},
          openSourceSearch: (_, _, _, _, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();

    await _scrollTo(tester, find.text('Partial metadata'));
    await tester.tap(find.text('Retry'));
    await tester.pumpAndSettle();

    expect(find.text('Refresh failed'), findsOneWidget);
    expect(find.text('Cached description'), findsOneWidget);
    expect(find.byKey(const Key('catalog-detail-loading')), findsNothing);
  });

  testWidgets('expanded detail uses a bounded supporting pane', (tester) async {
    tester.view.physicalSize = const Size(1200, 1000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    await tester.pumpWidget(
      _app(
        CatalogDetailPage(
          initialEntry: _anime,
          loadDetails: LoadCatalogEntryDetails(
            _Provider(
              onLoadDetails: (_) async => CatalogEntryDetails(
                entry: _anime,
                description: 'Description',
                averageScore: 90,
                format: CatalogFormat.tv,
                status: CatalogStatus.releasing,
                year: 2026,
              ),
            ),
          ),
          resolveCatalogSource: _resolver(),
          openMedia: (_, _) async {},
          openRelated: (_) {},
          openSourceSearch: (_, _, _, _, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.byKey(const Key('catalog-detail-supporting-pane')),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });

  testWidgets('compact source picker can be dismissed with visible Close', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(390, 844);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final manga = CatalogEntry(
      id: _related.id,
      title: 'Book A',
      type: MediaType.manga,
    );
    await tester.pumpWidget(
      _app(
        CatalogDetailPage(
          initialEntry: manga,
          loadDetails: LoadCatalogEntryDetails(
            _Provider(
              onLoadDetails: (_) async => CatalogEntryDetails(entry: manga),
            ),
          ),
          resolveCatalogSource: _resolver(),
          openMedia: (_, _) async {},
          openRelated: (_) {},
          openSourceSearch: (_, _, _, _, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Read'));
    await tester.pumpAndSettle();
    expect(find.byTooltip('Close'), findsOneWidget);
    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();
    expect(find.text('Read from'), findsNothing);
  });

  testWidgets('expanded source picker can be dismissed with visible Close', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(1200, 1000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final manga = CatalogEntry(
      id: _related.id,
      title: 'Book A',
      type: MediaType.manga,
    );
    await tester.pumpWidget(
      _app(
        CatalogDetailPage(
          initialEntry: manga,
          loadDetails: LoadCatalogEntryDetails(
            _Provider(
              onLoadDetails: (_) async => CatalogEntryDetails(entry: manga),
            ),
          ),
          resolveCatalogSource: _resolver(),
          openMedia: (_, _) async {},
          openRelated: (_) {},
          openSourceSearch: (_, _, _, _, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Read'));
    await tester.pumpAndSettle();
    expect(find.byTooltip('Close'), findsOneWidget);
    await tester.tap(find.byTooltip('Close'));
    await tester.pumpAndSettle();
    expect(find.text('Read from'), findsNothing);
  });

  testWidgets('filtered Search all passes only visible source IDs', (
    tester,
  ) async {
    Set<SourceId>? searchedIds;
    final manga = CatalogEntry(
      id: _related.id,
      title: 'Book A',
      type: MediaType.manga,
    );
    final sources = [
      _PickerMangaSource(
        id: const SourceId('test:en'),
        displayName: 'English source',
        languageCode: 'en',
      ),
      _PickerMangaSource(
        id: const SourceId('test:fr'),
        displayName: 'French source',
        languageCode: 'fr',
      ),
    ];
    await tester.pumpWidget(
      _app(
        CatalogDetailPage(
          initialEntry: manga,
          loadDetails: LoadCatalogEntryDetails(
            _Provider(
              onLoadDetails: (_) async => CatalogEntryDetails(entry: manga),
            ),
          ),
          resolveCatalogSource: _resolver(sources),
          openMedia: (_, _) async {},
          openRelated: (_) {},
          openSourceSearch: (_, _, _, ids, _) => searchedIds = ids,
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Read'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Language: All'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(MenuItemButton, 'EN'));
    await tester.pumpAndSettle();
    expect(find.text('English source'), findsOneWidget);
    expect(find.text('French source'), findsNothing);
    await tester.tap(find.text('Search EN sources'));

    expect(searchedIds, {const SourceId('test:en')});
    await tester.pumpAndSettle();
    await tester.tap(find.text('Read'));
    await tester.pumpAndSettle();
    expect(find.text('Language: All'), findsOneWidget);
    await tester.tap(find.text('Search all sources'));
    await tester.pumpAndSettle();
    expect(searchedIds, isNull);
  });

  testWidgets('manga Read resolves a selected source before opening media', (
    tester,
  ) async {
    Media? opened;
    CatalogEntry? searched;
    final manga = CatalogEntry(
      id: _related.id,
      title: 'Book A',
      type: MediaType.manga,
    );
    final source = _MangaSource(
      results: [
        MangaPreview(
          media: Media(
            title: 'Book A',
            type: MediaType.manga,
            source: const SourceMediaRef(
              sourceId: SourceId('test:manga'),
              itemId: 'book-a',
            ),
          ),
        ),
      ],
    );

    await tester.pumpWidget(
      _app(
        CatalogDetailPage(
          initialEntry: manga,
          loadDetails: LoadCatalogEntryDetails(
            _Provider(
              onLoadDetails: (_) async => CatalogEntryDetails(entry: manga),
            ),
          ),
          resolveCatalogSource: _resolver([source]),
          openMedia: (_, media) async {
            opened = media;
          },
          openRelated: (_) {},
          openSourceSearch: (entry, _, _, _, _) => searched = entry,
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Read'));
    await tester.pumpAndSettle();

    expect(find.text('Read from'), findsOneWidget);
    expect(find.text('Manga source [en]'), findsOneWidget);
    expect(searched, isNull);

    await tester.tap(find.text('Manga source [en]'));
    await tester.pumpAndSettle();

    expect(opened?.title, 'Book A');
    expect(searched, isNull);
    expect(find.text('Read from'), findsNothing);
  });
}

Future<void> _scrollTo(WidgetTester tester, Finder finder) async {
  final matches = finder.evaluate().toList(growable: false);
  expect(matches, isNotEmpty, reason: 'Scroll target not found: $finder');
  final target = find.byWidget(matches.first.widget);
  final verticalScroll = find.byWidgetPredicate(
    (widget) =>
        widget is Scrollable && widget.axisDirection == AxisDirection.down,
  );
  expect(verticalScroll, findsOneWidget);
  await tester.scrollUntilVisible(target, 300, scrollable: verticalScroll);
  await tester.pump();
}

Widget _app(Widget child) =>
    MaterialApp(theme: HikariTheme.darkTheme(), home: child);

final _anime = CatalogEntry(
  id: const CatalogEntryId(provider: 'test', value: '1'),
  title: 'Anime A',
  type: MediaType.anime,
  genres: ['Action'],
);

final _related = CatalogEntry(
  id: const CatalogEntryId(provider: 'test', value: '2'),
  title: 'Related series',
  type: MediaType.manga,
);

ResolveCatalogSource _resolver([Iterable<MediaSource> sources = const []]) {
  final registry = SourceRegistry(sources);
  return ResolveCatalogSource(
    searchManga: SearchManga(registry),
    searchNovels: SearchNovels(registry),
  );
}

final class _MangaSource implements MangaSearchSource, MangaPageSource {
  _MangaSource({this.results = const []});

  final List<MangaPreview> results;

  @override
  SourceId get id => const SourceId('test:manga');

  @override
  String get name => 'Manga source [en]';

  @override
  Future<MangaSearchPage> search(String query, {int page = 1}) async =>
      MangaSearchPage(results: results, hasNextPage: false, page: page);

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async => const [];

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async => Uint8List(0);
}

final class _PickerMangaSource
    implements MangaSearchSource, MangaPageSource, MediaSourcePresentation {
  _PickerMangaSource({
    required this.id,
    required this.displayName,
    required this.languageCode,
    this.presentationGroupId,
  });

  @override
  final SourceId id;
  @override
  final String displayName;
  @override
  final String? languageCode;

  @override
  final String? presentationGroupId;

  @override
  String get name => '$displayName [$languageCode]';

  @override
  Future<MangaSearchPage> search(String query, {int page = 1}) async =>
      MangaSearchPage(results: const [], hasNextPage: false, page: page);

  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef readable) async => const [];

  @override
  Future<Uint8List> readPage(SourceMediaRef page) async => Uint8List(0);
}

final class _Provider implements CatalogProvider {
  _Provider({
    Future<CatalogDiscovery> Function()? onDiscover,
    Future<CatalogEntryDetails?> Function(CatalogEntryId)? onLoadDetails,
  }) : _onDiscover =
           onDiscover ?? (() async => CatalogDiscovery(sections: const {})),
       _onLoadDetails = onLoadDetails ?? ((_) async => null);

  final Future<CatalogDiscovery> Function() _onDiscover;
  final Future<CatalogEntryDetails?> Function(CatalogEntryId) _onLoadDetails;

  @override
  String get id => 'test';

  @override
  Future<CatalogDiscovery> discover() => _onDiscover();

  @override
  Future<List<CatalogEntry>> search(String query, {MediaType? type}) async =>
      const [];

  @override
  Future<CatalogEntryDetails?> loadDetails(CatalogEntryId id) =>
      _onLoadDetails(id);

  @override
  Future<void> close() async {}
}
