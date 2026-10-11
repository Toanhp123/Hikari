import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/chapter_control_bar.dart';
import 'package:hikari/core/ui/patterns/primary_reading_cta.dart';

Widget _app(Widget child) {
  return MaterialApp(
    theme: HikariTheme.darkTheme(),
    home: Scaffold(body: child),
  );
}

void main() {
  group('ChapterControlBar', () {
    testWidgets('displays total count formatted as "X chapters"', (
      tester,
    ) async {
      await tester.pumpWidget(
        _app(
          ChapterControlBar(
            totalChapters: 24,
            reverseSourceOrder: false,
            onToggleSourceOrder: () {},
            isSearchExpanded: false,
            onToggleSearchExpanded: () {},
            searchQuery: '',
            onSearchChanged: (_) {},
            onClearSearch: () {},
          ),
        ),
      );

      expect(find.text('24 chapters'), findsOneWidget);
    });

    testWidgets('displays singular "1 chapter" when total count is 1', (
      tester,
    ) async {
      await tester.pumpWidget(
        _app(
          ChapterControlBar(
            totalChapters: 1,
            reverseSourceOrder: false,
            onToggleSourceOrder: () {},
            isSearchExpanded: false,
            onToggleSearchExpanded: () {},
            searchQuery: '',
            onSearchChanged: (_) {},
            onClearSearch: () {},
          ),
        ),
      );

      expect(find.text('1 chapter'), findsOneWidget);
    });

    testWidgets('shows "X of Y chapters" when filtered', (tester) async {
      await tester.pumpWidget(
        _app(
          ChapterControlBar(
            totalChapters: 40,
            filteredChapters: 5,
            reverseSourceOrder: false,
            onToggleSourceOrder: () {},
            isSearchExpanded: true,
            onToggleSearchExpanded: () {},
            searchQuery: 'ch',
            onSearchChanged: (_) {},
            onClearSearch: () {},
          ),
        ),
      );

      expect(find.text('5 of 40 chapters'), findsOneWidget);
    });

    testWidgets('tapping sort button calls onToggleSourceOrder', (
      tester,
    ) async {
      var sortToggled = false;
      await tester.pumpWidget(
        _app(
          ChapterControlBar(
            totalChapters: 10,
            reverseSourceOrder: false,
            onToggleSourceOrder: () => sortToggled = true,
            isSearchExpanded: false,
            onToggleSearchExpanded: () {},
            searchQuery: '',
            onSearchChanged: (_) {},
            onClearSearch: () {},
          ),
        ),
      );

      expect(find.byTooltip('Reverse chapter order'), findsOneWidget);
      await tester.tap(find.byTooltip('Reverse chapter order'));
      expect(sortToggled, isTrue);
    });

    testWidgets(
      'shows "Restore source order" tooltip when reverseSourceOrder is true',
      (tester) async {
        var sortToggled = false;
        await tester.pumpWidget(
          _app(
            ChapterControlBar(
              totalChapters: 10,
              reverseSourceOrder: true,
              onToggleSourceOrder: () => sortToggled = true,
              isSearchExpanded: false,
              onToggleSearchExpanded: () {},
              searchQuery: '',
              onSearchChanged: (_) {},
              onClearSearch: () {},
            ),
          ),
        );

        expect(find.byTooltip('Restore source order'), findsOneWidget);
        await tester.tap(find.byTooltip('Restore source order'));
        expect(sortToggled, isTrue);
      },
    );

    testWidgets(
      'tapping search icon reveals search input; typing calls onSearchChanged',
      (tester) async {
        var isSearchExpanded = false;
        var query = '';

        await tester.pumpWidget(
          StatefulBuilder(
            builder: (context, setState) {
              return _app(
                ChapterControlBar(
                  totalChapters: 10,
                  reverseSourceOrder: false,
                  onToggleSourceOrder: () {},
                  isSearchExpanded: isSearchExpanded,
                  onToggleSearchExpanded: () {
                    setState(() {
                      isSearchExpanded = !isSearchExpanded;
                    });
                  },
                  searchQuery: query,
                  onSearchChanged: (val) {
                    setState(() {
                      query = val;
                    });
                  },
                  onClearSearch: () {
                    setState(() {
                      query = '';
                    });
                  },
                ),
              );
            },
          ),
        );

        // Before tapping search icon: input is not shown
        expect(find.byType(TextField), findsNothing);
        expect(find.byTooltip('Search chapters'), findsOneWidget);

        // Tap search icon to toggle search
        await tester.tap(find.byTooltip('Search chapters'));
        await tester.pump();

        // Search input is now revealed
        expect(find.byType(TextField), findsOneWidget);

        // Typing into search input triggers onSearchChanged
        await tester.enterText(find.byType(TextField), 'Chapter 10');
        await tester.pump();

        expect(query, 'Chapter 10');
      },
    );

    testWidgets('tapping clear button in search input triggers onClearSearch', (
      tester,
    ) async {
      var clearCalled = false;
      var query = 'existing query';

      await tester.pumpWidget(
        StatefulBuilder(
          builder: (context, setState) {
            return _app(
              ChapterControlBar(
                totalChapters: 10,
                reverseSourceOrder: false,
                onToggleSourceOrder: () {},
                isSearchExpanded: true,
                onToggleSearchExpanded: () {},
                searchQuery: query,
                onSearchChanged: (val) {
                  setState(() {
                    query = val;
                  });
                },
                onClearSearch: () {
                  setState(() {
                    clearCalled = true;
                    query = '';
                  });
                },
              ),
            );
          },
        ),
      );

      expect(find.byTooltip('Clear search'), findsOneWidget);
      await tester.tap(find.byTooltip('Clear search'));
      await tester.pump();

      expect(clearCalled, isTrue);
      expect(query, '');
    });
  });

  group('PrimaryReadingCta', () {
    testWidgets(
      'renders with "Start reading" and play icon, triggers onPressed on tap',
      (tester) async {
        var pressed = false;
        await tester.pumpWidget(
          _app(PrimaryReadingCta(onPressed: () => pressed = true)),
        );

        expect(find.text('Start reading'), findsOneWidget);
        expect(find.byIcon(Icons.play_arrow_rounded), findsOneWidget);
        expect(find.byType(FilledButton), findsOneWidget);

        await tester.tap(find.byType(FilledButton));
        expect(pressed, isTrue);
      },
    );

    testWidgets('disables when enabled == false', (tester) async {
      var pressed = false;
      await tester.pumpWidget(
        _app(
          PrimaryReadingCta(enabled: false, onPressed: () => pressed = true),
        ),
      );

      final button = tester.widget<FilledButton>(find.byType(FilledButton));
      expect(button.onPressed, isNull);

      await tester.tap(find.byType(FilledButton));
      expect(pressed, isFalse);
    });

    testWidgets('renders custom label when provided', (tester) async {
      await tester.pumpWidget(
        _app(PrimaryReadingCta(label: 'Continue Chapter 4', onPressed: () {})),
      );

      expect(find.text('Continue Chapter 4'), findsOneWidget);
    });
  });
}
