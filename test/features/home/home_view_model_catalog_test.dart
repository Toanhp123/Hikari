import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/catalog/discover_catalog.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/features/home/home_view_model.dart';

void main() {
  for (final failOld in [false, true]) {
    test('stream supersedes pending reload (old failure: $failOld)', () async {
      const oldMedia = Media(
        title: 'Old',
        type: MediaType.manga,
        source: SourceMediaRef(sourceId: SourceId.local, itemId: 'old'),
      );
      const freshMedia = Media(
        title: 'Fresh',
        type: MediaType.manga,
        source: SourceMediaRef(sourceId: SourceId.local, itemId: 'fresh'),
      );
      final library = _ControlledLibrary();
      final progress = _ControlledProgress();
      final model = HomeViewModel(library, progressRepository: progress);
      addTearDown(library.controller.close);
      addTearDown(model.dispose);
      await Future<void>.delayed(Duration.zero);
      library.snapshot = [oldMedia];
      final reload = model.reload();
      await Future<void>.delayed(Duration.zero);
      library.controller.add([
        LibraryEntry(media: freshMedia, addedAt: DateTime.utc(2026)),
      ]);
      await Future<void>.delayed(Duration.zero);
      expect(model.state.libraryItems.single, freshMedia);
      expect(model.state.continueItems.single.media, freshMedia);
      if (failOld) {
        progress.pending.completeError(StateError('old progress failed'));
      } else {
        progress.pending.complete(
          _record(
            oldMedia.source,
            updatedAt: 1,
            position: TextPosition(progression: .1),
          ),
        );
      }
      await reload;
      expect(model.state.libraryItems.single, freshMedia);
      expect(model.state.continueItems.single.media, freshMedia);
      expect(model.state.progressError, isNull);
    });
  }

  test('catalog discovery and retry state live in HomeViewModel', () async {
    var calls = 0;
    final provider = _CatalogProvider(
      onDiscover: () async {
        if (++calls == 1) throw StateError('offline');
        return CatalogDiscovery(sections: {CatalogSection.featured: const []});
      },
    );
    final model = HomeViewModel(
      null,
      discoverCatalog: DiscoverCatalog(provider),
    );
    addTearDown(model.dispose);

    await Future<void>.delayed(Duration.zero);
    expect(model.state.catalogError, isA<StateError>());
    expect(model.state.catalogDiscovery, isNull);

    await model.reloadCatalog();
    expect(model.state.catalogError, isNull);
    expect(model.state.catalogDiscovery, isNotNull);
    expect(calls, 2);
  });

  test(
    'failed catalog refresh preserves the last discovery snapshot',
    () async {
      var calls = 0;
      final discovery = CatalogDiscovery(
        sections: {CatalogSection.featured: const []},
      );
      final provider = _CatalogProvider(
        onDiscover: () async {
          if (++calls == 1) return discovery;
          throw StateError('offline');
        },
      );
      final model = HomeViewModel(
        null,
        discoverCatalog: DiscoverCatalog(provider),
      );
      addTearDown(model.dispose);

      await Future<void>.delayed(Duration.zero);
      expect(model.state.catalogDiscovery, same(discovery));

      await model.reloadCatalog();
      expect(model.state.catalogDiscovery, same(discovery));
      expect(model.state.catalogError, isA<StateError>());
    },
  );

  test(
    'temporarily unavailable filter is restored when media returns',
    () async {
      const anime = Media(
        title: 'Anime',
        type: MediaType.anime,
        source: SourceMediaRef(sourceId: SourceId.local, itemId: 'anime'),
      );
      const manga = Media(
        title: 'Manga',
        type: MediaType.manga,
        source: SourceMediaRef(sourceId: SourceId.local, itemId: 'manga'),
      );
      final library = _ControlledLibrary();
      final model = HomeViewModel(library);
      addTearDown(library.controller.close);
      addTearDown(model.dispose);

      library.controller.add([
        LibraryEntry(media: anime, addedAt: DateTime.utc(2026)),
        LibraryEntry(media: manga, addedAt: DateTime.utc(2026)),
      ]);
      await Future<void>.delayed(Duration.zero);
      model.selectFilter(HomeFilterType.manga);
      expect(model.state.selectedFilter, HomeFilterType.manga);
      expect(model.effectiveFilter, HomeFilterType.manga);
      expect(model.visibleLibraryItems, [manga]);

      library.controller.add([
        LibraryEntry(media: anime, addedAt: DateTime.utc(2026)),
      ]);
      await Future<void>.delayed(Duration.zero);
      expect(model.state.selectedFilter, HomeFilterType.manga);
      expect(model.effectiveFilter, HomeFilterType.all);
      expect(model.visibleLibraryItems, [anime]);

      library.controller.add([
        LibraryEntry(media: anime, addedAt: DateTime.utc(2026)),
        LibraryEntry(media: manga, addedAt: DateTime.utc(2026)),
      ]);
      await Future<void>.delayed(Duration.zero);
      expect(model.effectiveFilter, HomeFilterType.manga);
      expect(model.visibleLibraryItems, [manga]);
    },
  );

  test('progress failures preserve Library and valid Continue items', () async {
    const valid = Media(
      title: 'Valid progress',
      type: MediaType.manga,
      source: SourceMediaRef(sourceId: SourceId.local, itemId: 'valid'),
    );
    const broken = Media(
      title: 'Broken progress',
      type: MediaType.anime,
      source: SourceMediaRef(sourceId: SourceId.local, itemId: 'broken'),
    );
    final library = _ObservableLibrary([valid, broken]);
    final progress = _Progress(
      {
        valid.source: _record(
          valid.source,
          updatedAt: 1,
          position: TextPosition(progression: .4),
        ),
      },
      failing: {broken.source},
    );
    final model = HomeViewModel(library, progressRepository: progress);
    addTearDown(model.dispose);

    await Future<void>.delayed(Duration.zero);
    expect(model.state.libraryItems.map((media) => media.title), [
      'Valid progress',
      'Broken progress',
    ]);
    expect(model.state.continueItems.map((item) => item.media.title), [
      'Valid progress',
    ]);
    expect(model.state.progressError, isA<StateError>());
    expect(model.state.error, isNull);
  });

  test(
    'Home Continue joins saved media to recent incomplete progress',
    () async {
      const first = Media(
        title: 'Old',
        type: MediaType.anime,
        source: SourceMediaRef(sourceId: SourceId.local, itemId: 'old'),
      );
      const second = Media(
        title: 'Recent',
        type: MediaType.manga,
        source: SourceMediaRef(sourceId: SourceId.local, itemId: 'recent'),
      );
      const completed = Media(
        title: 'Done',
        type: MediaType.anime,
        source: SourceMediaRef(sourceId: SourceId.local, itemId: 'done'),
      );
      final library = _Library([first, second, completed]);
      final progress = _Progress({
        first.source: _record(
          first.source,
          updatedAt: 1,
          position: VideoPosition(
            position: const Duration(seconds: 20),
            duration: const Duration(seconds: 100),
          ),
        ),
        second.source: _record(
          second.source,
          updatedAt: 2,
          position: PagePosition(pageIndex: 4, pageCount: 10),
        ),
        completed.source: _record(
          completed.source,
          updatedAt: 3,
          position: TextPosition(progression: 1),
          completed: true,
        ),
        const SourceMediaRef(
          sourceId: SourceId.local,
          itemId: 'not-saved',
        ): _record(
          const SourceMediaRef(sourceId: SourceId.local, itemId: 'not-saved'),
          updatedAt: 4,
          position: TextPosition(progression: .5),
        ),
      });
      final model = HomeViewModel(library, progressRepository: progress);
      await Future<void>.delayed(Duration.zero);
      expect(model.state.continueItems.map((item) => item.media.title), [
        'Recent',
        'Old',
      ]);
      expect(model.state.continueItems.last.progress, .2);
      model.dispose();
    },
  );
}

