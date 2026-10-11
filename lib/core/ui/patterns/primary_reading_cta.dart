import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';

/// Primary action for starting or continuing reading.
class PrimaryReadingCta extends StatelessWidget {
  const PrimaryReadingCta({
    super.key,
    this.label = 'Start reading',
    required this.onPressed,
    this.enabled = true,
    this.padding = const EdgeInsets.symmetric(
      horizontal: HikariSpacing.md,
      vertical: HikariSpacing.sm,
    ),
  });

  final String label;
  final VoidCallback? onPressed;
  final bool enabled;
  final EdgeInsetsGeometry padding;

  @override
  Widget build(BuildContext context) => Padding(
    padding: padding,
    child: SizedBox(
      width: double.infinity,
      child: FilledButton.icon(
        onPressed: enabled ? onPressed : null,
        icon: const Icon(Icons.play_arrow_rounded),
        label: Text(label),
        style: FilledButton.styleFrom(
          shape: const RoundedRectangleBorder(
            borderRadius: HikariRadius.borderMd,
          ),
          padding: const EdgeInsets.symmetric(
            vertical: HikariSpacing.md,
            horizontal: HikariSpacing.lg,
          ),
        ),
      ),
    ),
  );
}
