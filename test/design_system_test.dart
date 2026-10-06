import 'dart:io';
import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:hikari/app/theme/design_system/design_system.dart';

void main() {
  test('dark and OLED themes provide readable semantic color pairs', () {
    for (final oled in [false, true]) {
      for (final accent in [
        null,
        const Color(0xFF8B5CF6),
        const Color(0xFFEC4899),
        const Color(0xFF06B6D4),
        const Color(0xFF10B981),
        const Color(0xFFF59E0B),
        Colors.black,
      ]) {
        final theme = HikariDesignTheme.dark(oled: oled, accentSeed: accent);
        final colors = theme.colorScheme;
        final defaultColors = HikariDesignTheme.dark(oled: oled).colorScheme;
        expect(theme.brightness, Brightness.dark);
        expect(colors.surfaceDim, defaultColors.surfaceDim);
        expect(colors.surfaceBright, defaultColors.surfaceBright);
        expect(colors.surfaceDim, colors.surface);
        expect(colors.surfaceBright, colors.surfaceContainerHighest);
        final containers = [
          colors.surfaceContainerLowest,
          colors.surface,
          colors.surfaceContainerLow,
          colors.surfaceContainer,
          colors.surfaceContainerHigh,
          colors.surfaceContainerHighest,
        ];
        final defaults = [
          defaultColors.surfaceContainerLowest,
          defaultColors.surface,
          defaultColors.surfaceContainerLow,
          defaultColors.surfaceContainer,
          defaultColors.surfaceContainerHigh,
          defaultColors.surfaceContainerHighest,
        ];
        expect(containers, defaults);
        for (var i = 1; i < containers.length; i++) {
          expect(
            containers[i].computeLuminance(),
            greaterThanOrEqualTo(containers[i - 1].computeLuminance()),
          );
        }
        for (final surface in [
          colors.surface,
          colors.surfaceDim,
          colors.surfaceBright,
          colors.surfaceContainerLowest,
          colors.surfaceContainerLow,
          colors.surfaceContainer,
          colors.surfaceContainerHigh,
          colors.surfaceContainerHighest,
        ]) {
          expect(
            _contrast(colors.onSurface, surface),
            greaterThanOrEqualTo(4.5),
          );
          expect(
            _contrast(colors.onSurfaceVariant, surface),
            greaterThanOrEqualTo(4.5),
          );
          expect(_contrast(colors.primary, surface), greaterThanOrEqualTo(4.5));
        }
        for (final pair in [
          (colors.primary, colors.onPrimary),
          (colors.primaryContainer, colors.onPrimaryContainer),
          (colors.error, colors.onError),
          (colors.errorContainer, colors.onErrorContainer),
          (colors.inverseSurface, colors.onInverseSurface),
          (colors.secondaryContainer, colors.onSecondaryContainer),
          (
            theme.chipTheme.secondarySelectedColor!,
            WidgetStateProperty.resolveAs<Color>(
              theme.chipTheme.secondaryLabelStyle!.color!,
              {WidgetState.selected},
            ),
          ),
          (
            theme.listTileTheme.selectedTileColor!,
            theme.listTileTheme.selectedColor!,
          ),
          (
            theme.snackBarTheme.backgroundColor!,
            theme.snackBarTheme.contentTextStyle!.color!,
          ),
          (
            theme.snackBarTheme.backgroundColor!,
            theme.snackBarTheme.actionTextColor!,
          ),
          (
            theme.extension<HikariStatusColors>()!.warningContainer,
            theme.extension<HikariStatusColors>()!.onWarningContainer,
          ),
          (
            theme.extension<HikariStatusColors>()!.infoContainer,
            theme.extension<HikariStatusColors>()!.onInfoContainer,
          ),
        ]) {
          expect(_contrast(pair.$1, pair.$2), greaterThanOrEqualTo(4.5));
        }
        expect(theme.scaffoldBackgroundColor, colors.surface);
        if (oled) expect(colors.surface, Colors.black);
        expect(theme.textTheme.labelSmall!.fontSize, greaterThanOrEqualTo(12));
        expect(theme.textTheme.bodyLarge!.height, greaterThanOrEqualTo(1.5));
      }
    }
  });

  test('reading palettes keep prose contrast independent of app theme', () {
    for (final palette in HikariReadingPalette.values) {
      expect(
        _contrast(palette.content, palette.background),
        greaterThanOrEqualTo(7),
      );
    }
  });

  test('width classes have no fractional gaps and reject invalid widths', () {
    for (final (width, expected) in [
      (0.0, HikariLayoutClass.compact),
      (599.9, HikariLayoutClass.compact),
      (600.0, HikariLayoutClass.medium),
      (839.9, HikariLayoutClass.medium),
      (840.0, HikariLayoutClass.expanded),
      (1199.9, HikariLayoutClass.expanded),
      (1200.0, HikariLayoutClass.wide),
    ]) {
      expect(HikariLayout.classify(width), expected);
      expect(HikariLayout.gutter(width), greaterThanOrEqualTo(16));
    }
    for (final width in [-1.0, double.nan, double.infinity]) {
      expect(() => HikariLayout.classify(width), throwsArgumentError);
    }
  });

  testWidgets('motion duration follows accessibility setting', (tester) async {
    for (final disabled in [false, true]) {
      late Duration duration;
      await tester.pumpWidget(
        MediaQuery(
          data: MediaQueryData(disableAnimations: disabled),
          child: Builder(
            builder: (context) {
              duration = HikariDesignMotion.duration(
                context,
                HikariDesignMotion.standard,
              );
              return const SizedBox();
            },
          ),
        ),
      );
      expect(duration, disabled ? Duration.zero : HikariDesignMotion.standard);
    }
  });

  testWidgets('motion respects view disableAnimations signal', (tester) async {
    late Duration duration;
    await tester.pumpWidget(
      MaterialApp(
        home: Builder(
          builder: (context) {
            duration = HikariDesignMotion.duration(
              context,
              HikariDesignMotion.standard,
            );
            return const SizedBox();
          },
        ),
      ),
    );
    tester.platformDispatcher.accessibilityFeaturesTestValue =
        const FakeAccessibilityFeatures(disableAnimations: true);
    await tester.pump();
    expect(duration, Duration.zero);
    tester.platformDispatcher.clearAccessibilityFeaturesTestValue();
  });

  testWidgets('native actions scale, expose labels and retain touch targets', (
    tester,
  ) async {
    final semantics = tester.ensureSemantics();
    for (final scale in [1.0, 2.0]) {
      await tester.pumpWidget(
        MaterialApp(
          theme: HikariDesignTheme.dark(),
          home: MediaQuery(
            data: MediaQueryData(textScaler: TextScaler.linear(scale)),
            child: Scaffold(
              body: SingleChildScrollView(
                child: Column(
                  children: [
                    Builder(
                      builder: (context) => Semantics(
                        header: true,
                        headingLevel: 1,
                        child: Text(
                          'Section',
                          style: Theme.of(context).textTheme.titleLarge,
                        ),
                      ),
                    ),
                    ChoiceChip(
                      label: const Text('Selected choice'),
                      selected: true,
                      onSelected: (_) {},
                    ),
                    FilterChip(
                      label: const Text('Selected filter'),
                      selected: true,
                      onSelected: (_) {},
                    ),
                    FilledButton(
                      onPressed: () {},
                      child: const Text('Continue'),
                    ),
                    OutlinedButton(
                      onPressed: () {},
                      child: const Text('Choose'),
                    ),
                    TextButton(onPressed: () {}, child: const Text('Cancel')),
                    IconButton(
                      tooltip: 'Search',
                      onPressed: () {},
                      icon: const Icon(Icons.search),
                    ),
                    const TextField(
                      decoration: InputDecoration(labelText: 'Search library'),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      expect(find.byTooltip('Search'), findsOneWidget);
      await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
      await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
      await expectLater(tester, meetsGuideline(textContrastGuideline));
      final colors = Theme.of(tester.element(find.byType(ChoiceChip)))
          .colorScheme;
      for (final label in ['Selected choice', 'Selected filter']) {
        expect(
          DefaultTextStyle.of(tester.element(find.text(label))).style.color,
          colors.onSecondaryContainer,
        );
      }
    }
    semantics.dispose();
  });

  test('chip labels preserve disabled precedence and native borders', () {
    final theme = HikariDesignTheme.dark();
    final colors = theme.colorScheme;
    final chipTheme = theme.chipTheme;
    expect(chipTheme.secondarySelectedColor, colors.secondaryContainer);
    expect(chipTheme.side, isNull);
    for (final style in [
      chipTheme.labelStyle!,
      chipTheme.secondaryLabelStyle!,
    ]) {
      expect(
        WidgetStateProperty.resolveAs<Color>(style.color!, {
          WidgetState.selected,
        }),
        colors.onSecondaryContainer,
      );
      expect(
        WidgetStateProperty.resolveAs<Color>(style.color!, {
          WidgetState.selected,
          WidgetState.disabled,
        }),
        colors.onSurface.withValues(alpha: 0.38),
      );
    }
  });

  test(
    'native state styling and destructive action keep disabled precedence',
    () {
      final theme = HikariDesignTheme.dark();
      final style = HikariDesignTheme.destructiveAction(theme.colorScheme);
      expect(style.backgroundColor!.resolve({}), theme.colorScheme.error);
      expect(style.foregroundColor!.resolve({}), theme.colorScheme.onError);
      expect(
        style.backgroundColor!.resolve({WidgetState.disabled}),
        isNot(theme.colorScheme.error),
      );
      expect(
        theme.filledButtonTheme.style!.overlayColor!.resolve({
          WidgetState.focused,
        }),
        isNot(Colors.transparent),
      );
    },
  );

  test('directive check ignores comments and unrelated strings', () {
    const source = """
// import 'package:bad/comment.dart';
const sample = "import 'package:bad/string.dart';";
const multiline = '''
import 'package:bad/multiline.dart';
''';
/* export 'package:bad/block.dart'; */
import 'package:flutter/widgets.dart';
export 'first.dart' if (dart.library.io) 'second.dart';
""";
    expect(_directiveUris(source), [
      'package:flutter/widgets.dart',
      'first.dart',
      'second.dart',
    ]);
  });

  test(
    'foundation stays isolated and adoption is limited to approved seams',
    () {
      final directory = Directory('lib/app/theme/design_system');
      for (final file in directory.listSync().whereType<File>()) {
        for (final uri in _directiveUris(file.readAsStringSync())) {
          expect(
            uri.startsWith('package:flutter/') ||
                (!uri.contains('/') && uri.endsWith('.dart')),
            isTrue,
            reason: '${file.path}: $uri',
          );
        }
      }

      const approvedAdopters = {
        'lib/core/ui/components/hikari_search_bar.dart',
      };
      final adopters = <String>{};
      for (final file in Directory(
        'lib',
      ).listSync(recursive: true).whereType<File>()) {
        final path = file.path.replaceAll('\\', '/');
        if (!path.endsWith('.dart') || path.contains('/design_system/')) {
          continue;
        }
        if (_directiveUris(file.readAsStringSync())
            .any((uri) => uri.contains('design_system/'))) {
          adopters.add(path);
        }
      }
      expect(adopters, approvedAdopters);
    },
  );
}

Iterable<String> _directiveUris(String source) sync* {
  // Consume comments and whole strings before recognizing directive keywords.
  final tokens = RegExp(
    r'''//[^\r\n]*|/\*[\s\S]*?\*/|r?(?:"""[\s\S]*?"""|\x27\x27\x27[\s\S]*?\x27\x27\x27|"(?:\\.|[^"\\])*"|\x27(?:\\.|[^\x27\\])*\x27)|\b\w+\b|[^\s]''',
  );
  var dependency = false;
  for (final match in tokens.allMatches(source)) {
    final token = match.group(0)!;
    if (token.startsWith('//') || token.startsWith('/*')) continue;
    if (token == 'import' || token == 'export' || token == 'part') {
      dependency = true;
    }
    if (token == ';') dependency = false;
    final literal = token.startsWith('r') ? token.substring(1) : token;
    if (dependency && (literal.startsWith("'") || literal.startsWith('"'))) {
      yield literal.substring(1, literal.length - 1);
    }
  }
}

double _contrast(Color a, Color b) {
  final first = a.computeLuminance();
  final second = b.computeLuminance();
  return (math.max(first, second) + 0.05) / (math.min(first, second) + 0.05);
}
