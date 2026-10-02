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
  LibraryButtonViewModel(this._repository, this._media) {
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

  StreamSubscription<List<LibraryEntry>>? _subscription;
  LibraryButtonUiState _state = const LibraryButtonUiState();
  LibraryButtonUiState get state => _state;
  bool _disposed = false;
  int _revision = 0;

  Future<void> load() async {
    final revision = _revision;
    try {
      final saved = await _repository.contains(_media.source);
      if (_disposed || revision != _revision) return;
      _publish(_state.copyWith(saved: saved));
    } catch (_) {
      if (_disposed || revision != _revision) return;
      _publish(_state.copyWith(clearSaved: true));
    }
  }

  Future<bool> toggle() async {
    if (_state.busy) return false;
    _publish(_state.copyWith(busy: true));
    try {
      final saved = await _repository.contains(_media.source);
      if (saved) {
        await _repository.remove(_media.source);
      } else {
        await _repository.upsert(
          LibraryEntry(media: _media, addedAt: DateTime.now().toUtc()),
        );
      }
      if (_disposed) return true;
      _publish(_state.copyWith(saved: !saved));
      return true;
    } catch (_) {
      return false;
    } finally {
      if (!_disposed) {
        _publish(_state.copyWith(busy: false));
      }
    }
  }

  void _acceptEntries(List<LibraryEntry> entries) {
    _revision++;
    _publish(
      _state.copyWith(
        saved: entries.any((entry) => entry.media.source == _media.source),
      ),
    );
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
