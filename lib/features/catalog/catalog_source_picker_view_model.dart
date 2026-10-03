import 'package:flutter/foundation.dart';

import 'package:hikari/application/catalog/resolve_catalog_source.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';
import 'package:hikari/domain/media/source.dart';

enum CatalogSourcePickerStatus { choosing, resolving, candidates, empty, error }

@immutable
final class CatalogSourcePickerSource {
  const CatalogSourcePickerSource({
    required this.id,
    required this.name,
    required this.displayName,
    this.languageCode,
  });

  final SourceId id;
  final String name;
  final String displayName;
  final String? languageCode;
}

@immutable
final class CatalogSourcePickerCandidate {
  const CatalogSourcePickerCandidate({required this.media, this.metadata});

  final Media media;
  final MediaMetadata? metadata;
}

@immutable
final class CatalogSourcePickerUiState {
  const CatalogSourcePickerUiState({
    required this.sources,
    this.selectedSource,
    this.selectedLanguage,
    this.status = CatalogSourcePickerStatus.choosing,
    this.candidates = const [],
  });

  final List<CatalogSourcePickerSource> sources;
  final CatalogSourcePickerSource? selectedSource;
  final String? selectedLanguage;
  List<String> get languageCodes =>
      sources
          .map((source) => source.languageCode)
          .whereType<String>()
          .toSet()
          .toList()
        ..sort();
  final CatalogSourcePickerStatus status;
  final List<CatalogSourcePickerCandidate> candidates;

  List<CatalogSourcePickerSource> get visibleSources => selectedLanguage == null
      ? sources
      : sources
            .where((source) => source.languageCode == selectedLanguage)
            .toList();

  bool get showLanguageFilter => languageCodes.length > 1;
}

final class CatalogSourcePickerViewModel extends ChangeNotifier {
  CatalogSourcePickerViewModel({
    required this.entry,
    required ResolveCatalogSource resolver,
    this.details,
  }) : _resolver = resolver,
       _state = CatalogSourcePickerUiState(
         sources: List.unmodifiable(
           resolver
               .optionsFor(entry.type)
               .map(
                 (source) => CatalogSourcePickerSource(
                   id: source.id,
                   name: source.name,
                   displayName: source.displayName,
                   languageCode: source.languageCode,
                 ),
               ),
         ),
       );

  final CatalogEntry entry;
  final CatalogEntryDetails? details;
  final ResolveCatalogSource _resolver;

  CatalogSourcePickerUiState _state;
  CatalogSourcePickerUiState get state => _state;

  int _revision = 0;
  bool _disposed = false;

  void selectLanguage(String? language) {
    if (_disposed) return;
    final normalized = normalizeSourceLanguageCode(language);
    if (normalized != null && !_state.languageCodes.contains(normalized)) {
      return;
    }
    _revision++;
    _publish(
      CatalogSourcePickerUiState(
        sources: _state.sources,
        selectedLanguage: normalized,
      ),
    );
  }

  Future<Media?> selectSource(CatalogSourcePickerSource source) async {
    if (!_state.visibleSources.any((visible) => visible.id == source.id)) {
      return null;
    }
    final revision = ++_revision;
    final selectedLanguage = _state.selectedLanguage;
    _publish(
      CatalogSourcePickerUiState(
        sources: _state.sources,
        selectedSource: source,
        selectedLanguage: selectedLanguage,
        status: CatalogSourcePickerStatus.resolving,
      ),
    );

    try {
      final resolution = await _resolver.execute(
        entry: entry,
        details: details,
        sourceId: source.id,
      );
      if (_disposed || revision != _revision) return null;

      final match = resolution.match;
      if (match != null) return match.media;

      _publish(
        CatalogSourcePickerUiState(
          sources: _state.sources,
          selectedSource: source,
          selectedLanguage: selectedLanguage,
          status: resolution.candidates.isEmpty
              ? CatalogSourcePickerStatus.empty
              : CatalogSourcePickerStatus.candidates,
          candidates: List.unmodifiable(
            resolution.candidates.map(
              (candidate) => CatalogSourcePickerCandidate(
                media: candidate.media,
                metadata: candidate.metadata,
              ),
            ),
          ),
        ),
      );
    } catch (_) {
      if (_disposed || revision != _revision) return null;
      _publish(
        CatalogSourcePickerUiState(
          sources: _state.sources,
          selectedSource: source,
          selectedLanguage: selectedLanguage,
          status: CatalogSourcePickerStatus.error,
        ),
      );
    }
    return null;
  }

  Future<Media?> retry() async {
    final selected = _state.selectedSource;
    if (selected == null) return null;
    return selectSource(selected);
  }

  void chooseAnotherSource() {
    _revision++;
    _publish(
      CatalogSourcePickerUiState(
        sources: _state.sources,
        selectedLanguage: _state.selectedLanguage,
      ),
    );
  }

  void _publish(CatalogSourcePickerUiState state) {
    if (_disposed) return;
    _state = state;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _revision++;
    super.dispose();
  }
}
