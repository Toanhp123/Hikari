import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/features/settings/apk_actions_view_model.dart';
import 'package:hikari/features/settings/novel_actions_view_model.dart';

import 'plugin_management_test.dart' as helpers;

void main() {
  test(
    'APK view model serializes user actions and stops updates on dispose',
    () async {
      final session = helpers.management(store: helpers.MemoryStore());
      final model = ApkActionsViewModel(session);
      var completedCallbacks = 0;
      var secondRan = false;
      final pending = Completer<void>();
      final first = model.run(
        () => pending.future,
        canStart: () => true,
        onFinished: () => completedCallbacks++,
      );
      await model.run(
        () async {
          secondRan = true;
        },
        canStart: () => true,
        onFinished: () => completedCallbacks++,
      );
      expect(model.busy, isTrue);
      expect(secondRan, isFalse);
      model.dispose();
      pending.complete();
      await first;
      expect(completedCallbacks, 0);
    },
  );

  test('Novel view model never reports unknown outcomes as success', () async {
    final model = NovelActionsViewModel();
    addTearDown(model.dispose);
    await model.run(
      () async => throw StateError('private URL'),
      canStart: () => true,
    );
    expect(model.busy, isFalse);
    expect(model.error, contains('unknown'));
    expect(model.error, isNot(contains('private URL')));
  });
}
