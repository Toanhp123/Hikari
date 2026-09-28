import 'package:hikari/domain/media/media.dart';
import 'package:hikari/infrastructure/local_media/bounded_archive.dart';

String _archiveItemId(LocalEntry entry, String format) => LocalArchiveRef(
  locator: entry.id,
  displayName: entry.name,
  format: format,
).encode();

String _mediaTitle(String name) {
  final separator = name.lastIndexOf('.');
  return separator <= 0 ? name : name.substring(0, separator);
}

String _archiveFormat(String extension) => extension == 'epub' ? 'epub' : 'cbz';



const _pageExtensions = {'jpg', 'jpeg', 'png', 'webp'};
const _videoExtensions = {'mp4', 'mkv', 'webm', 'm4v'};
const _textExtensions = {'txt', 'md'};
const _archiveExtensions = {'cbz', 'epub'};

class LocalEntry {
  const LocalEntry({
    required this.id,
    required this.parentId,
    required this.name,
    required this.isDirectory,
    this.isArchive = false,
  });

  final String id;
  final String? parentId;
  final String name;
  final bool isDirectory;
  final bool isArchive;
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
  if (extension == 'epub') return MediaType.lightNovel;
  if (extension == 'cbz') return MediaType.manga;
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
        title: entry.isDirectory ? entry.name : _mediaTitle(entry.name),
        type: type,
        source: SourceMediaRef(
          sourceId: SourceId.local,
          itemId: entry.isDirectory
              ? entry.id
              : (_archiveExtensions.contains(_extension(entry.name))
                    ? _archiveItemId(
                        entry,
                        _archiveFormat(_extension(entry.name)),
                      )
                    : entry.id),
        ),
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

  for (
    var index = 0;
    index < leftSegments.length && index < rightSegments.length;
    index++
  ) {
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
