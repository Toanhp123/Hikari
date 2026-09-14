package app.openstory.catalog.domain.limits

object CatalogInputLimits {
    const val SOURCE_KEY_UTF8_BYTES = 128
    const val SOURCE_STORY_ID_UTF8_BYTES = 512
    const val TITLE_UNICODE_SCALARS = 1_024
    const val DESCRIPTION_UTF8_BYTES = 64 * 1_024
    const val AUTHORS = 32
    const val ARTISTS = 32
    const val GENRES = 64
    const val PERSON_UNICODE_SCALARS = 512
    const val GENRE_UNICODE_SCALARS = 256
    const val COVER_LOCATOR_CHARS = 4_096
    const val SOURCE_VERSION_UTF8_BYTES = 256
    const val STATUS_UNICODE_SCALARS = 512
    const val LANGUAGE_UNICODE_SCALARS = 128
    const val ALTERNATE_TITLES = 32
    const val CATALOG_LANGUAGE_TAGS = 16
    const val LANGUAGE_TAG_UTF8_BYTES = 64
    const val SEARCH_QUERY_UNICODE_SCALARS = 256
    const val CONTINUATION_UTF8_BYTES = 4_096
    const val SECTION_KEY_UTF8_BYTES = 256
    const val SECTION_LABEL_UNICODE_SCALARS = 256
    const val SUPPORTING_TEXT_UNICODE_SCALARS = 512
    const val SEARCH_PAGE_ITEMS = 60
    const val SECTION_PAGE_ITEMS = 100
    const val SIMILAR_ITEMS = 30
    const val LOCAL_ASSET_ID_UTF8_BYTES = 512
    const val LOCAL_ASSET_VERSION_UTF8_BYTES = 128
    const val STABLE_ARTWORK_TOKEN_UTF8_BYTES = 512
    const val DISCOVER_SECTIONS = 3
}
