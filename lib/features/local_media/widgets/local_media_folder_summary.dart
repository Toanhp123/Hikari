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
    this.failure,
  });

  final String? rootName;
  final LocalMediaStatus status;
  final int? total;
  final bool busy;
  final VoidCallback onChooseRoot;
  final LocalMediaFailure? failure;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final accessLost = failure == LocalMediaFailure.accessLost;
    final previousScan = status == LocalMediaStatus.error && total != null;
    final itemCount = total == 1 ? '1 local item' : '$total local items';
    final subtitle = total == null
        ? 'Choose a folder to scan'
        : previousScan
        ? '$itemCount · Previous scan'
        : itemCount;

    final details = Row(
      children: [
        Icon(
          accessLost ? Icons.folder_off_outlined : Icons.folder_open_rounded,
          color: accessLost ? colors.error : colors.primary,
        ),
        const SizedBox(width: HikariSpacing.md),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                rootName?.isNotEmpty == true
                    ? rootName!
                    : switch (status) {
                        LocalMediaStatus.loading => 'Checking local folder',
                        LocalMediaStatus.error => 'Folder unavailable',
                        _ => 'No folder selected',
                      },
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.titleMedium,
              ),
              Text(
                subtitle,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.bodySmall?.copyWith(
                  color: colors.onSurfaceVariant,
                ),
              ),
            ],
          ),
        ),
      ],
    );

    final chooseFolder = HikariButton(
      label: 'Choose folder',
      icon: const Icon(Icons.folder_open_rounded, size: 18),
      variant: HikariButtonVariant.secondary,
      onPressed: busy ? null : onChooseRoot,
    );

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
          child: LayoutBuilder(
            builder: (context, constraints) {
              // Keep a compact toolbar for selected roots without squeezing
              // the folder label or the action at larger text scales.
              final inline =
                  total != null &&
                  constraints.maxWidth >= 300 &&
                  MediaQuery.textScalerOf(context).scale(1) <= 1.3;
              if (inline) {
                return Row(
                  children: [
                    Expanded(child: details),
                    const SizedBox(width: HikariSpacing.sm),
                    chooseFolder,
                  ],
                );
              }
              return Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  details,
                  const SizedBox(height: HikariSpacing.sm),
                  Align(
                    alignment: AlignmentDirectional.centerEnd,
                    child: chooseFolder,
                  ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }
}
