// LNReader v3 CommonJS compatibility, layered over Hikari's bounded native host.
// Every stateful operation is source-scoped and passes the native policy gate.
// This file deliberately does NOT emulate @libs/aes, protobuf or arbitrary modules.
(() => {
  const originalRequire = globalThis.require;
  const native = request => JSON.parse(__hikariHost(JSON.stringify(request)));
  const prefix = 'v3:db:';
  const key = name => {
    if (typeof name !== 'string' || !name || name.length > 200) throw TypeError('Invalid storage key');
    return prefix + name;
  };
  const storage = Object.freeze({
    set(name, value, expires) {
      const item = {created:new Date().toISOString(), value};
      if (expires !== undefined) {
        const until = expires instanceof Date ? expires.getTime() : Number(expires);
        if (!Number.isFinite(until)) throw TypeError('Invalid expiry');
        item.expires = until;
      }
      native({op:'set',key:key(name),value:JSON.stringify(item)});
    },
    get(name, raw = false) {
      const data = native({op:'get',key:key(name)});
      if (data === null) return undefined;
      const item = JSON.parse(data);
      if (item.expires !== undefined && Date.now() > item.expires) {
        this.delete(name); return undefined;
      }
      return raw ? item : item.value;
    },
    delete(name) { native({op:'remove',key:key(name)}); },
    getAllKeys() { return native({op:'keys'}).filter(name => name.startsWith(prefix)).map(name => name.slice(prefix.length)); },
    clearAll() { for (const name of this.getAllKeys()) this.delete(name); },
  });
  const savedWebState = kind => ({get() {
    const value = native({op:'get',key:'v3:web:' + kind});
    return value === null ? undefined : JSON.parse(value);
  }});
  const isUrlAbsolute = value => typeof value === 'string' &&
    (value.startsWith('//') || /^[a-z][a-z\d+.-]*:\/\/[^\s/]+/i.test(value));
  const upstreamFetch = originalRequire('@libs/fetch').fetchApi;
  const fetchText = async (url, init, encoding) => {
    // Native host currently decodes UTF-8. Do not silently misdecode GBK or Shift-JIS.
    if (encoding && !/^utf-?8$/i.test(encoding)) return '';
    try { const response = await upstreamFetch(url, init); return response.ok ? await response.text() : ''; }
    catch (_) { return ''; }
  };
  const upstreamModules = Object.freeze({
    '@libs/fetch':Object.freeze({fetchApi:upstreamFetch,fetchText}),
    '@libs/isAbsoluteUrl':Object.freeze({isUrlAbsolute}),
    '@libs/defaultCover':Object.freeze({defaultCover:
      'https://github.com/lnreader/lnreader-plugins/blob/master/public/static/coverNotAvailable.webp?raw=true'}),
    'urlencode':Object.freeze({encode:encodeURIComponent,decode:decodeURIComponent}),
    '@libs/storage':Object.freeze({storage,localStorage:savedWebState('local'),sessionStorage:savedWebState('session')}),
  });
  globalThis.require = name => Object.hasOwn(upstreamModules,name)
    ? upstreamModules[name] : originalRequire(name);
  if (typeof globalThis.setTimeout !== 'function') {
    // Only a bounded synchronous delay is provided. No background timers or event loop.
    globalThis.setTimeout = (callback, ms=0, ...args) => {
      if (typeof callback !== 'function' || !Number.isInteger(ms) || ms < 0 || ms > 2000)
        throw TypeError('Unsupported timer');
      native({op:'delay',ms}); callback(...args);
      return 0;
    };
  }
})();
