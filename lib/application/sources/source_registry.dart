import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/source.dart';

/// App-wide index of source implementations keyed by their stable source ID.
///
/// Registration is intentionally static for now. A future extension runtime can
/// produce [MediaSource] instances without changing the application workflows.
final class SourceRegistry {
  SourceRegistry(Iterable<MediaSource> sources) : _sources = _index(sources);

  final Map<SourceId, MediaSource> _sources;

  static Map<SourceId, MediaSource> _index(Iterable<MediaSource> sources) {
    final registered = <SourceId, MediaSource>{};
    for (final source in sources) {
      if (registered.containsKey(source.id)) {
        throw StateError('Duplicate media source id: ${source.id}.');
      }
      registered[source.id] = source;
    }
    return Map.unmodifiable(registered);
  }

  MediaSource? find(SourceId id) => _sources[id];

  MediaSource require(SourceId id) {
    final source = find(id);
    if (source == null) {
      throw StateError('Media source is not registered: $id.');
    }
    return source;
  }

  T requireCapability<T extends MediaSource>(SourceId id) {
    final source = require(id);
    if (source is! T) {
      throw StateError('Media source $id does not support $T.');
    }
    return source;
  }

  List<T> withCapability<T extends MediaSource>() =>
      _sources.values.whereType<T>().toList(growable: false);
}
