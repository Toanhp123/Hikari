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

  testWidgets('HomeHeader grows for large text and keeps search reachable', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: MediaQuery(
          data: const MediaQueryData(textScaler: TextScaler.linear(2.5)),
          child: Scaffold(
            body: CustomScrollView(
              slivers: [
                HomeHeader(onSearch: () {}),
                const SliverToBoxAdapter(child: SizedBox(height: 500)),
              ],
            ),
          ),
        ),
      ),
    );
    expect(find.text('Hikari'), findsOneWidget);
    expect(find.byTooltip('Search'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('HomeHeader remains pinned when scrolling', (tester) async {
    await tester.pumpWidget(buildHeaderTest());

    final initialTop = tester.getTopLeft(find.text('Hikari')).dy;

    // The fixed-height pinned header still receives a scroll-dependent
    // shrinkOffset from Flutter's render sliver and fades in its glass surface.
    expect(find.byType(BackdropFilter), findsNothing);

    // Scroll down 200 pixels
    final scrollable = find.byType(Scrollable);
    await tester.drag(scrollable, const Offset(0, -200));
    await tester.pump();

    // Header title stays pinned near the top
    final scrolledTop = tester.getTopLeft(find.text('Hikari')).dy;
    expect(scrolledTop, equals(initialTop));

    expect(find.byType(BackdropFilter), findsOneWidget);

    await tester.drag(scrollable, const Offset(0, 250));
    await tester.pumpAndSettle();
    expect(find.byType(BackdropFilter), findsNothing);
  });

  testWidgets('compact HomeHeader keeps search reachable with scaled text', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(320, 720);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: MediaQuery(
          data: const MediaQueryData(textScaler: TextScaler.linear(2.0)),
          child: Scaffold(
            body: CustomScrollView(
              slivers: [
                HomeHeader(onSearch: () {}),
                const SliverToBoxAdapter(child: SizedBox(height: 800)),
              ],
            ),
          ),
        ),
      ),
    );
    expect(tester.takeException(), isNull);
    expect(find.byTooltip('Search'), findsOneWidget);
  });
}
