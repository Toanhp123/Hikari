import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/patterns/media_type_presentation.dart';
import 'package:hikari/domain/media/media.dart';

void main() {
  testWidgets('media badges use canonical surface and foreground pairs', (
    tester,
  ) async {
    for (final oled in [false, true]) {
      for (final seed in [null, const Color(0xFF06B6D4)]) {
        late List<({Color background, Color foreground})> pairs;
        late ThemeData theme;
        await tester.pumpWidget(
          MaterialApp(
            theme: HikariTheme.darkTheme(oled: oled, accentColor: seed),
            home: Builder(
              builder: (context) {
                theme = Theme.of(context);
                pairs = [
                  for (final type in MediaType.values)
                    mediaTypeBadgeColors(context, type),
                ];
                return const SizedBox.shrink();
              },
            ),
          ),
        );
        final status = theme.extension<HikariStatusColors>()!;
        expect(pairs[0].background, theme.colorScheme.primaryContainer);
        expect(pairs[0].foreground, theme.colorScheme.onPrimaryContainer);
        expect(pairs[1].background, status.warningContainer);
        expect(pairs[1].foreground, status.onWarningContainer);
        expect(pairs[2].background, status.infoContainer);
        expect(pairs[2].foreground, status.onInfoContainer);
      }
    }
  });
}
