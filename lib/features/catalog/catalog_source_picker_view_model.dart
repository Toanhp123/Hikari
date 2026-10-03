import 'package:flutter/foundation.dart';

import 'package:hikari/application/catalog/resolve_catalog_source.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/metadata.dart';

enum CatalogSourcePickerStatus {
  choosing,
  resolving,
  resolved,
  candidates,
  empty,
  error,
}

@immutable
final class CatalogSourcePickerSource {
  const CatalogSourcePickerSource({required this.id, required this.name});

  final SourceId id;
  final String name;
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
    this.status = CatalogSourcePickerStatus.choosing,
    this.candidates = const [],
  });

  final List<CatalogSourcePickerSource> sources;
  final CatalogSourcePickerSource? selectedSource;
  final CatalogSourcePickerStatus status;
  final List<CatalogSourcePickerCandidate> candidates;
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

  Future<Media?> selectSource(CatalogSourcePickerSource source) async {
    final revision = ++_revision;
    _publish(
      CatalogSourcePickerUiState(
        sources: _state.sources,
        selectedSource: source,
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
      if (match != null) {
        _publish(
          CatalogSourcePickerUiState(
            sources: _state.sources,
            selectedSource: source,
            status: CatalogSourcePickerStatus.resolved,
          ),
        );
        return match.media;
      }

      _publish(
        CatalogSourcePickerUiState(
          sources: _state.sources,
          selectedSource: source,
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
    _publish(CatalogSourcePickerUiState(sources: _state.sources));
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
