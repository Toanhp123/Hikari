// Exercised without npm dependencies or Android. This verifies the *source*
// engine graph and representative LNReader v3 usage. Packaged host and QuickJS
// still require the separate Android tests in COMPATIBILITY.md.
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {resolve, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
import vm from 'node:vm';
const root = dirname(fileURLToPath(import.meta.url));
const collected = [];
const values = new Map();
const env = {__hikariHost(value) {
  const request = JSON.parse(value);
  collected.push(request);
  switch(request.op) {
    case 'fetch':
      if (request.url.includes('/reject')) return JSON.stringify({__hikariHostError:'network-request-rejected'});
      if (request.url.includes('/large')) return JSON.stringify({__hikariHostError:'response-too-large'});
      if (request.url.includes('/missing')) return JSON.stringify({status:404,body:'not found'});
      return JSON.stringify({status:200, statusText:'OK', url:request.url,
        headers:{'content-type':'text/plain'},body:'RSC=1;series=Novel'});
    case 'absoluteUrl': return JSON.stringify(new URL(request.path, request.base).toString());
    case 'get': return JSON.stringify(values.get(request.key) ?? null);
    case 'set': values.set(request.key,request.value); return 'null';
    case 'remove': values.delete(request.key); return 'null';
    case 'keys': return JSON.stringify([...values.keys()]);
    case 'delay': return 'null';
    default: throw Error('Unknown operation: '+request.op);
  }
}};
const context = vm.createContext(env);
const all = new Map();
const virtuals = new Map();
const vendor = name => {
  if (virtuals.has(name)) return virtuals.get(name);
  const exports = name === 'cheerio/slim' ? ['load'] : name === 'htmlparser2' ? ['Parser'] : ['default'];
  const module = new vm.SyntheticModule(exports, function () {
    if (name === 'cheerio/slim') this.setExport('load',()=>({}));
    else if (name === 'htmlparser2') this.setExport('Parser',class {});
    else this.setExport('default',()=>({format:()=> '2026-01-01'}));
  }, {context,identifier:name});
  virtuals.set(name,module); return module;
};
const load = filename => {
  if (all.has(filename)) return all.get(filename);
  const module = new vm.SourceTextModule(readFileSync(filename,'utf8'),{context,identifier:filename});
  all.set(filename,module);return module;
};
const entry = load(resolve(root,'host.js'));
await entry.link((name, importer) => name.startsWith('.') ? load(resolve(dirname(importer.identifier),name)) : vendor(name));
await entry.evaluate();
const run = code => vm.runInContext(code,context,{timeout:5000});
assert.equal(await run(`(async () => {
  const {fetchApi} = require('@libs/fetch');
  const params = new URLSearchParams({q:'Classroom of the Elite',sort:'views'});
  const url='https://noveldex.io/series?'+params.toString();
  const result = await fetchApi(url,{headers:{'User-Agent':'NovelDex','Referer':'https://noveldex.io',RSC:'1'}});
  return result.ok && (await result.text()).includes('RSC=1');
})()`),true);
const novelDex = collected.find(r => r.op === 'fetch');
assert.equal(novelDex.init.headers.rsc,'1');
assert.equal(novelDex.init.headers.referer,'https://noveldex.io');
assert.ok(novelDex.url.includes('Classroom+of+the+Elite'));
assert.equal(await run(`(async () => {
  const api=require('@libs/fetch');
  const response=await api.fetchApi('https://example.org/missing');
  return response.status===404 && !response.ok && await api.fetchText('https://example.org/missing')==='';
})()`),true);
assert.equal(await run(`(async () => {
  try{await require('@libs/fetch').fetchApi('https://example.org/reject'); return false;}
  catch(error){return error.hikariReason==='network-request-rejected';}
})()`),true);
assert.equal(await run(`(async () => {
  try{await require('@libs/fetch').fetchApi('https://example.org/large');return false;}
  catch(error){return error.hikariReason==='response-too-large';}
})()`),true);
assert.equal(await run(`(async () => {
  const store=require('@libs/storage').storage;
  store.set('progress',{chapter:2});
  const found=store.get('progress').chapter===2;
  store.clearAll(); return found && store.get('progress')===undefined;
})()`),true);
assert.equal(run(`new URL('/chapter','https://example.org/base').href`),'https://example.org/chapter');
assert.equal(run(`(() => { const url=new URL('https://example.org/series');url.searchParams.set('q','Novel');return url.href;})()`),'https://example.org/series?q=Novel');
assert.equal(run(`new Headers({'RSC':'1'}).has('rsc')`),true);
assert.equal(run(`Array.from(new Headers({'RSC':'1'}).values())[0]`),'1');
assert.equal(run(`new URLSearchParams([['q','a'],['q','b']]).getAll('q').length`),2);
assert.equal(run(`new URLSearchParams({q:'a b'}).toString()`),'q=a+b');
assert.equal(run(`typeof require('htmlparser2').Parser`),'function');
assert.equal(run(`typeof require('dayjs')`),'function');
assert.equal(run(`typeof require('cheerio').load`),'function');
assert.throws(()=>run(`require('fs')`),/Unsupported module/);
const headers = run(`(() => {const h=new Headers();h.set('RSC','1');return h.get('rsc')})()`);
assert.equal(headers,'1');
assert.throws(()=>run(`new Headers({'bad\\r\\nname':'x'})`),/Invalid HTTP header/);
console.log('LNReader engine source contract passed (Node VM; native IO simulated)');
