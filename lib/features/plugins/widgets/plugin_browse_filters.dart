import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/features/plugins/plugin_labels.dart';
import 'package:hikari/features/plugins/plugin_manager_ui_state.dart';

/// Browse inputs render the immutable state and forward user intent only.
final class PluginBrowseFilters extends StatelessWidget {
  const PluginBrowseFilters({
    super.key,
    required this.state,
    required this.onQueryChanged,
    required this.onLanguageChanged,
    required this.onEcosystemChanged,
    required this.onTabChanged,
  });

  final PluginManagerUiState state;
  final ValueChanged<String> onQueryChanged;
  final ValueChanged<String?> onLanguageChanged;
  final ValueChanged<PluginEcosystem?> onEcosystemChanged;
  final ValueChanged<int> onTabChanged;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.stretch,
    children: [
      Wrap(
        spacing: HikariSpacing.sm,
        children: [
          for (final (index, label) in [
            'Available',
            'Installed',
            'Updates',
          ].indexed)
            ChoiceChip(
              label: Text(label),
              selected: state.tab == index,
              onSelected: (_) => onTabChanged(index),
            ),
        ],
      ),
      TextField(
        decoration: const InputDecoration(
          labelText: 'Search plugins or sources',
        ),
        onChanged: onQueryChanged,
      ),
      DropdownButtonFormField<PluginEcosystem>(
        initialValue: state.ecosystem,
        isExpanded: true,
        decoration: const InputDecoration(labelText: 'Ecosystem'),
        items: [
          const DropdownMenuItem<PluginEcosystem>(
            value: null,
            child: Text('All ecosystems'),
          ),
          ...PluginEcosystem.values.map(
            (e) => DropdownMenuItem(
              value: e,
              child: Text(pluginEcosystemLabel(e)),
            ),
          ),
        ],
        onChanged: onEcosystemChanged,
      ),
      DropdownButtonFormField<String>(
        key: ValueKey((
          state.listing.languages.join(String.fromCharCode(0)),
          state.language,
        )),
        initialValue: state.language,
        isExpanded: true,
        decoration: const InputDecoration(
          labelText: 'Source language (when declared)',
        ),
        items: [
          const DropdownMenuItem<String>(
            value: null,
            child: Text('All languages'),
          ),
          ...state.listing.languages.map(
            (language) =>
                DropdownMenuItem(value: language, child: Text(language)),
          ),
        ],
        onChanged: onLanguageChanged,
      ),
      const SizedBox(height: HikariSpacing.md),
      if (state.listing.rows.isEmpty)
        const Text(
          'No plugins match. Add or refresh a repository to discover offers.',
        ),
    ],
  );
}
