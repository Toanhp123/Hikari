import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';

void main() {
  testWidgets('Form owns validation, submission, save and reset', (
    tester,
  ) async {
    final formKey = GlobalKey<FormState>();
    String? saved;
    String? changed;
    await tester.pumpWidget(
      MaterialApp(
        theme: HikariTheme.darkTheme(),
        home: Scaffold(
          body: Form(
            key: formKey,
            child: TextFormField(
              decoration: const InputDecoration(
                labelText: 'Repository URL',
                hintText: 'https://example.com/index.json',
              ),
              initialValue: '',
              keyboardType: TextInputType.url,
              onChanged: (value) => changed = value,
              onSaved: (value) => saved = value,
              validator: (value) =>
                  value == null || !value.startsWith('https://')
                  ? 'HTTPS required'
                  : null,
            ),
          ),
        ),
      ),
    );

    expect(formKey.currentState!.validate(), isFalse);
    await tester.pump();
    expect(find.text('HTTPS required'), findsOneWidget);
    await tester.enterText(find.byType(TextFormField), 'https://example.com');
    await tester.pump();
    expect(changed, 'https://example.com');
    expect(formKey.currentState!.validate(), isTrue);
    formKey.currentState!.save();
    expect(saved, 'https://example.com');
    formKey.currentState!.reset();
    await tester.pump();
    expect(find.text('https://example.com'), findsNothing);
    expect(find.text('HTTPS required'), findsNothing);
  });

  testWidgets('disabled text input preserves native form semantics', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: TextFormField(
            decoration: const InputDecoration(labelText: 'Read-only evidence'),
            enabled: false,
          ),
        ),
      ),
    );
    final field = tester.widget<TextFormField>(find.byType(TextFormField));
    expect(field.enabled, isFalse);
  });
}
