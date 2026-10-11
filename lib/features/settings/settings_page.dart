import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/core/ui/components/hikari_scaffold.dart';
import 'package:hikari/features/settings/widgets/settings_content.dart';

export 'package:hikari/features/settings/widgets/settings_content.dart'
    show AppearanceSaveStatus;

/// Settings exposes only capabilities backed by real application callbacks.
///
/// Appearance is controlled by the app composition root and saved when its
/// persistence repository is configured. Cache UI is hidden without a service.
class SettingsPage extends StatefulWidget {
  const SettingsPage({
    super.key,
    this.isOled = false,
    this.selectedAccent,
    this.appearanceSaveStatus,
    this.onRetryAppearanceSave,
    this.onToggleOled,
    this.onSelectAccent,
    this.onClearCache,
    this.cacheSizeLabel,
    this.onChooseLocalFolder,
    this.loadLocalFolderName,
    this.localFolderRevision = 0,
  });

  final bool isOled;
  final Color? selectedAccent;

  /// Null when appearance persistence is not configured.
  final AppearanceSaveStatus? appearanceSaveStatus;
  final VoidCallback? onRetryAppearanceSave;
  final ValueChanged<bool>? onToggleOled;
  final ValueChanged<Color?>? onSelectAccent;
  final Future<void> Function()? onClearCache;
  final String? cacheSizeLabel;

  /// Returns true only when the user chooses a new root; cancel is not a success.
  final Future<bool> Function()? onChooseLocalFolder;

  /// Lightweight metadata read, not a recursive media scan.
  final Future<String?> Function()? loadLocalFolderName;

  /// Increment when the selected root changes outside Settings.
  final int localFolderRevision;

  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  late bool _isOled;
  bool _isClearingCache = false;
  bool _isChoosingFolder = false;
  bool _isCheckingFolder = false;
  bool _folderStatusUnavailable = false;
  String? _localFolderName;
  int _folderLookupGeneration = 0;

  @override
  void initState() {
    super.initState();
    _isOled = widget.isOled;
    unawaited(_loadLocalFolderName());
  }

  @override
  void didUpdateWidget(SettingsPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.isOled != widget.isOled) {
      _isOled = widget.isOled;
    }
    if (oldWidget.localFolderRevision != widget.localFolderRevision ||
        oldWidget.loadLocalFolderName != widget.loadLocalFolderName) {
      unawaited(_loadLocalFolderName());
    }
  }

  /// Ignore completions from older folder reads after a root selection changes.
  Future<void> _loadLocalFolderName() async {
    final load = widget.loadLocalFolderName;
    final generation = ++_folderLookupGeneration;
    if (load == null) {
      _localFolderName = null;
      _isCheckingFolder = false;
      _folderStatusUnavailable = false;
      return;
    }

    setState(() {
      _isCheckingFolder = true;
      _folderStatusUnavailable = false;
    });
    try {
      final name = await load();
      if (!mounted || generation != _folderLookupGeneration) return;
      setState(() {
        _localFolderName = name;
        _isCheckingFolder = false;
      });
    } catch (_) {
      if (!mounted || generation != _folderLookupGeneration) return;
      setState(() {
        _isCheckingFolder = false;
        _folderStatusUnavailable = true;
      });
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
      final changed = await widget.onChooseLocalFolder!();
      if (changed && mounted) {
        // A successful picker persists a new root, including when its display
        // name happens to match the previous folder's name.
        unawaited(_loadLocalFolderName());
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Local media folder updated.')),
        );
      }
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
  void dispose() {
    _folderLookupGeneration++;
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return HikariScaffold(
      useSafeArea: true,
      appBar: AppBar(title: const Text('Settings')),
      body: SettingsContent(
        isOled: _isOled,
        selectedAccent: widget.selectedAccent,
        appearanceSaveStatus: widget.appearanceSaveStatus,
        onRetryAppearanceSave: widget.onRetryAppearanceSave,
        isClearingCache: _isClearingCache,
        isChoosingFolder: _isChoosingFolder,
        isCheckingFolder: _isCheckingFolder,
        folderStatusUnavailable: _folderStatusUnavailable,
        localFolderName: _localFolderName,
        onRetryFolderStatus: widget.loadLocalFolderName == null
            ? null
            : _loadLocalFolderName,
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
