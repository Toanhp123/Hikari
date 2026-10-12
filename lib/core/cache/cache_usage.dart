/// Indexed sizes for the managed, reconstructible cache categories.
///
/// This is a logical byte snapshot, not the total app disk footprint.
final class CacheUsage {
  const CacheUsage(this.byCategory);

  final Map<String, int> byCategory;

  int get totalBytes => byCategory.values.fold(0, (sum, size) => sum + size);
}
