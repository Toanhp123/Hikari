const MANGAUPDATES_API_ORIGIN = "https://api.mangaupdates.com";
const PAGE_SIZE = 25;
const HOME_SECTION_SPECS = Object.freeze([
  Object.freeze({
    sourceId: "mangaupdates-popular",
    title: "MangaUpdates Popular",
    kind: "POPULAR",
    errorTag: "popular",
    orderBy: "week_pos",
  }),
  Object.freeze({
    sourceId: "mangaupdates-latest",
    title: "MangaUpdates Latest",
    kind: "LATEST_UPDATES",
    errorTag: "latest",
    feed: "releases",
  }),
  Object.freeze({
    sourceId: "mangaupdates-top-rated",
    title: "MangaUpdates Top Rated",
    kind: "TOP_RATED",
    errorTag: "top_rated",
    orderBy: "rating",
  }),
]);
const ALLOWED_REMOTE_PREFIXES = [
  "https://cdn.mangaupdates.com/",
  "https://mangaupdates.com/",
  "https://www.mangaupdates.com/",
];

const PROTOCOL_LIMITS = Object.freeze({
  idLength: 1024,
  textLength: 4096,
  authors: 100,
  collectionItems: 200,
  sectionItems: 200,
});
const PROTOCOL_CONTENT_TYPES = new Set(["LIGHT_NOVEL", "WEB_NOVEL", "MANGA", "ANIME"]);
const PROTOCOL_PUBLICATION_STATUSES = new Set(["ONGOING", "COMPLETED", "HIATUS", "CANCELLED", "UPCOMING"]);
const ISO_CONTROL = /[\u0000-\u001f\u007f-\u009f]/;
const INVALID_URL_WHITESPACE = /\s/;
const INVALID_PERCENT_ESCAPE = /%(?![0-9a-fA-F]{2})/;

function protocolGuardFailure(field) {
  throw pluginError(`plugin.mangaupdates_protocol_guard_${field}`, "MangaUpdates mapped an invalid catalog item");
}

function isProtocolText(value, maxLength) {
  return typeof value === "string" &&
    value.trim().length > 0 &&
    value.length <= maxLength &&
    !ISO_CONTROL.test(value);
}

function isProtocolHttpsUrl(value) {
  if (value == null) return true;
  if (typeof value !== "string" || !value.startsWith("https://") || INVALID_URL_WHITESPACE.test(value)) return false;
  if (INVALID_PERCENT_ESCAPE.test(value)) return false;
  const authorityStart = "https://".length;
  let authorityEnd = value.length;
  for (const marker of ["/", "?", "#"]) {
    const index = value.indexOf(marker, authorityStart);
    if (index >= 0 && index < authorityEnd) authorityEnd = index;
  }
  const authority = value.slice(authorityStart, authorityEnd);
  return authority.length > 0 && !authority.includes("@");
}

function assertProtocolStringCollection(values, field, maxItems) {
  if (!Array.isArray(values) || values.length > maxItems) protocolGuardFailure(field);
  for (const value of values) {
    if (!isProtocolText(value, PROTOCOL_LIMITS.textLength)) protocolGuardFailure(field);
  }
}

function assertProtocolCard(card) {
  if (!card || typeof card !== "object") protocolGuardFailure("item");
  if (!isProtocolText(card.sourceId, PROTOCOL_LIMITS.idLength)) protocolGuardFailure("source_id");
  if (!isProtocolText(card.title, PROTOCOL_LIMITS.textLength)) protocolGuardFailure("title");
  if (!PROTOCOL_CONTENT_TYPES.has(card.contentType)) protocolGuardFailure("content_type");
  assertProtocolStringCollection(card.authors, "authors", PROTOCOL_LIMITS.authors);
  if (!isProtocolHttpsUrl(card.coverUrl)) protocolGuardFailure("cover_url");
  if (card.score != null) {
    const value = Number(card.score.value);
    const scale = Number(card.score.scale);
    if (!Number.isFinite(value) || !Number.isFinite(scale) || scale <= 0 || value < 0 || value > scale) {
      protocolGuardFailure("score");
    }
  }
  assertProtocolStringCollection(card.genres, "genres", PROTOCOL_LIMITS.collectionItems);
  if (card.popularityRank != null && (!Number.isInteger(card.popularityRank) || card.popularityRank <= 0)) {
    protocolGuardFailure("popularity_rank");
  }
  if (card.publicationStatus != null && !PROTOCOL_PUBLICATION_STATUSES.has(card.publicationStatus)) {
    protocolGuardFailure("publication_status");
  }
  if (card.latestUpdate != null) {
    const at = Number(card.latestUpdate.atEpochMillis);
    if (!Number.isSafeInteger(at) || at < 0) protocolGuardFailure("latest_update_time");
    if (card.latestUpdate.releaseLabel != null &&
        !isProtocolText(card.latestUpdate.releaseLabel, PROTOCOL_LIMITS.textLength)) {
      protocolGuardFailure("latest_update_label");
    }
  }
}

