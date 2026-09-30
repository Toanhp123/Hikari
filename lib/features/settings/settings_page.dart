import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_button.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';

/// Settings screen for managing appearance, extensions, storage cache, and about info.
class SettingsPage extends StatefulWidget {
  const SettingsPage({
    super.key,
    this.isOled = false,
    this.onToggleOled,
    this.onSelectAccent,
    this.onClearCache,
    this.onChooseLocalFolder,
  });

  final bool isOled;
  final ValueChanged<bool>? onToggleOled;
  final ValueChanged<Color>? onSelectAccent;
  final Future<void> Function()? onClearCache;
  final Future<void> Function()? onChooseLocalFolder;

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  late bool _isOled;
  bool _isClearingCache = false;
  String _cacheSize = '28.4 MB';

  final List<Color> _accentColors = const [
    Color(0xFF8B5CF6), // Electric Violet
    Color(0xFFEC4899), // Electric Rose
    Color(0xFF06B6D4), // Cyan
    Color(0xFF10B981), // Emerald
    Color(0xFFF59E0B), // Amber
  ];

  @override
  void initState() {
    super.initState();
    _isOled = widget.isOled;
  }

  Future<void> _handleClearCache() async {
    setState(() => _isClearingCache = true);
    try {
      if (widget.onClearCache != null) {
        await widget.onClearCache!();
      } else {
        await Future<void>.delayed(const Duration(milliseconds: 400));
      }
      if (mounted) {
        setState(() {
          _cacheSize = '0.0 MB';
          _isClearingCache = false;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Cache cleared successfully!')),
        );
      }
    } catch (_) {
      if (mounted) {
        setState(() => _isClearingCache = false);
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('Failed to clear cache.')));
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = context.hikariColors;

    return HikariScaffold(
      useSafeArea: true,
      appBar: AppBar(
        title: Text(
          'Settings',
          style: HikariTypography.titleLarge.copyWith(
            color: colors.textPrimary,
            fontWeight: FontWeight.w700,
          ),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.all(HikariSpacing.lg),
        children: [
          // Section: Appearance
          _buildSectionHeader('Appearance', colors),
          _buildSettingCard(
            colors,
            children: [
              SwitchListTile(
                title: const Text('OLED Pure Black'),
                subtitle: const Text(
                  'Optimized for OLED displays to save power and enhance contrast',
                  style: TextStyle(fontSize: 12),
                ),
                value: _isOled,
                activeThumbColor: colors.primary,
                onChanged: (val) {
                  setState(() => _isOled = val);
                  widget.onToggleOled?.call(val);
                },
              ),
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
                    Row(
                      children: _accentColors.map((c) {
                        final isSelected =
                            colors.primary.toARGB32() == c.toARGB32();
                        return Padding(
                          padding: const EdgeInsets.only(
                            right: HikariSpacing.sm,
                          ),
                          child: GestureDetector(
                            onTap: () => widget.onSelectAccent?.call(c),
                            child: Container(
                              width: 36,
                              height: 36,
                              decoration: BoxDecoration(
                                color: c,
                                shape: BoxShape.circle,
                                border: isSelected
                                    ? Border.all(
                                        color: Colors.white,
                                        width: 2.5,
                                      )
                                    : null,
                                boxShadow: isSelected
                                    ? [
                                        BoxShadow(
                                          color: c.withValues(alpha: 0.6),
                                          blurRadius: 8,
                                        ),
                                      ]
                                    : null,
                              ),
                            ),
                          ),
                        );
                      }).toList(),
                    ),
                  ],
                ),
              ),
            ],
          ),

          const SizedBox(height: HikariSpacing.xl),

          // Section: Sources & Storage
          _buildSectionHeader('Sources & Storage', colors),
          _buildSettingCard(
            colors,
            children: [
              if (widget.onChooseLocalFolder != null)
                ListTile(
                  leading: const Icon(Icons.folder_open_rounded),
                  title: const Text('Local Media Folder'),
                  subtitle: const Text(
                    'Change or rescan your local manga/novel directory',
                    style: TextStyle(fontSize: 12),
                  ),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: widget.onChooseLocalFolder,
                ),
              ListTile(
                leading: const Icon(Icons.cleaning_services_rounded),
                title: const Text('Temporary Cache'),
                subtitle: Text(
                  'Images and cached reader pages: $_cacheSize',
                  style: const TextStyle(fontSize: 12),
                ),
                trailing: SizedBox(
                  width: 96,
                  child: HikariButton(
                    label: 'Clear',
                    size: HikariButtonSize.small,
                    variant: HikariButtonVariant.secondary,
                    isLoading: _isClearingCache,
                    onPressed: _handleClearCache,
                  ),
                ),
              ),
            ],
          ),

          const SizedBox(height: HikariSpacing.xl),

          // Section: About
          _buildSectionHeader('About Hikari', colors),
          _buildSettingCard(
            colors,
            children: [
              const ListTile(
                leading: Icon(Icons.auto_awesome),
                title: Text('Hikari'),
                subtitle: Text(
                  'Version 1.1.0 · Cinematic Neo-Material',
                  style: TextStyle(fontSize: 12),
                ),
              ),
              const Divider(height: 1),
              const ListTile(
                leading: Icon(Icons.verified_user_rounded),
                title: Text('Architecture Guard'),
                subtitle: Text(
                  'Clean Layered Architecture with ADR-003 Guardrails',
                  style: TextStyle(fontSize: 12),
                ),
              ),
              const Divider(height: 1),
              const ListTile(
                leading: Icon(Icons.code_rounded),
                title: Text('License'),
                subtitle: Text(
                  'Open Source under MIT License',
                  style: TextStyle(fontSize: 12),
                ),
              ),
            ],
          ),
          const SizedBox(height: HikariSpacing.xxl),
        ],
      ),
    );
  }

  Widget _buildSectionHeader(String title, HikariColors colors) {
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

  Widget _buildSettingCard(
    HikariColors colors, {
    required List<Widget> children,
  }) {
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
