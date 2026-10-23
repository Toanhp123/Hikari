import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/features/library/library_button_view_model.dart';
import 'package:hikari/features/library/widgets/library_button.dart';

void main() {
  test('LibraryButtonViewModel owns explicit membership operations', () async {
    const media = Media(
      title: 'Saved manga',
      type: MediaType.manga,
      source: SourceMediaRef(sourceId: SourceId.local, itemId: 'saved'),
    );
    final repository = _LibraryRepository(saved: true);
    final model = LibraryButtonViewModel(repository, media);
    addTearDown(model.dispose);

    await Future<void>.delayed(Duration.zero);
    expect(model.state.saved, isTrue);

    expect(await model.remove(), isTrue);
    expect(model.state.saved, isFalse);
    expect(repository.removed, media.source);
    expect(await model.add(), isTrue);
    expect(model.state.saved, isTrue);
    expect(repository.saved, isTrue);
  });

  test('confirmed removal never re-adds an externally removed title', () async {
    const media = Media(
      title: 'Saved manga',
      type: MediaType.manga,
      source: SourceMediaRef(sourceId: SourceId.local, itemId: 'saved'),
    );
    final repository = _LibraryRepository(saved: true);
    final model = LibraryButtonViewModel(repository, media);
    addTearDown(model.dispose);
    await Future<void>.delayed(Duration.zero);

    // Another Library surface removed it while confirmation was showing.
    repository.saved = false;
    expect(await model.remove(), isTrue);
    expect(repository.removed, isNull);
    expect(model.state.saved, isFalse);
  });

  test('explicit add never removes a title saved by another surface', () async {
    const media = Media(
      title: 'Saved manga',
      type: MediaType.manga,
      source: SourceMediaRef(sourceId: SourceId.local, itemId: 'saved'),
    );
    final repository = _LibraryRepository(saved: false);
    final model = LibraryButtonViewModel(repository, media);
    addTearDown(model.dispose);
    await Future<void>.delayed(Duration.zero);

    repository.saved = true;
    expect(await model.add(), isTrue);
    expect(repository.removed, isNull);
    expect(repository.saved, isTrue);
  });

  testWidgets('LibraryButton confirms both explicit membership commands', (
    tester,
  ) async {
    const media = Media(
      title: 'Saved manga',
      type: MediaType.manga,
      source: SourceMediaRef(sourceId: SourceId.local, itemId: 'saved'),
    );
    final repository = _LibraryRepository(saved: false);
    var changes = 0;
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: LibraryButton(
            repository: repository,
            media: media,
            onChanged: () => changes++,
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Add to library'));
    await tester.pumpAndSettle();
    expect(find.text('Add to Library?'), findsOneWidget);
    expect(repository.saved, isFalse);
    expect(changes, 0);
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(repository.saved, isFalse);
    await tester.tap(find.byTooltip('Add to library'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Add'));
    await tester.pumpAndSettle();
    expect(repository.saved, isTrue);
    expect(changes, 1);

    await tester.tap(find.byTooltip('Remove from library'));
    await tester.pumpAndSettle();
    expect(find.text('Remove from Library?'), findsOneWidget);
    expect(find.textContaining('Saved manga'), findsOneWidget);
    expect(
      tester
          .widget<IconButton>(
            find.descendant(
              of: find.byType(LibraryButton),
              matching: find.byType(IconButton),
            ),
          )
          .onPressed,
      isNull,
    );
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    expect(repository.saved, isTrue);
    expect(repository.removed, isNull);
    expect(changes, 1);

    await tester.tap(find.byTooltip('Remove from library'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Remove'));
    await tester.pumpAndSettle();
    expect(repository.saved, isFalse);
    expect(repository.removed, media.source);
    expect(changes, 2);
  });

  for (final saved in [false, true]) {
    testWidgets('membership $saved dismissal never mutates or loses focus', (
      tester,
    ) async {
      const media = Media(
        title: 'Saved manga',
        type: MediaType.manga,
        source: SourceMediaRef(sourceId: SourceId.local, itemId: 'saved'),
      );
      final repository = _LibraryRepository(saved: saved);
      var changes = 0;
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: LibraryButton(
              repository: repository,
              media: media,
              onChanged: () => changes++,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      final button = find.byTooltip(
        saved ? 'Remove from library' : 'Add to library',
      );
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pumpAndSettle();
      final originFocus = FocusManager.instance.primaryFocus;
      expect(
        originFocus?.context?.findAncestorWidgetOfExactType<IconButton>(),
        isNotNull,
      );
      for (final dismissal in ['Cancel', 'Back', 'Escape', 'barrier']) {
        await tester.tap(button);
        await tester.pumpAndSettle();
        final cancel = find.widgetWithText(TextButton, 'Cancel');
        expect(
          FocusManager.instance.primaryFocus?.context
              ?.findAncestorWidgetOfExactType<TextButton>()
              ?.autofocus,
          isTrue,
        );
        switch (dismissal) {
          case 'Cancel':
            await tester.tap(cancel);
          case 'Back':
            await tester.binding.handlePopRoute();
          case 'Escape':
            await tester.sendKeyEvent(LogicalKeyboardKey.escape);
          case 'barrier':
            await tester.tapAt(const Offset(5, 5));
        }
        await tester.pumpAndSettle();
        expect(find.byType(AlertDialog), findsNothing);
        expect(repository.saved, saved);
        expect(repository.removed, isNull);
        expect(changes, 0);
        expect(FocusManager.instance.primaryFocus, same(originFocus));
      }
    });
  }

  testWidgets(
    'confirmed button removal does not toggle after external removal',
    (tester) async {
      const media = Media(
        title: 'Saved manga',
        type: MediaType.manga,
        source: SourceMediaRef(sourceId: SourceId.local, itemId: 'saved'),
      );
      final repository = _LibraryRepository(saved: true);
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: LibraryButton(repository: repository, media: media),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Remove from library'));
      await tester.pumpAndSettle();
      repository.saved = false;
      await tester.tap(find.text('Remove'));
      await tester.pumpAndSettle();
      expect(repository.removed, isNull);
      expect(repository.saved, isFalse);
      expect(find.byTooltip('Add to library'), findsOneWidget);
    },
  );

  for (final saved in [false, true]) {
    testWidgets(
      'replaced LibraryButton ignores $saved confirmation for previous item',
      (tester) async {
        const first = Media(
          title: 'First manga',
          type: MediaType.manga,
          source: SourceMediaRef(sourceId: SourceId.local, itemId: 'first'),
        );
        const second = Media(
          title: 'Second manga',
          type: MediaType.manga,
          source: SourceMediaRef(sourceId: SourceId.local, itemId: 'second'),
        );
        final repository = _LibraryRepository(saved: saved);
        final selected = ValueNotifier<Media>(first);
        addTearDown(selected.dispose);
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: ValueListenableBuilder<Media>(
                valueListenable: selected,
                builder: (_, media, _) =>
                    LibraryButton(repository: repository, media: media),
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        await tester.tap(
          find.byTooltip(saved ? 'Remove from library' : 'Add to library'),
        );
        await tester.pumpAndSettle();
        selected.value = second;
        await tester.pumpAndSettle();
        await tester.tap(find.text(saved ? 'Remove' : 'Add'));
        await tester.pumpAndSettle();
        expect(repository.saved, saved);
        expect(repository.removed, isNull);
      },
    );
  }
}

final class _LibraryRepository implements LibraryRepository {
  _LibraryRepository({required this.saved});

  bool saved;
  SourceMediaRef? removed;

  @override
  Future<bool> contains(SourceMediaRef media) async => saved;

  @override
  Future<List<LibraryEntry>> loadAll() async => const [];

  @override
  Future<void> remove(SourceMediaRef media) async {
    removed = media;
    saved = false;
  }

  @override
  Future<void> upsert(LibraryEntry entry) async {
    saved = true;
  }
}
