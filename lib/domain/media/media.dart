enum MediaType { anime, manga, lightNovel }

final class SourceId {
  const SourceId(this.value);

  static const local = SourceId('local');

  final String value;

  @override
  bool operator ==(Object other) => other is SourceId && other.value == value;

  @override
  int get hashCode => value.hashCode;

  @override
  String toString() => value;
}

class SourceMediaRef {
  const SourceMediaRef({required this.sourceId, required this.itemId});

  final SourceId sourceId;
  final String itemId;

  @override
  bool operator ==(Object other) =>
      other is SourceMediaRef &&
      other.sourceId == sourceId &&
      other.itemId == itemId;

  @override
  int get hashCode => Object.hash(sourceId, itemId);

  @override
  String toString() => 'SourceMediaRef($sourceId, $itemId)';
}

class Media {
  const Media({required this.title, required this.type, required this.source});

  final String title;
  final MediaType type;
  final SourceMediaRef source;
}
