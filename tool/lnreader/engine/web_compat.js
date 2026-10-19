import {nativeCall} from './native_bridge.js';

// Minimal browser primitives required by LNReader v3; explicitly bounded.
// HTTP authorization, body size, and origin validation are native responsibilities.
export class SourceHeaders {
  constructor(initial = {}) {
    this._values = Object.create(null);
    const pairs = initial instanceof SourceHeaders ? initial.entries() :
      Array.isArray(initial) ? initial : Object.entries(initial);
    for (const [name, value] of pairs) this.set(name, value);
  }
  set(name, value) {
    name = String(name).toLowerCase(); value = String(value);
    if (!/^[!#$%&'*+.^_`|~0-9a-z-]{1,128}$/.test(name) ||
        value.length > 4096 || /[\r\n\0]/.test(value) ||
        (!Object.hasOwn(this._values, name) && Object.keys(this._values).length >= 64)) {
      throw TypeError('Invalid HTTP header');
    }
    this._values[name] = value;
  }
  append(name, value) {
    const old = this.get(name);
    this.set(name, old === null ? value : `${old}, ${value}`);
  }
  get(name) { return this._values[String(name).toLowerCase()] ?? null; }
  has(name) { return Object.hasOwn(this._values, String(name).toLowerCase()); }
  delete(name) { delete this._values[String(name).toLowerCase()]; }
  *entries() { yield* Object.entries(this._values); }
  *keys() { yield* Object.keys(this._values); }
  *values() { yield* Object.values(this._values); }
  forEach(callback, thisArg) {
    for (const [name, value] of this.entries()) callback.call(thisArg, value, name, this);
  }
  [Symbol.iterator]() { return this.entries(); }
  toJSON() { return {...this._values}; }
}

export class SourceSearchParams {
  constructor(init) {
    this.entriesList = [];
    if (typeof init === 'string') {
      const input = init.replace(/^\?/, '');
      for (const piece of input ? input.split('&') : []) {
        const at = piece.indexOf('=');
        const decode = text => decodeURIComponent(text.replace(/\+/g, ' '));
        this.append(decode(at === -1 ? piece : piece.slice(0, at)),
                    decode(at === -1 ? '' : piece.slice(at + 1)));
      }
    } else if (init instanceof SourceSearchParams || Array.isArray(init)) {
      for (const [name, value] of init) this.append(name, value);
    } else if (init && typeof init === 'object') {
      for (const [name, value] of Object.entries(init)) this.append(name, value);
    }
  }
  append(name, value) {
    if (this.entriesList.length >= 128) throw RangeError('Too many search parameters');
    this.entriesList.push([String(name), String(value)]);
    this._changed?.();
  }
  get(name) { return this.entriesList.find(([key]) => key === String(name))?.[1] ?? null; }
  getAll(name) { return this.entriesList.filter(([key]) => key === String(name)).map(([, value]) => value); }
  has(name) { return this.entriesList.some(([key]) => key === String(name)); }
  delete(name) { this.entriesList = this.entriesList.filter(([key]) => key !== String(name)); this._changed?.(); }
  set(name, value) {
    name = String(name); value = String(value);
    const at = this.entriesList.findIndex(([key]) => key === name);
    if (at < 0) return this.append(name, value);
    this.entriesList = this.entriesList.filter(([key], i) => key !== name || i === at);
    this.entriesList[at] = [name, value];
    this._changed?.();
  }
  sort() { this.entriesList.sort((a, b) => a[0] < b[0] ? -1 : a[0] > b[0] ? 1 : 0); this._changed?.(); }
  *entries() { yield* this.entriesList; }
  *keys() { for (const [key] of this.entriesList) yield key; }
  *values() { for (const [, value] of this.entriesList) yield value; }
  [Symbol.iterator]() { return this.entries(); }
  forEach(fn, thisArg) { for (const [name, value] of this.entriesList) fn.call(thisArg, value, name, this); }
  toString() {
    const encode = part => encodeURIComponent(part).replace(/[!'()~]/g, c => '%' + c.charCodeAt(0).toString(16).toUpperCase()).replace(/%20/g, '+');
    return this.entriesList.map(([key, value]) => `${encode(key)}=${encode(value)}`).join('&');
  }
}

export class SourceURL {
  constructor(path, base) {
    if (typeof path !== 'string' || path.length > 8192 ||
        (base !== undefined && (typeof base !== 'string' || base.length > 8192))) throw TypeError('Invalid URL');
    this.href = nativeCall({op:'absoluteUrl', base:base ?? path, path:base === undefined ? '' : path});
    if (typeof this.href !== 'string') throw TypeError('Invalid URL');
    this._params = new SourceSearchParams(this.search);
    this._params._changed = () => {
      const hash = this.hash;
      const query = this._params.toString();
      this.href = this.href.split('#')[0].split('?')[0] + (query ? '?' + query : '') + hash;
    };
  }
  get protocol() { return this.href.slice(0, this.href.indexOf(':') + 1); }
  get origin() { return this.href.match(/^https?:\/\/[^/\?#]+/i)?.[0] || ''; }
  get host() { return this.origin.replace(/^https?:\/\//i, ''); }
  get hostname() { return this.host.split(':')[0]; }
  get pathname() { return this.href.slice(this.origin.length).split(/[?#]/)[0] || '/'; }
  get hash() { const i = this.href.indexOf('#'); return i < 0 ? '' : this.href.slice(i); }
  get search() { const q = this.href.indexOf('?'); return q < 0 ? '' : this.href.slice(q).split('#')[0]; }
  get searchParams() { return this._params; }
  toString() { return this.href; }
  toJSON() { return this.href; }
}

export class SourceFormData {
  constructor() { this.fields = []; }
  append(name, value) {
    if (typeof name !== 'string' || !name || name.length > 256 ||
        typeof value !== 'string' || this.fields.length >= 32) throw TypeError('Unsupported form field');
    this.fields.push({name, value});
  }
  toJSON() { return {__hikariMultipart:'v1', fields:this.fields}; }
}

export function installWebPrimitives() {
  // Avoid browser/Node fallbacks: different engines must see identical APIs.
  globalThis.Headers = SourceHeaders;
  globalThis.URL = SourceURL;
  globalThis.URLSearchParams = SourceSearchParams;
  globalThis.FormData = SourceFormData;
}