function assertProtocolSection(section) {
  if (!section || typeof section !== "object") protocolGuardFailure("section");
  if (!isProtocolText(section.sourceId, PROTOCOL_LIMITS.idLength)) protocolGuardFailure("section_source_id");
  if (!isProtocolText(section.title, PROTOCOL_LIMITS.textLength)) protocolGuardFailure("section_title");
  if (!Array.isArray(section.items) || section.items.length > PROTOCOL_LIMITS.sectionItems) {
    protocolGuardFailure("section_items");
  }
  const sourceIds = new Set();
  for (const card of section.items) {
    assertProtocolCard(card);
    if (sourceIds.has(card.sourceId)) protocolGuardFailure("duplicate_source_id");
    sourceIds.add(card.sourceId);
  }
  return section;
}

function pluginError(code, message) {
  const error = new Error(message);
  error.code = code;
  return error;
}

async function requestJson(path, options) {
  const method = options?.method || "GET";
  const body = options?.body == null ? null : JSON.stringify(options.body);
  const headers = {Accept: "application/json"};
  if (body != null) headers["Content-Type"] = "application/json";

  const response = await host.http({
    url: `${MANGAUPDATES_API_ORIGIN}${path}`,
    method,
    headers,
    body,
  });

  if (response.status === 400) {
    throw pluginError("plugin.mangaupdates_bad_request", "MangaUpdates rejected the request");
  }
  if (response.status === 401 || response.status === 403) {
    throw pluginError("plugin.mangaupdates_access_denied", "MangaUpdates denied the request");
  }
  if (response.status === 404) {
    throw pluginError("plugin.mangaupdates_not_found", "MangaUpdates series not found");
  }
  if (response.status === 429) {
    throw pluginError("plugin.mangaupdates_rate_limited", "MangaUpdates API rate limited the request");
  }
  if (response.status < 200 || response.status >= 300) {
    const status = Number.isInteger(response.status) ? response.status : 0;
    throw pluginError(`plugin.mangaupdates_http_status_${status}`, "Unexpected MangaUpdates API status");
  }

  try {
    return JSON.parse(response.body);
  } catch (_) {
    throw pluginError("plugin.mangaupdates_invalid_response", "Invalid MangaUpdates API JSON");
  }
}

function boundedProtocolText(value, maxLength = PROTOCOL_LIMITS.textLength) {
  if (typeof value !== "string") return "";
  let result = "";
  for (const char of value.trim()) {
    const code = char.charCodeAt(0);
    const normalized = (code <= 0x1f || (code >= 0x7f && code <= 0x9f)) ? " " : char;
    if (result.length + normalized.length > maxLength) break;
    result += normalized;
  }
  return result.trim();
}

function distinctNonBlank(values, maxItems = PROTOCOL_LIMITS.collectionItems) {
  const seen = new Set();
  const result = [];
  for (const value of values || []) {
    const normalized = boundedProtocolText(value);
    if (!normalized || seen.has(normalized)) continue;
    seen.add(normalized);
    result.push(normalized);
    if (result.length >= maxItems) break;
  }
  return result;
}

function textValue(value) {
  if (typeof value === "string") return boundedProtocolText(value);
  if (!value || typeof value !== "object") return "";
  for (const key of ["name", "title", "text", "value"]) {
    const normalized = boundedProtocolText(value[key]);
    if (normalized) return normalized;
  }
  return "";
}

function positiveSeriesId(value) {
  const normalized = String(value == null ? "" : value).trim();
  return normalized.length <= PROTOCOL_LIMITS.idLength && /^[1-9][0-9]*$/.test(normalized)
    ? normalized
    : null;
}

