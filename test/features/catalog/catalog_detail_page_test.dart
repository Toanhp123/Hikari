import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/catalog/load_catalog_entry_details.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/catalog/catalog_detail_page.dart';

void main() {
  testWidgets(
    'catalog detail renders normalized fields and routes relations/search',
    (tester) async {
      CatalogEntry? related;
      CatalogEntry? searched;
      final detail = CatalogEntryDetails(
        entry: _anime,
        description: 'Description',
        warnings: ['Some fields unavailable'],
        relations: [
          CatalogRelatedEntry(
            relation: CatalogRelation.sequel,
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
            openSourceSearch: (entry) => searched = entry,
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Anime A'), findsWidgets);
      expect(find.text('Japanese title'), findsOneWidget);
      expect(find.text('Alternate'), findsOneWidget);
      expect(find.text('Action'), findsOneWidget);
      for (final value in [
        'Score 91',
        'Popularity 55',
        'Format: tv',
        'Status: finished',
        'summer 2025',
        '12 episodes',
        'Description',
        'Some fields unavailable',
        'Example Studio',
        'Writer',
      ]) {
        await tester.scrollUntilVisible(
          find.text(value),
          250,
          scrollable: find.byType(Scrollable).first,
        );
        expect(find.text(value), findsOneWidget, reason: value);
      }
      await tester.tap(find.text('Related series'));
      expect(related, _related);
      await tester.tap(find.text('Watch'));
      expect(searched, _anime);
      expect(searched?.title, 'Anime A');
      expect(searched?.type, MediaType.anime);
    },
  );

  testWidgets('catalog detail not-found and failed states can retry', (
    tester,
  ) async {
    var calls = 0;
    final failedAttempt = Completer<CatalogEntryDetails?>();
    final provider = _Provider(
      onLoadDetails: (_) {
        if (++calls == 1) return Future.value(null);
        if (calls == 2) return failedAttempt.future;
        return Future.value(CatalogEntryDetails(entry: _anime));
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
    expect(find.text('Catalog entry not found'), findsOneWidget);
    await tester.tap(find.text('Try Again'));
    await tester.pump(const Duration(milliseconds: 1));
    expect(calls, 2);
    failedAttempt.completeError(StateError('offline'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 1));
    await tester.pumpAndSettle();
    expect(find.text('Details unavailable'), findsOneWidget);
    await tester.tap(find.text('Try Again'));
    await tester.pumpAndSettle();
    expect(calls, 3);
    await tester.scrollUntilVisible(
      find.text('Watch'),
      250,
      scrollable: find.byType(Scrollable).first,
    );
    expect(find.text('Watch'), findsOneWidget);
  });

  testWidgets('manga detail exposes Read callback with CatalogEntry', (
    tester,
  ) async {
    CatalogEntry? searched;
    final manga = CatalogEntry(
      id: _related.id,
      title: 'Book A',
      type: MediaType.manga,
      alternateTitles: ['Translated title'],
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
    expect(searched?.title, 'Book A');
    expect(searched?.type, MediaType.manga);
  });
}

Widget _app(Widget child) =>
    MaterialApp(theme: HikariTheme.darkTheme(), home: child);

final _anime = CatalogEntry(
  id: const CatalogEntryId(provider: 'test', value: '1'),
  title: 'Anime A',
  type: MediaType.anime,
  coverUrl: 'https://example/cover',
  alternateTitles: ['Japanese title'],
  synonyms: ['Alternate'],
  genres: ['Action'],
  averageScore: 91,
  popularity: 55,
  format: CatalogFormat.tv,
  status: CatalogStatus.finished,
  season: CatalogSeason.summer,
  year: 2025,
  episodes: 12,
  studios: ['Example Studio'],
  staff: ['Writer'],
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
  Future<CatalogEntryDetails?> loadDetails(CatalogEntryId id) =>
      _onLoadDetails(id);

  @override
  Future<void> close() async {}
}
