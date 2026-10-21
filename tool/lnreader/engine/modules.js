import * as cheerio from 'cheerio/slim';
import dayjs from 'dayjs';
import {Parser} from 'htmlparser2';
import {fetchModule} from './fetch.js';
import {storageModule} from './storage.js';
import {nativeCall} from './native_bridge.js';

const NovelStatus = Object.freeze({Unknown:'Unknown', Ongoing:'Ongoing', Completed:'Completed',
  Licensed:'Licensed', PublishingFinished:'Publishing Finished',Cancelled:'Cancelled',
  OnHiatus:'On Hiatus', STUB:'STUB', Inactive:'Inactive'});
const FilterTypes = Object.freeze({TextInput:'Text',Picker:'Picker',CheckboxGroup:'Checkbox',
  Switch:'Switch',ExcludableCheckboxGroup:'XCheckbox'});
const isUrlAbsolute = value => typeof value === 'string' &&
  (value.startsWith('//') || /^[a-z][a-z\d+.-]*:\/\/[^\s/]+/i.test(value));
const packages = Object.freeze({
  cheerio, dayjs, htmlparser2:Object.freeze({Parser}),
  '@libs/fetch':fetchModule, '@libs/storage':storageModule,
  '@libs/filterInputs':Object.freeze({FilterTypes}),
  '@libs/novelStatus':Object.freeze({NovelStatus}),
  '@libs/isAbsoluteUrl':Object.freeze({isUrlAbsolute}),
  '@libs/defaultCover':Object.freeze({defaultCover:
    'https://github.com/lnreader/lnreader-plugins/blob/master/public/static/coverNotAvailable.webp?raw=true'}),
  urlencode:Object.freeze({encode:encodeURIComponent,decode:decodeURIComponent}),
  '@libs/url':Object.freeze({absoluteUrl:(base,path)=>nativeCall({op:'absoluteUrl',base,path})}),
});
export function installModuleRegistry() {
  globalThis.require = name => {
    if (Object.hasOwn(packages, name)) return packages[name];
    throw Error('Unsupported module: '+String(name));
  };
}
