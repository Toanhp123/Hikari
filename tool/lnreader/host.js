import * as cheerio from 'cheerio/slim';
import dayjs from 'dayjs';
const call = request => JSON.parse(__hikariHost(JSON.stringify(request)));
const fetchApi = async (url, init = {}) => {
  const result = call({op:'fetch', url, init});
  return {ok:result.status >= 200 && result.status < 300, status:result.status,
    text:async()=>result.body, json:async()=>JSON.parse(result.body)};
};
const storage = {getItem:key=>call({op:'get',key}),setItem:(key,value)=>call({op:'set',key,value}),removeItem:key=>call({op:'remove',key})};
const fetchText = async (url, init) => (await fetchApi(url,init)).text();
const modules = Object.freeze({cheerio, dayjs, '@libs/fetch':{fetchApi,fetchText},
  '@libs/storage':storage, '@libs/url':{absoluteUrl:(base,path)=>call({op:'absoluteUrl',base,path})}});
globalThis.require = name => { if (!Object.hasOwn(modules,name)) throw Error('Unsupported module: '+name); return modules[name]; };
globalThis.localStorage = storage;
globalThis.fetch = fetchApi;
