import 'dart:io';

import 'package:drift/native.dart';
import 'package:drift_dev/api/migrations_native.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:hikari/infrastructure/repositories/sqlite_library_repository.dart';
import 'package:hikari/infrastructure/repositories/sqlite_progress_repository.dart';

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
  test('version one database migrates rows without resetting them', () async {
    await db.close();
    final directory = await Directory.systemTemp.createTemp('hikari-v1-');
    final file = File('${directory.path}/state.sqlite');
    final oldRefs = [
      const SourceMediaRef(sourceId: SourceId.local, itemId: 'video'),
      const SourceMediaRef(sourceId: SourceId.local, itemId: 'page'),
      const SourceMediaRef(sourceId: SourceId.local, itemId: 'text'),
    ];
    final oldDatabase = UserDatabase(
      NativeDatabase(
        file,
        setup: (database) {
          database.execute('''
            CREATE TABLE progress_records (
              source_id TEXT NOT NULL,
              item_id TEXT NOT NULL,
              kind TEXT NOT NULL,
              position_ms INTEGER NULL,
              duration_ms INTEGER NULL,
              page_index INTEGER NULL,
              page_count INTEGER NULL,
              text_progression REAL NULL,
              completed INTEGER NOT NULL,
              updated_at INTEGER NOT NULL,
              PRIMARY KEY (source_id, item_id)
            )
          ''');
          database.execute('''
            CREATE TABLE library_records (
              source_id TEXT NOT NULL,
              item_id TEXT NOT NULL,
              title TEXT NOT NULL,
              media_type TEXT NOT NULL,
              added_at INTEGER NOT NULL,
              PRIMARY KEY (source_id, item_id)
            )
          ''');
          database.execute("""
            INSERT INTO progress_records
              (source_id, item_id, kind, position_ms, duration_ms, completed, updated_at)
            VALUES ('local', 'video', 'video', 10, 20, 0, 100)
          """);
          database.execute("""
            INSERT INTO progress_records
              (source_id, item_id, kind, page_index, page_count, completed, updated_at)
            VALUES ('local', 'page', 'page', 1, 3, 1, 101)
          """);
          database.execute("""
            INSERT INTO progress_records
              (source_id, item_id, kind, text_progression, completed, updated_at)
            VALUES ('local', 'text', 'text', 0.5, 0, 102)
          """);
          database.execute("""
            INSERT INTO library_records
              (source_id, item_id, title, media_type, added_at)
            VALUES ('local', 'old-library', 'Old row', 'manga', 103)
          """);
          database.execute('PRAGMA user_version = 1');
        },
      ),
    );
    try {
      final oldProgress = SqliteProgressRepository(oldDatabase);
      final video = (await oldProgress.load(oldRefs[0]))!;
      final page = (await oldProgress.load(oldRefs[1]))!;
      final text = (await oldProgress.load(oldRefs[2]))!;
      expect(
        (video.position as VideoPosition).position,
        const Duration(milliseconds: 10),
      );
      expect(
        (video.position as VideoPosition).duration,
        const Duration(milliseconds: 20),
      );
      expect(video.completed, isFalse);
      expect(
        video.updatedAt,
        DateTime.fromMillisecondsSinceEpoch(100, isUtc: true),
      );
      expect((page.position as PagePosition).pageIndex, 1);
      expect((page.position as PagePosition).pageCount, 3);
      expect(page.completed, isTrue);
      expect((text.position as TextPosition).progression, 0.5);
      expect(text.completed, isFalse);
      final oldLibrary = await SqliteLibraryRepository(oldDatabase).loadAll();
      expect(oldLibrary.single.media.title, 'Old row');
      expect(oldLibrary.single.media.type, MediaType.manga);
      expect(
        oldLibrary.single.addedAt,
        DateTime.fromMillisecondsSinceEpoch(103, isUtc: true),
      );
      expect(oldLibrary.single.media.source.itemId, 'old-library');
      final columns = await oldDatabase
          .customSelect('PRAGMA table_info(progress_records)')
          .get();
      expect(
        columns.map((row) => row.data['name']),
        contains('document_resource'),
      );
      await oldDatabase.validateDatabaseSchema();
    } finally {
      await oldDatabase.close();
      await directory.delete(recursive: true);
    }
  });

  test('document position roundtrips after file reopen', () async {
    await db.close();
    final directory = await Directory.systemTemp.createTemp('hikari-document-');
    final file = File('${directory.path}/state.sqlite');
    final first = UserDatabase(NativeDatabase.createInBackground(file));
    const document = SourceMediaRef(sourceId: SourceId.local, itemId: 'doc');
    try {
      await SqliteProgressRepository(first).save(
        MediaProgress(
          media: document,
          position: DocumentPosition(
            resource: 'chapter.xhtml',
            progression: 0.25,
            totalProgression: 1,
            locator: 'opaque-locator',
          ),
          completed: false,
          updatedAt: now,
        ),
      );
    } finally {
      await first.close();
    }
    final second = UserDatabase(NativeDatabase.createInBackground(file));
    try {
      final restored = await SqliteProgressRepository(second).load(document);
      expect(restored!.position, isA<DocumentPosition>());
      final position = restored.position as DocumentPosition;
      expect(position.resource, 'chapter.xhtml');
      expect(position.progression, 0.25);
      expect(position.totalProgression, 1);
      expect(position.locator, 'opaque-locator');
    } finally {
      await second.close();
      await directory.delete(recursive: true);
    }
  });

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
      sourceId: SourceId('remote'),
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
      sourceId: SourceId('remote'),
      itemId: 'series-id',
    );
    const chapter = SourceMediaRef(
      sourceId: SourceId('remote'),
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
  test('document rows reject irrelevant or malformed payloads', () async {
    await progress.save(record(DocumentPosition(resource: 'chapter')));
    await db.customStatement('UPDATE progress_records SET page_index = 1');
    await expectLater(progress.load(ref), throwsA(isA<FormatException>()));
    await progress.save(record(DocumentPosition(resource: 'chapter')));
    await db.customStatement(
      'UPDATE progress_records SET document_progression = 2',
    );
    await expectLater(progress.load(ref), throwsA(isA<FormatException>()));
  });

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
