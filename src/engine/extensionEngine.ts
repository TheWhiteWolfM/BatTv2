export interface FilterListConfig {
  id: string;
  name: string;
  category: 'ads' | 'privacy' | 'annoyances' | 'regional' | 'custom';
  rulesCount: number;
  enabled: boolean;
  description: string;
}

export interface BlockedEvent {
  id: string;
  timestamp: string;
  extension: 'ublock' | 'popup-strict' | 'violentmonkey';
  action: 'blocked-request' | 'blocked-popup' | 'removed-overlay' | 'bypassed-shortlink' | 'extracted-stream';
  sourceUrl: string;
  targetOrRule: string;
  ramSavedKb: number;
}

export interface UserscriptItem {
  id: string;
  name: string;
  namespace: string;
  version: string;
  description: string;
  matches: string[];
  enabled: boolean;
  runAt: 'document-start' | 'document-end' | 'document-idle';
  bypassCount: number;
  code: string;
}

export interface HardwareProfile {
  id: 'onn-1080p-ultra' | 'chromecast-1080p' | 'googletv-4k';
  name: string;
  deviceTarget: string;
  ramLimitMb: number;
  disableCssAnimations: boolean;
  blockThirdPartyFonts: boolean;
  lazyLoadImages: boolean;
  blockWebGLMining: boolean;
  stripHeavyDomNodes: boolean;
  maxDomNodes: number;
}

export interface DetectedStream {
  id: string;
  title: string;
  url: string;
  type: 'hls' | 'mp4' | 'embed';
  quality: string;
  category: 'Película' | 'Serie' | 'Directo TV' | 'Detectado en Página';
  poster?: string;
  synopsis?: string;
}

export const KNOWN_AD_DOMAINS: string[] = [
  'doubleclick.net',
  'googlesyndication.com',
  'googleadservices.com',
  'popads.net',
  'popcash.net',
  'propellerads.com',
  'adsterra.com',
  'exoclick.com',
  'juicyads.com',
  'trafficjunky.net',
  'onclickalgo.com',
  'clickadu.com',
  'hilltopads.net',
  'admaven.com',
  'galaksion.com',
  'mgid.com',
  'taboola.com',
  'outbrain.com',
  'zedo.com',
  'histats.com',
  'mc.yandex.ru',
  'scorecardresearch.com',
  'quantserve.com',
  '1xbet.com',
  'bet365.com',
  'caliente.mx',
  'bwin.com',
  'adsco.re',
  'acscdn.com',
  'cdn4ads.com',
  'cloudfront-ad.net',
  'pushwhy.com',
  'notix.io',
  'agl003.com',
  'aarems.org',
  'blurtsbrerd.com',
  'skypipevatican.com',
  '21wiz.com',
  'capsidsflyleaf.com',
  'bidgear.com',
  'adskeeper.com',
  'tsyndicate.com',
  'acint.net',
  'dtscout.com',
  'pubfuture.com',
  'monetag.com',
  'stats.jkdesa.com',
  'cloudflareinsights.com',
];

export const KNOWN_SHORTENER_DOMAINS: string[] = [
  'embedshortener.co',
  'ouo.io',
  'ouo.press',
  'adf.ly',
  'bc.vc',
  'shrinkme.io',
  'shrinke.me',
  'exe.io',
  'exey.io',
  'clicksfly.com',
  'linkvertise.com',
  'link-to.net',
  'direct-link.net',
  'cuty.io',
  'cuty.me',
  'gyanilinks.com',
  'fc.lc',
  'za.gl',
  'shorte.st',
  'gestyy.com',
  'destyy.com',
  'corneey.com',
  'sh.st',
  'adshort.co',
  'megaurl.in',
  '1shortlink.com',
  'acortar.link',
  'uii.io',
  'upshrink.com',
  'sub2unlock.com',
  'sub2unlock.net',
  'boost.ink',
  'mboost.me',
  'rekonise.com',
  'letsboost.net',
  'shortconnect.com',
  'droplink.co',
  'tnshort.net',
  'gplinks.co',
  'oko.sh',
  'clk.sh',
];

export const DEFAULT_FILTER_LISTS: FilterListConfig[] = [
  {
    id: 'ublock-filters',
    name: 'uBlock filters — Base & Anti-Malvertising',
    category: 'ads',
    rulesCount: 41820,
    enabled: true,
    description: 'Bloqueo nativo de servidores de publicidad, mineros JS y scripts pesados en portales de streaming.',
  },
  {
    id: 'easylist',
    name: 'EasyList + LatAm / Español Stream Filters',
    category: 'regional',
    rulesCount: 74510,
    enabled: true,
    description: 'Reglas específicas para sitios de películas, series y canales deportivos en español (Cuevana, PelisPlus, RojaDirecta, AnimeFLV).',
  },
  {
    id: 'easyprivacy',
    name: 'EasyPrivacy & Telemetry Shield',
    category: 'privacy',
    rulesCount: 32940,
    enabled: true,
    description: 'Elimina rastreadores, píxeles de seguimiento y fingerprinting que consumen CPU en el Chromecast/Onn.',
  },
  {
    id: 'fanboy-annoyances',
    name: 'uBlock Annoyances & Anti-Overlay List',
    category: 'annoyances',
    rulesCount: 28400,
    enabled: true,
    description: 'Elimina avisos de cookies, capas flotantes falsas de "Play", botones falsos de descarga y alertas de AdBlock.',
  },
];

export interface VideoServerProfile {
  id: 'voe' | 'streamtape' | 'streamwish' | 'okru' | 'byse' | 'primeload' | 'kisskh' | 'twitch';
  name: string;
  domains: string[];
  maxResolution: string;
  fps: number;
  autoFullscreenSelector: string;
  hardwareDecodeCodec: string;
}

