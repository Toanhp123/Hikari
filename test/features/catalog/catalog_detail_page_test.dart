import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/application/catalog/load_catalog_details.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/catalog/catalog_detail_page.dart';

void main() {
  testWidgets(
    'catalog detail renders normalized fields and routes relations/search',
    (tester) async {
      CatalogMedia? related;
      CatalogMedia? searched;
      final detail = CatalogDetails(
        media: _anime,
        description: 'Description',
        warnings: ['Some fields unavailable'],
        relations: [
          CatalogRelationMedia(
            relation: CatalogRelation.sequel,
            media: _related,
          ),
        ],
      );
      await tester.pumpWidget(
        _app(
          CatalogDetailPage(
            initial: _anime,
            loadDetails: LoadCatalogDetails(
              _Provider(onDetails: (_) async => detail),
            ),
            openRelated: (media) => related = media,
            openSourceSearch: (media) => searched = media,
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
    final failedAttempt = Completer<CatalogDetails?>();
    final provider = _Provider(
      onDetails: (_) {
        if (++calls == 1) return Future.value(null);
        if (calls == 2) return failedAttempt.future;
        return Future.value(CatalogDetails(media: _anime));
      },
    );
    await tester.pumpWidget(
      _app(
        CatalogDetailPage(
          initial: _anime,
          loadDetails: LoadCatalogDetails(provider),
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

  testWidgets('manga detail exposes Read callback with CatalogMedia', (
    tester,
  ) async {
    CatalogMedia? searched;
    final manga = CatalogMedia(
      id: _related.id,
      title: 'Book A',
      type: MediaType.manga,
      alternateTitles: ['Translated title'],
    );
    await tester.pumpWidget(
      _app(
        CatalogDetailPage(
          initial: manga,
          loadDetails: LoadCatalogDetails(
            _Provider(onDetails: (_) async => CatalogDetails(media: manga)),
          ),
          openRelated: (_) {},
          openSourceSearch: (media) => searched = media,
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

final _anime = CatalogMedia(
  id: const CatalogMediaId(provider: 'test', value: '1'),
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

final _related = CatalogMedia(
  id: const CatalogMediaId(provider: 'test', value: '2'),
  title: 'Related series',
  type: MediaType.manga,
);

final class _Provider implements CatalogProvider {
  _Provider({
    Future<CatalogDiscovery> Function()? onDiscover,
    Future<CatalogDetails?> Function(CatalogMediaId)? onDetails,
  }) : _onDiscover =
           onDiscover ?? (() async => CatalogDiscovery(sections: const {})),
       _onDetails = onDetails ?? ((_) async => null);

  final Future<CatalogDiscovery> Function() _onDiscover;
  final Future<CatalogDetails?> Function(CatalogMediaId) _onDetails;

  @override
  String get id => 'test';

  @override
  Future<CatalogDiscovery> discover() => _onDiscover();

  @override
  Future<CatalogDetails?> details(CatalogMediaId id) => _onDetails(id);

  @override
  Future<void> close() async {}
}
