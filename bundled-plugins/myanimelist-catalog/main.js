const MAL_API_ORIGIN = "https://api.myanimelist.net";
const MAL_ORIGIN = "https://myanimelist.net";
const MAL_IMAGE_ORIGIN = "https://cdn.myanimelist.net";
const PAGE_SIZE = 25;
const CARD_FIELDS = [
  "id",
  "title",
  "main_picture",
  "mean",
  "media_type",
  "authors{first_name,last_name}",
].join(",");
const DETAIL_FIELDS = [
  CARD_FIELDS,
  "alternative_titles",
  "synopsis",
  "popularity",
  "status",
  "genres",
].join(",");

function pluginError(code, message) {
  const error = new Error(message);
  error.code = code;
  return error;
}

async function getJson(path) {
  const response = await host.http({
    url: `${MAL_API_ORIGIN}${path}`,
    headers: {Accept: "application/json"},
  });
  if (response.status === 401 || response.status === 403) {
    throw pluginError("plugin.myanimelist_unauthorized", "MyAnimeList API rejected the client identifier");
  }
  if (response.status === 404) {
    throw pluginError("plugin.myanimelist_not_found", "MyAnimeList manga not found");
  }
  if (response.status === 429) {
    throw pluginError("plugin.myanimelist_rate_limited", "MyAnimeList API rate limited the request");
  }
  if (response.status !== 200) {
    throw pluginError("plugin.myanimelist_http_status", "Unexpected MyAnimeList API status");
  }
  try {
    return JSON.parse(response.body);
  } catch (_) {
    throw pluginError("plugin.myanimelist_invalid_response", "Invalid MyAnimeList API JSON");
  }
}

function distinctNonBlank(values) {
  const seen = new Set();
  const result = [];
  for (const value of values || []) {
    if (typeof value !== "string") continue;
    const normalized = value.trim();
    if (!normalized || seen.has(normalized)) continue;
    seen.add(normalized);
    result.push(normalized);
  }
  return result;
}

function contentType(mediaType) {
  const normalized = typeof mediaType === "string" ? mediaType.toLowerCase() : "";
  if (normalized === "novel") return "LIGHT_NOVEL";
  return "MANGA";
}

function languageTags(mediaType) {
  const normalized = typeof mediaType === "string" ? mediaType.toLowerCase() : "";
  if (normalized === "manhwa") return ["ko"];
  if (normalized === "manhua") return ["zh-hans"];
  return ["ja"];
}

function imageReference(item) {
  const candidate = item?.main_picture?.large || item?.main_picture?.medium;
  if (typeof candidate !== "string" || !candidate.startsWith(`${MAL_IMAGE_ORIGIN}/`)) {
    return null;
  }
  return candidate;
}

function scoreReference(item) {
  const score = Number(item?.mean);
  return Number.isFinite(score) && score > 0 && score <= 10
    ? {value: score, scale: 10}
    : null;
}

function authorName(author) {
  const node = author?.node || author;
  const first = typeof node?.first_name === "string" ? node.first_name.trim() : "";
  const last = typeof node?.last_name === "string" ? node.last_name.trim() : "";
  if (last && first) return `${last}, ${first}`;
  return last || first;
}

function authors(item) {
  return distinctNonBlank((item?.authors || []).map(authorName));
}

function toCard(item) {
  return {
    sourceId: String(item.id),
    title: String(item.title || "Untitled"),
    contentType: contentType(item.media_type),
    authors: authors(item),
    coverUrl: imageReference(item),
    score: scoreReference(item),
  };
}

function requestedOffset(nextToken) {
  if (nextToken == null) return 0;
  const parsed = Number(nextToken);
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : 0;
}

function nextOffsetToken(paging) {
  const next = typeof paging?.next === "string" ? paging.next : "";
  const match = /[?&]offset=([0-9]+)/.exec(next);
  return match ? match[1] : null;
}

function sourceUrl(sourceId) {
  return `${MAL_ORIGIN}/manga/${encodeURIComponent(String(sourceId))}`;
}

function aliases(item) {
  const alternative = item?.alternative_titles || {};
  return distinctNonBlank([
    alternative.en,
    alternative.ja,
    ...(Array.isArray(alternative.synonyms) ? alternative.synonyms : []),
  ]).filter(alias => alias !== item.title);
}

function genres(item) {
  return distinctNonBlank((item?.genres || []).map(value => value?.name));
}

function popularityRank(item) {
  const value = Number(item?.popularity);
  return Number.isInteger(value) && value > 0 ? value : null;
}

function requireMangaId(sourceId) {
  const value = String(sourceId || "");
  if (!/^[1-9][0-9]*$/.test(value)) {
    throw pluginError("plugin.myanimelist_source_id_invalid", "Invalid MyAnimeList manga ID");
  }
  return value;
}

function nodeItems(payload) {
  return Array.isArray(payload?.data)
    ? payload.data.map(entry => entry?.node).filter(Boolean)
    : [];
}

globalThis.openstoryPlugin = Object.freeze({
  catalog: Object.freeze({
  home: async () => {
    const payload = await getJson(
      `/v2/manga/ranking?ranking_type=manga&limit=${PAGE_SIZE}&fields=${encodeURIComponent(CARD_FIELDS)}`
    );
    return {
      sections: [{
        sourceId: "mal-top-manga",
        title: "MyAnimeList Top Manga",
        items: nodeItems(payload).map(toCard),
      }],
    };
  },

  search: async input => {
    const query = typeof input?.query === "string" ? input.query.trim() : "";
    if (!query) return {items: [], nextToken: null};
    const offset = requestedOffset(input?.nextToken);
    const payload = await getJson(
      `/v2/manga?q=${encodeURIComponent(query)}` +
        `&limit=${PAGE_SIZE}&offset=${offset}&fields=${encodeURIComponent(CARD_FIELDS)}`
    );
    return {
      items: nodeItems(payload).map(toCard),
      nextToken: nextOffsetToken(payload?.paging),
    };
  },

  details: async input => {
    const sourceId = requireMangaId(input?.sourceId);
    const item = await getJson(
      `/v2/manga/${encodeURIComponent(sourceId)}?fields=${encodeURIComponent(DETAIL_FIELDS)}`
    );
    if (!item || String(item.id) !== sourceId) {
      throw pluginError("plugin.myanimelist_invalid_response", "Mismatched MyAnimeList manga response");
    }
    return {
      sourceId,
      sourceUrl: sourceUrl(sourceId),
      title: String(item.title || "Untitled"),
      aliases: aliases(item),
      authors: authors(item),
      description: typeof item.synopsis === "string" && item.synopsis.trim() ? item.synopsis : null,
      genres: genres(item),
      contentType: contentType(item.media_type),
      languageTags: languageTags(item.media_type),
      coverUrl: imageReference(item),
      score: scoreReference(item),
      popularityRank: popularityRank(item),
    };
  },

  filters: async () => ({filters: []}),
  }),
});
