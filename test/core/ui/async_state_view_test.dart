import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/async_state_view.dart';

void main() {
  for (final status in AsyncViewStatus.values) {
    for (final sliver in [false, true]) {
      testWidgets(
        '$status remains reachable in ${sliver ? 'sliver' : 'bounded'} short large-text viewport',
        (tester) async {
          tester.view.physicalSize = const Size(320, 240);
          tester.view.devicePixelRatio = 1;
          addTearDown(tester.view.resetPhysicalSize);
          addTearDown(tester.view.resetDevicePixelRatio);
          var actions = 0;
          final view = AsyncStateView(
            status: status,
            contentBuilder: (_) => const Text('Content preserved'),
            emptyTitle: 'No matching content in this collection',
            emptyMessage: List.filled(12, 'Long empty explanation.').join(' '),
            errorTitle: 'Unable to load this collection',
            errorMessage: List.filled(
              12,
              'Long failure explanation.',
            ).join(' '),
            emptyAction: TextButton(
              onPressed: () => actions++,
              child: const Text('Explore'),
            ),
            onRetry: () => actions++,
          );
          await tester.pumpWidget(
            MaterialApp(
              theme: HikariTheme.darkTheme(),
              home: MediaQuery(
                data: const MediaQueryData(
                  size: Size(320, 240),
                  textScaler: TextScaler.linear(2),
                ),
                child: Scaffold(
                  body: sliver
                      ? CustomScrollView(
                          slivers: [SliverToBoxAdapter(child: view)],
                        )
                      : view,
                ),
              ),
            ),
          );
          expect(tester.takeException(), isNull);
          if (status == AsyncViewStatus.empty ||
              status == AsyncViewStatus.error) {
            final action = find.text(
              status == AsyncViewStatus.empty ? 'Explore' : 'Try Again',
            );
            await tester.ensureVisible(action);
            await tester.pump();
            await tester.tap(action);
            expect(actions, 1);
          }
          if (status == AsyncViewStatus.content) {
            expect(find.text('Content preserved'), findsOneWidget);
          }
          expect(tester.takeException(), isNull);
        },
      );
    }
  }

  testWidgets(
    'SliverFillRemaining intrinsic measurement accepts empty presentation',
    (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: CustomScrollView(
              slivers: [
                SliverFillRemaining(
                  hasScrollBody: false,
                  child: AsyncStateView(
                    status: AsyncViewStatus.empty,
                    contentBuilder: (_) => const SizedBox(),
                  ),
                ),
              ],
            ),
          ),
        ),
      );
      expect(tester.takeException(), isNull);
    },
  );
}
