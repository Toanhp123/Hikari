import 'package:flutter/material.dart';
import 'package:hikari/core/ui/foundation/hikari_spacing.dart';
import 'package:hikari/core/ui/controls/hikari_choice_menu.dart';
import 'package:hikari/core/ui/controls/hikari_search_field.dart';
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

  static const _views = [
    HikariChoiceOption<int>(value: 0, label: 'Available'),
    HikariChoiceOption<int>(value: 1, label: 'Installed'),
    HikariChoiceOption<int>(value: 2, label: 'Updates'),
  ];

  @override
  Widget build(BuildContext context) {
    final ecosystemOptions = [
      const HikariChoiceOption<PluginEcosystem>(
        value: null,
        label: 'All ecosystems',
        shortLabel: 'All',
      ),
      for (final ecosystem in PluginEcosystem.values)
        HikariChoiceOption(
          value: ecosystem,
          label: pluginEcosystemLabel(ecosystem),
        ),
    ];
    final languageOptions = [
      const HikariChoiceOption<String>(
        value: null,
        label: 'All languages',
        shortLabel: 'All',
      ),
      for (final language in state.listing.languages)
        HikariChoiceOption(value: language, label: language.toUpperCase()),
    ];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        HikariSearchField(
          initialQuery: state.query,
          hintText: 'Search plugins or sources',
          onChanged: onQueryChanged,
        ),
        const SizedBox(height: HikariSpacing.md),
        LayoutBuilder(
          builder: (context, constraints) {
            final canUseSegments =
                constraints.maxWidth >= 360 &&
                MediaQuery.textScalerOf(context).scale(1) <= 1.15;
            if (!canUseSegments) {
              return HikariChoiceMenu<int>(
                label: 'View',
                value: state.tab,
                options: _views,
                onChanged: (value) {
                  if (value != null) onTabChanged(value);
                },
                icon: Icons.view_list_outlined,
                expanded: true,
              );
            }
            return SegmentedButton<int>(
              segments: const [
                ButtonSegment(value: 0, label: Text('Available')),
                ButtonSegment(value: 1, label: Text('Installed')),
                ButtonSegment(value: 2, label: Text('Updates')),
              ],
              selected: {state.tab},
              showSelectedIcon: false,
              expandedInsets: EdgeInsets.zero,
              onSelectionChanged: (values) => onTabChanged(values.single),
            );
          },
        ),
        const SizedBox(height: HikariSpacing.md),
        Wrap(
          spacing: HikariSpacing.sm,
          runSpacing: HikariSpacing.sm,
          children: [
            HikariChoiceMenu<PluginEcosystem>(
              label: 'Ecosystem',
              value: state.ecosystem,
              options: ecosystemOptions,
              icon: Icons.category_outlined,
              onChanged: onEcosystemChanged,
            ),
            if (state.tab != 1)
              HikariChoiceMenu<String>(
                label: 'Language',
                value: state.language,
                options: languageOptions,
                icon: Icons.language_rounded,
                onChanged: onLanguageChanged,
              ),
          ],
        ),
        if (state.listing.rows.isEmpty) ...[
          const SizedBox(height: HikariSpacing.lg),
          Padding(
            padding: const EdgeInsets.all(HikariSpacing.sm),
            child: Text(
              switch (state.tab) {
                1 => 'No installed plugins match your filters.',
                2 => 'No updates match your filters.',
                _ => 'No plugins match. Add or refresh a repository to discover offers.',
              },
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
