import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_button.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';

/// Settings exposes only capabilities backed by real application callbacks.
///
/// Appearance remains app-session state until Hikari introduces a persisted
/// preferences contract. Cache UI is hidden when no cache service is composed.
class SettingsPage extends StatefulWidget {
  const SettingsPage({
    super.key,
    this.isOled = false,
    this.onToggleOled,
    this.onSelectAccent,
    this.onClearCache,
    this.cacheSizeLabel,
    this.onChooseLocalFolder,
  });

  final bool isOled;
  final ValueChanged<bool>? onToggleOled;
  final ValueChanged<Color>? onSelectAccent;
  final Future<void> Function()? onClearCache;
  final String? cacheSizeLabel;
  final Future<void> Function()? onChooseLocalFolder;

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  late bool _isOled;
  bool _isClearingCache = false;

  static const _accentColors = [
    Color(0xFF8B5CF6),
    Color(0xFFEC4899),
    Color(0xFF06B6D4),
    Color(0xFF10B981),
    Color(0xFFF59E0B),
  ];

  @override
  void initState() {
    super.initState();
    _isOled = widget.isOled;
  }

  @override
  void didUpdateWidget(SettingsPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.isOled != widget.isOled) {
      _isOled = widget.isOled;
    }
  }

  Future<void> _handleClearCache() async {
    final clearCache = widget.onClearCache;
    if (clearCache == null || _isClearingCache) return;

    setState(() => _isClearingCache = true);
    try {
      await clearCache();
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('Cache cleared.')));
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(const SnackBar(content: Text('Failed to clear cache.')));
    } finally {
      if (mounted) setState(() => _isClearingCache = false);
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
          _buildSectionHeader('Appearance', colors),
          _buildSettingCard(
            colors,
            children: [
              SwitchListTile(
                title: const Text('OLED Pure Black'),
                subtitle: const Text(
                  'Use a pure-black background on OLED displays.',
                  style: TextStyle(fontSize: 12),
                ),
                value: _isOled,
                activeThumbColor: colors.primary,
                onChanged: widget.onToggleOled == null
                    ? null
                    : (value) {
                        setState(() => _isOled = value);
                        widget.onToggleOled!(value);
                      },
              ),
              if (widget.onSelectAccent != null) ...[
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
                        children: _accentColors.map((color) {
                          final selected =
                              colors.primary.toARGB32() == color.toARGB32();
                          return Padding(
                            padding: const EdgeInsets.only(
                              right: HikariSpacing.sm,
                            ),
                            child: InkResponse(
                              onTap: () => widget.onSelectAccent!(color),
                              radius: 24,
                              child: Semantics(
                                label: 'Select accent color',
                                selected: selected,
                                button: true,
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
                          );
                        }).toList(),
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
          if (widget.onChooseLocalFolder != null ||
              widget.onClearCache != null) ...[
            const SizedBox(height: HikariSpacing.xl),
            _buildSectionHeader('Sources & Storage', colors),
            _buildSettingCard(
              colors,
              children: [
                if (widget.onChooseLocalFolder != null)
                  ListTile(
                    leading: const Icon(Icons.folder_open_rounded),
                    title: const Text('Local Media Folder'),
                    subtitle: const Text(
                      'Change Hikari\'s persisted local-media root.',
                      style: TextStyle(fontSize: 12),
                    ),
                    trailing: const Icon(Icons.chevron_right_rounded),
                    onTap: widget.onChooseLocalFolder,
                  ),
                if (widget.onChooseLocalFolder != null &&
                    widget.onClearCache != null)
                  const Divider(height: 1),
                if (widget.onClearCache != null)
                  ListTile(
                    leading: const Icon(Icons.cleaning_services_rounded),
                    title: const Text('Temporary Cache'),
                    subtitle: Text(
                      widget.cacheSizeLabel == null
                          ? 'Clear temporary cached content.'
                          : 'Cached content: ${widget.cacheSizeLabel}',
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
          ],
          const SizedBox(height: HikariSpacing.xl),
          _buildSectionHeader('About Hikari', colors),
          _buildSettingCard(
            colors,
            children: const [
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
