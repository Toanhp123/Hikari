import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/features/library/library_page.dart';
import 'package:hikari/features/local_media/local_media_page.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:hikari/infrastructure/repositories/sqlite_library_repository.dart';

void main() {
  const media = Media(
    title: 'Saved book',
    type: MediaType.lightNovel,
    source: SourceMediaRef(sourceId: SourceId.local, itemId: 'book'),
  );
  late UserDatabase db;
  late SqliteLibraryRepository library;
  setUp(() {
    db = UserDatabase(NativeDatabase.memory());
    library = SqliteLibraryRepository(db);
  });
  tearDown(() => db.close());
  testWidgets(
    'library empty state and persisted snapshots open without scan then remove',
    (tester) async {
      Media? opened;
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(),
          home: LibraryPage(
            repository: library,
            openMedia: (_, item) {
              opened = item;
            },
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text('Your library is empty.'), findsOneWidget);
      await library.upsert(
        LibraryEntry(media: media, addedAt: DateTime.utc(2026)),
      );
      await tester.pumpWidget(const SizedBox());
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(),
          home: LibraryPage(
            repository: library,
            openMedia: (_, item) {
              opened = item;
            },
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Saved book'));
      expect(opened!.source, media.source);
      await tester.tap(find.byTooltip('Remove from library'));
      await tester.pumpAndSettle();
      await tester.pumpAndSettle();
      expect(await library.contains(media.source), isFalse);
      await tester.pumpAndSettle();
      expect(find.text('Your library is empty.'), findsOneWidget);

      // LibraryPage owns a live Drift watch; dispose it before closing the
      // in-memory database in tearDown.
      await tester.pumpWidget(const SizedBox());
      await tester.pumpAndSettle();
    },
  );
  testWidgets('scan result toggles persisted library membership', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: LocalMediaPage(
          library: library,
          scanSelectedRoot: () async => [media],
          chooseRoot: () async => false,
          openMedia: (_, _) {},
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Add to library'));
    await tester.pumpAndSettle();
    expect(await library.contains(media.source), isTrue);
    await tester.tap(find.byTooltip('Remove from library'));
    await tester.pumpAndSettle();
    expect(await library.contains(media.source), isFalse);
    await tester.pumpWidget(const SizedBox());
    await tester.pumpAndSettle();
  });

  testWidgets('reactive repository updates an already mounted LibraryPage', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: LibraryPage(repository: library, openMedia: (_, _) {}),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Your library is empty.'), findsOneWidget);

    await library.upsert(
      LibraryEntry(media: media, addedAt: DateTime.utc(2026)),
    );
    await tester.pumpAndSettle();

    expect(find.text('Saved book'), findsOneWidget);

    // Dispose the page before the database tearDown closes Drift so the
    // reactive watch subscription is cancelled deterministically.
    await tester.pumpWidget(const SizedBox());
    await tester.pumpAndSettle();
  });
}
