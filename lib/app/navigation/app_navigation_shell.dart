import 'package:flutter/material.dart';
import 'package:hikari/core/ui/navigation/hikari_navigation_destination.dart';
import 'package:hikari/core/ui/navigation/hikari_navigation_shell.dart';

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

/// App owns destination identity, controller and lazily retained pages.
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
  late AppNavigationController _controller;
  late bool _ownsController;
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
  void didUpdateWidget(AppNavigationShell oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller == widget.controller) return;
    _controller.removeListener(_handleControllerChanged);
    if (_ownsController) _controller.dispose();
    _ownsController = widget.controller == null;
    _controller =
        widget.controller ??
        AppNavigationController(initialTab: widget.initialTab);
    _currentIndex = _controller.currentTab.index;
    _loadedIndices.add(_currentIndex);
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

  @override
  Widget build(BuildContext context) {
    if (!AppTab.values.every(widget.tabs.containsKey)) {
      throw ArgumentError('A page is required for every AppTab.');
    }
    return HikariNavigationShell(
      destinations: [
        for (final tab in AppTab.values)
          HikariNavigationDestination(
            id: tab.name,
            label: tab.label,
            icon: tab.icon,
            selectedIcon: tab.selectedIcon,
          ),
      ],
      selectedId: _controller.currentTab.name,
      onSelected: (id) => _controller.selectTab(AppTab.values.byName(id)),
      body: IndexedStack(
        index: _currentIndex,
        children: [
          for (final tab in AppTab.values)
            if (_loadedIndices.contains(tab.index))
              widget.tabs[tab]!
            else
              const SizedBox.shrink(),
        ],
      ),
    );
  }
}
