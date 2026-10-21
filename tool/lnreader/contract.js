(async()=>{
 const $=require('cheerio').load(await require('@libs/fetch').fetchText('https://example.org'));
 if($('h1').text()!=='Novel') throw Error('HTML parser');
 if(require('dayjs')('2025-01-02').format('YYYY-MM-DD')!=='2025-01-02') throw Error('date');
 localStorage.setItem('key','saved'); if(localStorage.getItem('key')!=='saved') throw Error('storage');
 if(require('@libs/url').absoluteUrl('https://example.org','/chapter')!=='https://example.org/chapter') throw Error('URL');
 // LNReader v3 compiled providers: 69shu requires status/filter enums and
 // FormData; Hako requires htmlparser2.Parser; Light Novel VN posts forms.
 const {NovelStatus} = require('@libs/novelStatus');
 const {FilterTypes} = require('@libs/filterInputs');
 if(NovelStatus.Completed!=='Completed'||NovelStatus.OnHiatus!=='On Hiatus'||
    FilterTypes.Picker!=='Picker'||FilterTypes.CheckboxGroup!=='Checkbox') throw Error('LNReader helpers');
 const events=[];
 const parser=new (require('htmlparser2').Parser)({
   onopentag(name,attrs){events.push(name+':'+(attrs.title||''))},
   ontext(value){events.push(value)},onclosetag(name){events.push('/'+name)}
 });
 parser.write('<a title="Hako">Novel</a>');parser.end();
 if(events.join('|')!=='a:Hako|Novel|/a')throw Error('Hako streaming parser');
 const form=new FormData();form.append('keyword','Hako');
 const post=await require('@libs/fetch').fetchApi('https://example.org/search',{method:'POST',body:form});
 if(!post.ok)throw Error('multipart response');
 if(new URL('/chapter','https://example.org').toString()!=='https://example.org/chapter')throw Error('URL constructor');
 const search=new URLSearchParams();search.append('name','light novel');
 if(search.toString()!=='name=light+novel')throw Error('URLSearchParams');

 let blocked=false; try{require('fs')}catch(_){blocked=true} if(!blocked)throw Error('module boundary');
 return true;
})()