function requireSeriesId(value) {
  const sourceId = positiveSeriesId(value);
  if (!sourceId) {
    throw pluginError("plugin.mangaupdates_source_id_invalid", "Invalid MangaUpdates series ID");
  }
  return sourceId;
}

function contentType(type) {
  const normalized = typeof type === "string" ? type.trim().toLowerCase() : "";
  if (normalized.includes("web novel")) return "WEB_NOVEL";
  if (normalized.includes("novel")) return "LIGHT_NOVEL";
  return "MANGA";
}

function languageTags(type) {
  const normalized = typeof type === "string" ? type.trim().toLowerCase() : "";
  if (normalized.includes("manhwa")) return ["ko"];
  if (normalized.includes("manhua")) return ["zh-hans"];
  if (normalized.includes("oel") || normalized.includes("english")) return ["en"];
  return ["ja"];
}

function allowedRemoteUrl(value) {
  if (typeof value !== "string") return null;
  const normalized = value.trim();
  if (!isProtocolHttpsUrl(normalized)) return null;
  return ALLOWED_REMOTE_PREFIXES.some(prefix => normalized.startsWith(prefix)) ? normalized : null;
}

function imageReference(item) {
  const image = item?.image;
  const urls = image?.url || image?.urls || {};
  const candidates = [
    urls.thumb,
    urls.thumbnail,
    urls.original,
    urls.large,
    image?.thumb,
    image?.thumbnail,
    image?.original,
  ];
  for (const candidate of candidates) {
    const allowed = allowedRemoteUrl(candidate);
    if (allowed) return allowed;
  }
  return null;
}

function scoreReference(item) {
  const score = Number(item?.bayesian_rating ?? item?.rating);
  return Number.isFinite(score) && score > 0 && score <= 10
    ? {value: score, scale: 10}
    : null;
}

function authors(item) {
  const values = Array.isArray(item?.authors) ? item.authors : [];
  return distinctNonBlank(values.map(textValue), PROTOCOL_LIMITS.authors);
}

function genres(item) {
  const values = Array.isArray(item?.genres) ? item.genres : [];
  return distinctNonBlank(values.map(value => {
    if (typeof value === "string") return value;
    if (typeof value?.genre === "string") return value.genre;
    return textValue(value);
  }), PROTOCOL_LIMITS.collectionItems);
}

function publicationStatus(item) {
  if (item?.completed === true) return "COMPLETED";
  const value = typeof item?.status === "string" ? item.status.toLowerCase() : "";
  if (value.includes("cancel")) return "CANCELLED";
  if (value.includes("hiatus")) return "HIATUS";
  if (value.includes("complete") || value.includes("finished")) return "COMPLETED";
  if (value.includes("upcoming") || value.includes("not yet")) return "UPCOMING";
  if (value.includes("ongoing") || value.includes("active")) return "ONGOING";
  return null;
}

function popularityRank(item) {
  const candidates = [
    item?.rank?.position?.week,
    item?.rank?.week,
    item?.popularity_rank,
    item?.rank,
  ];
  for (const candidate of candidates) {
    const value = Number(candidate);
    if (Number.isSafeInteger(value) && value > 0) return value;
  }
  return null;
}

function searchRecord(entry) {
  return entry?.record && typeof entry.record === "object" ? entry.record : entry;
}

function toCard(entry) {
  const item = searchRecord(entry);
  if (!item || typeof item !== "object") return null;
  const sourceId = positiveSeriesId(item.series_id ?? item.id);
  const title = textValue(item.title) || textValue(entry?.hit_title);
  if (!sourceId || !title) return null;
  return {
    sourceId,
    title,
    contentType: contentType(item.type),
    authors: authors(item),
    coverUrl: imageReference(item),
    score: scoreReference(item),
    genres: genres(item),
    popularityRank: popularityRank(item),
    publicationStatus: publicationStatus(item),
  };
}

function uniqueCards(results) {
  const seen = new Set();
  const items = [];
  for (const result of results || []) {
    const card = toCard(result);
    if (!card || seen.has(card.sourceId)) continue;
    seen.add(card.sourceId);
    items.push(card);
    if (items.length >= PROTOCOL_LIMITS.sectionItems) break;
  }
  return items;
}

