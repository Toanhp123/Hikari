/// Matches a trimmed, lowercase query without imposing chapter ordering.
bool matchesChapterQuery(
  String query, {
  required String title,
  double? chapterNumber,
}) =>
    query.isEmpty ||
    title.toLowerCase().contains(query) ||
    (chapterNumber?.toString().contains(query) ?? false);
