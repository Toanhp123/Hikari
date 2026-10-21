import {nativeCall} from './native_bridge.js';
const PREFIX = 'v3:db:';
const validatedKey = name => {
  if (typeof name !== 'string' || !name || name.length > 200) throw TypeError('Invalid storage key');
  return PREFIX + name;
};
export const storage = Object.freeze({
  set(name, value, expires) {
    const row = {created:new Date().toISOString(), value};
    if (expires !== undefined) {
      const time = expires instanceof Date ? expires.getTime() : Number(expires);
      if (!Number.isFinite(time)) throw TypeError('Invalid expiry');
      row.expires = time;
    }
    nativeCall({op:'set',key:validatedKey(name),value:JSON.stringify(row)});
  },
  get(name, raw = false) {
    const saved = nativeCall({op:'get',key:validatedKey(name)});
    if (saved === null) return undefined;
    const row = JSON.parse(saved);
    if (row.expires !== undefined && Date.now() > row.expires) {
      this.delete(name); return undefined;
    }
    return raw ? row : row.value;
  },
  delete(name) { nativeCall({op:'remove',key:validatedKey(name)}); },
  getAllKeys() {
    return nativeCall({op:'keys'}).filter(name => name.startsWith(PREFIX)).map(name => name.slice(PREFIX.length));
  },
  clearAll() { for (const name of this.getAllKeys()) this.delete(name); },
});
const webStorage = kind => Object.freeze({get() {
  const data = nativeCall({op:'get',key:`v3:web:${kind}`});
  return data === null ? undefined : JSON.parse(data);
}});
export const storageModule = Object.freeze({storage,
  localStorage:webStorage('local'), sessionStorage:webStorage('session')});
