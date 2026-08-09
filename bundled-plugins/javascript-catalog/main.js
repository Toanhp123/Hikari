const OPENSTORY_FIXTURE_ORIGIN = "https://javascript.openstory.example";

async function fixtureJson(path) {
  const response = await host.http({url: `${OPENSTORY_FIXTURE_ORIGIN}${path}`});
  if (response.status !== 200) {
    const error = new Error("fixture request failed");
    error.code = "plugin.fixture_http_status";
    throw error;
  }
  return JSON.parse(response.bodyText);
}

globalThis.openstoryPlugin = Object.freeze({
  home: async () => fixtureJson("/home"),
  search: async input => fixtureJson(
    `/search?q=${encodeURIComponent(input && typeof input.query === "string" ? input.query : "")}`
  ),
  details: async input => fixtureJson(
    `/story/${encodeURIComponent(input.sourceId)}`
  ),
  filters: async () => [],
});
