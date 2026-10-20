import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/domain/plugins/apk_plugins.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/features/plugins/widgets/apk_operation_card.dart';

void main() {
  testWidgets(
    'recovery actions stay visible while technical evidence is expandable',
    (tester) async {
      var resumes = 0;
      final operation = ApkInstallOperation(
        id: '11111111-1111-4111-8111-111111111111',
        pluginId: PluginId(PluginEcosystem.manga, 'org.example.manga'),
        versionCode: 12,
        signer: 'a' * 64,
        kind: PluginOperation.install,
        status: ApkInstallStatus.pendingUserAction,
        stageTransferred: true,
      );
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariTheme.darkTheme(),
          home: Scaffold(
            body: SingleChildScrollView(
              child: ApkOperationCard(
                operation: operation,
                enabled: true,
                onResume: () => resumes++,
                onCancel: () {},
                onRetire: () {},
              ),
            ),
          ),
        ),
      );
      expect(find.textContaining('pendingUserAction'), findsOneWidget);
      expect(find.text('Resume Android confirmation'), findsOneWidget);
      expect(find.textContaining('Signer:'), findsNothing);
      await tester.tap(find.text('Technical evidence'));
      await tester.pumpAndSettle();
      expect(find.textContaining('Signer:'), findsOneWidget);
      await tester.tap(find.text('Resume Android confirmation'));
      expect(resumes, 1);
      expect(tester.takeException(), isNull);
    },
  );
}
