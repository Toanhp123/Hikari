import 'package:flutter/material.dart';
import 'package:hikari/app/theme/hikari_theme.dart';
import 'package:hikari/core/ui/components/hikari_chip.dart';
import 'package:hikari/domain/plugins/plugins.dart';
import 'package:hikari/features/plugins/plugin_labels.dart';
import 'package:hikari/features/plugins/plugin_manager_ui_state.dart';

/// Search and facet controls; state is owned by the manager view model.
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
  Widget build(BuildContext context) {
    final ecosystemField = DropdownButtonFormField<PluginEcosystem>(
      initialValue: state.ecosystem,
      isExpanded: true,
      decoration: const InputDecoration(labelText: 'Ecosystem'),
      items: [
        const DropdownMenuItem<PluginEcosystem>(
          value: null,
          child: Text('All ecosystems'),
        ),
        ...PluginEcosystem.values.map(
          (value) => DropdownMenuItem(
            value: value,
            child: Text(pluginEcosystemLabel(value)),
          ),
        ),
      ],
      onChanged: onEcosystemChanged,
    );
    final languageField = DropdownButtonFormField<String>(
      key: ValueKey((state.listing.languages.join('\u0000'), state.language)),
      initialValue: state.language,
      isExpanded: true,
      decoration: const InputDecoration(labelText: 'Source language'),
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
    );
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        HikariChipRow(
          children: [
            for (final (index, label) in [
              'Available',
              'Installed',
              'Updates',
            ].indexed)
              HikariChip(
                label: label,
                isSelected: state.tab == index,
                onTap: () => onTabChanged(index),
              ),
          ],
        ),
        const SizedBox(height: HikariSpacing.md),
        TextField(
          decoration: const InputDecoration(
            labelText: 'Search plugins or sources',
            prefixIcon: Icon(Icons.search_rounded),
          ),
          onChanged: onQueryChanged,
          textInputAction: TextInputAction.search,
        ),
        const SizedBox(height: HikariSpacing.md),
        LayoutBuilder(
          builder: (context, constraints) {
            final sideBySide =
                constraints.maxWidth >= 520 &&
                MediaQuery.textScalerOf(context).scale(1) <= 1.3;
            if (!sideBySide) {
              return Column(
                children: [
                  ecosystemField,
                  const SizedBox(height: HikariSpacing.sm),
                  languageField,
                ],
              );
            }
            return Row(
              children: [
                Expanded(child: ecosystemField),
                const SizedBox(width: HikariSpacing.md),
                Expanded(child: languageField),
              ],
            );
          },
        ),
        if (state.listing.rows.isEmpty) ...[
          const SizedBox(height: HikariSpacing.lg),
          Padding(
            padding: const EdgeInsets.all(HikariSpacing.sm),
            child: Text(
              'No plugins match. Add or refresh a repository to discover offers.',
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
          ),
        ],
      ],
    );
  }
}
