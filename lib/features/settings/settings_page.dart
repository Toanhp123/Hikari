import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/features/settings/widgets/settings_content.dart';

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
  bool _isChoosingFolder = false;

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

  void _toggleOled(bool value) {
    setState(() => _isOled = value);
    widget.onToggleOled?.call(value);
  }

  Future<void> _chooseLocalFolder() async {
    if (_isChoosingFolder || widget.onChooseLocalFolder == null) return;
    setState(() => _isChoosingFolder = true);
    try {
      await widget.onChooseLocalFolder!();
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: const Text('Could not choose a local folder. Try again.'),
          action: SnackBarAction(label: 'Retry', onPressed: _chooseLocalFolder),
        ),
      );
    } finally {
      if (mounted) setState(() => _isChoosingFolder = false);
    }
  }

  Future<void> _clearCache() async {
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
      body: SettingsContent(
        isOled: _isOled,
        isClearingCache: _isClearingCache,
        isChoosingFolder: _isChoosingFolder,
        onToggleOled: widget.onToggleOled == null ? null : _toggleOled,
        onSelectAccent: widget.onSelectAccent,
        onClearCache: widget.onClearCache == null ? null : _clearCache,
        cacheSizeLabel: widget.cacheSizeLabel,
        onChooseLocalFolder: widget.onChooseLocalFolder == null
            ? null
            : _chooseLocalFolder,
      ),
    );
  }
}
