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
    AppTab.library => Icons.collections_bookmark_outlined,
    AppTab.settings => Icons.settings_outlined,
  };

  IconData get selectedIcon => switch (this) {
    AppTab.home => Icons.home_rounded,
    AppTab.search => Icons.search_rounded,
    AppTab.library => Icons.collections_bookmark_rounded,
    AppTab.settings => Icons.settings_rounded,
  };
}

/// Allows descendant widgets to programmatically read or change the selected tab.
class AppNavigationScope extends InheritedWidget {
  const AppNavigationScope({
    super.key,
    required this.currentIndex,
    required this.selectTab,
    required super.child,
  });

  final int currentIndex;
  final void Function(int) selectTab;

  static AppNavigationScope? of(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<AppNavigationScope>();

  @override
  bool updateShouldNotify(AppNavigationScope oldWidget) =>
      currentIndex != oldWidget.currentIndex;
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

    final shell = isCompact
        ? Scaffold(
            extendBody: true,
            backgroundColor: Colors.transparent,
            body: IndexedStack(index: _currentIndex, children: children),
            bottomNavigationBar: _buildGlassmorphicBottomBar(context),
          )
        : Scaffold(
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

    return AppNavigationScope(
      currentIndex: _currentIndex,
      selectTab: _selectTab,
      child: shell,
    );
  }

  Widget _buildGlassmorphicBottomBar(BuildContext context) {
    final colors = context.hikariColors;
    final bottomInset = MediaQuery.of(context).padding.bottom;

    return RepaintBoundary(
      child: SafeArea(
        top: false,
        child: Padding(
          padding: EdgeInsets.fromLTRB(20, 0, 20, bottomInset > 0 ? 8 : 16),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(32),
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
              child: Container(
                height: 64,
                decoration: BoxDecoration(
                  color: colors.isOled
                      ? const Color(0xEB0B0F17)
                      : const Color(0xD9121826),
                  borderRadius: BorderRadius.circular(32),
                  border: Border.all(
                    color: Colors.white.withValues(alpha: 0.12),
                    width: 1.0,
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.5),
                      blurRadius: 28,
                      offset: const Offset(0, 10),
                    ),
                    BoxShadow(
                      color: colors.primary.withValues(alpha: 0.15),
                      blurRadius: 20,
                      offset: const Offset(0, 4),
                    ),
                  ],
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
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
                                  horizontal: 14,
                                  vertical: 4,
                                ),
                                decoration: BoxDecoration(
                                  color: isSelected
                                      ? colors.primary.withValues(alpha: 0.22)
                                      : Colors.transparent,
                                  borderRadius: BorderRadius.circular(20),
                                  border: isSelected
                                      ? Border.all(
                                          color: colors.primaryGlow.withValues(
                                            alpha: 0.4,
                                          ),
                                          width: 1.0,
                                        )
                                      : null,
                                ),
                                child: Icon(
                                  isSelected ? tab.selectedIcon : tab.icon,
                                  size: 21,
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
                                      ? FontWeight.w700
                                      : FontWeight.w500,
                                  color: isSelected
                                      ? colors.textPrimary
                                      : colors.textMuted,
                                ),
                              ),
                              Container(
                                margin: const EdgeInsets.only(top: 2),
                                width: 14,
                                height: 2,
                                decoration: BoxDecoration(
                                  color: isSelected
                                      ? colors.primaryGlow
                                      : Colors.transparent,
                                  borderRadius: BorderRadius.circular(1),
                                  boxShadow: isSelected
                                      ? [
                                          BoxShadow(
                                            color: colors.primaryGlow,
                                            blurRadius: 4,
                                            spreadRadius: 0.5,
                                          ),
                                        ]
                                      : null,
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
