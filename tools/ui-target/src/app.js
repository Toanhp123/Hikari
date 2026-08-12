(() => {
  'use strict';

  const data = window.HIKARI_MOCK;
  const params = new URLSearchParams(window.location.search);
  const screen = params.get('screen') || 'discover';
  const theme = params.get('theme') === 'light' ? 'light' : 'dark';
  const targetWidth = Number(params.get('width') || window.innerWidth);
  const targetHeight = Number(params.get('height') || window.innerHeight);
  const root = document.getElementById('app');

  document.documentElement.dataset.theme = theme;
  document.documentElement.dataset.screen = screen;
  document.documentElement.dataset.targetSize = targetWidth >= 520 ? 'medium' : targetWidth >= 400 ? 'large' : 'compact';
  document.documentElement.dataset.browserViewport = `${window.innerWidth}x${window.innerHeight}`;
  document.documentElement.dataset.viewportMode =
    window.innerWidth === targetWidth && window.innerHeight === targetHeight ? 'native' : 'fixed-target';
  document.documentElement.style.setProperty('--target-width', `${targetWidth}px`);
  document.documentElement.style.setProperty('--target-height', `${targetHeight}px`);

  const storiesById = new Map(data.stories.map((story) => [story.id, story]));
  const story = storiesById.get('story-moonlit-archive');

  const escapeHtml = (value) => String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');

  function hash(input) {
    let value = 2166136261;
    for (let index = 0; index < input.length; index += 1) {
      value ^= input.charCodeAt(index);
      value = Math.imul(value, 16777619);
    }
    return value >>> 0;
  }

  function paletteFor(id) {
    const value = hash(id);
    const hueA = value % 360;
    const hueB = (hueA + 56 + ((value >>> 8) % 72)) % 360;
    return {
      a: `hsl(${hueA} 34% 45%)`,
      b: `hsl(${hueB} 30% 30%)`,
      glow: `hsl(${hueA} 55% 64%)`
    };
  }

  function monogram(title) {
    return title
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2)
      .map((part) => part[0])
      .join('')
      .toUpperCase();
  }

  function artworkSvg(item, mode = 'cover') {
    const palette = paletteFor(item.id);
    const wide = mode === 'backdrop';
    const width = wide ? 1200 : 600;
    const height = wide ? 800 : 900;
    const label = escapeHtml(monogram(item.title));
    const title = escapeHtml(item.shortTitle || item.title);
    const svg = `
      <svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
        <defs>
          <linearGradient id="g" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stop-color="${palette.a}"/>
            <stop offset="1" stop-color="${palette.b}"/>
          </linearGradient>
          <radialGradient id="r" cx="0.22" cy="0.18" r="0.72">
            <stop offset="0" stop-color="${palette.glow}" stop-opacity="0.8"/>
            <stop offset="1" stop-color="${palette.glow}" stop-opacity="0"/>
          </radialGradient>
        </defs>
        <rect width="100%" height="100%" fill="url(#g)"/>
        <rect width="100%" height="100%" fill="url(#r)"/>
        <circle cx="${wide ? 920 : 460}" cy="${wide ? 160 : 190}" r="${wide ? 190 : 130}" fill="none" stroke="white" stroke-opacity="0.18" stroke-width="3"/>
        <path d="M0 ${height * 0.74} C ${width * 0.22} ${height * 0.55}, ${width * 0.52} ${height * 0.96}, ${width} ${height * 0.62} L ${width} ${height} L 0 ${height} Z" fill="black" fill-opacity="0.23"/>
        <path d="M${width * 0.1} ${height * 0.18} L${width * 0.34} ${height * 0.08} L${width * 0.48} ${height * 0.3} L${width * 0.3} ${height * 0.43} Z" fill="white" fill-opacity="0.08"/>
        <text x="${wide ? 72 : 54}" y="${wide ? 640 : 720}" fill="white" fill-opacity="0.92" font-family="Georgia,serif" font-weight="700" font-size="${wide ? 74 : 68}">${label}</text>
        <text x="${wide ? 74 : 56}" y="${wide ? 690 : 772}" fill="white" fill-opacity="0.72" font-family="Arial,sans-serif" font-size="${wide ? 24 : 22}">${title}</text>
      </svg>`;
    return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
  }

  function backdrop(item) {
    return `<div class="backdrop" aria-hidden="true"><img src="${artworkSvg(item, 'backdrop')}" alt=""></div>`;
  }

  function cover(item, options = {}) {
    const classes = ['cover-card', options.compact ? 'compact' : '', options.tall ? 'tall' : ''].filter(Boolean).join(' ');
    const progress = options.progress === false || item.progress === undefined ? '' : `
      <div class="cover-progress"><span style="width:${Math.round(item.progress * 100)}%"></span></div>`;
    return `
      <article class="story-tile ${options.className || ''}">
        <div class="${classes}">
          <img src="${artworkSvg(item)}" alt="Abstract artwork for ${escapeHtml(item.title)}">
          ${options.badge ? `<span class="corner-badge">${escapeHtml(options.badge)}</span>` : ''}
          ${progress}
        </div>
        ${options.hideMeta ? '' : `<h3>${escapeHtml(item.shortTitle || item.title)}</h3><p>${escapeHtml(item.type)} · ${escapeHtml(item.score)} ★</p>`}
      </article>`;
  }

  function topBar(title, options = {}) {
    return `
      <header class="topbar ${options.focused ? 'focused' : ''}">
        <div class="topbar-copy">
          ${options.focused ? '<button class="icon-button" aria-label="Back">←</button>' : ''}
          <div>
            <span class="eyebrow">${escapeHtml(options.eyebrow || 'HIKARI')}</span>
            ${title ? `<h1>${escapeHtml(title)}</h1>` : ''}
          </div>
        </div>
        ${options.search ? `<div class="search-pill"><span>⌕</span><span>${escapeHtml(options.search)}</span></div>` : ''}
        ${options.utility === false ? '' : '<button class="avatar" aria-label="Open utilities">HK</button>'}
      </header>`;
  }

  function floatingNav(active) {
    return `
      <nav class="floating-nav glass" aria-label="Primary">
        ${['discover', 'home', 'library'].map((item) => `
          <div class="nav-item ${item === active ? 'selected' : ''}" aria-current="${item === active ? 'page' : 'false'}">
            <span class="nav-icon">${item === 'discover' ? '◇' : item === 'home' ? '⌂' : '▥'}</span>
            <span>${item[0].toUpperCase()}${item.slice(1)}</span>
          </div>`).join('')}
      </nav>`;
  }

  function section(title, action = 'See all') {
    return `<div class="section-heading"><h2>${escapeHtml(title)}</h2><span>${escapeHtml(action)} →</span></div>`;
  }

  function chip(label, selected = false) {
    return `<span class="chip ${selected ? 'selected' : ''}">${escapeHtml(label)}</span>`;
  }

  function shelf(ids, options = {}) {
    return `<div class="shelf">${ids.map((id, index) => cover(storiesById.get(id), { badge: options.badges ? options.badges[index] : '', progress: options.progress })).join('')}</div>`;
  }

  function page(content, options = {}) {
    root.innerHTML = `
      <main class="screen ${options.className || ''}">
        ${options.backdrop ? backdrop(options.backdrop) : ''}
        <div class="screen-scrim" aria-hidden="true"></div>
        <div class="screen-content ${options.contentClass || ''}">${content}</div>
        ${options.nav ? floatingNav(options.nav) : ''}
      </main>`;
  }

  function featuredHero(item) {
    return `
      <section class="featured-hero">
        ${cover(item, { hideMeta: true, progress: false, tall: true })}
        <div class="featured-copy">
          <span class="eyebrow accent">FEATURED STORY</span>
          <h2>${escapeHtml(item.title)}</h2>
          <div class="rating-line"><strong>${escapeHtml(item.status)}</strong><span>·</span><strong>${escapeHtml(item.score)} ★</strong></div>
          <p>${escapeHtml(item.type)} · ${escapeHtml(item.genres.join(' · '))} · ${item.chapters} chapters</p>
          <div class="badge-row">${chip(item.source)}${chip(item.language)}${chip('Mapped')}</div>
          <button class="primary-button">Open story</button>
        </div>
      </section>`;
  }

  function renderDiscover() {
    page(`
      ${topBar('', { search: 'Search all stories' })}
      <div class="content-stack">
        ${featuredHero(story)}
        <div class="shortcut-row"><button class="shortcut">Genres <span>24</span></button><button class="shortcut">New releases <span>Today</span></button></div>
        ${section('Trending stories')}
        ${shelf(data.shelves.trending, { progress: false })}
        ${section('Fresh from your sources', 'Browse')}
        ${shelf(data.shelves.fresh, { progress: false })}
      </div>
    `, { backdrop: story, nav: 'discover', className: 'discover-screen' });
  }

  function renderHome() {
    const reading = storiesById.get('story-moonlit-archive');
    page(`
      ${topBar('Home', { eyebrow: 'YOUR READING' })}
      <div class="content-stack">
        <section class="summary-panel glass">
          <div><span class="eyebrow">THIS WEEK</span><strong>3h 42m</strong><small>reading time</small></div>
          <div><strong>18</strong><small>chapters read</small></div>
          <div><strong>6</strong><small>in library</small></div>
        </section>
        ${section('Continue reading', 'Resume')}
        <article class="continue-card glass">
          ${cover(reading, { compact: true, hideMeta: true, progress: false })}
          <div class="continue-copy"><span class="eyebrow accent">68% COMPLETE</span><h2>${escapeHtml(reading.shortTitle)}</h2><p>${escapeHtml(reading.currentChapter)}</p><div class="linear-progress"><span style="width:68%"></span></div><button class="primary-button compact-button">Continue</button></div>
        </article>
        ${section('Reading', '3 stories')}
        ${shelf(data.shelves.reading)}
        ${section('Want to read', '2 stories')}
        ${shelf(data.shelves.planned, { progress: false })}
      </div>
    `, { backdrop: reading, nav: 'home', className: 'home-screen' });
  }

  function renderLibrary() {
    page(`
      ${topBar('Library', { eyebrow: 'YOUR COLLECTION' })}
      <div class="content-stack">
        <div class="filter-scroll">${chip('All · 6', true)}${chip('Reading · 3')}${chip('Planned · 2')}${chip('Paused · 0')}${chip('Completed · 1')}</div>
        <div class="library-toolbar glass"><div class="inline-search">⌕ <span>Search library</span></div><button class="icon-button">↕</button><button class="icon-button">▦</button></div>
        <div class="library-summary"><span>6 stories</span><span>Last activity ↓</span></div>
        <section class="library-grid">
          ${data.stories.map((item, index) => cover(item, { badge: index === 0 ? 'READING' : index === 2 ? 'DONE' : '', progress: true })).join('')}
        </section>
      </div>
    `, { nav: 'library', className: 'library-screen' });
  }

  function renderSearch() {
    page(`
      ${topBar('Search', { focused: true, utility: false, eyebrow: 'DISCOVER' })}
      <div class="content-stack focused-stack">
        <div class="search-field glass"><span>⌕</span><strong>moon</strong><span class="muted">×</span></div>
        <div class="filter-scroll">${chip('All sources', true)}${chip('Light novel')}${chip('English')}${chip('Fantasy')}</div>
        <div class="result-meta"><span>8 results</span><span>2 catalog sources</span></div>
        <section class="result-list">
          ${[story, storiesById.get('story-cinder-library'), storiesById.get('story-salt-clock')].map((item) => `
            <article class="result-row glass">
              ${cover(item, { compact: true, hideMeta: true, progress: false })}
              <div><span class="eyebrow accent">${escapeHtml(item.catalog)}</span><h2>${escapeHtml(item.title)}</h2><p>${escapeHtml(item.type)} · ${escapeHtml(item.genres.join(' · '))}</p><div class="badge-row">${chip(`${item.score} ★`)}${chip(item.status)}</div></div>
            </article>`).join('')}
        </section>
        <div class="inline-notice">One source timed out. Cached results remain available.</div>
      </div>
    `, { backdrop: story, className: 'focused-screen search-screen' });
  }

  function storyHeader(activeTab) {
    return `
      ${topBar('', { focused: true, utility: true, eyebrow: 'STORY DETAIL' })}
      <section class="story-hero">
        ${cover(story, { hideMeta: true, progress: false, tall: true })}
        <div class="story-hero-copy"><span class="eyebrow accent">${story.status} · ${story.score} ★</span><h1>${escapeHtml(story.title)}</h1><p>${escapeHtml(story.author)} · ${escapeHtml(story.type)}</p><div class="badge-row">${story.genres.map((genre) => chip(genre)).join('')}</div><div class="hero-actions"><button class="primary-button">▶ Read</button><button class="secondary-button">Reading⌄</button><button class="icon-button glass">↓</button></div></div>
      </section>
      <nav class="detail-tabs">${['Overview', 'Chapters', 'Sources'].map((tab) => `<span class="${tab.toLowerCase() === activeTab ? 'active' : ''}">${tab}</span>`).join('')}</nav>`;
  }

  function renderStoryOverview() {
    page(`
      ${storyHeader('overview')}
      <div class="detail-body">
        <section class="detail-panel"><span class="eyebrow">SYNOPSIS</span><p>When an apprentice archivist finds a moonlit index that rewrites itself, every borrowed story begins to leave a trail back to its reader.</p></section>
        ${section('Metadata', '')}
        <div class="metadata-grid"><div><span>42</span><small>chapters</small></div><div><span>English</span><small>language</small></div><div><span>Mapped</span><small>reading source</small></div><div><span>2h</span><small>freshness</small></div></div>
        ${section('Linked catalogs', '')}
        <div class="source-mini glass"><strong>MyAnimeList</strong><span>Catalog metadata · refreshed 12m ago</span><b>Healthy</b></div>
      </div>
    `, { backdrop: story, className: 'focused-screen story-screen' });
  }

  function renderStorySources() {
    page(`
      ${storyHeader('sources')}
      <div class="detail-body">
        <div class="mapping-health glass"><span class="status-dot"></span><div><strong>Reading source mapped</strong><small>MangaDex · English · confirmed</small></div><button class="secondary-button">Review</button></div>
        ${section('Linked sources', '')}
        <div class="source-list">${data.sources.map((source, index) => `<article class="source-row glass"><div class="source-logo">${escapeHtml(source.name.slice(0, 2).toUpperCase())}</div><div><strong>${escapeHtml(source.name)}</strong><span>${escapeHtml(source.capability)} · ${escapeHtml(source.language)}</span><small>${escapeHtml(source.freshness)}</small></div><div class="source-score"><b>${escapeHtml(source.score)}</b><span>${escapeHtml(source.state)}</span></div>${index === 2 ? '<button class="secondary-button">Review</button>' : ''}</article>`).join('')}</div>
      </div>
    `, { backdrop: story, className: 'focused-screen story-screen' });
  }

  function renderStoryChapters() {
    page(`
      ${storyHeader('chapters')}
      <div class="detail-body chapter-body">
        <div class="chapter-toolbar glass"><div><strong>42 chapters</strong><small>MangaDex · English</small></div><button class="secondary-button">Newest ↓</button></div>
        <div class="chapter-list">${data.chapters.map((chapter) => `<article class="chapter-row"><div class="chapter-number">${escapeHtml(chapter.number)}</div><div><strong>${escapeHtml(chapter.title)}</strong><span>${escapeHtml(chapter.source)} · ${escapeHtml(chapter.language)} · ${escapeHtml(chapter.age)}</span></div><span>${chapter.downloaded ? '✓' : '↓'}</span></article>`).join('')}</div>
      </div>
    `, { backdrop: story, className: 'focused-screen story-screen chapters-screen' });
  }

  function renderMapping() {
    page(`
      ${topBar('Source mapping', { focused: true, utility: false, eyebrow: 'REVIEW BEFORE CONFIRMING' })}
      <div class="content-stack focused-stack">
        <article class="mapping-story glass">${cover(story, { compact: true, hideMeta: true, progress: false })}<div><span class="eyebrow accent">CANONICAL STORY</span><h2>${escapeHtml(story.title)}</h2><p>Current reading source: MangaDex · English</p></div></article>
        <section class="glass mapping-sheet">
          <span class="eyebrow">CANDIDATE</span><div class="source-row plain"><div class="source-logo">MN</div><div><strong>Mirror Novel</strong><span>Content source · English</span><small>81% title/metadata match · updated yesterday</small></div><div class="source-score"><b>81%</b><span>Review</span></div></div>
          <div class="evidence-grid"><div><small>Title</small><strong>Exact</strong></div><div><small>Author</small><strong>Close</strong></div><div><small>Chapters</small><strong>35 found</strong></div></div>
          <div class="warning-box">Changing the reading mapping affects chapter and Reader source selection. Catalog identity remains unchanged.</div>
          <div class="sheet-actions"><button class="secondary-button">Keep MangaDex</button><button class="primary-button">Confirm mapping</button></div>
        </section>
      </div>
    `, { backdrop: story, className: 'focused-screen mapping-screen' });
  }

  function renderDownloads() {
    page(`
      ${topBar('Downloads', { focused: true, utility: false, eyebrow: 'LOCAL STORAGE' })}
      <div class="content-stack focused-stack">
        <section class="storage-card glass"><div><span class="eyebrow">OFFLINE LIBRARY</span><strong>1.8 GB</strong><small>of 4 GB target</small></div><div class="donut">45%</div></section>
        ${section('Queue', '2 active')}
        <div class="download-list">${data.downloads.map((item) => `<article class="download-row glass"><div class="download-icon">↓</div><div><strong>${escapeHtml(item.title)}</strong><span>${escapeHtml(item.detail)}</span><small>${escapeHtml(item.state)}</small><div class="linear-progress"><span style="width:${Math.round(item.progress * 100)}%"></span></div></div><button class="icon-button">⋮</button></article>`).join('')}</div>
      </div>
    `, { className: 'focused-screen utility-screen' });
  }

  function renderUpdates() {
    page(`
      ${topBar('Updates', { focused: true, utility: false, eyebrow: 'YOUR LIBRARY' })}
      <div class="content-stack focused-stack">
        <div class="update-summary glass"><span class="status-dot"></span><strong>4 new chapter updates</strong><span>Last sync 12 minutes ago</span></div>
        <div class="update-feed">${data.updates.map((item, index) => { const itemStory = data.stories[index % data.stories.length]; return `<article class="update-row">${cover(itemStory, { compact: true, hideMeta: true, progress: false })}<div><span class="eyebrow accent">${escapeHtml(item.when)}</span><strong>${escapeHtml(item.story)}</strong><p>${escapeHtml(item.chapter)}</p><small>MangaDex · English</small></div><button class="icon-button">›</button></article>`; }).join('')}</div>
      </div>
    `, { className: 'focused-screen utility-screen' });
  }

  function renderReader() {
    page(`
      <div class="reader-backdrop"></div>
      <header class="reader-top glass"><button class="icon-button">←</button><div><span class="eyebrow">${escapeHtml(story.shortTitle)}</span><strong>Chapter 28 · The Glass Orchard</strong></div><button class="icon-button">⋮</button></header>
      <article class="reader-page">
        <span class="reader-kicker">CHAPTER TWENTY-EIGHT</span>
        <h1>The Glass Orchard</h1>
        <p>The archive opened only after midnight, when the lamps had forgotten which shelves they were meant to guard.</p>
        <p>Ren traced a fingertip along the silver index. A new line appeared beneath the fox seal, written in a hand that looked almost like her own.</p>
        <p>Outside, the rain moved over the tiled roof in patient waves. Somewhere between one page and the next, a bell rang from a room that did not exist yesterday.</p>
        <p>She closed the book. The words remained luminous on the inside of her eyelids.</p>
      </article>
      <footer class="reader-controls glass"><button class="icon-button">‹</button><div class="reader-progress"><div><span style="width:68%"></span></div><small>68% · 18 min left</small></div><button class="secondary-button">MangaDex · EN⌄</button><button class="icon-button">Aa</button><button class="icon-button">›</button></footer>
    `, { className: 'reader-screen focused-screen' });
  }

  function futureTarget(owner, text) {
    return `<div class="future-target"><span>${escapeHtml(owner)} visual target</span><strong>${escapeHtml(text)}</strong></div>`;
  }

  function renderPluginManager() {
    page(`
      ${topBar('Plugins', { focused: true, utility: false, eyebrow: 'SOURCE RUNTIME' })}
      <div class="content-stack focused-stack">
        ${futureTarget('Wave 11', 'Reference only · not a current clickable route')}
        <div class="utility-note">Installed plugin metadata only. No remote marketplace is represented in this target.</div>
        ${section('Installed', '2 plugins')}
        <div class="plugin-list">${data.plugins.map((plugin) => `<article class="plugin-row glass"><div class="source-logo">${escapeHtml(plugin.name.slice(0, 2).toUpperCase())}</div><div><strong>${escapeHtml(plugin.name)}</strong><span>${escapeHtml(plugin.capability)} · v${escapeHtml(plugin.version)}</span><small>${escapeHtml(plugin.status)}</small></div><span class="health-pill">Healthy</span></article>`).join('')}</div>
        ${section('Runtime diagnostics', '')}<div class="diagnostics glass"><div><span>Sandbox policy</span><b>Enforced</b></div><div><span>Package verification</span><b>Verified</b></div><div><span>Last refresh</span><b>12m ago</b></div></div>
      </div>
    `, { className: 'focused-screen utility-screen' });
  }

  function renderSettings() {
    page(`
      ${topBar('Settings', { focused: true, utility: false, eyebrow: 'LOCAL PREFERENCES' })}
      <div class="content-stack focused-stack">
        ${futureTarget('Wave 10', 'Reference only · not a current clickable route')}
        <section class="settings-group glass"><h2>Appearance</h2><div class="setting-row"><div><strong>Theme</strong><span>System · dark · light</span></div><span class="value-pill">System</span></div><div class="setting-row"><div><strong>Reduce motion</strong><span>Limit non-essential transitions</span></div><span class="switch"><i></i></span></div><div class="setting-row"><div><strong>Library layout</strong><span>Grid or compact list</span></div><span class="value-pill">Grid</span></div></section>
        <section class="settings-group glass"><h2>Reader</h2><div class="setting-row"><div><strong>Text size</strong><span>Chapter typography</span></div><span class="value-pill">100%</span></div><div class="setting-row"><div><strong>Keep screen awake</strong><span>While reading</span></div><span class="switch on"><i></i></span></div></section>
        <section class="settings-group glass"><h2>Storage</h2><div class="setting-row"><div><strong>Offline downloads</strong><span>1.8 GB used</span></div><span>›</span></div></section>
      </div>
    `, { className: 'focused-screen utility-screen' });
  }

  function stateShell(title, subtitle, body, className = '') {
    page(`
      ${topBar('', { search: 'Search all stories' })}
      <div class="state-stage ${className}">${body}<h1>${escapeHtml(title)}</h1><p>${escapeHtml(subtitle)}</p></div>
    `, { backdrop: story, nav: 'discover', className: 'discover-screen state-screen' });
  }

  function renderLoading() {
    stateShell('Loading Discover', 'Cached artwork appears immediately when available.', `<div class="skeleton-hero"><div class="skeleton cover-skeleton"></div><div class="skeleton-lines"><i></i><i></i><i></i></div></div><div class="skeleton-shelf">${'<div class="skeleton"></div>'.repeat(4)}</div>`, 'loading-state');
  }

  function renderEmpty() {
    stateShell('Nothing here yet', 'Add a catalog source or refresh Discover to start exploring.', '<div class="state-glyph">◇</div><button class="primary-button">Refresh sources</button>', 'empty-state');
  }

  function renderError() {
    stateShell('Discover unavailable', 'No usable cached catalog data is available. Check the source and try again.', '<div class="state-glyph danger">!</div><button class="primary-button">Try again</button><button class="secondary-button">Open diagnostics</button>', 'error-state');
  }

  function renderPartialFailure() {
    page(`
      ${topBar('', { search: 'Search all stories' })}
      <div class="content-stack">
        <div class="inline-notice warning"><strong>MyAnimeList refresh failed.</strong><span>Showing cached shelves and successful MangaDex data.</span></div>
        ${featuredHero(story)}
        ${section('Cached trending stories', 'Updated 1h ago')}
        ${shelf(data.shelves.trending, { progress: false })}
      </div>
    `, { backdrop: story, nav: 'discover', className: 'discover-screen state-screen' });
  }

  function renderOffline() {
    page(`
      ${topBar('', { search: 'Search cached stories' })}
      <div class="content-stack">
        <div class="inline-notice offline"><strong>Offline</strong><span>Cached catalog and library content remains available.</span></div>
        ${featuredHero(story)}
        ${section('Available offline', '4 stories')}
        ${shelf(data.shelves.reading, { progress: false })}
      </div>
    `, { backdrop: story, nav: 'discover', className: 'discover-screen state-screen' });
  }

  function renderOverview() {
    root.innerHTML = `
      <main class="overview-screen">
        <section class="overview-copy">
          <span class="eyebrow accent">APPROVED TARGET SYSTEM · TASK 2</span>
          <h1>Artwork first.<br>Glass with restraint.</h1>
          <p>Deterministic target-pack renderer for Hikari's Discover / Home / Library redesign.</p>
          <div class="principle-grid">
            <article><b>01</b><h2>Artwork-first</h2><p>StoryId-derived abstract artwork leads every cover and backdrop.</p></article>
            <article><b>02</b><h2>Selective glass</h2><p>Search, utility surfaces, floating navigation, sheets, and Reader chrome.</p></article>
            <article><b>03</b><h2>Product density</h2><p>Confident hierarchy, dense shelves, useful metadata, purposeful actions.</p></article>
            <article><b>04</b><h2>Responsive reflow</h2><p>360×800, 412×892, and 600×960 dp are rendered independently at 2×.</p></article>
          </div>
          <div class="api-note"><strong>Glass behavior</strong><span>API 31+ · bounded blur</span><span>API 26–30 · translucent fallback with identical geometry</span></div>
        </section>
        <section class="overview-device">
          <div class="mini-device">
            <div class="mini-backdrop" style="background-image:url('${artworkSvg(story, 'backdrop')}')"></div>
            <div class="mini-content">
              <div class="mini-search glass">Search all stories <span>⌕</span></div>
              <div class="mini-hero">${cover(story, { hideMeta: true, progress: false })}<div><span class="eyebrow accent">ONGOING · 4.8 ★</span><h2>${escapeHtml(story.title)}</h2><p>Light novel · Fantasy · Mystery</p></div></div>
              ${section('Fresh from your sources', '')}
              <div class="mini-shelf">${data.shelves.fresh.slice(0, 4).map((id) => cover(storiesById.get(id), { hideMeta: true, progress: false })).join('')}</div>
              ${floatingNav('discover')}
            </div>
          </div>
          <div class="overview-ownership"><span>Current visual flow</span><strong>Discover · Home · Library</strong><small>Settings → Wave 10 · Plugins → Wave 11</small></div>
        </section>
      </main>`;
  }

  const renderers = {
    discover: renderDiscover,
    home: renderHome,
    library: renderLibrary,
    search: renderSearch,
    storyOverview: renderStoryOverview,
    storySources: renderStorySources,
    storyChapters: renderStoryChapters,
    mapping: renderMapping,
    downloads: renderDownloads,
    updates: renderUpdates,
    reader: renderReader,
    pluginManager: renderPluginManager,
    settings: renderSettings,
    stateLoading: renderLoading,
    stateEmpty: renderEmpty,
    stateError: renderError,
    statePartialFailure: renderPartialFailure,
    stateOffline: renderOffline,
    overview: renderOverview
  };

  const renderer = renderers[screen];
  if (!renderer) {
    throw new Error(`Unknown UI target screen: ${screen}`);
  }
  renderer();
  document.documentElement.dataset.renderComplete = 'true';
})();
