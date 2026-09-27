import 'package:hikari/domain/media/media.dart';

const _pageExtensions = {'jpg', 'jpeg', 'png', 'webp'};
const _videoExtensions = {'mp4', 'mkv', 'webm', 'm4v'};
const _textExtensions = {'txt', 'md'};

class LocalEntry {
  const LocalEntry({
    required this.id,
    required this.parentId,
    required this.name,
    required this.isDirectory,
  });

  final String id;
  final String? parentId;
  final String name;
  final bool isDirectory;
}

String _extension(String name) =>
    name.contains('.') ? name.split('.').last.toLowerCase() : '';

bool isPage(LocalEntry entry) =>
    !entry.isDirectory && _pageExtensions.contains(_extension(entry.name));

MediaType? _classifyEntry(LocalEntry entry, Set<String?> imageParents) {
  if (entry.isDirectory) {
    return imageParents.contains(entry.id) ? MediaType.manga : null;
  }

  final extension = _extension(entry.name);
  if (_videoExtensions.contains(extension)) return MediaType.anime;
  if (_textExtensions.contains(extension)) return MediaType.lightNovel;
  return null;
}

List<Media> classifyLocalEntries(List<LocalEntry> entries) {
  final imageParents = entries.where(isPage).map((e) => e.parentId).toSet();
  final result = <Media>[];
  for (final entry in entries) {
    final type = _classifyEntry(entry, imageParents);
    if (type == null) continue;
    result.add(
      Media(
        title: entry.isDirectory
            ? entry.name
            : entry.name.substring(0, entry.name.lastIndexOf('.')),
        type: type,
        source: SourceMediaRef(sourceId: SourceId.local, itemId: entry.id),
      ),
    );
  }
  result.sort((leftMedia, rightMedia) {
    final order = compareLocalNames(leftMedia.title, rightMedia.title);
    return order != 0
        ? order
        : leftMedia.source.itemId.compareTo(rightMedia.source.itemId);
  });
  return result;
}

int compareLocalNames(String leftName, String rightName) {
  final chunks = RegExp(r'\d+|\D+');
  final leftSegments = chunks
      .allMatches(leftName.toLowerCase())
      .map((match) => match[0]!)
      .toList();
  final rightSegments = chunks
      .allMatches(rightName.toLowerCase())
      .map((match) => match[0]!)
      .toList();

  for (var index = 0;
      index < leftSegments.length && index < rightSegments.length;
      index++) {
    var leftSegment = leftSegments[index];
    var rightSegment = rightSegments[index];
    if (RegExp(r'^\d').hasMatch(leftSegment) &&
        RegExp(r'^\d').hasMatch(rightSegment)) {
      leftSegment = leftSegment.replaceFirst(RegExp(r'^0+'), '');
      rightSegment = rightSegment.replaceFirst(RegExp(r'^0+'), '');
      final lengthOrder = leftSegment.length.compareTo(rightSegment.length);
      if (lengthOrder != 0) return lengthOrder;
    }
    final segmentOrder = leftSegment.compareTo(rightSegment);
    if (segmentOrder != 0) return segmentOrder;
  }

  final segmentCountOrder = leftSegments.length.compareTo(rightSegments.length);
  return segmentCountOrder != 0
      ? segmentCountOrder
      : leftName.compareTo(rightName);
}
