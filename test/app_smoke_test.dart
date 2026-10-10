import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:drift/native.dart';
import 'package:flutter/services.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/progress/progress.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';
import 'package:hikari/infrastructure/repositories/sqlite_library_repository.dart';
import 'package:hikari/infrastructure/repositories/sqlite_progress_repository.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/app.dart';
import 'package:hikari/app/app_dependencies.dart';
import 'package:hikari/features/manga_reader/manga_reader_page.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const channel = MethodChannel('hikari/local_media');
  final messenger =
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;

  tearDown(() => messenger.setMockMethodCallHandler(channel, null));

  testWidgets(
    'persisted Library opens text through shared route without scan results',
    (tester) async {
      final db = UserDatabase(NativeDatabase.memory());
      const ref = SourceMediaRef(
        sourceId: SourceId.local,
        itemId: 'persisted-book',
      );
      await SqliteLibraryRepository(db).upsert(
        LibraryEntry(
          media: const Media(
            title: 'Persisted book',
            type: MediaType.lightNovel,
            source: ref,
          ),
          addedAt: DateTime.utc(2026),
        ),
      );
      await SqliteProgressRepository(db).save(
        MediaProgress(
          media: ref,
          position: TextPosition(progression: 0.6),
          completed: false,
          updatedAt: DateTime.utc(2026),
        ),
      );
      final calls = <String>[];
      messenger.setMockMethodCallHandler(channel, (call) async {
        calls.add(call.method);
        expect(call.method, 'read');
        return Uint8List.fromList(
          utf8.encode(List.filled(150, 'Persisted text').join('\n')),
        );
      });
      final dependencies = AppDependencies.create(database: db);
      await tester.pumpWidget(HikariApp(dependencies: dependencies));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Library'));
      await tester.pumpAndSettle();
      await tester.tapAt(tester.getCenter(find.text('Persisted book')));
      await tester.pump();
      await tester.runAsync(
        () async => Future<void>.delayed(const Duration(milliseconds: 100)),
      );
      await tester.pumpAndSettle();
      final scroll = tester
          .widget<SingleChildScrollView>(find.byType(SingleChildScrollView))
          .controller!;
      expect(
        scroll.offset / scroll.position.maxScrollExtent,
        closeTo(0.6, 0.001),
      );
      expect(calls, ['read']);
      await tester.pumpWidget(const SizedBox());
      // Advance fake time so Drift's deferred stream disposal can finish.
      await tester.pump(Duration.zero);
      await dependencies.dispose();
      await db.close();
    },
  );

  testWidgets('persisted local manga opens direct reader route', (
    tester,
  ) async {
    final db = UserDatabase(NativeDatabase.memory());
    const ref = SourceMediaRef(sourceId: SourceId.local, itemId: 'folder');
    await SqliteLibraryRepository(db).upsert(
      LibraryEntry(
        media: const Media(
          title: 'Saved manga',
          type: MediaType.manga,
          source: ref,
        ),
        addedAt: DateTime.utc(2026),
      ),
    );
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'children');
      return <Object?>[];
    });
    final dependencies = AppDependencies.create(database: db);
    await tester.pumpWidget(HikariApp(dependencies: dependencies));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Library'));
    await tester.pumpAndSettle();
    await tester.tapAt(tester.getCenter(find.text('Saved manga')));
    await tester.pumpAndSettle();
    expect(find.byType(MangaReaderPage), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
    // Advance fake time so Drift's deferred stream disposal can finish.
    await tester.pump(Duration.zero);
    await dependencies.dispose();
    await db.close();
  });

  testWidgets('Settings and Local root changes refresh cached Local', (
    tester,
  ) async {
    final db = UserDatabase(NativeDatabase.memory());
    var root = 0;
    var cancel = false;
    var picks = 0;
    var scans = 0;
    messenger.setMockMethodCallHandler(channel, (call) async {
      switch (call.method) {
        case 'pickTree':
          picks++;
          if (cancel) return null;
          root++;
          return {'id': 'root-$root', 'name': 'Root'};
        case 'selectedTree':
          scans++;
          return {'id': 'root-$root', 'name': 'Root'};
        case 'children':
          return [
            {
              'id': 'book-$root',
              'name': 'Book $root.txt',
              'isDirectory': false,
            },
          ];
        case 'read':
          return Uint8List.fromList(utf8.encode('Local reading content'));
      }
      throw StateError(call.method);
    });
    final dependencies = AppDependencies.create(database: db);
    await tester.pumpWidget(HikariApp(dependencies: dependencies));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Local'));
    await tester.pump();
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 200)),
    );
    await tester.pumpAndSettle();
    expect(find.text('Book 0'), findsOneWidget);
    expect(picks, 0);
    await tester.tap(find.byTooltip('Settings'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Local Media Folder'));
    await tester.pump();
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 200)),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Local'));
    await tester.pumpAndSettle();
    expect(find.text('Book 1'), findsOneWidget);
    expect(find.text('Book 0'), findsNothing);
    await tester.tap(find.text('Choose folder'));
    await tester.pump();
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 200)),
    );
    await tester.pumpAndSettle();
    expect(find.text('Book 2'), findsOneWidget);
    expect(scans, 3); // One scan per Local root selection.
    await tester.tap(find.byTooltip('Add to library'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Library'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Remove from library'));
    await tester.pumpAndSettle();
    await tester.tap(find.byTooltip('Local'));
    await tester.pumpAndSettle();
    expect(find.byTooltip('Add to library'), findsOneWidget);
    expect(find.byTooltip('Remove from library'), findsNothing);
    expect(scans, 3); // Membership updates do not rescan the folder.
    cancel = true;
    await tester.tap(find.text('Choose folder'));
    await tester.pump();
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 200)),
    );
    await tester.pumpAndSettle();
    expect(find.text('Book 2'), findsOneWidget);
    await tester.tap(find.byTooltip('Local'));
    await tester.pumpAndSettle();
    await tester.tapAt(tester.getCenter(find.text('Book 2')));
    await tester.pump();
    await tester.runAsync(
      () => Future<void>.delayed(const Duration(milliseconds: 200)),
    );
    await tester.pumpAndSettle();
    expect(find.text('Local reading content'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
    await tester.pump(Duration.zero);
    await dependencies.dispose();
    await db.close();
  });

  testWidgets('boots the Hikari app', (tester) async {
    messenger.setMockMethodCallHandler(channel, (call) async {
      expect(call.method, 'selectedTree');
      return null;
    });

    final db = UserDatabase(NativeDatabase.memory());
    final dependencies = AppDependencies.create(
      database: db,
      ownsDatabase: true,
    );
    await tester.pumpWidget(HikariApp(dependencies: dependencies));
    await tester.pumpAndSettle();

    expect(find.text('Local media'), findsNothing);
    expect(find.text('Open Local'), findsNothing);
    await tester.tap(find.widgetWithText(IconButton, 'Search'));
    await tester.pumpAndSettle();
    expect(
      find.text('Search the catalog, then choose a source from details.'),
      findsOneWidget,
    );
    await tester.tap(find.byTooltip('Local'));
    await tester.pumpAndSettle();
    expect(find.text('Choose a folder to find local media.'), findsOneWidget);
    expect(find.text('Choose folder'), findsOneWidget);

    await tester.pumpWidget(const SizedBox());
    // Advance fake time so Drift's deferred stream disposal can finish.
    await tester.pump(Duration.zero);
    await dependencies.dispose();
  });
}
