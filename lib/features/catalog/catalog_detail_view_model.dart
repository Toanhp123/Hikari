import 'package:flutter/foundation.dart';

import 'package:hikari/application/catalog/load_catalog_entry_details.dart';
import 'package:hikari/domain/catalog/catalog.dart';

enum CatalogDetailStatus { loading, ready, error }

@immutable
final class CatalogDetailUiState {
  const CatalogDetailUiState({
    required this.initialEntry,
    this.details,
    this.status = CatalogDetailStatus.loading,
  });

  final CatalogEntry initialEntry;
  final CatalogEntryDetails? details;
  final CatalogDetailStatus status;

  CatalogEntry get entry => details?.entry ?? initialEntry;
  bool get initialLoading =>
      status == CatalogDetailStatus.loading && details == null;
  bool get refreshing =>
      status == CatalogDetailStatus.loading && details != null;
  bool get refreshFailed =>
      status == CatalogDetailStatus.error && details != null;
  bool get failed => status == CatalogDetailStatus.error && details == null;
}

final class CatalogDetailViewModel extends ChangeNotifier {
  CatalogDetailViewModel({
    required CatalogEntry initialEntry,
    required this._loadDetails,
  }) : _state = CatalogDetailUiState(initialEntry: initialEntry);

  final LoadCatalogEntryDetails _loadDetails;

  CatalogDetailUiState _state;
  CatalogDetailUiState get state => _state;

  int _loadRevision = 0;
  bool _disposed = false;

  Future<void> load({bool forceRefresh = false}) async {
    final revision = ++_loadRevision;
    final id = _state.initialEntry.id;
    _publish(
      CatalogDetailUiState(
        initialEntry: _state.initialEntry,
        details: _state.details,
        status: CatalogDetailStatus.loading,
      ),
    );
    CatalogSnapshot<CatalogEntryDetails>? cached;
    try {
      if (_state.details == null) {
        cached = await _loadDetails.cached(id);
        if (_disposed || revision != _loadRevision) return;
        if (cached != null) {
          _publish(
            CatalogDetailUiState(
              initialEntry: _state.initialEntry,
              details: cached.data,
              status: cached.isFresh && !forceRefresh
                  ? CatalogDetailStatus.ready
                  : CatalogDetailStatus.loading,
            ),
          );
          if (cached.isFresh && !forceRefresh) return;
        }
      }
      final details = forceRefresh || cached != null
          ? await _loadDetails.refresh(id)
          : await _loadDetails.execute(id);
      if (_disposed || revision != _loadRevision) return;
      _publish(
        CatalogDetailUiState(
          initialEntry: _state.initialEntry,
          details: details ?? _state.details,
          status: details == null && _state.details != null
              ? CatalogDetailStatus.error
              : CatalogDetailStatus.ready,
        ),
      );
    } catch (_) {
      if (_disposed || revision != _loadRevision) return;
      _publish(
        CatalogDetailUiState(
          initialEntry: _state.initialEntry,
          details: _state.details,
          status: CatalogDetailStatus.error,
        ),
      );
    }
  }

  void _publish(CatalogDetailUiState state) {
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
