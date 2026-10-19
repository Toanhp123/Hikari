// LNReader v3 engine entrypoint. Runtime interfaces are implemented once in
// engine/ and bundled into a single offline asset for QuickJS on Android.
import {installWebPrimitives} from './engine/web_compat.js';
import {installModuleRegistry} from './engine/modules.js';
import {fetchApi} from './engine/fetch.js';
import {nativeCall} from './engine/native_bridge.js';

globalThis.__hikariEngineProfile = 'v3-text-alpha';
installWebPrimitives();
installModuleRegistry();
// Older LNReader bundles occasionally reference these globals directly.
globalThis.localStorage = Object.freeze({
  setItem:(name,value)=>nativeCall({op:'set',key:name,value:String(value)}),
  getItem:name=>nativeCall({op:'get',key:name}),
  removeItem:name=>nativeCall({op:'remove',key:name}),
});
globalThis.fetch = fetchApi;
if (typeof globalThis.setTimeout !== 'function') {
  globalThis.setTimeout = (callback,ms=0,...args) => {
    if (typeof callback !== 'function' || !Number.isInteger(ms) || ms < 0 || ms > 2000) {
      throw TypeError('Unsupported timer');
    }
    nativeCall({op:'delay',ms}); callback(...args);
    return 0;
  };
}
