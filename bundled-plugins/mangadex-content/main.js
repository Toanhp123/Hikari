const MANGADEX_API_ORIGIN = "https://api.mangadex.org";
const MANGADEX_ORIGIN = "https://mangadex.org";
const PAGE_SIZE = 20;
const CHAPTER_PAGE_SIZE = 100;
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function pluginError(code, message) {
  const error = new Error(message);
  error.code = code;
  return error;
}

async function getJson(path) {
  const response = await host.http({
    url: `${MANGADEX_API_ORIGIN}${path}`,
    headers: {Accept: "application/json"},
  });
  if (response.status === 404) {
    throw pluginError("plugin.mangadex_not_found", "MangaDex manga not found");
  }
  if (response.status === 429) {
    throw pluginError("plugin.mangadex_rate_limited", "MangaDex API rate limited the request");
  }
  if (response.status < 200 || response.status >= 300) {
    throw pluginError("plugin.mangadex_http_status", "Unexpected MangaDex API status");
  }
  try {
    return JSON.parse(response.body);
  } catch (_) {
    throw pluginError("plugin.mangadex_invalid_response", "Invalid MangaDex API JSON");
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

function localizedValues(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) return [];
  return Object.values(value).filter(item => typeof item === "string");
}

function primaryTitle(attributes) {
  const titles = attributes?.title || {};
  const preferred = [titles.en, titles["ja-ro"], titles.ja].find(
    value => typeof value === "string" && value.trim()
  );
  return preferred?.trim() || distinctNonBlank(localizedValues(titles))[0] || "Untitled";
}

function aliases(attributes, title) {
  const values = [];
  for (const entry of attributes?.altTitles || []) {
    values.push(...localizedValues(entry));
  }
  values.push(...localizedValues(attributes?.title));
  return distinctNonBlank(values).filter(value => value !== title);
}

function authors(resource) {
  return distinctNonBlank(
    (resource?.relationships || [])
      .filter(relation => relation?.type === "author" || relation?.type === "artist")
      .map(relation => relation?.attributes?.name)
  );
}

function sourceUrl(sourceStoryId) {
  return `${MANGADEX_ORIGIN}/title/${encodeURIComponent(sourceStoryId)}`;
}

function toCandidate(resource) {
  const sourceStoryId = requireMangaId(resource?.id);
  const title = primaryTitle(resource?.attributes);
  return {
    sourceStoryId,
    title,
    authors: authors(resource),
    aliases: aliases(resource?.attributes, title),
    contentType: "MANGA",
    sourceUrl: sourceUrl(sourceStoryId),
  };
}

function requireMangaId(value) {
  const sourceStoryId = typeof value === "string" ? value.trim() : "";
  if (!UUID_PATTERN.test(sourceStoryId)) {
    throw pluginError("plugin.mangadex_source_id_invalid", "Invalid MangaDex manga ID");
  }
  return sourceStoryId;
}

function requestedOffset(nextToken) {
  if (nextToken == null) return 0;
  const parsed = Number(nextToken);
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : 0;
}

function nextOffsetToken(payload) {
  const offset = Number(payload?.offset);
  const limit = Number(payload?.limit);
  const total = Number(payload?.total);
  if (![offset, limit, total].every(Number.isFinite) || limit <= 0) return null;
  const next = offset + limit;
  return next < total ? String(next) : null;
}

function dataItems(payload) {
  return Array.isArray(payload?.data) ? payload.data : [];
}

function chapterMode(value) {
  return value === "RECENT" || value === "INCREMENTAL" ? value : "FULL";
}

function chapterFeedPath(input) {
  const sourceStoryId = requireMangaId(input?.sourceStoryId);
  const mode = chapterMode(input?.mode);
  const offset = requestedOffset(input?.nextToken);
  const limit = mode === "RECENT" ? PAGE_SIZE : CHAPTER_PAGE_SIZE;
  const orderField = mode === "INCREMENTAL"
    ? "updatedAt"
    : mode === "RECENT" ? "readableAt" : "chapter";
  const orderDirection = mode === "RECENT" ? "desc" : "asc";
  let path = `/manga/${encodeURIComponent(sourceStoryId)}/feed` +
    `?limit=${limit}&offset=${offset}` +
    `&translatedLanguage%5B%5D=en` +
    `&order%5B${orderField}%5D=${orderDirection}`;
  if (mode === "INCREMENTAL" && typeof input?.checkpoint === "string" && input.checkpoint.trim()) {
    path += `&updatedAtSince=${encodeURIComponent(input.checkpoint.trim())}`;
  }
  return path;
}

function chapterTimestamp(attributes) {
  const value = attributes?.readableAt || attributes?.publishAt || attributes?.createdAt;
  const timestamp = typeof value === "string" ? Date.parse(value) : NaN;
  return Number.isFinite(timestamp) && timestamp >= 0 ? timestamp : null;
}

function toRelease(resource) {
  const sourceReleaseId = requireMangaId(resource?.id);
  const attributes = resource?.attributes || {};
  const title = typeof attributes.title === "string" && attributes.title.trim()
    ? attributes.title.trim()
    : null;
  const rawNumber = typeof attributes.chapter === "string" && attributes.chapter.trim()
    ? attributes.chapter.trim()
    : null;
  const languageTag = typeof attributes.translatedLanguage === "string" && attributes.translatedLanguage.trim()
    ? attributes.translatedLanguage.trim().toLowerCase()
    : null;
  return {
    sourceReleaseId,
    title,
    rawNumber,
    languageTag,
    publishedAtEpochMillis: chapterTimestamp(attributes),
  };
}

function mangaIdFromUrl(url) {
  const value = typeof url === "string" ? url.trim() : "";
  const match = /^https:\/\/mangadex\.org\/title\/([0-9a-f-]{36})(?:[/?#].*)?$/i.exec(value);
  return match ? requireMangaId(match[1]) : null;
}

function requireAtHomeDocument(payload) {
  const baseUrl = typeof payload?.baseUrl === "string" ? payload.baseUrl.trim() : "";
  const hash = typeof payload?.chapter?.hash === "string" ? payload.chapter.hash.trim() : "";
  const filenames = Array.isArray(payload?.chapter?.data) ? payload.chapter.data : [];
  if (!baseUrl.startsWith("https://") || !hash || filenames.length === 0) {
    throw pluginError("plugin.mangadex_invalid_response", "Invalid MangaDex chapter image metadata");
  }
  const blocks = filenames.map(filename => {
    const normalized = typeof filename === "string" ? filename.trim() : "";
    if (!normalized || normalized.includes("/") || normalized.includes("\\")) {
      throw pluginError("plugin.mangadex_invalid_response", "Invalid MangaDex chapter image filename");
    }
    return {
      type: "image",
      stableId: `${hash}/${normalized}`,
      imageUrl: `${baseUrl.replace(/\/$/, "")}/data/${encodeURIComponent(hash)}/${encodeURIComponent(normalized)}`,
    };
  });
  return {title: null, blocks};
}

async function fetchChapter(sourceReleaseId) {
  const chapterId = requireMangaId(sourceReleaseId);
  const payload = await getJson(`/at-home/server/${encodeURIComponent(chapterId)}`);
  return requireAtHomeDocument(payload);
}

async function fetchManga(sourceStoryId) {
  const payload = await getJson(
    `/manga/${encodeURIComponent(sourceStoryId)}?includes%5B%5D=author&includes%5B%5D=artist`
  );
  const resource = payload?.data;
  if (!resource || resource.type !== "manga" || resource.id !== sourceStoryId) {
    throw pluginError("plugin.mangadex_invalid_response", "Mismatched MangaDex manga response");
  }
  return toCandidate(resource);
}

globalThis.openstoryPlugin = Object.freeze({
  content: Object.freeze({
    search: async input => {
      const query = typeof input?.query === "string" ? input.query.trim() : "";
      if (!query) return {items: [], nextToken: null};
      const offset = requestedOffset(input?.nextToken);
      const payload = await getJson(
        `/manga?title=${encodeURIComponent(query)}` +
          `&limit=${PAGE_SIZE}&offset=${offset}` +
          `&includes%5B%5D=author&includes%5B%5D=artist`
      );
      return {
        items: dataItems(payload).filter(item => item?.type === "manga").map(toCandidate),
        nextToken: nextOffsetToken(payload),
      };
    },

    resolveUrl: async input => {
      const sourceStoryId = mangaIdFromUrl(input?.url);
      if (!sourceStoryId) {
        throw pluginError("plugin.mangadex_url_invalid", "Unsupported MangaDex title URL");
      }
      return fetchManga(sourceStoryId);
    },

    chapters: async input => {
      const payload = await getJson(chapterFeedPath(input));
      return {
        items: dataItems(payload).filter(item => item?.type === "chapter").map(toRelease),
        nextToken: nextOffsetToken(payload),
      };
    },

    chapter: async input => fetchChapter(input?.sourceReleaseId),
  }),
});
