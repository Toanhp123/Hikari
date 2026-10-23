import 'package:flutter/material.dart';
import 'package:hikari/core/ui/hikari_ui.dart';

/// A bounded, synthetic app shell: no production routing or retained tabs.
class PageNavigationPreview extends StatefulWidget {
  const PageNavigationPreview({
    super.key,
    required this.width,
    required this.stress,
  });

  final double width;
  final bool stress;

  @override
  State<PageNavigationPreview> createState() => _PageNavigationPreviewState();
}

class _PageNavigationPreviewState extends State<PageNavigationPreview> {
  bool _sixDestinations = false;
  String _selected = 'home';
  int _actionCount = 0;

  @override
  Widget build(BuildContext context) {
    final destinations = [
      const HikariNavigationDestination(
        id: 'home',
        label: 'Home',
        icon: Icons.home_outlined,
      ),
      const HikariNavigationDestination(
        id: 'catalog',
        label: 'Catalog',
        icon: Icons.explore_outlined,
      ),
      const HikariNavigationDestination(
        id: 'library',
        label: 'Library',
        icon: Icons.book_outlined,
      ),
      const HikariNavigationDestination(
        id: 'local',
        label: 'Local',
        icon: Icons.folder_outlined,
      ),
      const HikariNavigationDestination(
        id: 'settings',
        label: 'Settings',
        icon: Icons.settings_outlined,
      ),
      if (_sixDestinations)
        const HikariNavigationDestination(
          id: 'extra',
          label: 'Extra',
          icon: Icons.more_horiz,
        ),
    ];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const HikariSectionHeader(
          title: 'Page & navigation',
          description: 'Local-width shell, page header and content region. Preview only.',
        ),
        SwitchListTile.adaptive(
          title: const Text('Six destinations'),
          subtitle: const Text(
            'At compact widths, six destinations use a drawer.',
          ),
          contentPadding: EdgeInsets.zero,
          value: _sixDestinations,
          onChanged: (value) => setState(() {
            _sixDestinations = value;
            if (!value && _selected == 'extra') _selected = 'home';
          }),
        ),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: SizedBox(
            width: widget.width,
            height: 460,
            child: MediaQuery(
              data: MediaQuery.of(context).copyWith(
                size: Size(widget.width, 460),
                padding: EdgeInsets.zero,
                viewInsets: EdgeInsets.zero,
              ),
              child: HikariNavigationShell(
                destinations: destinations,
                selectedId: _selected,
                onSelected: (id) => setState(() => _selected = id),
                body: HikariPageScaffold(
                  showAmbientGlow: true,
                  body: SingleChildScrollView(
                    child: HikariContentRegion(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          HikariPageHeader(
                            title: widget.stress
                                ? 'A long translated gallery page heading العربية'
                                : 'Page composition',
                            subtitle:
                                'Selected destination: $_selected'
                                ' · Actions: $_actionCount',
                            actions: [
                              HikariButton(
                                label: 'Sample action',
                                onPressed: () => setState(() => _actionCount++),
                              ),
                            ],
                          ),
                          const SizedBox(height: HikariSpacing.lg),
                          const HikariListRow(
                            title: Text('Bounded page content'),
                            subtitle: Text(
                              'The page owns scrolling; the region owns gutters.',
                            ),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }
}