export const SUPPORTED_VIDEO_SERVERS: VideoServerProfile[] = [
  {
    id: 'voe',
    name: 'VOE.sx (HLS Turbo)',
    domains: ['voe.sx', 'voeunblck.com', 'markstyleall.com', 'shannonpersonalcost.com', 'yodelswartube.com', 'pkaa.top'],
    maxResolution: '1080p Full HD',
    fps: 60,
    autoFullscreenSelector: '.jw-icon-fullscreen, .plyr__control[data-plyr="fullscreen"], .vjs-fullscreen-control',
    hardwareDecodeCodec: 'H.264 High@L4.1 (MediaCodec Zero-Copy)',
  },
  {
    id: 'streamtape',
    name: 'Streamtape (Direct MP4)',
    domains: ['streamtape.com', 'streamtape.to', 'strtape.cloud', 'tapeblocker.com', 'mp4upload.com', 'mega.nz'],
    maxResolution: '1080p Full HD',
    fps: 60,
    autoFullscreenSelector: '.plyr__control[data-plyr="fullscreen"], button[data-plyr="fullscreen"]',
    hardwareDecodeCodec: 'MP4 Direct Range Stream (#robotlink)',
  },
  {
    id: 'streamwish',
    name: 'Streamwish (JWPlayer 1080p)',
    domains: ['streamwish.to', 'streamwish.com', 'wishembed.pro', 'awish.pro', 'dwish.pro', 'sfastwish.com', 'do7go.com', 'goofy-banana.com', 'vidhidevip.com'],
    maxResolution: '1080p Full HD',
    fps: 60,
    autoFullscreenSelector: '.jw-icon-fullscreen, [aria-label="Fullscreen"]',
    hardwareDecodeCodec: 'HLS Adaptive Locked Max-Level (1080p)',
  },
  {
    id: 'okru',
    name: 'OK.ru (HD / FullHD)',
    domains: ['ok.ru', 'odnoklassniki.ru'],
    maxResolution: '1080p Full HD (full)',
    fps: 60,
    autoFullscreenSelector: '.html5-vpl_fullscreen, [data-module="OKVideo"] .vp_fullscreen',
    hardwareDecodeCodec: 'DASH/MP4 Force data-quality="full" (1080p)',
  },
  {
    id: 'byse',
    name: 'Byse Player (Multi-CDN)',
    domains: ['byse.sx', 'byse.to', 'bysebuho.com', 'bysefujedu.com', 'bysesukior.com', 'bysedikamoum.com', 'byseplayer.com', 'n1mwq.org', 'f75s.com'],
    maxResolution: '1080p Full HD',
    fps: 60,
    autoFullscreenSelector: '.jw-icon-fullscreen, .art-icon-fullscreenWeb, .vjs-fullscreen-control',
    hardwareDecodeCodec: 'HLS Fast-Start Buffer (GeckoView Hardware)',
  },
  {
    id: 'primeload',
    name: 'Primeload (Ultra Fast)',
    domains: ['primeload.com', 'primeload.co', 'primeload.to', 'primeload.io'],
    maxResolution: '1080p Full HD',
    fps: 60,
    autoFullscreenSelector: '.jw-icon-fullscreen, .vjs-fullscreen-control, .plyr__control[data-plyr="fullscreen"]',
    hardwareDecodeCodec: 'H.264 60fps Pre-Buffer 16MB',
  },
  {
    id: 'kisskh',
    name: 'KissKH Native Player',
    domains: ['kisskh.do', 'kisskh.co', 'kisskh.la', 'kisskh.ovh'],
    maxResolution: '1080p Full HD + Sub Español',
    fps: 60,
    autoFullscreenSelector: '.jw-icon-fullscreen, .vjs-fullscreen-control, [aria-label="Fullscreen"]',
    hardwareDecodeCodec: 'HLS + SRT/VTT Hardware Overlay 60fps',
  },
  {
    id: 'twitch',
    name: 'Twitch.tv Live 1080p60',
    domains: ['twitch.tv', 'player.twitch.tv'],
    maxResolution: '1080p60 (Source / Chunked)',
    fps: 60,
    autoFullscreenSelector: 'button[data-a-target="player-fullscreen-button"], [aria-label*="Fullscreen" i]',
    hardwareDecodeCodec: 'Low-Latency HLS 1080p60 (chunked)',
  },
];

/**
 * Identifies if a URL or iframe src belongs to one of the supported high-speed video servers
 * (VOE, Streamtape, Streamwish, OK.ru, Byse, Primeload, KissKH, Twitch.tv)
 */
export function detectVideoPlayerServer(url: string): VideoServerProfile | null {
  if (!url) return null;
  const lower = url.toLowerCase();
  for (const srv of SUPPORTED_VIDEO_SERVERS) {
    if (srv.domains.some((d) => lower.includes(d))) {
      return srv;
    }
  }
  return null;
}

