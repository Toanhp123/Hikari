import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/features/library/library_page.dart';
import 'package:hikari/features/local_media/local_media_page.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:hikari/infrastructure/repositories/user_state_repositories.dart';

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
    },
  );
  testWidgets('scan refreshes membership after returning from Library', (
    tester,
  ) async {
    await library.upsert(
      LibraryEntry(media: media, addedAt: DateTime.utc(2026)),
    );
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) => LocalMediaPage(
            library: library,
            scanSelectedRoot: () async => [media],
            chooseRoot: () async => false,
            openMedia: (_, _) {},
            openLibrary: () async {
              await Navigator.of(context).push(
                MaterialPageRoute<void>(
                  builder: (_) =>
                      LibraryPage(repository: library, openMedia: (_, _) {}),
                ),
              );
            },
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Library'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Remove from library'));
    await tester.pumpAndSettle();
    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(find.byTooltip('Add to library'), findsOneWidget);
    expect(find.byTooltip('Remove from library'), findsNothing);
  });
  testWidgets('scan result toggles persisted library membership', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
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
  });
}