MediaProgress _record(
  SourceMediaRef ref, {
  required int updatedAt,
  required ProgressPosition position,
  bool completed = false,
}) => MediaProgress(
  media: ref,
  position: position,
  completed: completed,
  updatedAt: DateTime.utc(2026, 1, updatedAt),
);

final class _ObservableLibrary extends _Library
    implements ObservableLibraryRepository {
  _ObservableLibrary(super.media);

  @override
  Stream<List<LibraryEntry>> watchAll() => Stream.value(
    media
        .map((item) => LibraryEntry(media: item, addedAt: DateTime.utc(2026)))
        .toList(),
  );
}

class _Library implements LibraryRepository {
  _Library(this.media);
  final List<Media> media;
  @override
  Future<List<LibraryEntry>> loadAll() async => media
      .map((item) => LibraryEntry(media: item, addedAt: DateTime.utc(2026)))
      .toList();
  @override
  Future<bool> contains(SourceMediaRef media) async => false;
  @override
  Future<void> remove(SourceMediaRef media) async {}
  @override
  Future<void> upsert(LibraryEntry entry) async {}
}

final class _ControlledLibrary extends _Library
    implements ObservableLibraryRepository {
  _ControlledLibrary() : super([]);
  final controller = StreamController<List<LibraryEntry>>();
  List<Media> snapshot = [];
  @override
  Stream<List<LibraryEntry>> watchAll() => controller.stream;
  @override
  Future<List<LibraryEntry>> loadAll() async => snapshot
      .map((media) => LibraryEntry(media: media, addedAt: DateTime.utc(2026)))
      .toList();
}

final class _ControlledProgress implements ProgressRepository {
  final pending = Completer<MediaProgress?>();
  @override
  Future<MediaProgress?> load(SourceMediaRef media) => media.itemId == 'old'
      ? pending.future
      : Future.value(
          _record(media, updatedAt: 2, position: TextPosition(progression: .5)),
        );
  @override
  Future<void> save(MediaProgress progress) async {}
  @override
  Future<void> delete(SourceMediaRef media) async {}
}

final class _Progress implements ProgressRepository {
  _Progress(this.records, {this.failing = const {}});
  final Map<SourceMediaRef, MediaProgress> records;
  final Set<SourceMediaRef> failing;
  @override
  Future<MediaProgress?> load(SourceMediaRef media) async {
    if (failing.contains(media)) throw StateError('progress unavailable');
    return records[media];
  }

  @override
  Future<void> save(MediaProgress progress) async {}
  @override
  Future<void> delete(SourceMediaRef media) async {}
}

final class _CatalogProvider implements CatalogProvider {
  _CatalogProvider({required this.onDiscover});

  final Future<CatalogDiscovery> Function() onDiscover;

  @override
  String get id => 'test';

  @override
  Future<CatalogDiscovery> discover() => onDiscover();

  @override
  Future<List<CatalogEntry>> search(String query, {MediaType? type}) async =>
      const [];

  @override
  Future<CatalogEntryDetails?> loadDetails(CatalogEntryId id) async => null;

  @override
  Future<void> close() async {}
}