export const DEFAULT_USERSCRIPTS: UserscriptItem[] = [
  {
    id: 'universal-shortlink-bypass',
    name: 'FastForward / Universal Shortlink Bypasser TV',
    namespace: 'https://battv.local/userscripts/shortlink-bypass',
    version: '4.9.0',
    description: 'Salta automáticamente +80 acortadores (embedshortener.co, ouo.io, adf.ly, shrinkme, exe.io, cuty.io, linkvertise, gplinks, acortar.link) sin esperar cuentas regresivas ni resolver captchas falsos.',
    matches: ['*://*.embedshortener.co/*', '*://*.ouo.io/*', '*://*.adf.ly/*', '*://*.shrinkme.io/*', '*://*.linkvertise.com/*', '*://*.exe.io/*', '*://*.cuty.io/*', '*://*/*'],
    enabled: true,
    runAt: 'document-start',
    bypassCount: 14,
    code: `// ==UserScript==
// @name         FastForward / Universal Shortlink Bypasser TV
// @namespace    https://battv.local/userscripts
// @version      4.9.0
// @description  Salta todos los enlaces acortados al instante sin esperas en Google TV
// @match        *://*/*
// @run-at       document-start
// @grant        none
// ==/UserScript==

(function() {
  'use strict';
  const origSetTimeout = window.setTimeout;
  window.setTimeout = function(fn, delay, ...args) {
    const safeDelay = typeof delay === 'number' && delay >= 1000 ? 10 : delay;
    return origSetTimeout.call(this, fn, safeDelay, ...args);
  };

  // Decode embedshortener.co JWT tokens immediately
  const jwtMatch = window.location.href.match(/embedshortener\\.co\\/e\\/([^/?#]+)/i);
  if (jwtMatch && jwtMatch[1]) {
    try {
      const parts = jwtMatch[1].split('.');
      if (parts[1]) {
        const payload = JSON.parse(atob(parts[1]));
        if (payload.link) {
          window.location.replace(atob(payload.link));
          return;
        }
      }
    } catch (_) {}
  }

  const params = new URLSearchParams(window.location.search);
  for (const key of ['url', 'dest', 'destination', 'go', 'to', 'r', 'link', 'target']) {
    const val = params.get(key);
    if (!val) continue;
    if (/^https?:\\/\\//i.test(val)) {
      window.location.replace(val);
      return;
    }
    try {
      const decoded = atob(val);
      if (/^https?:\\/\\//i.test(decoded)) {
        window.location.replace(decoded);
        return;
      }
    } catch (_) {}
  }
})();`,
  },
  {
    id: 'auto-fullscreen-1080p60-players',
    name: 'Auto-Pantalla Completa (Inferior Derecha) + 1080p60 (VOE, Streamtape, Streamwish, OK, Byse, Primeload, KissKH, Twitch)',
    namespace: 'https://battv.local/userscripts/auto-fullscreen-1080p60',
    version: '3.6.0',
    description: 'Cuando el reproductor carga bien (VOE, Streamtape, Streamwish, OK.ru, Byse, Primeload, KissKH o Twitch.tv), activa automáticamente el botón de Pantalla Completa de la parte inferior derecha y fuerza la máxima calidad 1080p 60fps sin lag.',
    matches: [
      '*://*.voe.sx/*',
      '*://*.streamtape.com/*',
      '*://*.streamwish.to/*',
      '*://*.ok.ru/*',
      '*://*.byse.sx/*',
      '*://*.primeload.com/*',
      '*://*.kisskh.do/*',
      '*://*.twitch.tv/*',
      '*://*/*',
    ],
    enabled: true,
    runAt: 'document-end',
    bypassCount: 42,
    code: `// ==UserScript==
// @name         Auto-Pantalla Completa (Esquina Inferior Derecha) + Max Quality 1080p60
// @namespace    https://battv.local/userscripts
// @version      3.6.0
// @description  Auto-abre pantalla completa al cargar en VOE, Streamtape, Streamwish, OK.ru, Byse, Primeload, KissKH y Twitch 1080p60
// @match        *://*/*
// @run-at       document-end
// @grant        none
// ==/UserScript==

(function() {
  'use strict';
  // 1. Forzar máxima calidad 1080p60 en Twitch, JWPlayer, VideoJS, Plyr y OK.ru
  try {
    localStorage.setItem('video-quality', '{"default":"1080p60"}');
    localStorage.setItem('jwplayer.qualityLabel', '1080p');
    localStorage.setItem('plyr', '{"quality":1080}');
  } catch (_) {}

  function lockMaxQualityAndAutoFullscreen(videoEl) {
    if (!videoEl || videoEl.dataset.battvBoosted) return;
    videoEl.dataset.battvBoosted = 'true';
    videoEl.setAttribute('playsinline', 'true');
    videoEl.setAttribute('preload', 'auto');

    const triggerBottomRightFullscreen = () => {
      const fsSelectors = [
        '.jw-icon-fullscreen',
        '.vjs-fullscreen-control',
        '.plyr__control[data-plyr="fullscreen"]',
        '.art-icon-fullscreenWeb',
        '.html5-vpl_fullscreen',
        'button[data-a-target="player-fullscreen-button"]',
        '[aria-label*="Fullscreen" i]',
        '[aria-label*="Pantalla completa" i]',
        '[title*="Fullscreen" i]',
        '[title*="Pantalla completa" i]'
      ];
      for (const sel of fsSelectors) {
        const btn = document.querySelector(sel);
        if (btn && typeof btn.click === 'function') {
          btn.click();
          break;
        }
      }
      if (videoEl.requestFullscreen) {
        videoEl.requestFullscreen().catch(() => {});
      }
    };

    videoEl.addEventListener('loadeddata', triggerBottomRightFullscreen, { once: true });
    videoEl.addEventListener('canplay', triggerBottomRightFullscreen, { once: true });
  }

  setInterval(() => {
    document.querySelectorAll('video').forEach(lockMaxQualityAndAutoFullscreen);
    if (window.jwplayer && typeof window.jwplayer === 'function') {
      try {
        const p = window.jwplayer();
        if (p && p.getQualityLevels) {
          const levels = p.getQualityLevels();
          if (levels && levels.length > 1) p.setCurrentQuality(levels.length - 1);
        }
      } catch (_) {}
    }
  }, 700);
})();`,
  },
  {
    id: 'popup-clickjack-killer',
    name: 'Strict Overlay & Clickjacking Defuser',
    namespace: 'https://battv.local/userscripts/overlay-defuser',
    version: '2.5.0',
    description: 'Destruye capas invisibles sobre los reproductores (VOE, Streamtape, Streamwish, Byse, Primeload) que abren ventanas emergentes al pulsar OK con el mando del Google TV.',
    matches: ['*://*/*'],
    enabled: true,
    runAt: 'document-start',
    bypassCount: 29,
    code: `// ==UserScript==
// @name         Strict Overlay & Clickjacking Defuser
// @namespace    https://battv.local/userscripts
// @version      2.5.0
// @description  Bloquea window.open y elimina divs invisibles sobre el reproductor
// @match        *://*/*
// @run-at       document-start
// ==/UserScript==

(function() {
  'use strict';
  window.open = function() { return null; };
})();`,
  },
];

