import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/core/ui/layout/hikari_content_region.dart';
import 'package:hikari/core/ui/layout/hikari_page_header.dart';
import 'package:hikari/core/ui/layout/hikari_page_scaffold.dart';

void main() {
  testWidgets('legacy ambient default and canonical opt-in keep same chrome', (
    tester,
  ) async {
    for (final scaffold in <Widget>[
      const HikariScaffold(body: Text('Body')),
      const HikariPageScaffold(showAmbientGlow: true, body: Text('Body')),
    ]) {
      await tester.pumpWidget(MaterialApp(home: scaffold));
      expect(find.byType(Positioned), findsNWidgets(2));
      expect(find.byType(SafeArea), findsOneWidget);
      expect(find.text('Body'), findsOneWidget);
    }
    await tester.pumpWidget(
      const MaterialApp(home: HikariPageScaffold(body: Text('Body'))),
    );
    expect(find.byType(Positioned), findsNothing);
  });

  testWidgets('box and sliver content use same wide bounds', (tester) async {
    await tester.binding.setSurfaceSize(const Size(1800, 600));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    await tester.pumpWidget(
      const MaterialApp(
        home: Column(
          children: [
            HikariContentRegion(
              child: SizedBox(
                key: Key('box'),
                width: double.infinity,
                height: 20,
              ),
            ),
            Expanded(
              child: CustomScrollView(
                slivers: [
                  HikariContentRegion.sliver(
                    sliver: SliverToBoxAdapter(
                      child: SizedBox(key: Key('sliver'), height: 20),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
    expect(
      tester.getSize(find.byKey(const Key('box'))).width,
      tester.getSize(find.byKey(const Key('sliver'))).width,
    );
    expect(
      tester.getTopLeft(find.byKey(const Key('box'))).dx,
      tester.getTopLeft(find.byKey(const Key('sliver'))).dx,
    );
  });

  testWidgets('SafeArea consumed once and page lets slivers own scrolling', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        builder: (_, child) => MediaQuery(
          data: const MediaQueryData(
            size: Size(800, 600),
            padding: EdgeInsets.only(top: 24, bottom: 20),
          ),
          child: child!,
        ),
        home: HikariPageScaffold(
          body: SafeArea(
            child: CustomScrollView(
              slivers: [
                const SliverToBoxAdapter(
                  child: SizedBox(key: Key('first'), height: 40),
                ),
                HikariContentRegion.sliver(
                  sliver: SliverList.builder(
                    itemCount: 50,
                    itemBuilder: (_, i) => Text('Row $i'),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
    expect(tester.getTopLeft(find.byKey(const Key('first'))).dy, 24);
    expect(find.byType(Scrollable), findsOneWidget);
    await tester.drag(find.byType(CustomScrollView), const Offset(0, -500));
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
  });
  for (final width in <double>[375, 1440]) {
    testWidgets('content and header at $width reflow 3x actions', (
      tester,
    ) async {
      tester.view.physicalSize = Size(width, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
      await tester.pumpWidget(
        MaterialApp(
          builder: (context, child) => MediaQuery(
            data: MediaQuery.of(context)
                .copyWith(textScaler: TextScaler.linear(3)),
            child: child!,
          ),
          home: HikariPageScaffold(
            body: SingleChildScrollView(
              child: HikariContentRegion(
                child: HikariPageHeader(
                  title: 'Long page heading',
                  subtitle: 'Details',
                  actions: [
                    TextButton(onPressed: () {}, child: const Text('Search')),
                    TextButton(onPressed: () {}, child: const Text('Refresh')),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
      expect(tester.takeException(), isNull);
      expect(
        tester.getSize(find.byType(HikariPageHeader)).width,
        lessThanOrEqualTo(1440),
      );
      await tester.ensureVisible(find.text('Refresh'));
      expect(find.text('Refresh').hitTestable(), findsOneWidget);
    });
  }
}
