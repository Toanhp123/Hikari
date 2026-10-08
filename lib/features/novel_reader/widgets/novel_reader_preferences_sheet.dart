import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/features/novel_reader/novel_reader_theme.dart';

class NovelReaderPreferencesSheet extends StatefulWidget {
  const NovelReaderPreferencesSheet({
    super.key,
    required this.theme,
    required this.fontSize,
    required this.onThemeChanged,
    required this.onFontSizeChanged,
  });

  final NovelReaderTheme theme;
  final double fontSize;
  final ValueChanged<NovelReaderTheme> onThemeChanged;
  final ValueChanged<double> onFontSizeChanged;

  @override
  State<NovelReaderPreferencesSheet> createState() =>
      _NovelReaderPreferencesSheetState();
}

class _NovelReaderPreferencesSheetState
    extends State<NovelReaderPreferencesSheet> {
  late NovelReaderTheme _theme = widget.theme;
  late double _fontSize = widget.fontSize;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.all(HikariSpacing.lg),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Reading Preferences',
            style: Theme.of(context).textTheme.titleMedium!
                .copyWith(color: colors.onSurface),
          ),
          const SizedBox(height: HikariSpacing.md),
          Text(
            'Color Theme',
            style: Theme.of(context).textTheme.labelSmall
                ?.copyWith(color: colors.onSurfaceVariant),
          ),
          const SizedBox(height: HikariSpacing.xs),
          Row(
            children: NovelReaderTheme.values.map((theme) {
              final selected = _theme == theme;
              return Padding(
                padding: const EdgeInsets.only(right: HikariSpacing.sm),
                child: GestureDetector(
                  onTap: () {
                    setState(() => _theme = theme);
                    widget.onThemeChanged(theme);
                  },
                  child: Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      color: theme.bg,
                      shape: BoxShape.circle,
                      border: Border.all(
                        color: selected
                            ? colors.primary.withValues(alpha: 0.8)
                            : colors.outline,
                        width: selected ? 2.5 : 1.0,
                      ),
                    ),
                    child: Center(
                      child: Text(
                        'Aa',
                        style: Theme.of(context).textTheme.labelSmall?.copyWith(
                          color: theme.fg,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ),
                ),
              );
            }).toList(),
          ),
          const SizedBox(height: HikariSpacing.md),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'Font Size',
                style: Theme.of(context).textTheme.labelSmall
                    ?.copyWith(color: colors.onSurfaceVariant),
              ),
              Text(
                '${_fontSize.toInt()}sp',
                style: Theme.of(context).textTheme.labelSmall?.copyWith(
                  color: colors.onSurface,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
          Slider(
            value: _fontSize,
            min: 12,
            max: 26,
            divisions: 7,
            onChanged: (value) {
              setState(() => _fontSize = value);
              widget.onFontSizeChanged(value);
            },
          ),
        ],
      ),
    );
  }
}
