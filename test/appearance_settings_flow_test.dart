import 'dart:async';

import 'package:drift/native.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/app.dart';
import 'package:hikari/app/app_dependencies.dart';
import 'package:hikari/domain/settings/appearance_settings.dart';
import 'package:hikari/infrastructure/persistence/user_database.dart';

void main() {
  testWidgets('failed appearance write offers retry of the current selection', (
    tester,
  ) async {
    final errors = <FlutterErrorDetails>[];
    final originalErrorHandler = FlutterError.onError;
    FlutterError.onError = errors.add;
    addTearDown(() => FlutterError.onError = originalErrorHandler);

    final database = UserDatabase(NativeDatabase.memory());
    final dependencies = AppDependencies.create(database: database);
    final repository = _ControlledAppearanceRepository();
    await tester.pumpWidget(
      HikariApp(dependencies: dependencies, appearanceRepository: repository),
    );
    await tester.tap(find.byTooltip('Settings'));
    await tester.pumpAndSettle();

    expect(find.text('Appearance saved on this device.'), findsNothing);
    await tester.tap(find.byType(Switch));
    await tester.pump();
    expect(repository.writes, hasLength(1));
    expect(repository.writes.single.isOled, isTrue);
    expect(find.text('Saving appearance…'), findsOneWidget);

    repository.fail(0);
    await tester.pumpAndSettle();
    expect(errors, hasLength(1));
    expect(find.textContaining('Could not save appearance.'), findsOneWidget);
    expect(find.text('Appearance saved on this device.'), findsNothing);

    await tester.tap(find.text('Retry'));
    await tester.pump();
    expect(repository.writes, hasLength(2));
    expect(repository.writes.last.isOled, isTrue);
    expect(find.text('Saving appearance…'), findsOneWidget);
    expect(find.text('Retry'), findsNothing);

    repository.succeed(1);
    await tester.pumpAndSettle();
    expect(find.text('Appearance saved on this device.'), findsOneWidget);
    expect(find.textContaining('Could not save appearance.'), findsNothing);

    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(Duration.zero);
    await dependencies.dispose();
    await database.close();
  });

  testWidgets('stale failed write cannot mark newer appearance as failed', (
    tester,
  ) async {
    final originalErrorHandler = FlutterError.onError;
    final errors = <FlutterErrorDetails>[];
    FlutterError.onError = errors.add;
    addTearDown(() => FlutterError.onError = originalErrorHandler);
    final semantics = tester.ensureSemantics();

    final database = UserDatabase(NativeDatabase.memory());
    final dependencies = AppDependencies.create(database: database);
    final repository = _ControlledAppearanceRepository();
    await tester.pumpWidget(
      HikariApp(dependencies: dependencies, appearanceRepository: repository),
    );
    await tester.tap(find.byTooltip('Settings'));
    await tester.pumpAndSettle();

    await tester.tap(find.byType(Switch));
    await tester.pump();
    await tester.tap(find.bySemanticsLabel('Cyan accent'));
    await tester.pump();
    // The second write must wait for the first, preserving storage order.
    expect(repository.writes, hasLength(1));
    repository.fail(0);
    await tester.pump();
    await tester.pump();
    expect(errors, hasLength(1));
    expect(repository.writes, hasLength(2));
    expect(repository.writes.last.isOled, isTrue);
    expect(repository.writes.last.accentArgb, 0xFF06B6D4);
    expect(find.text('Saving appearance…'), findsOneWidget);
    expect(find.textContaining('Could not save appearance.'), findsNothing);

    repository.succeed(1);
    await tester.pumpAndSettle();
    expect(find.text('Appearance saved on this device.'), findsOneWidget);

    // A later change still persists normally after an earlier failure.
    await tester.tap(find.bySemanticsLabel('Default accent'));
    await tester.pump();
    expect(repository.writes, hasLength(3));
    expect(repository.writes.last.accentArgb, isNull);
    repository.succeed(2);
    await tester.pumpAndSettle();
    expect(find.text('Appearance saved on this device.'), findsOneWidget);
    expect(errors, hasLength(1));

    semantics.dispose();
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(Duration.zero);
    await dependencies.dispose();
    await database.close();
  });

  testWidgets('retry in flight does not overwrite a newer selection', (
    tester,
  ) async {
    final originalErrorHandler = FlutterError.onError;
    FlutterError.onError = (_) {};
    addTearDown(() => FlutterError.onError = originalErrorHandler);
    final semantics = tester.ensureSemantics();

    final database = UserDatabase(NativeDatabase.memory());
    final dependencies = AppDependencies.create(database: database);
    final repository = _ControlledAppearanceRepository();
    await tester.pumpWidget(
      HikariApp(dependencies: dependencies, appearanceRepository: repository),
    );
    await tester.tap(find.byTooltip('Settings'));
    await tester.pumpAndSettle();

    await tester.tap(find.byType(Switch));
    await tester.pump();
    repository.fail(0);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Retry'));
    await tester.pump();
    expect(repository.writes, hasLength(2));

    // The user changes their accent while the retry is still on disk.
    await tester.tap(find.bySemanticsLabel('Violet accent'));
    await tester.pump();
    repository.succeed(1);
    await tester.pump();
    await tester.pump();
    expect(find.text('Saving appearance…'), findsOneWidget);
    expect(repository.writes, hasLength(3));
    expect(repository.writes.last.accentArgb, 0xFF8B5CF6);
    repository.succeed(2);
    await tester.pumpAndSettle();
    expect(find.text('Appearance saved on this device.'), findsOneWidget);

    semantics.dispose();
    await tester.pumpWidget(const SizedBox.shrink());
    await tester.pump(Duration.zero);
    await dependencies.dispose();
    await database.close();
  });
}

final class _ControlledAppearanceRepository
    implements AppearanceSettingsRepository {
  final writes = <AppearanceSettings>[];
  final _completions = <Completer<void>>[];

  @override
  Future<AppearanceSettings> load() async => const AppearanceSettings();

  @override
  Future<void> save(AppearanceSettings settings) {
    writes.add(settings);
    final pending = Completer<void>();
    _completions.add(pending);
    return pending.future;
  }

  void fail(int attempt) =>
      _completions[attempt].completeError(StateError('Simulated disk failure'));

  void succeed(int attempt) => _completions[attempt].complete();
}
