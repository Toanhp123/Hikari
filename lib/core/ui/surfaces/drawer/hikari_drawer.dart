import 'package:flutter/material.dart';
import 'package:hikari/core/ui/navigation/hikari_navigation_destination.dart';

/// Navigation-only. Shell closes temporary drawer before emitting selection.
class HikariDrawer extends StatelessWidget {
  const HikariDrawer({
    super.key,
    required this.destinations,
    required this.selectedId,
    required this.onSelected,
  });
  final List<HikariNavigationDestination> destinations;
  final String selectedId;
  final ValueChanged<String> onSelected;

  @override
  Widget build(BuildContext context) => Drawer(
    child: SafeArea(
      child: ListView(
        children: [
          for (final destination in destinations)
            ListTile(
              selected: destination.id == selectedId,
              leading: Icon(
                destination.id == selectedId
                    ? destination.selectedIcon
                    : destination.icon,
              ),
              title: Text(destination.label),
              onTap: () => onSelected(destination.id),
            ),
        ],
      ),
    ),
  );
}
