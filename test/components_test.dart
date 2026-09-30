import 'package:flutter/material.dart';
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

  group('HikariSearchBar', () {
    testWidgets('debounces query input and clears input on close button tap', (
      tester,
    ) async {
      String currentQuery = '';
      await tester.pumpWidget(
        testWrapper(
          HikariSearchBar(
            debounceDuration: const Duration(milliseconds: 50),
            onChanged: (val) => currentQuery = val,
          ),
        ),
      );

      await tester.enterText(find.byType(TextField), 'Bleach');
      await tester.pump(const Duration(milliseconds: 20));
      expect(currentQuery, ''); // not yet debounced

      await tester.pump(const Duration(milliseconds: 60));
      expect(currentQuery, 'Bleach'); // debounced

      await tester.tap(find.byIcon(Icons.close_rounded));
      await tester.pumpAndSettle();
      expect(currentQuery, '');
    });
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
