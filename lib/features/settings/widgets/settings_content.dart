import 'package:flutter/material.dart';
import 'package:hikari/core/cache/cache_usage.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_button.dart';

/// Persistence state for the most recently requested appearance selection.
enum AppearanceSaveStatus { saved, saving, failed }

class SettingsContent extends StatelessWidget {
  const SettingsContent({
    super.key,
    required this.isOled,
    this.selectedAccent,
    this.appearanceSaveStatus,
    this.onRetryAppearanceSave,
    required this.isClearingCache,
    required this.isChoosingFolder,
    this.isCheckingFolder = false,
    this.folderStatusUnavailable = false,
    this.localFolderName,
    this.onRetryFolderStatus,
    this.onToggleOled,
    this.onSelectAccent,
    this.onClearCache,
    this.cacheUsage,
    this.isLoadingCacheUsage = false,
    this.cacheUsageUnavailable = false,
    this.onRetryCacheUsage,
    this.onChooseLocalFolder,
  });

  final bool isOled;
  final Color? selectedAccent;
  final AppearanceSaveStatus? appearanceSaveStatus;
  final VoidCallback? onRetryAppearanceSave;
  final bool isClearingCache;
  final bool isChoosingFolder;
  final bool isCheckingFolder;
  final bool folderStatusUnavailable;
  final String? localFolderName;
  final VoidCallback? onRetryFolderStatus;
  final ValueChanged<bool>? onToggleOled;
  final ValueChanged<Color?>? onSelectAccent;
  final VoidCallback? onClearCache;
  final CacheUsage? cacheUsage;
  final bool isLoadingCacheUsage;
  final bool cacheUsageUnavailable;
  final VoidCallback? onRetryCacheUsage;
  final VoidCallback? onChooseLocalFolder;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    // Keep preferences readable instead of stretching each row on desktop.
    return Align(
      alignment: Alignment.topCenter,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 640),
        child: ListView(
          padding: const EdgeInsets.all(HikariSpacing.lg),
          children: [
            const _SectionHeader(title: 'Appearance'),
            _SettingsCard(
              children: [
                SwitchListTile(
                  title: const Text('OLED Pure Black'),
                  subtitle: const Text(
                    'Use black main backgrounds on OLED displays.',
                  ),
                  value: isOled,
                  onChanged: onToggleOled,
                ),
                if (onSelectAccent != null) ...[
                  const Divider(height: 1),
                  Padding(
                    padding: const EdgeInsets.all(HikariSpacing.md),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Accent Color',
                          style: Theme.of(context).textTheme.bodyLarge,
                        ),
                        const SizedBox(height: HikariSpacing.sm),
                        Wrap(
                          spacing: HikariSpacing.sm,
                          runSpacing: HikariSpacing.sm,
                          children: [
                            _AccentSwatch(
                              label: 'Default accent',
                              color: HikariTheme.defaultAccentSeed,
                              selected: selectedAccent == null,
                              isDefault: true,
                              onTap: () => onSelectAccent!(null),
                            ),
                            for (final (name, color)
                                in HikariTheme.accentPresets)
                              _AccentSwatch(
                                label: '$name accent',
                                color: color,
                                selected: selectedAccent == color,
                                onTap: () => onSelectAccent!(color),
                              ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ],
                if (appearanceSaveStatus != null)
                  Padding(
                    padding: const EdgeInsetsDirectional.only(
                      start: HikariSpacing.md,
                      end: HikariSpacing.md,
                      bottom: HikariSpacing.md,
                    ),
                    child: appearanceSaveStatus == AppearanceSaveStatus.failed
                        ? Semantics(
                            liveRegion: true,
                            child: Row(
                              children: [
                                Icon(
                                  Icons.error_outline_rounded,
                                  color: colors.error,
                                  size: 20,
                                ),
                                const SizedBox(width: HikariSpacing.sm),
                                Expanded(
                                  child: Text(
                                    'Could not save appearance. Changes may be lost after restart.',
                                    style: Theme.of(context).textTheme.bodySmall
                                        ?.copyWith(color: colors.error),
                                  ),
                                ),
                                if (onRetryAppearanceSave != null)
                                  TextButton(
                                    onPressed: onRetryAppearanceSave,
                                    child: const Text('Retry'),
                                  ),
                              ],
                            ),
                          )
                        : Text(
                            appearanceSaveStatus == AppearanceSaveStatus.saving
                                ? 'Saving appearance…'
                                : 'Appearance saved on this device.',
                            style: Theme.of(context).textTheme.bodySmall
                                ?.copyWith(color: colors.onSurfaceVariant),
                          ),
                  ),
              ],
            ),
            if (onChooseLocalFolder != null || onClearCache != null) ...[
              const SizedBox(height: HikariSpacing.xl),
              const _SectionHeader(title: 'Files & storage'),
              _SettingsCard(
                children: [
                  if (onChooseLocalFolder != null)
                    ListTile(
                      leading: const Icon(Icons.folder_open_rounded),
                      title: const Text('Local Media Folder'),
                      subtitle: Text(
                        isCheckingFolder
                            ? 'Checking selected folder…'
                            : folderStatusUnavailable
                            ? 'Folder unavailable. Check access or try again.'
                            : localFolderName == null
                            ? 'No folder selected. Choose one to scan media.'
                            : localFolderName!,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      trailing: isChoosingFolder
                          ? const SizedBox(
                              width: 24,
                              height: 24,
                              child: CircularProgressIndicator(strokeWidth: 2),
                            )
                          : const Icon(Icons.chevron_right_rounded),
                      onTap: isChoosingFolder ? null : onChooseLocalFolder,
                    ),
                  if (folderStatusUnavailable && onRetryFolderStatus != null)
                    Padding(
                      padding: const EdgeInsetsDirectional.only(
                        end: HikariSpacing.sm,
                        bottom: HikariSpacing.xs,
                      ),
                      child: Align(
                        alignment: AlignmentDirectional.centerEnd,
                        child: TextButton.icon(
                          onPressed: isCheckingFolder
                              ? null
                              : onRetryFolderStatus,
                          icon: const Icon(Icons.refresh_rounded),
                          label: const Text('Retry folder check'),
                        ),
                      ),
                    ),
                  if (onChooseLocalFolder != null && onClearCache != null)
                    const Divider(height: 1),
                  if (onClearCache != null)
                    ListTile(
                      leading: const Icon(Icons.cleaning_services_rounded),
                      title: const Text('Temporary Cache'),
                      subtitle: Text(
                        isLoadingCacheUsage
                            ? 'Measuring cache…'
                            : cacheUsageUnavailable
                            ? 'Cache size unavailable.'
                            : cacheUsage == null
                            ? 'Clear cached reading, artwork and catalog metadata.'
                            : 'Cached temporary data: '
                                  '${_formatCacheBytes(cacheUsage!.totalBytes)}',
                      ),
                      trailing: SizedBox(
                        width: 96,
                        child: HikariButton(
                          label: 'Clear',
                          size: HikariButtonSize.small,
                          variant: HikariButtonVariant.secondary,
                          isLoading: isClearingCache,
                          onPressed: onClearCache,
                        ),
                      ),
                    ),
                  if (cacheUsage != null && onClearCache != null)
                    Padding(
                      padding: const EdgeInsetsDirectional.only(
                        start: HikariSpacing.md,
                        end: HikariSpacing.md,
                        bottom: HikariSpacing.md,
                      ),
                      child: Column(
                        children: [
                          for (final entry in cacheUsage!.byCategory.entries)
                            Padding(
                              padding: const EdgeInsets.symmetric(
                                vertical: HikariSpacing.xs,
                              ),
                              child: Row(
                                children: [
                                  Expanded(child: Text(entry.key)),
                                  Text(_formatCacheBytes(entry.value)),
                                ],
                              ),
                            ),
                          Text(
                            'Indexed reading, artwork and catalog metadata. '
                            'Local files, native HTTP cache and image memory '
                            'are excluded.',
                            style: Theme.of(context).textTheme.bodySmall
                                ?.copyWith(color: colors.onSurfaceVariant),
                          ),
                        ],
                      ),
                    ),
                  if (cacheUsageUnavailable && onRetryCacheUsage != null)
                    Align(
                      alignment: AlignmentDirectional.centerEnd,
                      child: TextButton(
                        onPressed: isLoadingCacheUsage
                            ? null
                            : onRetryCacheUsage,
                        child: const Text('Retry cache size'),
                      ),
                    ),
                ],
              ),
            ],
            const SizedBox(height: HikariSpacing.xl),
            const _SectionHeader(title: 'About Hikari'),
            const _SettingsCard(
              children: [
                ListTile(
                  leading: Icon(Icons.auto_awesome),
                  title: Text('Hikari'),
                  subtitle: Text(
                    'Manga, novels, and local video in one place.',
                  ),
                ),
              ],
            ),
            const SizedBox(height: HikariSpacing.xxl),
          ],
        ),
      ),
    );
  }
}

/// A single color choice with a 48dp target, focus feedback and non-color cue.
String _formatCacheBytes(int bytes) {
  if (bytes < 1024) return '$bytes B';
  final kib = bytes / 1024;
  if (kib < 1024) return '${kib.toStringAsFixed(1)} KiB';
  return '${(kib / 1024).toStringAsFixed(1)} MiB';
}

class _AccentSwatch extends StatelessWidget {
  const _AccentSwatch({
    required this.label,
    required this.color,
    required this.selected,
    required this.onTap,
    this.isDefault = false,
  });

