import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_button.dart';
import 'package:hikari/features/local_media/local_media_view_model.dart';

class LocalMediaFolderSummary extends StatelessWidget {
  const LocalMediaFolderSummary({
    super.key,
    required this.rootName,
    required this.status,
    required this.total,
    required this.busy,
    required this.onChooseRoot,
  });

  final String? rootName;
  final LocalMediaStatus status;
  final int? total;
  final bool busy;
  final VoidCallback onChooseRoot;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        HikariSpacing.lg,
        HikariSpacing.sm,
        HikariSpacing.lg,
        0,
      ),
      child: Material(
        color: colors.surfaceContainer,
        borderRadius: HikariRadius.borderMd,
        child: Padding(
          padding: const EdgeInsets.all(HikariSpacing.md),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Icon(Icons.folder_open_rounded, color: colors.primary),
                  const SizedBox(width: HikariSpacing.md),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          rootName?.isNotEmpty == true
                              ? rootName!
                              : switch (status) {
                                  LocalMediaStatus.loading =>
                                    'Checking local folder',
                                  LocalMediaStatus.error =>
                                    'Folder unavailable',
                                  _ => 'No folder selected',
                                },
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.titleMedium,
                        ),
                        Text(
                          total == null
                              ? 'Choose a folder to scan'
                              : total == 1
                              ? '1 local item'
                              : '$total local items',
                          style: theme.textTheme.bodySmall?.copyWith(
                            color: colors.onSurfaceVariant,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: HikariSpacing.sm),
              Align(
                alignment: AlignmentDirectional.centerEnd,
                child: HikariButton(
                  label: 'Choose folder',
                  icon: const Icon(Icons.folder_open_rounded, size: 18),
                  variant: HikariButtonVariant.secondary,
                  onPressed: busy ? null : onChooseRoot,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
