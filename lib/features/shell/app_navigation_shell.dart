import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';

enum AppTab {
  home,
  search,
  library,
  settings;

  String get label => switch (this) {
    AppTab.home => 'Home',
    AppTab.search => 'Search',
    AppTab.library => 'Library',
    AppTab.settings => 'Settings',
  };

  IconData get icon => switch (this) {
    AppTab.home => Icons.home_outlined,
    AppTab.search => Icons.search_rounded,
    AppTab.library => Icons.bookmarks_outlined,
    AppTab.settings => Icons.tune_rounded,
  };

  IconData get selectedIcon => switch (this) {
    AppTab.home => Icons.home_rounded,
    AppTab.search => Icons.manage_search_rounded,
    AppTab.library => Icons.bookmarks_rounded,
    AppTab.settings => Icons.tune_rounded,
  };
}

/// Adaptive Navigation Shell supporting Glassmorphism Bottom Nav (< 600dp)
/// and Navigation Rail / Sidebar (>= 600dp) with IndexedStack state preservation.
class AppNavigationShell extends StatefulWidget {
  const AppNavigationShell({
    super.key,
    required this.tabs,
    this.initialIndex = 0,
    this.onTabChanged,
  });

  final List<Widget> tabs;
  final int initialIndex;
  final ValueChanged<int>? onTabChanged;

  @override
  State<AppNavigationShell> createState() => _AppNavigationShellState();
}

class _AppNavigationShellState extends State<AppNavigationShell> {
  late int _currentIndex;
  late final Set<int> _loadedIndices;

  @override
  void initState() {
    super.initState();
    _currentIndex = widget.initialIndex;
    _loadedIndices = {_currentIndex};
  }

  void _selectTab(int index) {
    if (_currentIndex == index) return;
    setState(() {
      _currentIndex = index;
      _loadedIndices.add(index);
    });
    widget.onTabChanged?.call(index);
  }

  @override
  Widget build(BuildContext context) {
    final isCompact = context.isCompact;

    final children = [
      for (var i = 0; i < widget.tabs.length; i++)
        if (_loadedIndices.contains(i))
          widget.tabs[i]
        else
          const SizedBox.shrink(),
    ];

    if (isCompact) {
      return Scaffold(
        backgroundColor: Colors.transparent,
        body: IndexedStack(index: _currentIndex, children: children),
        bottomNavigationBar: _buildGlassmorphicBottomBar(context),
      );
    } else {
      return Scaffold(
        backgroundColor: Colors.transparent,
        body: Row(
          children: [
            _buildNavigationRail(context),
            Expanded(
              child: IndexedStack(index: _currentIndex, children: children),
            ),
          ],
        ),
      );
    }
  }

  Widget _buildGlassmorphicBottomBar(BuildContext context) {
    final colors = context.hikariColors;

    return RepaintBoundary(
      child: ClipRRect(
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 15, sigmaY: 15),
          child: Container(
            height: 64,
            decoration: BoxDecoration(
              color: colors.surface.withValues(
                alpha: colors.isOled ? 0.95 : 0.8,
              ),
              border: Border(
                top: BorderSide(color: colors.borderSubtle, width: 1.0),
              ),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceAround,
              children: AppTab.values.map((tab) {
                final isSelected = tab.index == _currentIndex;
                return Expanded(
                  child: GestureDetector(
                    onTap: () => _selectTab(tab.index),
                    behavior: HitTestBehavior.opaque,
                    child: Tooltip(
                      message: tab.label,
                      child: Column(
                        mainAxisAlignment: MainAxisAlignment.center,
                        children: [
                          AnimatedContainer(
                            duration: HikariMotion.fast,
                            curve: HikariMotion.curveStandard,
                            padding: const EdgeInsets.symmetric(
                              horizontal: 16,
                              vertical: 4,
                            ),
                            decoration: BoxDecoration(
                              color: isSelected
                                  ? colors.primary.withValues(alpha: 0.16)
                                  : Colors.transparent,
                              borderRadius: HikariRadius.borderCapsule,
                            ),
                            child: Icon(
                              isSelected ? tab.selectedIcon : tab.icon,
                              size: 22,
                              color: isSelected
                                  ? colors.primaryGlow
                                  : colors.textSecondary,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            tab.label,
                            style: TextStyle(
                              fontSize: 11,
                              fontWeight: isSelected
                                  ? FontWeight.w600
                                  : FontWeight.w500,
                              color: isSelected
                                  ? colors.textPrimary
                                  : colors.textMuted,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                );
              }).toList(),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildNavigationRail(BuildContext context) {
    final colors = context.hikariColors;

    return RepaintBoundary(
      child: Container(
        width: 80,
        decoration: BoxDecoration(
          color: colors.surface,
          border: Border(right: BorderSide(color: colors.border, width: 1.0)),
        ),
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
                    color: colors.primary.withValues(alpha: 0.4),
                    blurRadius: 10,
                  ),
                ],
              ),
              child: const Icon(
                Icons.auto_awesome,
                color: Colors.white,
                size: 22,
              ),
            ),
            const SizedBox(height: HikariSpacing.xxl),
            // Navigation Items
            ...AppTab.values.map((tab) {
              final isSelected = tab.index == _currentIndex;
              return Padding(
                padding: const EdgeInsets.symmetric(vertical: HikariSpacing.sm),
                child: IconButton(
                  tooltip: tab.label,
                  onPressed: () => _selectTab(tab.index),
                  icon: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Container(
                        padding: const EdgeInsets.all(8),
                        decoration: BoxDecoration(
                          color: isSelected
                              ? colors.primary.withValues(alpha: 0.2)
                              : Colors.transparent,
                          borderRadius: HikariRadius.borderMd,
                        ),
                        child: Icon(
                          isSelected ? tab.selectedIcon : tab.icon,
                          size: 24,
                          color: isSelected
                              ? colors.primaryGlow
                              : colors.textSecondary,
                        ),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        tab.label,
                        style: TextStyle(
                          fontSize: 10,
                          fontWeight: isSelected
                              ? FontWeight.w600
                              : FontWeight.w500,
                          color: isSelected
                              ? colors.textPrimary
                              : colors.textMuted,
                        ),
                      ),
                    ],
                  ),
                ),
              );
            }),
            const Spacer(),
          ],
        ),
      ),
    );
  }
}
