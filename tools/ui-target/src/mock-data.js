window.HIKARI_MOCK = {
  stories: [
    {
      id: 'story-moonlit-archive',
      title: 'The Fox of the Moonlit Archive',
      shortTitle: 'Moonlit Archive',
      author: 'Mira Aoshima',
      score: '4.8',
      status: 'ONGOING',
      type: 'Light novel',
      genres: ['Fantasy', 'Mystery'],
      chapters: 42,
      source: 'MangaDex',
      catalog: 'MyAnimeList',
      language: 'English',
      progress: 0.68,
      currentChapter: 'Chapter 28 · The Glass Orchard'
    },
    {
      id: 'story-asterism-protocol',
      title: 'Asterism Protocol',
      shortTitle: 'Asterism Protocol',
      author: 'Ren Igarashi',
      score: '4.6',
      status: 'ONGOING',
      type: 'Web novel',
      genres: ['Sci-fi', 'Thriller'],
      chapters: 77,
      source: 'MangaDex',
      catalog: 'MyAnimeList',
      language: 'English',
      progress: 0.31,
      currentChapter: 'Chapter 24 · Signal Bloom'
    },
    {
      id: 'story-quiet-sword-saint',
      title: 'The Quiet Sword Saint',
      shortTitle: 'Quiet Sword Saint',
      author: 'Toma Kisaragi',
      score: '4.7',
      status: 'COMPLETE',
      type: 'Light novel',
      genres: ['Adventure', 'Drama'],
      chapters: 58,
      source: 'MangaDex',
      catalog: 'MyAnimeList',
      language: 'English',
      progress: 1,
      currentChapter: 'Chapter 58 · After the Snow'
    },
    {
      id: 'story-cinder-library',
      title: 'Cinder Library',
      shortTitle: 'Cinder Library',
      author: 'Nami Vale',
      score: '4.4',
      status: 'ONGOING',
      type: 'Web novel',
      genres: ['Fantasy', 'Romance'],
      chapters: 35,
      source: 'MangaDex',
      catalog: 'MyAnimeList',
      language: 'English',
      progress: 0.12,
      currentChapter: 'Chapter 4 · Ashbound'
    },
    {
      id: 'story-salt-clock',
      title: 'The Salt Clock',
      shortTitle: 'The Salt Clock',
      author: 'Eli Rowan',
      score: '4.3',
      status: 'ONGOING',
      type: 'Light novel',
      genres: ['Mystery', 'Drama'],
      chapters: 19,
      source: 'MangaDex',
      catalog: 'MyAnimeList',
      language: 'English',
      progress: 0,
      currentChapter: 'Chapter 1 · Low Tide'
    },
    {
      id: 'story-orchid-engine',
      title: 'Orchid Engine',
      shortTitle: 'Orchid Engine',
      author: 'Ilya Moss',
      score: '4.5',
      status: 'ONGOING',
      type: 'Web novel',
      genres: ['Sci-fi', 'Fantasy'],
      chapters: 63,
      source: 'MangaDex',
      catalog: 'MyAnimeList',
      language: 'English',
      progress: 0.44,
      currentChapter: 'Chapter 28 · Green Circuit'
    }
  ],
  shelves: {
    trending: ['story-moonlit-archive', 'story-asterism-protocol', 'story-quiet-sword-saint', 'story-cinder-library'],
    fresh: ['story-cinder-library', 'story-salt-clock', 'story-orchid-engine', 'story-moonlit-archive'],
    reading: ['story-moonlit-archive', 'story-asterism-protocol', 'story-orchid-engine'],
    planned: ['story-salt-clock', 'story-cinder-library'],
    completed: ['story-quiet-sword-saint']
  },
  chapters: [
    { number: '42', title: 'Lanterns Beyond the Index', source: 'MangaDex', language: 'EN', age: '2h', downloaded: false },
    { number: '41', title: 'The Door That Remembers', source: 'MangaDex', language: 'EN', age: '1d', downloaded: true },
    { number: '40.5', title: 'Interlude · Paper Birds', source: 'MangaDex', language: 'EN', age: '3d', downloaded: false },
    { number: '40', title: 'A Map in Silver Ink', source: 'MangaDex', language: 'EN', age: '5d', downloaded: true },
    { number: '39', title: 'The Borrowed Moon', source: 'MangaDex', language: 'EN', age: '8d', downloaded: false }
  ],
  sources: [
    { name: 'MangaDex', capability: 'CONTENT', language: 'English', state: 'Mapped', freshness: '2 hours ago', score: '98%' },
    { name: 'MyAnimeList', capability: 'CATALOG', language: 'English', state: 'Linked metadata', freshness: '12 minutes ago', score: '94%' },
    { name: 'Mirror Novel', capability: 'CONTENT', language: 'English', state: 'Candidate', freshness: '1 day ago', score: '81%' }
  ],
  downloads: [
    { title: 'Moonlit Archive · Ch. 41', detail: 'MangaDex · English', state: 'Downloading', progress: 0.72 },
    { title: 'Asterism Protocol · Ch. 23', detail: 'MangaDex · English', state: 'Queued', progress: 0.08 },
    { title: 'Quiet Sword Saint · Ch. 58', detail: 'MangaDex · English', state: 'Complete', progress: 1 },
    { title: 'Orchid Engine · Ch. 27', detail: 'MangaDex · English', state: 'Failed · tap to retry', progress: 0.44 }
  ],
  updates: [
    { story: 'The Fox of the Moonlit Archive', chapter: 'Chapter 42 · Lanterns Beyond the Index', when: '2 hours ago' },
    { story: 'Asterism Protocol', chapter: 'Chapter 77 · Blue Array', when: '5 hours ago' },
    { story: 'Orchid Engine', chapter: 'Chapter 63 · Root Access', when: 'Yesterday' },
    { story: 'Cinder Library', chapter: 'Chapter 35 · The Last Ember', when: '2 days ago' }
  ],
  plugins: [
    { name: 'MangaDex', version: '1.4.0', capability: 'Content source', status: 'Installed · healthy' },
    { name: 'MyAnimeList', version: '1.2.1', capability: 'Catalog source', status: 'Installed · healthy' }
  ]
};
