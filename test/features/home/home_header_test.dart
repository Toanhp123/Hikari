import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/features/home/widgets/home_header.dart';

void main() {
  Widget buildHeaderTest({VoidCallback? onSearch, double scrollOffset = 0.0}) {
    final controller = ScrollController(initialScrollOffset: scrollOffset);
    return MaterialApp(
      theme: HikariTheme.darkTheme(),
      home: Scaffold(
        body: CustomScrollView(
          controller: controller,
          slivers: [
            HomeHeader(onSearch: onSearch),
            SliverToBoxAdapter(
              child: Container(
                height: 1200,
                color: Colors.blue.withValues(alpha: 0.2),
              ),
            ),
          ],
        ),
      ),
    );
  }

  testWidgets('HomeHeader renders brand identity and triggers search action', (
    tester,
  ) async {
    var searchTapped = false;
    await tester.pumpWidget(
      buildHeaderTest(onSearch: () => searchTapped = true),
    );

    expect(find.text('Hikari'), findsOneWidget);
    expect(find.byIcon(Icons.auto_awesome), findsOneWidget);
    expect(find.byTooltip('Search'), findsOneWidget);

    await tester.tap(find.byTooltip('Search'));
    expect(searchTapped, isTrue);
  });

  testWidgets('HomeHeader remains pinned when scrolling', (tester) async {
    await tester.pumpWidget(buildHeaderTest());

    final initialTop = tester.getTopLeft(find.text('Hikari')).dy;

    // Scroll down 200 pixels
    final scrollable = find.byType(Scrollable);
    await tester.drag(scrollable, const Offset(0, -200));
    await tester.pump();

    // Header title stays pinned near the top
    final scrolledTop = tester.getTopLeft(find.text('Hikari')).dy;
    expect(scrolledTop, equals(initialTop));

    // BackdropFilter is present in the tree
    expect(find.byType(BackdropFilter), findsOneWidget);
  });
}
