import 'package:flutter/widgets.dart';

/// App supplies identity; core only renders it and emits the same ID.
final class HikariNavigationDestination {
  const HikariNavigationDestination({
    required this.id,
    required this.label,
    required this.icon,
    IconData? selectedIcon,
  }) : selectedIcon = selectedIcon ?? icon;
  final String id;
  final String label;
  final IconData icon;
  final IconData selectedIcon;
}