function releaseEpochMillis(record) {
  const rfc3339 = record?.time_added?.as_rfc3339;
  if (typeof rfc3339 === "string" && rfc3339.trim()) {
    const parsed = Date.parse(rfc3339);
    if (Number.isSafeInteger(parsed) && parsed >= 0) return parsed;
  }
  const timestamp = Number(record?.time_added?.timestamp);
  if (Number.isSafeInteger(timestamp) && timestamp >= 0) {
    const millis = timestamp < 1_000_000_000_000 ? timestamp * 1000 : timestamp;
    if (Number.isSafeInteger(millis) && millis >= 0) return millis;
  }
  const releaseDate = record?.release_date;
  if (typeof releaseDate === "string" && releaseDate.trim()) {
    const parsed = Date.parse(releaseDate);
    if (Number.isSafeInteger(parsed) && parsed >= 0) return parsed;
  }
  return null;
}

function releaseLabel(record) {
  const parts = [];
  const volume = boundedProtocolText(record?.volume);
  const chapter = boundedProtocolText(record?.chapter);
  if (volume) parts.push(`Vol. ${volume}`);
  if (chapter) parts.push(`Ch. ${chapter}`);
  const label = boundedProtocolText(parts.join(" "));
  return label || null;
}

function releaseCard(entry) {
  if (!entry || typeof entry !== "object") return null;
  const record = entry.record && typeof entry.record === "object" ? entry.record : entry;
  const metadata = entry.metadata && typeof entry.metadata === "object" ? entry.metadata : {};
  const nestedSeries = entry.series && typeof entry.series === "object" ? entry.series : {};
  const metadataSeries = metadata.series && typeof metadata.series === "object" ? metadata.series : {};
  const sourceId = positiveSeriesId(
    entry.series_id ??
      entry.seriesId ??
      nestedSeries.series_id ??
      nestedSeries.id ??
      metadata.series_id ??
      metadataSeries.series_id ??
      metadataSeries.id,
  );
  const title =
    textValue(metadataSeries.title) ||
    textValue(nestedSeries.title) ||
    textValue(entry.title) ||
    textValue(record.title) ||
    textValue(metadata.title);
  if (!sourceId || !title) return null;

  const card = toCard({
    series_id: sourceId,
    title,
    type: metadataSeries.type ?? nestedSeries.type ?? entry.type ?? metadata.type,
    authors: metadataSeries.authors ?? nestedSeries.authors ?? entry.authors ?? metadata.authors,
    image: metadataSeries.image ?? nestedSeries.image ?? entry.image ?? metadata.image,
    bayesian_rating:
      metadataSeries.bayesian_rating ??
      nestedSeries.bayesian_rating ??
      entry.bayesian_rating ??
      metadata.bayesian_rating,
    genres: metadataSeries.genres ?? nestedSeries.genres ?? entry.genres ?? metadata.genres,
    rank: metadataSeries.rank ?? nestedSeries.rank ?? entry.rank ?? metadata.rank,
    status: metadataSeries.status ?? nestedSeries.status ?? entry.status ?? metadata.status,
    completed: metadataSeries.completed ?? nestedSeries.completed ?? entry.completed ?? metadata.completed,
  });
  if (!card) return null;
  const atEpochMillis = releaseEpochMillis(record);
  if (atEpochMillis == null) return null;
  return {
    ...card,
    latestUpdate: {
      atEpochMillis,
      releaseLabel: releaseLabel(record),
    },
  };
}

function uniqueReleaseCards(results) {
  const seen = new Set();
  const items = [];
  for (const result of results || []) {
    const card = releaseCard(result);
    if (!card || seen.has(card.sourceId)) continue;
    seen.add(card.sourceId);
    items.push(card);
    if (items.length >= PROTOCOL_LIMITS.sectionItems) break;
  }
  return items;
}

async function homeSection(spec) {
  if (spec.feed === "releases") {
    const payload = await requestJson("/v1/releases/days?page=1&include_metadata=true", {method: "GET"});
    const results = Array.isArray(payload?.results) ? payload.results : [];
    return assertProtocolSection({
      sourceId: spec.sourceId,
      title: spec.title,
      kind: spec.kind,
      items: uniqueReleaseCards(results),
    });
  }

  const payload = await requestJson("/v1/series/search", {
    method: "POST",
    body: {
      page: 1,
      perpage: PAGE_SIZE,
      orderby: spec.orderBy,
    },
  });
  const results = Array.isArray(payload?.results) ? payload.results : [];
  return assertProtocolSection({
    sourceId: spec.sourceId,
    title: spec.title,
    kind: spec.kind,
    items: uniqueCards(results),
  });
}

