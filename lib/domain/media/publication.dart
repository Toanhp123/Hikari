import 'dart:typed_data';

import 'package:hikari/domain/media/media.dart';
import 'package:hikari/domain/media/novel.dart';
import 'package:hikari/domain/media/reading.dart';
import 'package:hikari/domain/media/source.dart';

final class PublicationSection {
  const PublicationSection({required this.resource, required this.title});

  final String resource;
  final String title;
}

final class PublicationLink {
  const PublicationLink({
    required this.label,
    required this.resource,
    this.fragment,
  });

  final String label;
  final String resource;
  final String? fragment;
}

final class PublicationResource {
  const PublicationResource({required this.resource, required this.mediaType});

  final String resource;
  final String mediaType;
}

final class Publication {
  Publication({
    required this.metadata,
    required List<PublicationSection> spine,
    List<PublicationLink> toc = const [],
    List<PublicationResource> resources = const [],
  }) : spine = List.unmodifiable(spine),
       toc = List.unmodifiable(toc),
       resources = List.unmodifiable(resources);

  final MediaMetadata metadata;
  final List<PublicationSection> spine;
  final List<PublicationLink> toc;
  final List<PublicationResource> resources;
}

abstract interface class PublicationSource implements MediaSource {
  bool canOpenPublication(SourceMediaRef publication);
  Future<Publication> publication(SourceMediaRef publication);
  Future<NovelChapterContent> readSection(
    SourceMediaRef publication,
    String resource,
  );
  Future<Uint8List> readResource(SourceMediaRef resource);
}
