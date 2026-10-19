import {nativeCall} from './native_bridge.js';
import {SourceHeaders, SourceFormData, SourceSearchParams} from './web_compat.js';

// Default application-level headers. Framing and compression are native-owned
// so OkHttp can safely manage Connection and transparent gzip decoding.
const DEFAULT_HEADERS = Object.freeze({
  'Accept':'*/*', 'Accept-Language':'*', 'Sec-Fetch-Mode':'cors',
  'Cache-Control':'max-age=0',
  'User-Agent':'Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36',
});
function normalizedInit(init = {}) {
  const headers = new SourceHeaders(DEFAULT_HEADERS);
  if (init.headers) for (const [name, value] of new SourceHeaders(init.headers)) headers.set(name,value);
  const options = {...init};
  if (options.body instanceof SourceSearchParams) {
    options.body = options.body.toString();
    if (!headers.has('content-type')) headers.set('content-type','application/x-www-form-urlencoded;charset=UTF-8');
  }
  if (options.body instanceof SourceFormData) options.body = options.body.toJSON();
  options.headers = headers.toJSON();
  return options;
}
function requestNative(url, init, encoding) {
  try {
    const response = nativeCall({op:'fetch',url:String(url),init:normalizedInit(init),
      ...(encoding ? {encoding} : {})});
    globalThis.__hikariFetchIssue = response.status >= 400 ? 'provider-http-error' : null;
    return response;
  } catch (error) {
    globalThis.__hikariFetchIssue = error.hikariReason || 'network-request-rejected';
    if (!error.hikariReason) error.hikariReason = 'network-request-rejected';
    throw error;
  }
}
function asResponse(row, url) {
  return Object.freeze({
    ok:row.status >= 200 && row.status < 300,
    status:row.status, statusText:row.statusText || '',
    url:row.url || String(url), redirected:!!row.redirected,
    headers:new SourceHeaders(row.headers || {}),
    text:async () => row.body, json:async () => JSON.parse(row.body),
  });
}
export async function fetchApi(url, init = {}) {
  return asResponse(requestNative(url, init), url);
}
export async function fetchText(url, init, encoding) {
  // LNReader upstream's fetchText returns '' on errors and non-2xx responses.
  // Explicit charset decoding is native-owned; never pretend GBK is UTF-8.
  if (encoding && !/^(utf-?8|gbk|gb2312|gb18030|big5|shift[-_]?jis|euc-jp)$/i.test(encoding)) {
    globalThis.__hikariFetchIssue = 'unsupported-encoding';
    return '';
  }
  try {
    const response = requestNative(url, init, encoding);
    return response.status >= 200 && response.status < 300 ? response.body : '';
  } catch (_) { return ''; }
}
export const fetchModule = Object.freeze({fetchApi, fetchText});