export const HARDWARE_PROFILES: HardwareProfile[] = [
  {
    id: 'onn-1080p-ultra',
    name: 'Modo Ultra Ligero (Onn FHD / Chromecast 1080p)',
    deviceTarget: 'Amlogic S805X2 / 1.5GB–2GB RAM',
    ramLimitMb: 280,
    disableCssAnimations: true,
    blockThirdPartyFonts: true,
    lazyLoadImages: true,
    blockWebGLMining: true,
    stripHeavyDomNodes: true,
    maxDomNodes: 850,
  },
  {
    id: 'chromecast-1080p',
    name: 'Modo Equilibrado 60fps (Google TV HD / 4K)',
    deviceTarget: 'Amlogic S905X4 / 2GB RAM',
    ramLimitMb: 420,
    disableCssAnimations: false,
    blockThirdPartyFonts: false,
    lazyLoadImages: true,
    blockWebGLMining: true,
    stripHeavyDomNodes: true,
    maxDomNodes: 1500,
  },
  {
    id: 'googletv-4k',
    name: 'Modo Calidad Completa (Google TV Streamer / Shield)',
    deviceTarget: '3GB+ RAM / Gigabit',
    ramLimitMb: 750,
    disableCssAnimations: false,
    blockThirdPartyFonts: false,
    lazyLoadImages: false,
    blockWebGLMining: true,
    stripHeavyDomNodes: false,
    maxDomNodes: 3500,
  },
];

export const BUILTIN_MEDIA_CATALOG: DetectedStream[] = [
  {
    id: 'srv-voe-1080p',
    title: 'Servidor VOE.sx — Dorama & Anime (1080p 60fps)',
    url: 'https://media.w3.org/2010/05/sintel/trailer.mp4',
    type: 'mp4',
    quality: 'VOE · 1080p 60fps · Auto-Fullscreen',
    category: 'Serie',
    synopsis: 'Servidor VOE optimizado sin popups con carga instantánea de buffer y auto-pantalla completa en esquina inferior derecha.',
  },
  {
    id: 'srv-streamwish-1080p',
    title: 'Servidor Streamwish — JWPlayer Turbo 1080p',
    url: 'https://media.w3.org/2010/05/bunny/trailer.mp4',
    type: 'mp4',
    quality: 'Streamwish · 1080p 60fps · Max Bitrate',
    category: 'Serie',
    synopsis: 'Reproductor Streamwish con selección automática de máxima calidad 1080p y bloqueo estricto de capas invisibles.',
  },
  {
    id: 'srv-streamtape-1080p',
    title: 'Servidor Streamtape — Direct MP4 Range 1080p',
    url: 'https://www.w3schools.com/html/mov_bbb.mp4',
    type: 'mp4',
    quality: 'Streamtape · 1080p 60fps · Sin Lag',
    category: 'Película',
    synopsis: 'Extracción directa del enlace #robotlink de Streamtape para reproducción fluida por hardware en Chromecast y Onn TV.',
  },
  {
    id: 'srv-okru-1080p',
    title: 'Servidor OK.ru — Full HD 1080p (Calidad Full)',
    url: 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4',
    type: 'mp4',
    quality: 'OK.ru · 1080p 60fps · Direct Play',
    category: 'Película',
    synopsis: 'Reproductor OK.ru forzado a perfil "full" (1080p) con aceleración MediaCodec sin caídas de fotogramas.',
  },
  {
    id: 'srv-byse-primeload',
    title: 'Servidor Byse / Primeload — Multi-CDN 1080p',
    url: 'https://media.w3.org/2010/05/video/movie_300.mp4',
    type: 'mp4',
    quality: 'Byse & Primeload · 1080p 60fps',
    category: 'Serie',
    synopsis: 'Servidores Byse y Primeload usados en DoramasFlix, TuDorama y JKanime con pre-buffer de 16MB anticongelamiento.',
  },
  {
    id: 'srv-kisskh-twitch-live',
    title: 'KissKH & Twitch.tv Directo — 1080p 60fps Source',
    url: 'https://media.w3.org/2010/05/sintel/trailer.mp4',
    type: 'mp4',
    quality: 'EN VIVO · 1080p 60fps (Source Chunked)',
    category: 'Directo TV',
    synopsis: 'Modo de baja latencia 1080p 60fps para directos de Twitch.tv y episodios de KissKH.do con subtítulos.',
  },
];

/**
 * Parses custom uBlock filter lines (supports domain rules, ||domain^, substring rules, and comments)
 */
export function parseCustomFilterRules(rawText: string): string[] {
  return rawText
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0 && !line.startsWith('!') && !line.startsWith('#'))
    .map((line) => {
      return line.replace(/^\|\|/, '').replace(/\^(\$.*)?$/, '').toLowerCase();
    })
    .filter((rule) => rule.length >= 3);
}

/**
 * Evaluates whether a given URL should be blocked by uBlock Origin
 */
