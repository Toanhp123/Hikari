import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_button.dart';
import 'package:hikari/core/ui/components/hikari_chip.dart';
import 'package:hikari/core/ui/components/hikari_icon_button.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/core/ui/components/hikari_search_bar.dart';
import 'package:hikari/core/ui/patterns/async_state_view.dart';
import 'package:hikari/core/ui/patterns/media_progress_bar.dart';

void main() {
  Widget testWrapper(Widget child) {
    return MaterialApp(
      theme: HikariTheme.darkTheme(),
      home: Scaffold(body: child),
    );
  }

  group('HikariButton', () {
    testWidgets('renders label and handles tap', (tester) async {
      var tapped = false;
      await tester.pumpWidget(
        testWrapper(
          HikariButton(label: 'Click Me', onPressed: () => tapped = true),
        ),
      );

      expect(find.text('Click Me'), findsOneWidget);
      await tester.tap(find.text('Click Me'));
      await tester.pumpAndSettle();
      expect(tapped, isTrue);
    });

    testWidgets('native action supports focus and keyboard activation', (
      tester,
    ) async {
      var activations = 0;
      await tester.pumpWidget(
        testWrapper(
          HikariButton(
            label: 'Keyboard Action',
            onPressed: () => activations++,
          ),
        ),
      );
      expect(find.byType(FilledButton), findsOneWidget);
      expect(
        tester.getSize(find.byType(FilledButton)).height,
        greaterThanOrEqualTo(48),
      );
      final focus = Focus.of(tester.element(find.text('Keyboard Action')));
      focus.requestFocus();
      await tester.pump();
      expect(focus.hasFocus, isTrue);
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();
      expect(activations, 1);
    });

    testWidgets('loading blocks native action while keeping its label', (
      tester,
    ) async {
      var activations = 0;
      await tester.pumpWidget(
        testWrapper(
          HikariButton(
            label: 'Retry',
            isLoading: true,
            onPressed: () => activations++,
          ),
        ),
      );
      expect(find.text('Retry'), findsOneWidget);
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(
        tester.widget<FilledButton>(find.byType(FilledButton)).onPressed,
        isNull,
      );
      expect(activations, 0);
    });

    testWidgets('shows loading indicator when isLoading is true', (
      tester,
    ) async {
      await tester.pumpWidget(
        testWrapper(
          HikariButton(label: 'Submit', isLoading: true, onPressed: () {}),
        ),
      );

      expect(find.byType(CircularProgressIndicator), findsOneWidget);
    });
  });

  group('HikariIconButton', () {
    testWidgets('renders icon and triggers onPressed', (tester) async {
      var tapped = false;
      await tester.pumpWidget(
        testWrapper(
          HikariIconButton(
            icon: const Icon(Icons.play_arrow),
            tooltip: 'Play',
            onPressed: () => tapped = true,
          ),
        ),
      );

      expect(find.byIcon(Icons.play_arrow), findsOneWidget);
      await tester.tap(find.byType(HikariIconButton));
      await tester.pumpAndSettle();
      expect(tapped, isTrue);
    });
  });

  group('HikariChip', () {
    testWidgets('renders label and toggles selection', (tester) async {
      var selected = false;
      await tester.pumpWidget(
        testWrapper(
          StatefulBuilder(
            builder: (context, setState) {
              return HikariChip(
                label: 'Anime',
                isSelected: selected,
                onTap: () => setState(() => selected = !selected),
              );
            },
          ),
        ),
      );

      expect(find.text('Anime'), findsOneWidget);
      await tester.tap(find.text('Anime'));
      await tester.pumpAndSettle();
      expect(selected, isTrue);
    });
  });

  group('native shared control contracts', () {
    testWidgets('filter chip exposes selected semantics and keyboard action', (
      tester,
    ) async {
      final semantics = tester.ensureSemantics();
      var activations = 0;
      await tester.pumpWidget(
        testWrapper(
          HikariChip(
            label: 'Manga',
            isSelected: true,
            onTap: () => activations++,
          ),
        ),
      );
      final filter = find.byType(FilterChip);
      expect(filter, findsOneWidget);
      expect(tester.widget<FilterChip>(filter).selected, isTrue);
      await tester.tap(find.text('Manga'));
      expect(activations, 1);
      final focus = Focus.of(tester.element(find.text('Manga')));
      focus.requestFocus();
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.space);
      await tester.pump();
      expect(activations, 2);
      semantics.dispose();
    });

    testWidgets('disabled icon control remains a native disabled action', (
      tester,
    ) async {
      await tester.pumpWidget(
        testWrapper(
          const HikariIconButton(
            icon: Icon(Icons.refresh),
            tooltip: 'Refresh',
            onPressed: null,
          ),
        ),
      );
      expect(
        tester.widget<IconButton>(find.byType(IconButton)).onPressed,
        isNull,
      );
    });

    testWidgets('icon button exposes native semantics and keyboard action', (
      tester,
    ) async {
      var activations = 0;
      await tester.pumpWidget(
        testWrapper(
          HikariIconButton(
            icon: const Icon(Icons.search),
            tooltip: 'Search',
            onPressed: () => activations++,
          ),
        ),
      );
      final button = find.byType(IconButton);
      expect(button, findsOneWidget);
      expect(tester.getSize(button).height, greaterThanOrEqualTo(48));
      final focus = Focus.of(tester.element(find.byIcon(Icons.search)));
      focus.requestFocus();
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();
      expect(activations, 1);
    });
  });

  group('HikariSearchBar', () {
    testWidgets('inherits canonical theme and preserves search behavior', (
      tester,
    ) async {
      String currentQuery = '';
      const accent = Color(0xFF06B6D4);
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(accentColor: accent),
          home: Scaffold(
            body: HikariSearchBar(
              debounceDuration: const Duration(milliseconds: 50),
              onChanged: (val) => currentQuery = val,
            ),
          ),
        ),
      );

      final searchBar = find.byType(SearchBar);
      expect(searchBar, findsOneWidget);
      final inheritedTheme = Theme.of(tester.element(searchBar));
      final rootTheme = HikariTheme.darkTheme(accentColor: accent);
      expect(inheritedTheme.colorScheme.primary, rootTheme.colorScheme.primary);
      expect(
        inheritedTheme.searchBarTheme.constraints!.minHeight,
        HikariSize.fieldMinHeight,
      );

      await tester.enterText(searchBar, 'Bleach');
      await tester.pump(const Duration(milliseconds: 20));
      expect(currentQuery, ''); // not yet debounced

      await tester.pump(const Duration(milliseconds: 60));
      expect(currentQuery, 'Bleach'); // debounced
      expect(find.byTooltip('Clear search'), findsOneWidget);

      await tester.tap(find.byTooltip('Clear search'));
      await tester.pumpAndSettle();
      expect(currentQuery, '');
    });
  });

  testWidgets('HikariSearchBar replaces a changing external controller', (
    tester,
  ) async {
    final original = TextEditingController(text: 'Old');
    final replacement = TextEditingController(text: 'New');
    addTearDown(original.dispose);
    addTearDown(replacement.dispose);
    Widget app(TextEditingController controller) => MaterialApp(
      home: Scaffold(
        body: HikariSearchBar(controller: controller, onChanged: (_) {}),
      ),
    );
    await tester.pumpWidget(app(original));
    expect(find.text('Old'), findsOneWidget);
    await tester.pumpWidget(app(replacement));
    expect(find.text('New'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  group('AsyncStateView', () {
    testWidgets('renders loading state', (tester) async {
      await tester.pumpWidget(
        testWrapper(
          AsyncStateView(
            status: AsyncViewStatus.loading,
            contentBuilder: (_) => const Text('Content'),
          ),
        ),
      );

      expect(find.text('Loading content...'), findsOneWidget);
      expect(find.text('Content'), findsNothing);
    });

    testWidgets('renders empty state with action', (tester) async {
      var actionTriggered = false;
      await tester.pumpWidget(
        testWrapper(
          AsyncStateView(
            status: AsyncViewStatus.empty,
            emptyTitle: 'No Media',
            emptyMessage: 'Explore library to add items',
            emptyAction: TextButton(
              onPressed: () => actionTriggered = true,
              child: const Text('Add Media'),
            ),
            contentBuilder: (_) => const Text('Content'),
          ),
        ),
      );

      expect(find.text('No Media'), findsOneWidget);
      expect(find.text('Explore library to add items'), findsOneWidget);
      await tester.tap(find.text('Add Media'));
      expect(actionTriggered, isTrue);
    });

    testWidgets('renders error state with retry button', (tester) async {
      var retried = false;
      await tester.pumpWidget(
        testWrapper(
          AsyncStateView(
            status: AsyncViewStatus.error,
            errorMessage: 'Failed to connect',
            onRetry: () => retried = true,
            contentBuilder: (_) => const Text('Content'),
          ),
        ),
      );

      expect(find.text('Failed to connect'), findsOneWidget);
      await tester.tap(find.text('Try Again'));
      expect(retried, isTrue);
    });
  });

  group('MediaProgressBar and HikariScaffold', () {
    testWidgets('MediaProgressBar clamps and renders', (tester) async {
      await tester.pumpWidget(
        testWrapper(
          const SizedBox(width: 200, child: MediaProgressBar(progress: 0.75)),
        ),
      );
      expect(find.byType(MediaProgressBar), findsOneWidget);
    });

    testWidgets('HikariScaffold renders body', (tester) async {
      await tester.pumpWidget(
        testWrapper(const HikariScaffold(body: Text('Scaffold Content'))),
      );
      expect(find.text('Scaffold Content'), findsOneWidget);
    });
  });
}
