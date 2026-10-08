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
    return ListView(
      padding: const EdgeInsets.all(HikariSpacing.lg),
      children: [
        _SectionHeader(title: 'Appearance'),
        _SettingsCard(
          children: [
            SwitchListTile(
              title: const Text('OLED Pure Black'),
              subtitle: const Text(
                'Use a pure-black background on OLED displays.',
              ),
              value: isOled,
              activeThumbColor: colors.primary,
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
                      style: Theme.of(context).textTheme.bodyMedium
                          ?.copyWith(fontWeight: FontWeight.w500),
                    ),
                    const SizedBox(height: HikariSpacing.sm),
                    SingleChildScrollView(
                      scrollDirection: Axis.horizontal,
                      child: Row(
                        children: [
                          Padding(
                            padding: const EdgeInsets.only(
                              right: HikariSpacing.sm,
                            ),
                            child: Semantics(
                              label: 'Default accent',
                              selected: selectedAccent == null,
                              button: true,
                              child: InkWell(
                                onTap: () => onSelectAccent!(null),
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
                                        color: HikariTheme.defaultAccentSeed,
                                        border: selectedAccent == null
                                            ? Border.all(
                                                color: colors.onSurface,
                                                width: 2.5,
                                              )
                                            : null,
                                      ),
                                      child: const Icon(
                                        Icons.restart_alt_rounded,
                                        size: 21,
                                        color: Colors.black87,
                                      ),
                                    ),
                                  ),
                                ),
                              ),
                            ),
                          ),
                          ...HikariTheme.accentPresets.map((accent) {
                            final (name, color) = accent;
                            final selected = selectedAccent == color;
                            return Padding(
                              padding: const EdgeInsets.only(
                                right: HikariSpacing.sm,
                              ),
                              child: Semantics(
                                label: '$name accent',
                                selected: selected,
                                button: true,
                                child: InkWell(
                                  onTap: () => onSelectAccent!(color),
                                  customBorder: const CircleBorder(),
                                  child: SizedBox(
                                    width: 48,
                                    height: 48,
                                    child: Center(
                                      child: Container(
                                        width: 36,
                                        height: 36,
                                        decoration: BoxDecoration(
                                          color: color,
                                          shape: BoxShape.circle,
                                          border: selected
                                              ? Border.all(
                                                  color: colors.onSurface,
                                                  width: 2.5,
                                                )
                                              : null,
                                        ),
                                      ),
                                    ),
                                  ),
                                ),
                              ),
                            );
                          }),
                        ],
                      ),
                    ),
                    const SizedBox(height: HikariSpacing.xs),
                    Text(
                      'Appearance preferences currently apply to this app session.',
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
          _SectionHeader(title: 'Sources & Storage'),
          _SettingsCard(
            children: [
              if (onChooseLocalFolder != null)
                ListTile(
                  leading: const Icon(Icons.folder_open_rounded),
                  title: const Text('Local Media Folder'),
                  subtitle: const Text(
                    'Change Hikari\'s persisted local-media root.',
                  ),
                  trailing: const Icon(Icons.chevron_right_rounded),
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
        _SectionHeader(title: 'About Hikari'),
        const _SettingsCard(
          children: [
            ListTile(
              leading: Icon(Icons.auto_awesome),
              title: Text('Hikari'),
              subtitle: Text('Anime, manga, and light-novel media client.'),
            ),
          ],
        ),
        const SizedBox(height: HikariSpacing.xxl),
      ],
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
      child: Text(
        title,
        style: Theme.of(context).textTheme.labelMedium?.copyWith(
          color: colors.primary.withValues(alpha: 0.8),
          fontWeight: FontWeight.w600,
          letterSpacing: 0.5,
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
        side: BorderSide(color: colors.outline),
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(children: children),
    );
  }
}
