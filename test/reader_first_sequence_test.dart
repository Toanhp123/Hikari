import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/application/media/open_manga_chapter.dart';
import 'package:hikari/application/media/open_novel_chapter.dart';
import 'package:hikari/application/media/read_novel_chapter_content.dart';
import 'package:hikari/application/sources/source_registry.dart';
import 'package:hikari/core/cache/byte_cache.dart';
import 'package:hikari/domain/media/manga.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/features/remote_manga/manga_chapter_reader_view_model.dart';
import 'package:hikari/features/remote_novel/novel_chapter_reader_view_model.dart';

const _id = SourceId('remote');
const _a = SourceMediaRef(sourceId: _id, itemId: 'a');
const _b = SourceMediaRef(sourceId: _id, itemId: 'b');
const _c = SourceMediaRef(sourceId: _id, itemId: 'c');
const _foreign = SourceMediaRef(sourceId: SourceId('elsewhere'), itemId: 'x');

final class _Manga implements MangaPageSource {
  @override
  SourceId get id => _id;
  @override
  String get name => 'Remote';
  @override
  Future<List<SourceMediaRef>> pages(SourceMediaRef chapter) async => [
    SourceMediaRef(sourceId: _id, itemId: '${chapter.itemId}-page'),
  ];
  @override
  Future<Uint8List> readPage(SourceMediaRef page) async => Uint8List(0);
}

final class _Novel implements NovelChapterSource {
  @override
  SourceId get id => _id;
  @override
  String get name => 'Remote';
  @override
  Future<RichReadingContent> chapterContent(SourceMediaRef chapter) async =>
      RichReadingContent(html: '<p>Cached</p>');
  @override
  Future<Uint8List> readResource(SourceMediaRef resource) async => Uint8List(0);
}

final class _Progress implements ProgressRepository {
  @override
  Future<MediaProgress?> load(SourceMediaRef ref) async => null;
  @override
  Future<void> save(MediaProgress progress) async {}
  @override
  Future<void> delete(SourceMediaRef ref) async {}
}

final class _Cache implements ByteCache {
  @override
  Future<Uint8List?> read(String namespace, String key) async => null;
  @override
  Future<void> write(String namespace, String key, Uint8List bytes) async {}
  @override
  Future<void> close() async {}
}

void main() {
  test(
    'Manga sequence reconciles without replacing the active chapter target',
    () async {
      final opener = OpenMangaChapter(SourceRegistry([_Manga()]), _Progress());
      final opened = await opener.execute(
        MangaChapter(title: 'Saved', source: _b),
      );
      final vm = MangaChapterReaderViewModel(
        initialTarget: opened,
        chaptersInReadingOrder: [MangaChapter(title: 'Saved', source: _b)],
        openChapter: opener,
      );
      addTearDown(vm.dispose);
      expect(vm.canOpenPrevious, isFalse);
      expect(vm.canOpenNext, isFalse);
      final previous = vm.state.target;
      expect(
        vm.reconcileSequence([
          MangaChapter(title: 'First', source: _a),
          MangaChapter(title: 'Updated', source: _b),
          MangaChapter(title: 'Last', source: _c),
        ]),
        isTrue,
      );
      expect(identical(previous, vm.state.target), isTrue);
      expect(vm.currentChapter.title, 'Updated');
      expect(vm.state.chapterIndex, 1);
      expect(vm.canOpenPrevious, isTrue);
      expect(vm.canOpenNext, isTrue);
      expect(
        vm.reconcileSequence([MangaChapter(title: 'Unrelated', source: _c)]),
        isFalse,
      );
      expect(vm.state.target.chapter.source, _b);
      expect(
        () => vm.reconcileSequence([
          MangaChapter(title: 'Foreign', source: _foreign),
        ]),
        throwsArgumentError,
      );
      vm.close();
      expect(
        vm.reconcileSequence([MangaChapter(title: 'Saved', source: _b)]),
        isFalse,
      );
    },
  );

  test(
    'Novel sequence update keeps text target and rejects stale refresh',
    () async {
      final opener = OpenNovelChapter(
        SourceRegistry([_Novel()]),
        _Progress(),
        ReadNovelChapterContent(_Cache()),
      );
      final opened = await opener.execute(
        NovelChapter(title: 'Saved', source: _b),
      );
      final vm = NovelChapterReaderViewModel(
        initialTarget: opened,
        chaptersInReadingOrder: [NovelChapter(title: 'Saved', source: _b)],
        openChapter: opener,
      );
      addTearDown(vm.dispose);
      final oldTarget = vm.state.target;
      expect(
        vm.reconcileSequence([
          NovelChapter(title: 'First', source: _a),
          NovelChapter(title: 'New name', source: _b),
          NovelChapter(title: 'Third', source: _c),
        ]),
        isTrue,
      );
      expect(identical(oldTarget, vm.state.target), isTrue);
      expect(vm.currentChapter.title, 'New name');
      expect(vm.canOpenNext, isTrue);
      await vm.openNext();
      expect(vm.state.target.chapter.source, _c);
      expect(
        vm.reconcileSequence([NovelChapter(title: 'Only old', source: _b)]),
        isFalse,
      );
      expect(vm.state.target.chapter.source, _c);
      vm.close();
    },
  );
}
