import 'dart:io';

import 'package:drift/native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:hikari/infrastructure/repositories/user_state_repositories.dart';

void main() {
  const ref = SourceMediaRef(sourceId: SourceId.local, itemId: 'item');
  final now = DateTime.utc(2026, 9, 26);
  late UserDatabase db;
  late SqliteProgressRepository progress;
  late SqliteLibraryRepository library;
  setUp(() {
    db = UserDatabase(NativeDatabase.memory());
    progress = SqliteProgressRepository(db);
    library = SqliteLibraryRepository(db);
  });
  tearDown(() => db.close());
  MediaProgress record(
    ProgressPosition position, {
    SourceMediaRef media = ref,
    bool completed = false,
  }) => MediaProgress(
    media: media,
    position: position,
    completed: completed,
    updatedAt: now,
  );
  LibraryEntry entry({String title = 'Snapshot'}) => LibraryEntry(
    media: Media(title: title, type: MediaType.manga, source: ref),
    addedAt: now,
  );
  test('all position kinds roundtrip; upsert replaces payload and never infers completion', () async {
    expect(await progress.load(ref), isNull);
    await progress.save(
      record(
        VideoPosition(
          position: const Duration(seconds: 40),
          duration: const Duration(seconds: 60),
        ),
      ),
    );
    final video = (await progress.load(ref))!;
    expect(
      (video.position as VideoPosition).position,
      const Duration(seconds: 40),
    );
    expect(video.updatedAt, now);
    await progress.save(
      record(PagePosition(pageIndex: 2, pageCount: 3), completed: true),
    );
    final page = (await progress.load(ref))!;
    expect((page.position as PagePosition).pageIndex, 2);
    expect(page.completed, isTrue);
    await progress.save(record(TextPosition(progression: 1)));
    final text = (await progress.load(ref))!;
    expect((text.position as TextPosition).progression, 1);
    expect(text.completed, isFalse);
  });
  test('remote series snapshot survives close and reopen', () async {
    await db.close();
    final directory = await Directory.systemTemp.createTemp('hikari-remote-');
    final file = File('${directory.path}/state.sqlite');
    final first = UserDatabase(NativeDatabase.createInBackground(file));
    const series = SourceMediaRef(
      sourceId: SourceId('mangadex'),
      itemId: 'series-id',
    );
    try {
      await SqliteLibraryRepository(first).upsert(
        LibraryEntry(
          media: const Media(
            title: 'Remote Series',
            type: MediaType.manga,
            source: series,
          ),
          addedAt: now,
        ),
      );
    } finally {
      await first.close();
    }

    final second = UserDatabase(NativeDatabase.createInBackground(file));
    try {
      final restored = (await SqliteLibraryRepository(second).loadAll()).single;
      expect(restored.media.title, 'Remote Series');
      expect(restored.media.source, series);
      expect(restored.media.type, MediaType.manga);
    } finally {
      await second.close();
      await directory.delete(recursive: true);
    }
  });

  test('removing remote series keeps chapter progress', () async {
    const series = SourceMediaRef(
      sourceId: SourceId('mangadex'),
      itemId: 'series-id',
    );
    const chapter = SourceMediaRef(
      sourceId: SourceId('mangadex'),
      itemId: 'chapter-id',
    );
    await library.upsert(
      LibraryEntry(
        media: const Media(
          title: 'Remote Series',
          type: MediaType.manga,
          source: series,
        ),
        addedAt: now,
      ),
    );
    await progress.save(
      record(PagePosition(pageIndex: 1, pageCount: 3), media: chapter),
    );

    await library.remove(series);

    expect(await library.contains(series), isFalse);
    final saved = await progress.load(chapter);
    expect(saved, isNotNull);
    expect((saved!.position as PagePosition).pageIndex, 1);
    expect((saved.position as PagePosition).pageCount, 3);
  });

  test(
    'source keys do not collide; library operations independent from progress',
    () async {
      const other = SourceMediaRef(sourceId: SourceId('other'), itemId: 'item');
      await progress.save(record(TextPosition(progression: 0.2)));
      await progress.save(record(TextPosition(progression: 0.8), media: other));
      expect(await library.contains(ref), isFalse);
      await library.upsert(entry());
      await library.upsert(entry(title: 'Updated'));
      expect((await library.loadAll()).single.media.title, 'Updated');
      expect(await library.contains(ref), isTrue);
      await library.remove(ref);
      expect(await library.loadAll(), isEmpty);
      expect(await progress.load(ref), isNotNull);
      await library.upsert(entry());
      await progress.delete(ref);
      expect(await library.contains(ref), isTrue);
      expect(await progress.load(ref), isNull);
      expect((await progress.load(other))!.media, other);
    },
  );
  test(
    'concurrent writes preserve invocation order and deletion is independent',
    () async {
      await Future.wait([
        for (var i = 0; i < 20; i++)
          progress.save(record(TextPosition(progression: i / 20))),
      ]);
      expect(
        ((await progress.load(ref))!.position as TextPosition).progression,
        0.95,
      );
      await library.upsert(
        LibraryEntry(
          media: const Media(
            title: 'Other',
            type: MediaType.anime,
            source: SourceMediaRef(sourceId: SourceId('other'), itemId: 'item'),
          ),
          addedAt: now,
        ),
      );
      await library.upsert(entry());
      await library.remove(ref);
      expect(
        (await library.loadAll()).single.media.source.sourceId,
        const SourceId('other'),
      );
      expect(await progress.load(ref), isNotNull);
    },
  );
  test('file database survives close and reopen', () async {
    await db.close();
    final directory = await Directory.systemTemp.createTemp('hikari-test-');
    final file = File('${directory.path}/state.sqlite');
    final first = UserDatabase(NativeDatabase.createInBackground(file));
    try {
      await SqliteLibraryRepository(first).upsert(entry());
      await SqliteProgressRepository(first)
          .save(record(TextPosition(progression: 0.6)));
    } finally {
      await first.close();
    }
    final second = UserDatabase(NativeDatabase.createInBackground(file));
    try {
      expect(
        (await SqliteLibraryRepository(second).loadAll()).single.media.source,
        ref,
      );
      expect(
        ((await SqliteProgressRepository(second).load(ref))!.position
                as TextPosition)
            .progression,
        0.6,
      );
    } finally {
      await second.close();
      await directory.delete(recursive: true);
    }
  });
  test(
    'stores stable media type values and restores every supported type',
    () async {
      for (final type in MediaType.values) {
        await library.upsert(
          LibraryEntry(
            media: Media(
              title: type.name,
              type: type,
              source: SourceMediaRef(
                sourceId: SourceId.local,
                itemId: type.name,
              ),
            ),
            addedAt: now,
          ),
        );
      }

      final stored = await db
          .customSelect(
            'SELECT item_id, media_type FROM library_records ORDER BY item_id',
          )
          .get();
      expect(stored.map((row) => row.data['media_type']), [
        'anime',
        'light_novel',
        'manga',
      ]);
      final restored = await library.loadAll();
      expect(
        {for (final entry in restored) entry.media.title: entry.media.type},
        {
          'anime': MediaType.anime,
          'manga': MediaType.manga,
          'lightNovel': MediaType.lightNovel,
        },
      );
    },
  );
  test(
    'invalid discriminators and inconsistent payloads fail clearly',
    () async {
      await progress.save(record(TextPosition(progression: 0.5)));
      for (final change in [
        "kind = 'unknown'",
        "text_progression = 2",
        "position_ms = 3",
        "text_progression = NULL",
        "completed = 7",
      ]) {
        await progress.save(record(TextPosition(progression: 0.5)));
        await db.customStatement('UPDATE progress_records SET $change');
        await expectLater(progress.load(ref), throwsA(isA<FormatException>()));
      }
      await library.upsert(entry());
      await db.customStatement(
        "UPDATE library_records SET media_type = 'unknown'",
      );
      await expectLater(library.loadAll(), throwsA(isA<FormatException>()));
    },
  );
}
