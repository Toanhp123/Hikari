import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_button.dart';

class SettingsContent extends StatelessWidget {
  const SettingsContent({
    super.key,
    required this.isOled,
    this.selectedAccent,
    required this.isClearingCache,
    required this.isChoosingFolder,
    this.onToggleOled,
    this.onSelectAccent,
    this.onClearCache,
    this.cacheSizeLabel,
    this.onChooseLocalFolder,
  });

  final bool isOled;
  final Color? selectedAccent;
  final bool isClearingCache;
  final bool isChoosingFolder;
  final ValueChanged<bool>? onToggleOled;
  final ValueChanged<Color?>? onSelectAccent;
  final VoidCallback? onClearCache;
  final String? cacheSizeLabel;
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
                        const SizedBox(height: HikariSpacing.sm),
                        Text(
                          'Appearance preferences are saved on this device.',
                          style: Theme.of(context).textTheme.bodySmall
                              ?.copyWith(color: colors.onSurfaceVariant),
                        ),
                      ],
                    ),
                  ),
                ],
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
                      subtitle: const Text(
                        'Choose the folder Hikari scans for local media.',
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
                  if (onChooseLocalFolder != null && onClearCache != null)
                    const Divider(height: 1),
                  if (onClearCache != null)
                    ListTile(
                      leading: const Icon(Icons.cleaning_services_rounded),
                      title: const Text('Temporary Cache'),
                      subtitle: Text(
                        cacheSizeLabel == null
                            ? 'Clear temporary cached content.'
                            : 'Cached content: $cacheSizeLabel',
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
                  subtitle: Text('Manga, novels, and local video in one place.'),
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
                      selected ? Icons.check_rounded : Icons.restart_alt_rounded,
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
