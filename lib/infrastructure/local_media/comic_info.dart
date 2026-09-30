import 'package:xml/xml.dart';

final class ComicInfo {
  const ComicInfo({
    this.title,
    this.series,
    this.number,
    this.summary,
    this.authors = const [],
    this.artists = const [],
    this.genres = const [],
    this.tags = const [],
    this.language,
    this.rating,
    this.rightToLeft = false,
    this.pageOrder = const [],
    this.cover,
  });

  final String? title;
  final String? series;
  final double? number;
  final String? summary;
  final List<String> authors;
  final List<String> artists;
  final List<String> genres;
  final List<String> tags;
  final String? language;
  final double? rating;
  final bool rightToLeft;
  final List<ComicPage> pageOrder;
  final int? cover;

  factory ComicInfo.parse(String xml) {
    final root = XmlDocument.parse(xml).rootElement;
    String? value(String name) {
      final node = root.getElement(name);
      final text = node?.innerText.trim();
      return text == null || text.isEmpty ? null : text;
    }

    List<String> split(String name) => (value(name) ?? '')
        .split(',')
        .map((item) => item.trim())
        .where((item) => item.isNotEmpty)
        .toList(growable: false);

    final pages =
        root
            .getElement('Pages')
            ?.children
            .whereType<XmlElement>()
            .where((element) => element.name.local == 'Page')
            .toList() ??
        const <XmlElement>[];
    final pageOrder = <ComicPage>[];
    for (final page in pages) {
      final image = int.tryParse(page.getAttribute('Image') ?? '');
      if (image == null || image < 0) continue;
      pageOrder.add(
        ComicPage(
          image: image,
          type: page.getAttribute('Type'),
          doublePage: page.getAttribute('DoublePage')?.toLowerCase() == 'true',
          width: int.tryParse(page.getAttribute('ImageWidth') ?? ''),
          height: int.tryParse(page.getAttribute('ImageHeight') ?? ''),
        ),
      );
    }
    final manga = value('Manga')?.toLowerCase();
    final rating = double.tryParse(value('CommunityRating') ?? '');
    return ComicInfo(
      title: value('Title'),
      series: value('Series'),
      number: double.tryParse(value('Number') ?? ''),
      summary: value('Summary'),
      authors: [...split('Writer'), ...split('Translator')],
      artists: [
        ...split('Penciller'),
        ...split('Inker'),
        ...split('Colorist'),
        ...split('Letterer'),
        ...split('CoverArtist'),
      ],
      genres: split('Genre'),
      tags: split('Tags'),
      language: value('LanguageISO'),
      rating: rating != null && rating >= 0 && rating <= 5 ? rating : null,
      rightToLeft:
          manga == 'yesandrighttoleft' || manga == 'yes and right to left',
      pageOrder: List.unmodifiable(pageOrder),
      cover: pageOrder
          .where((page) => page.type?.toLowerCase() == 'frontcover')
          .firstOrNull
          ?.image,
    );
  }
}

final class ComicPage {
  const ComicPage({
    required this.image,
    this.type,
    this.doublePage = false,
    this.width,
    this.height,
  });

  final int image;
  final String? type;
  final bool doublePage;
  final int? width;
  final int? height;
}
