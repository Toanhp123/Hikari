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
  final ValueChanged<Color>? onSelectAccent;
  final VoidCallback? onClearCache;
  final String? cacheSizeLabel;
  final VoidCallback? onChooseLocalFolder;

  static const _accentColors = <(String, Color)>[
    ('Violet', Color(0xFF8B5CF6)),
    ('Pink', Color(0xFFEC4899)),
    ('Cyan', Color(0xFF06B6D4)),
    ('Green', Color(0xFF10B981)),
    ('Amber', Color(0xFFF59E0B)),
  ];

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;
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
                style: TextStyle(fontSize: 12),
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
                    const Text(
                      'Accent Color',
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: FontWeight.w500,
                      ),
                    ),
                    const SizedBox(height: HikariSpacing.sm),
                    SingleChildScrollView(
                      scrollDirection: Axis.horizontal,
                      child: Row(
                        children: _accentColors.map((accent) {
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
                                                color: colors.textPrimary,
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
                        }).toList(),
                      ),
                    ),
                    const SizedBox(height: HikariSpacing.xs),
                    Text(
                      'Appearance preferences currently apply to this app session.',
                      style: TextStyle(fontSize: 11, color: colors.textMuted),
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
                    style: TextStyle(fontSize: 12),
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
                    style: const TextStyle(fontSize: 12),
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
              subtitle: Text(
                'Anime, manga, and light-novel media client.',
                style: TextStyle(fontSize: 12),
              ),
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
    final colors = context.hikariColors;
    return Padding(
      padding: const EdgeInsets.only(
        left: HikariSpacing.xs,
        bottom: HikariSpacing.sm,
      ),
      child: Text(
        title,
        style: TextStyle(
          fontSize: 12,
          fontWeight: FontWeight.w600,
          color: colors.primaryGlow,
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
    final colors = context.hikariColors;
    return Material(
      color: colors.surfaceContainer,
      shape: RoundedRectangleBorder(
        borderRadius: HikariRadius.borderMd,
        side: BorderSide(color: colors.border),
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(children: children),
    );
  }
}
