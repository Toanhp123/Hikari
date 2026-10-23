import 'package:flutter/material.dart';
import 'package:hikari/core/ui/hikari_ui.dart';

/// Theme tokens as applied by Material; colors and spacing remain core-owned.
class FoundationPreview extends StatelessWidget {
  const FoundationPreview({super.key});

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const HikariSectionHeader(
          title: 'Foundations',
          description: 'Semantic colors, typography, spacing and radius.',
        ),
        Wrap(
          spacing: HikariSpacing.sm,
          runSpacing: HikariSpacing.sm,
          children: [
            _Swatch(
              label: 'Primary',
              color: scheme.primary,
              onColor: scheme.onPrimary,
            ),
            _Swatch(
              label: 'Secondary',
              color: scheme.secondary,
              onColor: scheme.onSecondary,
            ),
            _Swatch(
              label: 'Surface',
              color: scheme.surfaceContainer,
              onColor: scheme.onSurface,
            ),
            _Swatch(
              label: 'Error',
              color: scheme.error,
              onColor: scheme.onError,
            ),
          ],
        ),
        const SizedBox(height: HikariSpacing.lg),
        Text('Headline medium', style: text.headlineMedium),
        Text('Title large', style: text.titleLarge),
        Text('Body medium', style: text.bodyMedium),
        const SizedBox(height: HikariSpacing.lg),
        Container(
          padding: const EdgeInsets.all(HikariSpacing.lg),
          decoration: BoxDecoration(
            borderRadius: HikariRadius.borderMd,
            color: scheme.surfaceContainerLow,
          ),
          child: const Text('Medium radius with large spacing'),
        ),
      ],
    );
  }
}

class _Swatch extends StatelessWidget {
  const _Swatch({
    required this.label,
    required this.color,
    required this.onColor,
  });

  final String label;
  final Color color;
  final Color onColor;

  @override
  Widget build(BuildContext context) => Container(
    width: 136,
    height: 64,
    alignment: Alignment.center,
    decoration: BoxDecoration(
      color: color,
      borderRadius: HikariRadius.borderSm,
    ),
    child: Text(label, style: TextStyle(color: onColor)),
  );
}
