import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/features/reading/widgets/chapter_list_controls.dart';
import 'package:hikari/core/ui/controls/hikari_icon_button.dart';

Widget _app(Widget child) {
  return MaterialApp(
    theme: HikariTheme.darkTheme(),
    home: Scaffold(body: child),
  );
}

void main() {
  group('ChapterListControls', () {
    testWidgets('displays total count formatted as "X chapters"', (
      tester,
    ) async {
      await tester.pumpWidget(
        _app(
          ChapterListControls(
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
          ChapterListControls(
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
          ChapterListControls(
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
          ChapterListControls(
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

      expect(find.byType(HikariIconButton), findsNWidgets(2));
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
            ChapterListControls(
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
                ChapterListControls(
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
              ChapterListControls(
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
}
