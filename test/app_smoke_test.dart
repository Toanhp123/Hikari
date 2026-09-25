import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/app.dart';

void main() {
  testWidgets('boots the Hikari app', (tester) async {
    await tester.pumpWidget(const HikariApp());

    expect(find.text('Hikari'), findsOneWidget);
  });
}
