import 'dart:async';

import 'package:flutter/material.dart';
import 'package:hikari/core/ui/surfaces/adaptive/hikari_presentation_models.dart';
import 'package:hikari/core/ui/surfaces/dialog/hikari_confirmation_dialog.dart';
import 'package:hikari/core/cache/cache_usage.dart';
import 'package:hikari/core/ui/layout/hikari_page_scaffold.dart';
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
    this.onManagePlugins,
    this.isOled = false,
    this.selectedAccent,
    this.appearanceSaveStatus,
    this.onRetryAppearanceSave,
    this.onToggleOled,
    this.onSelectAccent,
    this.onClearCache,
    this.loadCacheUsage,
    this.onChooseLocalFolder,
    this.loadLocalFolderName,
    this.localFolderRevision = 0,
  });

  final VoidCallback? onManagePlugins;
  final bool isOled;
  final Color? selectedAccent;

  /// Null when appearance persistence is not configured.
  final AppearanceSaveStatus? appearanceSaveStatus;
  final VoidCallback? onRetryAppearanceSave;
  final ValueChanged<bool>? onToggleOled;
  final ValueChanged<Color?>? onSelectAccent;
  final Future<void> Function()? onClearCache;
  final Future<CacheUsage> Function()? loadCacheUsage;

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
  bool _cacheConfirmationOpen = false;
  bool _isLoadingCacheUsage = false;
  bool _cacheUsageUnavailable = false;
  CacheUsage? _cacheUsage;
  int _cacheUsageGeneration = 0;
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
    unawaited(_loadCacheUsage());
  }

  @override
  void didUpdateWidget(SettingsPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.loadCacheUsage != widget.loadCacheUsage) {
      unawaited(_loadCacheUsage());
    }
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

  Future<void> _loadCacheUsage() async {
    final load = widget.loadCacheUsage;
    final generation = ++_cacheUsageGeneration;
    if (load == null) {
      if (mounted) {
        setState(() {
          _cacheUsage = null;
          _isLoadingCacheUsage = false;
          _cacheUsageUnavailable = false;
        });
      }
      return;
    }
    setState(() {
      _isLoadingCacheUsage = true;
      _cacheUsageUnavailable = false;
    });
    try {
      final usage = await load();
      if (!mounted || generation != _cacheUsageGeneration) return;
      setState(() {
        _cacheUsage = usage;
        _isLoadingCacheUsage = false;
      });
    } catch (_) {
      if (!mounted || generation != _cacheUsageGeneration) return;
      setState(() {
        _cacheUsage = null;
        _isLoadingCacheUsage = false;
        _cacheUsageUnavailable = true;
      });
    }
  }

  Future<void> _clearCache() async {
    final clearCache = widget.onClearCache;
    if (clearCache == null || _isClearingCache || _cacheConfirmationOpen) {
      return;
    }

    _cacheConfirmationOpen = true;
    late final HikariPresentationResult<bool> confirmed;
    try {
      confirmed = await showHikariConfirmationDialog(
        context: context,
        useRootNavigator: true,
        title: 'Clear temporary cache?',
        content: const Text(
          'Cached manga pages, novel chapters, artwork and catalog metadata '
          'will be loaded again when needed. Local previews may be rebuilt. '
          'Your Library, reading progress, selected sources and local files will not be deleted.',
        ),
        confirmLabel: 'Clear cache',
        destructive: true,
      );
    } finally {
      _cacheConfirmationOpen = false;
    }
    if (confirmed is! HikariCompleted<bool> ||
        confirmed.value != true ||
        !mounted ||
        widget.onClearCache != clearCache ||
        _isClearingCache) {
      return;
    }

    setState(() => _isClearingCache = true);
    var cleared = false;
    try {
      await clearCache();
      cleared = true;
    } catch (_) {
      // Maintenance errors propagate. Partial deletion is not success.
    } finally {
      if (mounted) {
        await _loadCacheUsage();
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                cleared
                    ? 'Temporary cache cleared.'
                    : 'Could not clear all temporary cache. Try again.',
              ),
            ),
          );
          setState(() => _isClearingCache = false);
        }
      }
    }
  }

  @override
  void dispose() {
    _folderLookupGeneration++;
    _cacheUsageGeneration++;
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return HikariPageScaffold(
      useSafeArea: true,
      showAmbientGlow: true,
      appBar: AppBar(title: const Text('Settings')),
      body: SettingsContent(
        onManagePlugins: widget.onManagePlugins,
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
        cacheUsage: _cacheUsage,
        isLoadingCacheUsage: _isLoadingCacheUsage,
        cacheUsageUnavailable: _cacheUsageUnavailable,
        onRetryCacheUsage: widget.loadCacheUsage == null
            ? null
            : _loadCacheUsage,
        onChooseLocalFolder: widget.onChooseLocalFolder == null
            ? null
            : _chooseLocalFolder,
      ),
    );
  }
}
