import 'package:hikari/application/media/read_manga_page.dart';
import 'package:hikari/application/media/read_novel_chapter_content.dart';
import 'package:hikari/application/media/read_novel_resource.dart';
import 'package:hikari/application/sources/read_source_artwork.dart';
import 'package:hikari/core/cache/cache_maintenance.dart';
import 'package:hikari/core/cache/cache_usage.dart';

/// Management of the four explicit, reconstructible reading and artwork namespaces.
///
/// The native HTTP cache, decoded images, SAF documents, local archive copies
/// and all durable user state are intentionally outside this capability.
final class CacheManagement {
  CacheManagement(this._cache);

  final CacheMaintenance _cache;

  static const namespaces = <String, String>{
    ReadSourceArtwork.cacheNamespace: 'Source artwork',
    ReadMangaPage.cacheNamespace: 'Manga pages',
    ReadNovelChapterContent.cacheNamespace: 'Novel chapters',
    ReadNovelResource.cacheNamespace: 'Novel resources',
  };

  Future<CacheUsage> usage() async {
    final sizes = await _cache.sizes(namespaces.keys);
    return CacheUsage(
      Map.unmodifiable({
        for (final category in namespaces.entries)
          category.value: sizes[category.key] ?? 0,
      }),
    );
  }

  Future<void> clear() => _cache.clearNamespaces(namespaces.keys);
}
