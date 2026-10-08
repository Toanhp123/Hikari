import 'dart:math' as math;
import 'dart:ui';

import 'package:flutter/material.dart';

import 'package:hikari/app/theme/hikari_theme.dart';

enum AppTab {
  home,
  search,
  local,
  library,
  settings;

  String get label => switch (this) {
    AppTab.home => 'Home',
    AppTab.search => 'Search',
    AppTab.local => 'Local',
    AppTab.library => 'Library',
    AppTab.settings => 'Settings',
  };

  IconData get icon => switch (this) {
    AppTab.home => Icons.home_outlined,
    AppTab.search => Icons.search_rounded,
    AppTab.local => Icons.folder_open_rounded,
    AppTab.library => Icons.collections_bookmark_outlined,
    AppTab.settings => Icons.settings_outlined,
  };

  IconData get selectedIcon => switch (this) {
    AppTab.home => Icons.home_rounded,
    AppTab.search => Icons.search_rounded,
    AppTab.local => Icons.folder_open_rounded,
    AppTab.library => Icons.collections_bookmark_rounded,
    AppTab.settings => Icons.settings_rounded,
  };
}

/// App-owned navigation state shared with the root composition layer.
final class AppNavigationController extends ChangeNotifier {
  AppNavigationController({AppTab initialTab = AppTab.home})
    : _currentTab = initialTab;

  AppTab _currentTab;
  AppTab get currentTab => _currentTab;

  void selectTab(AppTab tab) {
    if (_currentTab == tab) return;
    _currentTab = tab;
    notifyListeners();
  }
}

/// Adaptive Navigation Shell supporting Docked Tonal Bottom Bar (< 600dp)
/// and Navigation Rail / Sidebar (>= 600dp) with IndexedStack state preservation.
class AppNavigationShell extends StatefulWidget {
  const AppNavigationShell({
    super.key,
    required this.tabs,
    this.initialTab = AppTab.home,
    this.controller,
    this.onTabChanged,
  });

  final Map<AppTab, Widget> tabs;
  final AppTab initialTab;
  final AppNavigationController? controller;
  final ValueChanged<AppTab>? onTabChanged;

  @override
  State<AppNavigationShell> createState() => _AppNavigationShellState();
}

class _AppNavigationShellState extends State<AppNavigationShell> {
  late final AppNavigationController _controller;
  late final bool _ownsController;
  late int _currentIndex;
  late final Set<int> _loadedIndices;

  @override
  void initState() {
    super.initState();
    _ownsController = widget.controller == null;
    _controller =
        widget.controller ??
        AppNavigationController(initialTab: widget.initialTab);
    _currentIndex = _controller.currentTab.index;
    _loadedIndices = {_currentIndex};
    _controller.addListener(_handleControllerChanged);
  }

  @override
  void dispose() {
    _controller.removeListener(_handleControllerChanged);
    if (_ownsController) _controller.dispose();
    super.dispose();
  }

  void _handleControllerChanged() {
    final nextIndex = _controller.currentTab.index;
    if (_currentIndex == nextIndex) return;
    setState(() {
      _currentIndex = nextIndex;
      _loadedIndices.add(nextIndex);
    });
    widget.onTabChanged?.call(_controller.currentTab);
  }

  void _selectTab(AppTab tab) {
    _controller.selectTab(tab);
  }

  @override
  Widget build(BuildContext context) {
    if (!AppTab.values.every(widget.tabs.containsKey)) {
      throw ArgumentError('A page is required for every AppTab.');
    }
    final isCompact =
        HikariBreakpoints.classify(MediaQuery.sizeOf(context).width) ==
        HikariWidthClass.compact;

    final children = [
      for (final tab in AppTab.values)
        if (_loadedIndices.contains(tab.index))
          widget.tabs[tab]!
        else
          const SizedBox.shrink(),
    ];

    // Keep visited pages under the same Element ancestry on every resize.
    // Only the surrounding navigation chrome changes at 600dp.
    return Scaffold(
      extendBody: true,
      backgroundColor: Colors.transparent,
      body: Row(
        children: [
          if (!isCompact) _buildNavigationRail(context),
          Expanded(
            child: IndexedStack(index: _currentIndex, children: children),
          ),
        ],
      ),
      bottomNavigationBar: isCompact ? _buildDockedBottomBar(context) : null,
    );
  }

