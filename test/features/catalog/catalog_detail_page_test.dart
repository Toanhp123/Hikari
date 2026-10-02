import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/catalog/load_catalog_entry_details.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/catalog/catalog_detail_page.dart';

void main() {
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
          openRelated: (_) {},
          openSourceSearch: (entry) => searched = entry,
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
          openRelated: (entry) => related = entry,
          openSourceSearch: (_) {},
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
          openRelated: (_) {},
          openSourceSearch: (_) {},
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
          openRelated: (_) {},
          openSourceSearch: (entry) => searched = entry,
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
          openRelated: (_) {},
          openSourceSearch: (_) {},
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
          openRelated: (_) {},
          openSourceSearch: (_) {},
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
          openRelated: (_) {},
          openSourceSearch: (_) {},
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

  testWidgets('manga detail exposes Read callback with CatalogEntry', (
    tester,
  ) async {
    CatalogEntry? searched;
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
          openRelated: (_) {},
          openSourceSearch: (entry) => searched = entry,
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.text('Read'));
    expect(searched, manga);
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
