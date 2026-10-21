// Checks bundled browser-less host API wiring without Android or network.
// This is NOT an untrusted-code sandbox or a native QuickJS security test.
import {readFileSync} from 'node:fs';
import {runInNewContext} from 'node:vm';
import {fileURLToPath} from 'node:url';
import {dirname, resolve} from 'node:path';
const root = dirname(fileURLToPath(import.meta.url));
const host = readFileSync(resolve(root, '../../android/app/src/main/assets/lnreader/host.js'), 'utf8');
const contract = readFileSync(resolve(root, 'contract.js'), 'utf8');
let multipartObserved = false;
const env = {__hikariHost(json) {
  const request = JSON.parse(json);
  switch (request.op) {
    case 'fetch':
      if (request.init?.body?.__hikariMultipart === 'v1' &&
          request.init.body.fields?.[0]?.name === 'keyword' &&
          request.init.body.fields?.[0]?.value === 'Hako') multipartObserved = true;
      return JSON.stringify({status:200, body:'<h1>Novel</h1>'});
    case 'get': return JSON.stringify('saved');
    case 'absoluteUrl': return JSON.stringify('https://example.org/chapter');
    default: return 'null';
  }
}};
const result = await runInNewContext(`${host}\n${contract}`, env, {timeout:10000});
if (result !== true || !multipartObserved) throw Error('Bundled host contract failed');
console.log('Bundled LNReader host contract passed (Node, mocked native IO)');
