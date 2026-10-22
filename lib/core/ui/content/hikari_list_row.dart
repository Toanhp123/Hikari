import 'package:flutter/material.dart';

import '../foundation/hikari_size.dart';
import '../foundation/hikari_spacing.dart';

/// Reflowing domain-free row, without ListTile's fixed subtitle/line assumptions.
class HikariListRow extends StatelessWidget {
  const HikariListRow({
    super.key,
    required this.title,
    this.subtitle,
    this.leading,
    this.trailing,
    this.onTap,
    this.enabled = true,
  });

  final Widget title;
  final Widget? subtitle;
  final Widget? leading;
  final Widget? trailing;
  final VoidCallback? onTap;
  final bool enabled;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final foreground = enabled
        ? theme.colorScheme.onSurface
        : theme.colorScheme.onSurface.withValues(alpha: .38);
    return Semantics(
      button: onTap != null,
      enabled: onTap == null ? null : enabled,
      child: InkWell(
        onTap: enabled ? onTap : null,
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: HikariSize.touchTarget),
          child: Padding(
            padding: const EdgeInsets.all(HikariSpacing.md),
            child: DefaultTextStyle.merge(
              style: theme.textTheme.bodyLarge!.copyWith(color: foreground),
              child: IconTheme.merge(
                data: IconThemeData(color: foreground),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (leading != null) ...[
                      leading!,
                      const SizedBox(width: HikariSpacing.md),
                    ],
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          title,
                          if (subtitle != null) ...[
                            const SizedBox(height: HikariSpacing.xs),
                            DefaultTextStyle.merge(
                              style: theme.textTheme.bodyMedium!.copyWith(
                                color: enabled
                                    ? theme.colorScheme.onSurfaceVariant
                                    : foreground,
                              ),
                              child: subtitle!,
                            ),
                          ],
                        ],
                      ),
                    ),
                    if (trailing != null) ...[
                      const SizedBox(width: HikariSpacing.sm),
                      trailing!,
                    ],
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
