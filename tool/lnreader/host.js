import * as cheerio from 'cheerio/slim';
import dayjs from 'dayjs';
import {Parser} from 'htmlparser2';

// Every native operation still passes through the bounded Kotlin host.
const call = request => JSON.parse(__hikariHost(JSON.stringify(request)));

// Narrow compatibility with LNReader's text-only search forms. Native owns
// multipart encoding, boundary generation, payload caps and origin checking.
class HikariFormData {
  constructor() { this._fields = []; }
  append(name, value) {
    if (typeof name !== 'string' || typeof value !== 'string' || !name ||
        name.length > 256 || this._fields.length >= 32) throw Error('Unsupported form field');
    this._fields.push({name, value});
  }
  toJSON() { return {__hikariMultipart:'v1', fields:this._fields}; }
}
if (typeof globalThis.FormData === 'function') throw Error('Unexpected ambient FormData');
globalThis.FormData = HikariFormData;

// QuickJS has no browser URL globals; resolve through the native host.
// This exposes only href and toString, not the full WHATWG URL surface.
if (typeof globalThis.URL === 'undefined') globalThis.URL = class HikariURL {
  constructor(path, base) {
    if (typeof path !== 'string' || path.length > 8192 ||
        (base !== undefined && typeof base !== 'string')) throw TypeError('Invalid URL');
    this.href = call({op:'absoluteUrl', base:base || path, path:base ? path : ''});
    if (typeof this.href !== 'string') throw TypeError('Invalid URL');
  }
  toString() { return this.href; }
  toJSON() { return this.href; }
};
if (typeof globalThis.URLSearchParams === 'undefined') globalThis.URLSearchParams = class HikariURLSearchParams {
  constructor(init) {
    this._entries = [];
    if (init && typeof init === 'object') {
      if (Array.isArray(init)) for (const entry of init) this.append(entry[0], entry[1]);
      else for (const key of Object.keys(init)) this.append(key, init[key]);
    } else if (typeof init === 'string' && init) {
      for (const piece of init.replace(/^\?/, '').split('&')) {
        const pos = piece.indexOf('=');
        const decode = value => decodeURIComponent(value.replace(/\+/g, ' '));
        this.append(decode(pos < 0 ? piece : piece.slice(0, pos)),
                    decode(pos < 0 ? '' : piece.slice(pos + 1)));
      }
    }
  }
  append(name, value) {
    if (this._entries.length >= 64) throw Error('Too many query parameters');
    this._entries.push([String(name), String(value)]);
  }
  toString() {
    return this._entries.map(([name,value]) =>
      [name,value].map(x => encodeURIComponent(x).replace(/%20/g, '+')).join('=')).join('&');
  }
};

const fetchApi = async (url, init = {}) => {
  const result = call({op:'fetch', url, init});
  return {ok:result.status >= 200 && result.status < 300, status:result.status,
    text:async()=>result.body, json:async()=>JSON.parse(result.body)};
};
const storage = {getItem:key=>call({op:'get',key}),setItem:(key,value)=>call({op:'set',key,value}),removeItem:key=>call({op:'remove',key})};
const fetchText = async (url, init) => (await fetchApi(url,init)).text();
const NovelStatus = Object.freeze({Unknown:'Unknown',Ongoing:'Ongoing',Completed:'Completed',
  Licensed:'Licensed',PublishingFinished:'Publishing Finished',Cancelled:'Cancelled',
  OnHiatus:'On Hiatus',STUB:'STUB',Inactive:'Inactive'});
const FilterTypes = Object.freeze({TextInput:'Text',Picker:'Picker',CheckboxGroup:'Checkbox',
  Switch:'Switch',ExcludableCheckboxGroup:'XCheckbox'});
const modules = Object.freeze({cheerio, dayjs, htmlparser2:Object.freeze({Parser}),
  '@libs/filterInputs':Object.freeze({FilterTypes}),
  '@libs/novelStatus':Object.freeze({NovelStatus}),
  '@libs/fetch':Object.freeze({fetchApi,fetchText}),
  '@libs/storage':storage, '@libs/url':{absoluteUrl:(base,path)=>call({op:'absoluteUrl',base,path})}});
globalThis.require = name => { if (!Object.hasOwn(modules,name)) throw Error('Unsupported module: '+name); return modules[name]; };
globalThis.localStorage = storage;
globalThis.fetch = fetchApi;
