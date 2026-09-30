// Authored contract fixture; no upstream plugin copied and no live catalogue claimed.
const cheerio = require('cheerio');
const dayjs = require('dayjs');
module.exports = {
 imageRequestInit:{headers:{Referer:'https://example.org/'}},
 async searchNovels(term, page) {
  const $ = cheerio.load('<h1>Contract novel</h1>');
  localStorage.setItem('last-query',term);
  return page === 1 ? [{name:$('h1').text(),path:'https://example.org/novel'}] : [];
 },
 async parseNovel(path) { return {name:'Contract novel',path,summary:'Authored runtime fixture',author:'Hikari',genres:'Test',status:'Completed',rating:4.5,totalPages:2,chapters:[{name:'Chapter 1.5',path:'https://example.org/chapter/1',chapterNumber:1.5,releaseTime:dayjs('2025-01-02').format('YYYY-MM-DD'),scanlator:['Fixture']}]}; },
 async parsePage(path,page) {return {chapters:page==='2'?[{name:'Chapter 2',path:'https://example.org/chapter/2'}]:[]};},
 async parseChapter(path) {return '<h1>Contract chapter</h1><p>Rendered through bounded QuickJS.</p><table><tr><td>Rich HTML</td></tr></table>';}
};
