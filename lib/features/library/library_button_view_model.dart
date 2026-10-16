import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:hikari/domain/library/library.dart';
import 'package:hikari/domain/media/media.dart';

@immutable
final class LibraryButtonUiState {
  const LibraryButtonUiState({this.saved, this.busy = false});

  final bool? saved;
  final bool busy;

  LibraryButtonUiState copyWith({
    bool? saved,
    bool clearSaved = false,
    bool? busy,
  }) {
    return LibraryButtonUiState(
      saved: clearSaved ? null : saved ?? this.saved,
      busy: busy ?? this.busy,
    );
  }
}

final class LibraryButtonViewModel extends ChangeNotifier {
  LibraryButtonViewModel(this._repository, this._media, {this.artwork}) {
    if (_repository case final ObservableLibraryRepository observable) {
      _subscription = observable.watchAll().listen(
        _acceptEntries,
        onError: (_) => _invalidateSaved(),
      );
    }
    unawaited(load());
  }

  final LibraryRepository _repository;
  final Media _media;
  SourceMediaRef? artwork;

  void updateArtwork(SourceMediaRef? artwork) {
    if (_disposed || this.artwork == artwork) return;
    this.artwork = artwork;
    _artworkSyncStarted = false;
    if (_state.saved == true && !_state.busy) _syncArtwork();
  }

  StreamSubscription<List<LibraryEntry>>? _subscription;
  LibraryButtonUiState _state = const LibraryButtonUiState();
  LibraryButtonUiState get state => _state;
  bool _disposed = false;
  int _revision = 0;
  bool _artworkSyncStarted = false;

  Future<void> load() async {
    final revision = _revision;
    try {
      final saved = await _repository.contains(_media.source);
      if (_disposed || revision != _revision) return;
      _publish(_state.copyWith(saved: saved));
      if (saved) _syncArtwork();
    } catch (_) {
      if (_disposed || revision != _revision) return;
      _publish(_state.copyWith(clearSaved: true));
    }
  }

  /// Adds membership only. Changes from other surfaces cannot turn this
  /// explicit user action into a removal.
  Future<bool> add() async {
    if (_disposed || _state.busy) return false;
    _publish(_state.copyWith(busy: true));
    try {
      if (!await _repository.contains(_media.source)) {
        await _repository.upsert(
          LibraryEntry(
            media: _media,
            addedAt: DateTime.now().toUtc(),
            artwork: artwork,
          ),
        );
      }
      if (!_disposed) _publish(_state.copyWith(saved: true));
      return true;
    } catch (_) {
      return false;
    } finally {
      if (!_disposed) _publish(_state.copyWith(busy: false));
    }
  }

  /// Removes membership only. A confirmation must never fall back to adding
  /// the title if it was removed elsewhere while the dialog was open.
  Future<bool> remove() async {
    if (_disposed || _state.busy) return false;
    _publish(_state.copyWith(busy: true));
    try {
      if (await _repository.contains(_media.source)) {
        await _repository.remove(_media.source);
      }
      if (!_disposed) _publish(_state.copyWith(saved: false));
      return true;
    } catch (_) {
      return false;
    } finally {
      if (!_disposed) _publish(_state.copyWith(busy: false));
    }
  }

  void _acceptEntries(List<LibraryEntry> entries) {
    _revision++;
    final saved = entries.any((entry) => entry.media.source == _media.source);
    _publish(_state.copyWith(saved: saved));
    if (saved) _syncArtwork();
  }

  void _syncArtwork() {
    final cover = artwork;
    if (_artworkSyncStarted || cover == null) return;
    if (_repository case final LibraryArtworkRepository repository) {
      _artworkSyncStarted = true;
      // Covers saved before the schema change are filled without re-adding rows.
      unawaited(
        repository
            .updateArtwork(_media.source, cover)
            .catchError((Object _) {}),
      );
    }
  }

  void _invalidateSaved() {
    _revision++;
    _publish(_state.copyWith(clearSaved: true));
  }

  void _publish(LibraryButtonUiState state) {
    if (_disposed) return;
    _state = state;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    unawaited(_subscription?.cancel());
    super.dispose();
  }
}