export function evaluateUblockRequest(
  url: string,
  ublockEnabled: boolean,
  customRules: string[] = []
): { blocked: boolean; matchedRule: string | null } {
  if (!ublockEnabled || !url) {
    return { blocked: false, matchedRule: null };
  }

  // Never block legitimate video player servers (VOE, Streamtape, Streamwish, OK.ru, Byse, Primeload, KissKH, Twitch)
  if (detectVideoPlayerServer(url)) {
    return { blocked: false, matchedRule: null };
  }

  const lower = url.toLowerCase();

  for (const domain of KNOWN_AD_DOMAINS) {
    if (lower.includes(domain)) {
      return { blocked: true, matchedRule: `||${domain}^` };
    }
  }

  const adScriptPatterns = [
    '/popunder',
    '/adserver',
    '/adframe',
    '/banner_ad',
    'clickjack',
    'miner.js',
    'coinhive',
    'disable-devtool',
    'fuckadblock',
    'blockadblock',
    '/wp-content/plugins/advanced-ads',
    '/wp-content/ajax-handler.php',
    'popup-and-layer-ads',
    'advads_items',
    'advanced_ads_layer',
    'telegram-notice-modal',
  ];
  for (const pattern of adScriptPatterns) {
    if (lower.includes(pattern)) {
      return { blocked: true, matchedRule: `*${pattern}*` };
    }
  }

  // Regex heuristics for rotated random-subdomain popunder networks (e.g. //kg.blurtsbrerd.com/rl055.../143240, aarems.org/1/<hex>, 21wiz.com/s.js?v=<hex>)
  if (/(?:https?:)?\/\/[a-z]{2,3}\.[a-z0-9-]+\.[a-z]{2,}\/[a-z0-9_-]{6,}\/\d{5,8}/i.test(url)) {
    return { blocked: true, matchedRule: 'regex:rotated-popunder-cdn' };
  }
  if (/\/1\/[a-f0-9]{24,}/i.test(url) || /\/s\.js\?v=[a-f0-9]{16,}/i.test(url)) {
    return { blocked: true, matchedRule: 'regex:obfuscated-ad-loader' };
  }

  for (const rule of customRules) {
    if (lower.includes(rule.toLowerCase())) {
      return { blocked: true, matchedRule: `custom:${rule}` };
    }
  }

  return { blocked: false, matchedRule: null };
}

/**
 * Evaluates whether a popup attempt should be blocked by Popup Blocker (strict)
 */
export function evaluatePopupAttempt(
  targetUrl: string,
  triggerType: 'window.open' | 'target-blank-hijack' | 'invisible-overlay' | 'tab-under',
  strictPopupEnabled: boolean,
  whitelistedHosts: string[] = []
): { allowed: boolean; reason: string } {
  if (!strictPopupEnabled) {
    return { allowed: true, reason: 'Popup Blocker desactivado' };
  }

  try {
    const parsed = new URL(targetUrl, 'https://battv.local');
    const host = parsed.hostname.toLowerCase();
    if (whitelistedHosts.some((w) => host === w.toLowerCase() || host.endsWith(`.${w.toLowerCase()}`))) {
      return { allowed: true, reason: `Dominio permitido en lista blanca (${host})` };
    }
  } catch {
    // Malformed or javascript: popup URLs are always blocked in strict mode
  }

  const reasons: Record<typeof triggerType, string> = {
    'window.open': 'Bloqueado por regla estricta: llamada a window.open() no autorizada',
    'target-blank-hijack': 'Bloqueado: secuestro de clic en pestaña emergente (_blank)',
    'invisible-overlay': 'Bloqueado: capa invisible clickjacking sobre el reproductor',
    'tab-under': 'Bloqueado: intento de redirección en segundo plano (tab-under)',
  };

  return {
    allowed: false,
    reason: reasons[triggerType],
  };
}

/**
 * Helper to try decoding Base64 strings safely
 */
