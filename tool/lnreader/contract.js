(async()=>{
 const $=require('cheerio').load(await require('@libs/fetch').fetchText('https://example.org'));
 if($('h1').text()!=='Novel') throw Error('HTML parser');
 if(require('dayjs')('2025-01-02').format('YYYY-MM-DD')!=='2025-01-02') throw Error('date');
 localStorage.setItem('key','saved'); if(localStorage.getItem('key')!=='saved') throw Error('storage');
 if(require('@libs/url').absoluteUrl('https://example.org','/chapter')!=='https://example.org/chapter') throw Error('URL');
 let blocked=false; try{require('fs')}catch(_){blocked=true} if(!blocked)throw Error('module boundary');
 return true;
})()