  final String label;
  final Color color;
  final bool selected;
  final bool isDefault;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final iconColor =
        ThemeData.estimateBrightnessForColor(color) == Brightness.dark
        ? Colors.white
        : Colors.black;
    return Semantics(
      label: label,
      selected: selected,
      button: true,
      child: InkWell(
        onTap: onTap,
        customBorder: const CircleBorder(),
        child: SizedBox(
          width: 48,
          height: 48,
          child: Center(
            child: Container(
              width: 36,
              height: 36,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: color,
                border: selected
                    ? Border.all(color: colors.onSurface, width: 2.5)
                    : null,
              ),
              child: selected || isDefault
                  ? Icon(
                      selected
                          ? Icons.check_rounded
                          : Icons.restart_alt_rounded,
                      size: 21,
                      color: iconColor,
                    )
                  : null,
            ),
          ),
        ),
      ),
    );
  }
}

class _SectionHeader extends StatelessWidget {
  const _SectionHeader({required this.title});

  final String title;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Padding(
      padding: const EdgeInsets.only(
        left: HikariSpacing.xs,
        bottom: HikariSpacing.sm,
      ),
      child: Semantics(
        header: true,
        child: Text(
          title,
          style: Theme.of(context).textTheme.labelMedium?.copyWith(
            color: colors.primary.withValues(alpha: 0.8),
            fontWeight: FontWeight.w600,
            letterSpacing: 0.5,
          ),
        ),
      ),
    );
  }
}

class _SettingsCard extends StatelessWidget {
  const _SettingsCard({required this.children});

  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Material(
      color: colors.surfaceContainer,
      shape: RoundedRectangleBorder(
        borderRadius: HikariRadius.borderMd,
        side: BorderSide(color: colors.outlineVariant),
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(children: children),
    );
  }
}