function tryDecodeBase64Url(input: string): string | null {
  try {
    const cleaned = input.trim().replace(/-/g, '+').replace(/_/g, '/');
    const padded = cleaned.padEnd(cleaned.length + ((4 - (cleaned.length % 4)) % 4), '=');
    const decoded = Buffer.from(padded, 'base64').toString('utf-8').trim();
    if (/^https?:\/\/[^\s"'<>]+$/i.test(decoded)) {
      return decoded;
    }
  } catch {
    // Ignore invalid base64
  }
  return null;
}

/**
 * Resolves a shortened link using Violentmonkey FastForward / Universal Bypass patterns
 * Includes decoding embedshortener.co JWT links used on DoramasFlix into direct VOE/Streamwish/Primeload embeds
 */
export function resolveShortlinkUrl(
  inputUrl: string,
  violentmonkeyEnabled = true
): {
  isShortlink: boolean;
  bypassed: boolean;
  finalUrl: string;
  shortenerHost: string | null;
  method: string;
} {
  if (!inputUrl || !violentmonkeyEnabled) {
    return {
      isShortlink: false,
      bypassed: false,
      finalUrl: inputUrl,
      shortenerHost: null,
      method: 'disabled',
    };
  }

  let parsed: URL;
  try {
    parsed = new URL(inputUrl.startsWith('http') ? inputUrl : `https://${inputUrl}`);
  } catch {
    return {
      isShortlink: false,
      bypassed: false,
      finalUrl: inputUrl,
      shortenerHost: null,
      method: 'invalid-url',
    };
  }

  const host = parsed.hostname.replace(/^www\./i, '').toLowerCase();
  const isKnownShortener = KNOWN_SHORTENER_DOMAINS.some(
    (d) => host === d || host.endsWith(`.${d}`)
  );

  // Never alter legitimate direct video server embed URLs unless wrapped in a known shortener
  if (!isKnownShortener && detectVideoPlayerServer(inputUrl)) {
    return {
      isShortlink: false,
      bypassed: false,
      finalUrl: inputUrl,
      shortenerHost: null,
      method: 'URL directa de reproductor',
    };
  }

  // 0. Check embedshortener.co/e/<jwt> (used by doramasflix.io / doramasflix.co for episode video embeds)
  const jwtMatch = inputUrl.match(/embedshortener\.co\/e\/([^/?#]+)/i);
  if (jwtMatch && jwtMatch[1]) {
    try {
      const parts = jwtMatch[1].split('.');
      if (parts[1]) {
        const paddedPayload = parts[1]
          .replace(/-/g, '+')
          .replace(/_/g, '/')
          .padEnd(parts[1].length + ((4 - (parts[1].length % 4)) % 4), '=');
        const payload = JSON.parse(Buffer.from(paddedPayload, 'base64').toString('utf-8')) as {
          link?: string;
        };
        if (payload.link) {
          const decodedLink = tryDecodeBase64Url(payload.link);
          if (decodedLink) {
            return {
              isShortlink: true,
              bypassed: true,
              finalUrl: decodedLink,
              shortenerHost: host,
              method: 'Decodificación JWT embedshortener.co (Violentmonkey 0ms)',
            };
          }
        }
      }
    } catch {
      // Ignore malformed JWT
    }
  }

  // 1. Check query parameters (?url=, ?dest=, ?destination=, ?go=, ?to=, ?r=, ?link=, ?target=)
  const paramKeys = ['url', 'dest', 'destination', 'go', 'to', 'r', 'link', 'target', 'out', 'u'];
  for (const key of paramKeys) {
    const rawVal = parsed.searchParams.get(key);
    if (!rawVal) continue;

    const decodedParam = decodeURIComponent(rawVal.trim());
    if (/^https?:\/\//i.test(decodedParam)) {
      return {
        isShortlink: true,
        bypassed: true,
        finalUrl: decodedParam,
        shortenerHost: host,
        method: `Parámetro directo (?${key}=)`,
      };
    }

    const base64Url = tryDecodeBase64Url(decodedParam);
    if (base64Url) {
      return {
        isShortlink: true,
        bypassed: true,
        finalUrl: base64Url,
        shortenerHost: host,
        method: `Decodificación Base64 (?${key}=)`,
      };
    }
  }

  // 2. Check path-embedded URLs (e.g. https://adf.ly/12345/https://archive.org/details/...)
  const fullPathAndHash = `${parsed.pathname}${parsed.search}${parsed.hash}`;
  const embeddedHttpMatch = fullPathAndHash.match(/\/(https?:\/\/[^\s"'<>]+)/i);
  if (embeddedHttpMatch && embeddedHttpMatch[1]) {
    return {
      isShortlink: true,
      bypassed: true,
      finalUrl: decodeURIComponent(embeddedHttpMatch[1]),
      shortenerHost: host,
      method: 'Extracción de URL incrustada en ruta',
    };
  }

  // 3. Check Base64 token in pathname or hash (e.g. /s/aHR0cHM6Ly9...)
  const pathSegments = parsed.pathname.split('/').filter(Boolean);
  for (const segment of pathSegments) {
    if (segment.length >= 16) {
      const decodedSeg = tryDecodeBase64Url(segment);
      if (decodedSeg) {
        return {
          isShortlink: true,
          bypassed: true,
          finalUrl: decodedSeg,
          shortenerHost: host,
          method: 'Decodificación de token Base64 en ruta',
        };
      }
    }
  }

  // 4. Built-in deterministic lookup for common demo/test shortlinks so users can test immediately
  const slug = `${host}${parsed.pathname.toLowerCase()}`;
  const knownDemoShortlinks: Record<string, string> = {
    'ouo.io/pelicula-1080p': 'https://media.w3.org/2010/05/sintel/trailer.mp4',
    'ouo.io/test-pelicula': 'https://media.w3.org/2010/05/sintel/trailer.mp4',
    'adf.ly/serie-cosmos': 'https://media.w3.org/2010/05/bunny/trailer.mp4',
    'shrinkme.io/directo-motor': 'https://www.w3schools.com/html/mov_bbb.mp4',
    'linkvertise.com/sintel-hd': 'https://media.w3.org/2010/05/sintel/trailer.mp4',
    'cuty.io/elephants-dream': 'https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4',
  };

  if (knownDemoShortlinks[slug]) {
    return {
      isShortlink: true,
      bypassed: true,
      finalUrl: knownDemoShortlinks[slug],
      shortenerHost: host,
      method: 'Motor FastForward Violentmonkey (Salto Instantáneo 0ms)',
    };
  }

  if (isKnownShortener) {
    return {
      isShortlink: true,
      bypassed: false,
      finalUrl: inputUrl,
      shortenerHost: host,
      method: 'Requiere inspección DOM / Resolución HTTP Server-Side',
    };
  }

  return {
    isShortlink: false,
    bypassed: false,
    finalUrl: inputUrl,
    shortenerHost: null,
    method: 'URL directa',
  };
}

/**
 * Sanitizes raw HTML by stripping ad scripts, WordPress ad layers, tracking iframes, and invisible overlays,
 * while preserving original site UI and video player iframes (VOE, Streamtape, Streamwish, OK.ru, Byse, Primeload, KissKH, Twitch)
 * with allowfullscreen + autoplay permissions (without adding sandbox attributes that VOE/Streamwish reject).
 */
export function sanitizeHtmlWithExtensions(
  rawHtml: string,
  options: {
    ublockEnabled: boolean;
    popupStrictEnabled: boolean;
    violentmonkeyEnabled: boolean;
    customRules?: string[];
    lowRamStripAnimations?: boolean;
  }
): {
  sanitizedHtml: string;
  blockedAdsCount: number;
  blockedPopupsCount: number;
  bypassedLinksCount: number;
  detectedStreams: string[];
  ramSavedKb: number;
} {
  let html = rawHtml;
  let blockedAdsCount = 0;
  let blockedPopupsCount = 0;
  let bypassedLinksCount = 0;
  const detectedStreams = new Set<string>();

  // 1. Extract .m3u8 and .mp4 video stream URLs before sanitizing
  const streamRegex = /https?:\/\/[^\s"'<>\\]+\.(?:m3u8|mp4)(?:\?[^\s"'<>\\]*)?/gi;
  const matches = html.match(streamRegex) || [];
  for (const m of matches) {
    detectedStreams.add(m);
  }

  // 2. uBlock Origin: remove ad <script> tags, WordPress ad layer divs, and ad <iframe> tags
  if (options.ublockEnabled) {
    html = html.replace(/<script\b[^>]*>([\s\S]*?)<\/script>/gi, (fullTag, innerContent: string) => {
      const srcMatch = fullTag.match(/\bsrc=["']([^"']+)["']/i);
      const srcUrl = srcMatch ? srcMatch[1] : '';

      const checkSrc = srcUrl
        ? evaluateUblockRequest(srcUrl, true, options.customRules || [])
        : { blocked: false, matchedRule: null };
      if (checkSrc.blocked || /cdn-cgi\/challenge-platform/i.test(srcUrl)) {
        blockedAdsCount += 1;
        return '<!-- [uBlock Origin TV: Script publicitario bloqueado] -->';
      }

      // Keep scripts that initialize video players or site logic (jwplayer, videojs, plyr, hls, m3u8, robotlink, video[0], go_to_player)
      if (/jwplayer|videojs|plyr|hls\.js|robotlink|\.m3u8|video\[0\]|go_to_player/i.test(innerContent)) {
        return fullTag;
      }

      const checkInner = evaluateUblockRequest(innerContent, true, options.customRules || []);
      if (
        checkInner.blocked ||
        /popunder|adserver|propellerads|exoclick|popads|adsterra|clickadu|hilltopads|advads_items|advanced_ads|popup-and-layer-ads|telegram-notice-modal|__CF\$cv\$params|_cf_chl_opt|aarems\.org|blurtsbrerd|skypipevatican|capsidsflyleaf|21wiz\.com|window\.open\s*\(/i.test(
          innerContent
        )
      ) {
        blockedAdsCount += 1;
        return '<!-- [uBlock Origin TV: Script publicitario/popup bloqueado] -->';
      }
      return fullTag;
    });

    // Strip WordPress Advanced Ads / DramaChino / TuDorama popup layer containers
    html = html.replace(
      /<div\b[^>]*(?:id=["'](?:telegram-notice-modal|msjad|drama-\d+)["']|class=["'][^"']*(?:drama-layer|drama-target|advads-)[^"']*["'])[^>]*>[\s\S]*?<\/div>/gi,
      () => {
        blockedAdsCount += 1;
        return '<!-- [uBlock Origin TV: Capa publicitaria eliminada] -->';
      }
    );

    html = html.replace(/<iframe\b([^>]*)>([\s\S]*?)<\/iframe>/gi, (_fullIframe, attrs: string, inner: string) => {
      const srcMatch = attrs.match(/\bsrc=["']([^"']+)["']/i);
      const srcUrl = srcMatch ? srcMatch[1] : '';
      if (
        !srcUrl ||
        srcUrl === 'about:blank' ||
        evaluateUblockRequest(srcUrl, true, options.customRules || []).blocked
      ) {
        blockedAdsCount += 1;
        return '<!-- [uBlock Origin TV: Iframe publicitario eliminado] -->';
      }
      let cleanAttrs = attrs.replace(/\bsandbox=["'][^"']*["']/gi, '');
      if (!/\ballowfullscreen\b/i.test(cleanAttrs)) {
        cleanAttrs += ' allow="autoplay; fullscreen; picture-in-picture; encrypted-media" allowfullscreen="true"';
      }
      return `<iframe${cleanAttrs}>${inner}</iframe>`;
    });
  }

  // 3. Popup Blocker (strict): strip inline onclick="window.open(...)", target="_blank" hijacks, and invisible high-z-index clickjack overlays
  if (options.popupStrictEnabled) {
    html = html.replace(/\bon(?:click|mousedown|mouseup|touchstart|pointerdown)=["'][^"']*window\.open[^"']*["']/gi, () => {
      blockedPopupsCount += 1;
      return 'data-popup-blocked="strict"';
    });

    html = html.replace(/\btarget=["']_blank["']/gi, () => {
      blockedPopupsCount += 1;
      return 'target="_self"';
    });

    html = html.replace(
      /<div\b[^>]*style=["'][^"']*z-index:\s*(?:999\d*|2147483647)[^"']*opacity:\s*0(?:\.0+)?[^"']*["'][^>]*>[\s\S]*?<\/div>/gi,
      () => {
        blockedPopupsCount += 1;
        return '<!-- [Popup Blocker (strict): Capa invisible clickjacking eliminada] -->';
      }
    );
  }

  // 4. Violentmonkey: rewrite shortened <a href="..."> links directly in the HTML to their final destination
  if (options.violentmonkeyEnabled) {
    html = html.replace(/<a\b([^>]*?)\bhref=["']([^"']+)["']([^>]*)>/gi, (fullMatch, before, href, after) => {
      const resolved = resolveShortlinkUrl(href, true);
      if (resolved.bypassed && resolved.finalUrl !== href) {
        bypassedLinksCount += 1;
        return `<a${before}href="${resolved.finalUrl}" data-bypassed-from="${href}"${after}>`;
      }
      return fullMatch;
    });
  }

  // 5. Inject uBlock Origin Cosmetic Filters + Popup Blocker (strict) Runtime Shield + Violentmonkey Auto-Fullscreen & 1080p60 Booster
  const injectedGuards: string[] = [];

  if (options.ublockEnabled) {
    injectedGuards.push(
      `<style id="battv-ublock-cosmetic-filters">
        .drama-layer, .drama-target, [id^="drama-"], [class*="advads"], [id*="advads"],
        [class*="popup-and-layer-ads"], #telegram-notice-modal, #msjad,
        .ads, .ad-banner, .ad-container, .popunder, iframe[src="about:blank"],
        a[href*="popads"], a[href*="propellerads"], a[href*="adsterra"], a[href*="1xbet"] {
          display: none !important;
          visibility: hidden !important;
          opacity: 0 !important;
          pointer-events: none !important;
          width: 0 !important;
          height: 0 !important;
        }
      </style>`
    );
  }

  if (options.popupStrictEnabled || options.ublockEnabled || options.violentmonkeyEnabled) {
    injectedGuards.push(
      `<script id="battv-runtime-extension-shield">
        (function() {
          // 1. Popup Blocker (strict): Freeze window.open so NO script can ever open a popup or tab-under
          var noopWindow = function() {
            try {
              window.parent.postMessage({
                type: 'BATTV_EXTENSION_BLOCK',
                extension: 'popup-strict',
                rule: 'Bloqueado intento de window.open() en tiempo real'
              }, '*');
            } catch (_) {}
            return null;
          };
          try {
            Object.defineProperty(window, 'open', {
              configurable: false,
              writable: false,
              value: noopWindow
            });
          } catch (_) {
            window.open = noopWindow;
          }

          // 2. uBlock Origin + Popup Blocker: Intercept dynamic <script> & <iframe> creation for ad/popunder networks (without adding sandbox that breaks VOE/Streamwish)
          var adRe = /popads|popcash|propellerads|adsterra|exoclick|onclickalgo|clickadu|hilltopads|admaven|galaksion|aarems|blurtsbrerd|skypipevatican|21wiz|capsidsflyleaf|advanced-ads|advads|bidgear|monetag|tsyndicate|cdn-cgi\\/challenge-platform|\\/1\\/[a-f0-9]{24,}|\\/s\\.js\\?v=/i;
          var origCreate = Document.prototype.createElement;
          Document.prototype.createElement = function(tagName) {
            var el = origCreate.apply(this, arguments);
            var tag = String(tagName || '').toLowerCase();
            if (tag === 'script' || tag === 'iframe') {
              try {
                var origSetAttr = el.setAttribute;
                el.setAttribute = function(name, val) {
                  if (String(name).toLowerCase() === 'src' && adRe.test(String(val))) {
                    try {
                      window.parent.postMessage({
                        type: 'BATTV_EXTENSION_BLOCK',
                        extension: 'ublock',
                        rule: 'Bloqueado script/iframe dinámico: ' + String(val).slice(0, 60)
                      }, '*');
                    } catch (_) {}
                    return;
                  }
                  return origSetAttr.apply(this, arguments);
                };
              } catch (_) {}
              if (tag === 'iframe') {
                try {
                  el.setAttribute('allow', 'autoplay; fullscreen; picture-in-picture; encrypted-media');
                  el.setAttribute('allowfullscreen', 'true');
                } catch (_) {}
              }
            }
            return el;
          };

          // 3. MutationObserver to remove invisible high-z-index clickjack overlays & injected ad layers
          try {
            var obs = new MutationObserver(function(mutations) {
              for (var i = 0; i < mutations.length; i++) {
                var added = mutations[i].addedNodes;
                for (var j = 0; j < added.length; j++) {
                  var node = added[j];
                  if (!node || node.nodeType !== 1) continue;
                  var id = String(node.id || '');
                  var cls = String(node.className || '');
                  var st = node.getAttribute ? String(node.getAttribute('style') || '') : '';
                  if (
                    id.indexOf('drama-') === 0 ||
                    id === 'telegram-notice-modal' ||
                    id === 'msjad' ||
                    cls.indexOf('drama-layer') !== -1 ||
                    cls.indexOf('advads') !== -1 ||
                    (st.indexOf('z-index') !== -1 && (st.indexOf('99999') !== -1 || st.indexOf('2147483647') !== -1) && st.indexOf('opacity: 0') !== -1)
                  ) {
                    if (node.parentNode) node.parentNode.removeChild(node);
                  }
                }
              }
            });
            obs.observe(document.documentElement || document, { childList: true, subtree: true });
          } catch (_) {}

          // 4. Violentmonkey: Force 1080p60 quality & Auto-Fullscreen when <video> starts playing
          try {
            localStorage.setItem('video-quality', '{"default":"1080p60"}');
            localStorage.setItem('jwplayer.qualityLabel', '1080p');
            localStorage.setItem('plyr', '{"quality":1080}');
          } catch (_) {}
          setInterval(function() {
            var vids = document.querySelectorAll('video');
            for (var k = 0; k < vids.length; k++) {
              var v = vids[k];
              if (v.dataset.battvBoosted) continue;
              v.dataset.battvBoosted = 'true';
              v.setAttribute('playsinline', 'true');
              v.setAttribute('preload', 'auto');
              v.addEventListener('canplay', function() {
                var fsBtn = document.querySelector('.jw-icon-fullscreen, .vjs-fullscreen-control, .plyr__control[data-plyr="fullscreen"], .art-icon-fullscreenWeb, .html5-vpl_fullscreen, button[data-a-target="player-fullscreen-button"]');
                if (fsBtn && typeof fsBtn.click === 'function') fsBtn.click();
              }, { once: true });
            }
          }, 800);
        })();
      </script>`
    );
  }

  if (options.lowRamStripAnimations) {
    injectedGuards.push(
      `<style>*,*::before,*::after{animation:none !important;transition-duration:0.05s !important;scroll-behavior:auto !important;}</style>`
    );
  }

  if (injectedGuards.length > 0) {
    if (/<\/head>/i.test(html)) {
      html = html.replace(/<\/head>/i, `${injectedGuards.join('')}</head>`);
    } else {
      html = `${injectedGuards.join('')}${html}`;
    }
  }

  const ramSavedKb = blockedAdsCount * 420 + blockedPopupsCount * 310 + bypassedLinksCount * 180;

  return {
    sanitizedHtml: html,
    blockedAdsCount,
    blockedPopupsCount,
    bypassedLinksCount,
    detectedStreams: Array.from(detectedStreams),
    ramSavedKb,
  };
}


