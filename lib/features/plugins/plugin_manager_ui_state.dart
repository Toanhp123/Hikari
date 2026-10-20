import 'package:hikari/domain/plugins/plugins.dart';

/// Immutable, derived browsing data. No native operations or widget context.
final class PluginListing {
  PluginListing({
    required Map<PluginId, String> names,
    required List<String> languages,
    required List<PluginId> rows,
  }) : names = Map.unmodifiable(names),
       languages = List.unmodifiable(languages),
       rows = List.unmodifiable(rows);

  final Map<PluginId, String> names;
  final List<String> languages;
  final List<PluginId> rows;
}

/// A single immutable UI snapshot. It is rebuilt when an input or repository
/// command changes, never by reading a getter during Widget.build.
final class PluginManagerUiState {
  const PluginManagerUiState({
    required this.busy,
    required this.error,
    required this.query,
    required this.language,
    required this.ecosystem,
    required this.tab,
    required this.listing,
  });

  final bool busy;
  final String? error;
  final String query;
  final String? language;
  final PluginEcosystem? ecosystem;
  final int tab;
  final PluginListing listing;
}