  Widget _buildDockedBottomBar(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final textTheme = theme.textTheme;
    final labelMetrics = TextPainter(
      text: TextSpan(
        text: 'Settings',
        style: (textTheme.labelSmall ?? const TextStyle()).copyWith(
          fontSize: 12,
        ),
      ),
      textDirection: Directionality.of(context),
      textScaler: MediaQuery.textScalerOf(context),
      maxLines: 1,
    )..layout();
    final barHeight = math.max(72.0, 32 + 4 + labelMetrics.height + 16);
    labelMetrics.dispose();

    return RepaintBoundary(
      child: ClipRect(
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 16, sigmaY: 16),
          child: Container(
            decoration: BoxDecoration(
              color: colors.surfaceContainerLowest.withValues(alpha: 0.88),
              border: Border(
                top: BorderSide(
                  color: colors.outlineVariant.withValues(alpha: 0.6),
                  width: HikariRadius.borderWidth,
                ),
              ),
            ),
            child: SafeArea(
              top: false,
              child: SizedBox(
                height: barHeight,
                child: Row(
                  children: AppTab.values.map((tab) {
                    final isSelected = tab.index == _currentIndex;
                    return Expanded(
                      child: Semantics(
                        button: true,
                        selected: isSelected,
                        label: tab.label,
                        onTap: () => _selectTab(tab),
                        child: ExcludeSemantics(
                          child: Tooltip(
                            message: tab.label,
                            child: InkWell(
                              onTap: () => _selectTab(tab),
                              splashColor: colors.primary.withValues(
                                alpha: 0.12,
                              ),
                              highlightColor: Colors.transparent,
                              child: SizedBox(
                                height: barHeight,
                                child: Column(
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    AnimatedContainer(
                                      duration: HikariMotion.duration(
                                        context,
                                        HikariMotion.standard,
                                      ),
                                      curve: HikariMotion.curveStandard,
                                      width: isSelected ? 60 : 40,
                                      height: 32,
                                      decoration: BoxDecoration(
                                        color: isSelected
                                            ? colors.secondaryContainer
                                            : Colors.transparent,
                                        borderRadius: BorderRadius.circular(16),
                                      ),
                                      alignment: Alignment.center,
                                      child: Icon(
                                        isSelected
                                            ? tab.selectedIcon
                                            : tab.icon,
                                        size: 22,
                                        color: isSelected
                                            ? colors.onSecondaryContainer
                                            : colors.onSurfaceVariant,
                                      ),
                                    ),
                                    const SizedBox(height: 4),
                                    AnimatedDefaultTextStyle(
                                      duration: HikariMotion.duration(
                                        context,
                                        HikariMotion.standard,
                                      ),
                                      curve: HikariMotion.curveStandard,
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                      style:
                                          (textTheme.labelSmall ??
                                                  const TextStyle())
                                              .copyWith(
                                                fontSize: 12,
                                                fontWeight: isSelected
                                                    ? FontWeight.w600
                                                    : FontWeight.w500,
                                                letterSpacing: 0.2,
                                                color: isSelected
                                                    ? colors.onSurface
                                                    : colors.onSurfaceVariant,
                                              ),
                                      child: Text(tab.label),
                                    ),
                                  ],
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
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildNavigationRail(BuildContext context) {
    final theme = Theme.of(context);
    final colors = theme.colorScheme;
    final textTheme = theme.textTheme;

    return RepaintBoundary(
      child: Container(
        width: 88,
        decoration: BoxDecoration(
          color: colors.surfaceContainerLow,
          border: Border(
            right: BorderSide(
              color: colors.outlineVariant.withValues(alpha: 0.6),
              width: HikariRadius.borderWidth,
            ),
          ),
        ),
        child: SafeArea(
          right: false,
          child: Column(
            children: [
              const SizedBox(height: HikariSpacing.xl),
              // Hikari Brand Logo Icon
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [colors.primary, colors.secondary],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  borderRadius: HikariRadius.borderMd,
                  boxShadow: [
                    BoxShadow(
                      color: colors.primary.withValues(alpha: 0.25),
                      blurRadius: 12,
                      offset: const Offset(0, 4),
                    ),
                  ],
                ),
                child: Icon(
                  Icons.auto_awesome,
                  color: colors.onPrimary,
                  size: 22,
                ),
              ),
              const SizedBox(height: HikariSpacing.xxl),
              // Navigation Items
              Expanded(
                child: ListView(
                  padding: EdgeInsets.zero,
                  children: AppTab.values.map((tab) {
                    final isSelected = tab.index == _currentIndex;
                    return Padding(
                      padding: const EdgeInsets.symmetric(
                        vertical: HikariSpacing.xs,
                        horizontal: HikariSpacing.sm,
                      ),
                      child: IconButton(
                        isSelected: isSelected,
                        tooltip: tab.label,
                        onPressed: () => _selectTab(tab),
                        style: IconButton.styleFrom(
                          padding: const EdgeInsets.symmetric(
                            vertical: HikariSpacing.md,
                          ),
                          shape: const RoundedRectangleBorder(
                            borderRadius: HikariRadius.borderMd,
                          ),
                        ),
                        icon: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            AnimatedContainer(
                              duration: HikariMotion.duration(
                                context,
                                HikariMotion.standard,
                              ),
                              curve: HikariMotion.curveStandard,
                              width: isSelected ? 56 : 40,
                              height: 32,
                              decoration: BoxDecoration(
                                color: isSelected
                                    ? colors.secondaryContainer
                                    : Colors.transparent,
                                borderRadius: BorderRadius.circular(16),
                              ),
                              alignment: Alignment.center,
                              child: Icon(
                                isSelected ? tab.selectedIcon : tab.icon,
                                size: 22,
                                color: isSelected
                                    ? colors.onSecondaryContainer
                                    : colors.onSurfaceVariant,
                              ),
                            ),
                            const SizedBox(height: 4),
                            AnimatedDefaultTextStyle(
                              duration: HikariMotion.duration(
                                context,
                                HikariMotion.standard,
                              ),
                              curve: HikariMotion.curveStandard,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: (textTheme.labelSmall ?? const TextStyle())
                                  .copyWith(
                                    fontSize: 12,
                                    fontWeight: isSelected
                                        ? FontWeight.w600
                                        : FontWeight.w500,
                                    letterSpacing: 0.2,
                                    color: isSelected
                                        ? colors.onSurface
                                        : colors.onSurfaceVariant,
                                  ),
                              child: Text(tab.label),
                            ),
                          ],
                        ),
                      ),
                    );
                  }).toList(),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
