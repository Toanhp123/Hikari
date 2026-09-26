import 'package:hikari/domain/media/media.dart';

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
    !entry.isDirectory &&
    const {'jpg', 'jpeg', 'png', 'webp'}.contains(_extension(entry.name));

List<Media> classifyLocalEntries(List<LocalEntry> entries) {
  final imageParents = entries.where(isPage).map((e) => e.parentId).toSet();
  final result = <Media>[];
  for (final entry in entries) {
    final extension = _extension(entry.name);
    // ponytail: extension-only MVP assumption; metadata classification is deferred.
    final type = entry.isDirectory
        ? (imageParents.contains(entry.id) ? MediaType.manga : null)
        : const {'mp4', 'mkv', 'webm', 'm4v'}.contains(extension)
        ? MediaType.anime
        : const {'txt', 'md'}.contains(extension)
        ? MediaType.lightNovel
        : null;
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
  result.sort((a, b) {
    final order = compareLocalNames(a.title, b.title);
    return order != 0 ? order : a.source.itemId.compareTo(b.source.itemId);
  });
  return result;
}

int compareLocalNames(String a, String b) {
  final chunks = RegExp(r'\d+|\D+');
  final left = chunks.allMatches(a.toLowerCase()).map((m) => m[0]!).toList();
  final right = chunks.allMatches(b.toLowerCase()).map((m) => m[0]!).toList();
  for (var i = 0; i < left.length && i < right.length; i++) {
    var x = left[i];
    var y = right[i];
    if (RegExp(r'^\d').hasMatch(x) && RegExp(r'^\d').hasMatch(y)) {
      x = x.replaceFirst(RegExp(r'^0+'), '');
      y = y.replaceFirst(RegExp(r'^0+'), '');
      final length = x.length.compareTo(y.length);
      if (length != 0) return length;
    }
    final order = x.compareTo(y);
    if (order != 0) return order;
  }
  final length = left.length.compareTo(right.length);
  return length != 0 ? length : a.compareTo(b);
}