function requestedPage(nextToken) {
  if (nextToken == null) return 1;
  const page = Number(nextToken);
  return Number.isInteger(page) && page > 0 ? page : 1;
}

function nextPageToken(payload, requested, resultCount) {
  const page = Number(payload?.page);
  const currentPage = Number.isInteger(page) && page > 0 ? page : requested;
  const perPageValue = Number(payload?.per_page ?? payload?.perpage);
  const perPage = Number.isInteger(perPageValue) && perPageValue > 0 ? perPageValue : PAGE_SIZE;
  const totalHits = Number(payload?.total_hits);
  if (Number.isFinite(totalHits) && totalHits >= 0) {
    return currentPage * perPage < totalHits ? String(currentPage + 1) : null;
  }
  return resultCount >= perPage ? String(currentPage + 1) : null;
}

function aliases(item) {
  const collections = [
    item?.associated,
    item?.associated_titles,
    item?.aliases,
    item?.alternative_titles,
    item?.alt_titles,
  ];
  const values = [];
  for (const collection of collections) {
    if (Array.isArray(collection)) {
      for (const value of collection) values.push(textValue(value));
    } else if (collection && typeof collection === "object") {
      for (const value of Object.values(collection)) values.push(textValue(value));
    } else {
      values.push(textValue(collection));
    }
  }
  const canonical = textValue(item?.title);
  return distinctNonBlank(values, PROTOCOL_LIMITS.collectionItems)
    .filter(value => value !== canonical)
    .slice(0, PROTOCOL_LIMITS.collectionItems);
}

function description(item) {
  if (typeof item?.description !== "string") return null;
  const normalized = item.description.trim();
  return normalized ? normalized.slice(0, 200_000) : null;
}

function sourceUrl(item) {
  return allowedRemoteUrl(item?.url ?? item?.series_url);
}

function unwrapDetails(payload) {
  if (payload?.record && typeof payload.record === "object") return payload.record;
  if (payload?.series && typeof payload.series === "object") return payload.series;
  return payload;
}

globalThis.openstoryPlugin = Object.freeze({
  catalog: Object.freeze({
    home: async () => {
      const sections = [];
      for (const spec of HOME_SECTION_SPECS) {
        try {
          sections.push(await homeSection(spec));
        } catch (failure) {
          const code = failure && typeof failure.code === "string"
            ? failure.code
            : "plugin.execution_failed";
          throw pluginError(`${code}.home_${spec.errorTag}`, "MangaUpdates home section failed");
        }
      }
      return {sections};
    },

    search: async input => {
      const query = typeof input?.query === "string" ? input.query.trim() : "";
      if (!query) return {items: [], nextToken: null};
      const page = requestedPage(input?.nextToken);
      const payload = await requestJson("/v1/series/search", {
        method: "POST",
        body: {
          search: query,
          stype: "title",
          page,
          perpage: PAGE_SIZE,
        },
      });
      const results = Array.isArray(payload?.results) ? payload.results : [];
      const items = uniqueCards(results);
      return {
        items,
        nextToken: nextPageToken(payload, page, results.length),
      };
    },

    details: async input => {
      const sourceId = requireSeriesId(input?.sourceId);
      const payload = await requestJson(`/v1/series/${encodeURIComponent(sourceId)}`);
      const item = unwrapDetails(payload);
      const returnedId = positiveSeriesId(item?.series_id ?? item?.id);
      if (!item || returnedId !== sourceId) {
        throw pluginError("plugin.mangaupdates_invalid_response", "Mismatched MangaUpdates series response");
      }
      const title = textValue(item.title);
      if (!title) {
        throw pluginError("plugin.mangaupdates_invalid_response", "MangaUpdates series title is missing");
      }
      return {
        sourceId,
        sourceUrl: sourceUrl(item),
        title,
        aliases: aliases(item),
        authors: authors(item),
        description: description(item),
        genres: genres(item),
        contentType: contentType(item.type),
        languageTags: languageTags(item.type),
        coverUrl: imageReference(item),
        score: scoreReference(item),
        popularityRank: popularityRank(item),
        publicationStatus: publicationStatus(item),
      };
    },

    filters: async () => ({filters: []}),
  }),
});
