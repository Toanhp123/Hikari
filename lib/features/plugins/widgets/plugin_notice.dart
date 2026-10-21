import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';

/// Feature-level notice for errors, delayed native evidence and consent context.
/// Does not interpret plugin operation status or grant any authority.
final class PluginNotice extends StatelessWidget {
  const PluginNotice({super.key, required this.message, this.error = false});

  final String message;
  final bool error;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final foreground = error
        ? colors.onErrorContainer
        : colors.onSurfaceVariant;
    final background = error
        ? colors.errorContainer
        : colors.surfaceContainerHigh;
    return Semantics(
      liveRegion: error,
      child: Material(
        color: background,
        borderRadius: HikariRadius.borderMd,
        child: Padding(
          padding: const EdgeInsets.all(HikariSpacing.md),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(
                error
                    ? Icons.error_outline_rounded
                    : Icons.info_outline_rounded,
                color: foreground,
                size: HikariSize.icon,
              ),
              const SizedBox(width: HikariSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      message,
                      style: Theme.of(context).textTheme.bodySmall
                          ?.copyWith(color: foreground),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
