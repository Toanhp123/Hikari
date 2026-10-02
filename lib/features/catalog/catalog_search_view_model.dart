import 'package:flutter/foundation.dart';
import 'package:hikari/application/catalog/search_catalog.dart';
import 'package:hikari/domain/catalog/catalog.dart';
import 'package:hikari/domain/media/media.dart';

enum CatalogSearchFilter {
  all(null),
  anime(MediaType.anime),
  manga(MediaType.manga),
  novel(MediaType.lightNovel);

  const CatalogSearchFilter(this.mediaType);

  final MediaType? mediaType;
}

enum CatalogSearchStatus { idle, loading, ready, empty, error }

@immutable
final class CatalogSearchUiState {
  const CatalogSearchUiState({
    this.inputQuery = '',
    this.submittedQuery = '',
    this.filter = CatalogSearchFilter.all,
    this.status = CatalogSearchStatus.idle,
    this.entries = const [],
  });

  final String inputQuery;
  final String submittedQuery;
  final CatalogSearchFilter filter;
  final CatalogSearchStatus status;
  final List<CatalogEntry> entries;

  CatalogSearchUiState copyWith({
    String? inputQuery,
    String? submittedQuery,
    CatalogSearchFilter? filter,
    CatalogSearchStatus? status,
    List<CatalogEntry>? entries,
  }) {
    return CatalogSearchUiState(
      inputQuery: inputQuery ?? this.inputQuery,
      submittedQuery: submittedQuery ?? this.submittedQuery,
      filter: filter ?? this.filter,
      status: status ?? this.status,
      entries: entries ?? this.entries,
    );
  }
}

final class CatalogSearchViewModel extends ChangeNotifier {
  CatalogSearchViewModel(this._searchCatalog);

  final SearchCatalog _searchCatalog;

  CatalogSearchUiState _state = const CatalogSearchUiState();
  CatalogSearchUiState get state => _state;

  int _searchRevision = 0;
  bool _disposed = false;

  void updateQuery(String rawQuery) {
    final inputQuery = rawQuery.trim();
    if (inputQuery == _state.inputQuery) return;

    final matchesSubmitted = inputQuery == _state.submittedQuery;
    if (!matchesSubmitted) _searchRevision++;
    _publish(
      _state.copyWith(
        inputQuery: inputQuery,
        status: matchesSubmitted ? _state.status : CatalogSearchStatus.idle,
        entries: matchesSubmitted ? _state.entries : const [],
      ),
    );
  }

  Future<void> submitQuery(String rawQuery) async {
    final query = rawQuery.trim();
    final revision = ++_searchRevision;
    if (query.isEmpty) {
      _publish(
        _state.copyWith(
          inputQuery: '',
          submittedQuery: '',
          status: CatalogSearchStatus.idle,
          entries: const [],
        ),
      );
      return;
    }

    _publish(
      _state.copyWith(
        inputQuery: query,
        submittedQuery: query,
        status: CatalogSearchStatus.loading,
        entries: const [],
      ),
    );

    try {
      final entries = await _searchCatalog.execute(
        query,
        type: _state.filter.mediaType,
      );
      if (_disposed || revision != _searchRevision) return;
      _publish(
        _state.copyWith(
          status: entries.isEmpty
              ? CatalogSearchStatus.empty
              : CatalogSearchStatus.ready,
          entries: List<CatalogEntry>.unmodifiable(entries),
        ),
      );
    } catch (_) {
      if (_disposed || revision != _searchRevision) return;
      _publish(
        _state.copyWith(status: CatalogSearchStatus.error, entries: const []),
      );
    }
  }

  Future<void> selectFilter(CatalogSearchFilter filter) async {
    if (filter == _state.filter) return;
    _publish(_state.copyWith(filter: filter));
    if (_state.submittedQuery.isNotEmpty &&
        _state.inputQuery == _state.submittedQuery) {
      await submitQuery(_state.submittedQuery);
    }
  }

  Future<void> retry() => submitQuery(_state.submittedQuery);

  void _publish(CatalogSearchUiState state) {
    if (_disposed) return;
    _state = state;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    super.dispose();
  }
}
