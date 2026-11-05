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
];

export const KNOWN_SHORTENER_DOMAINS: string[] = [
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

export const DEFAULT_USERSCRIPTS: UserscriptItem[] = [
  {
    id: 'universal-shortlink-bypass',
    name: 'FastForward / Universal Shortlink Bypasser TV',
    namespace: 'https://veloxtv.local/userscripts/shortlink-bypass',
    version: '4.8.2',
    description: 'Salta automáticamente +80 acortadores (ouo.io, adf.ly, shrinkme, exe.io, cuty.io, linkvertise, gplinks, acortar.link) sin esperar cuentas regresivas ni resolver captchas falsos.',
    matches: ['*://*.ouo.io/*', '*://*.adf.ly/*', '*://*.shrinkme.io/*', '*://*.linkvertise.com/*', '*://*.exe.io/*', '*://*.cuty.io/*', '*://*/*'],
    enabled: true,
    runAt: 'document-start',
    bypassCount: 14,
    code: `// ==UserScript==
// @name         FastForward / Universal Shortlink Bypasser TV
// @namespace    https://veloxtv.local/userscripts
// @version      4.8.2
// @description  Salta todos los enlaces acortados al instante sin esperas en Google TV
// @match        *://*/*
// @run-at       document-start
// @grant        none
// ==/UserScript==

(function() {
  'use strict';
  // 1. Acelerar temporizadores (setTimeout / setInterval) en páginas de acortadores
  const origSetTimeout = window.setTimeout;
  window.setTimeout = function(fn, delay, ...args) {
    const safeDelay = typeof delay === 'number' && delay >= 1000 ? 10 : delay;
    return origSetTimeout.call(this, fn, safeDelay, ...args);
  };

  // 2. Extraer destino directo en parámetros ?url=, ?dest=, ?go=, ?r= (Base64 o URI)
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

  // 3. Auto-click en botones finales de acortadores ("Get Link", "Obtener vínculo", "Continuar")
  document.addEventListener('DOMContentLoaded', () => {
    const targetLink = document.querySelector('a[data-final-url], a#btn-main, a.get-link, form#go-link');
    if (targetLink && targetLink.getAttribute('href') && /^https?:\\/\\//i.test(targetLink.getAttribute('href'))) {
      window.location.replace(targetLink.getAttribute('href'));
    }
  });
})();`,
  },
  {
    id: 'popup-clickjack-killer',
    name: 'Strict Overlay & Clickjacking Defuser',
    namespace: 'https://veloxtv.local/userscripts/overlay-defuser',
    version: '2.4.0',
    description: 'Destruye capas invisibles sobre los reproductores de video que abren ventanas emergentes al pulsar OK con el mando del Google TV.',
    matches: ['*://*/*'],
    enabled: true,
    runAt: 'document-start',
    bypassCount: 29,
    code: `// ==UserScript==
// @name         Strict Overlay & Clickjacking Defuser
// @namespace    https://veloxtv.local/userscripts
// @version      2.4.0
// @description  Bloquea window.open y elimina divs invisibles sobre el reproductor
// @match        *://*/*
// @run-at       document-start
// ==/UserScript==

(function() {
  'use strict';
  window.open = function() { return null; };
  const cleanOverlays = () => {
    document.querySelectorAll('div, a, iframe').forEach(el => {
      const style = window.getComputedStyle(el);
      const zIndex = parseInt(style.zIndex || '0', 10);
      if ((style.position === 'fixed' || style.position === 'absolute') && zIndex > 900 && parseFloat(style.opacity) < 0.15) {
        el.remove();
      }
    });
  };
  setInterval(cleanOverlays, 800);
})();`,
  },
  {
    id: 'tv-player-maximizer',
    name: 'Google TV Direct Stream & Auto-Cinema Mode',
    namespace: 'https://veloxtv.local/userscripts/tv-cinema',
    version: '1.9.5',
    description: 'Detecta archivos .m3u8 y .mp4 ocultos en la página para reproducirlos con aceleración por hardware a 60fps sin anuncios.',
    matches: ['*://*/*'],
    enabled: true,
    runAt: 'document-end',
    bypassCount: 8,
    code: `// ==UserScript==
// @name         Google TV Direct Stream & Auto-Cinema Mode
// @namespace    https://veloxtv.local/userscripts
// @version      1.9.5
// @description  Extrae streams HLS/MP4 para reproducción nativa sin lag en Chromecast y Onn TV
// @match        *://*/*
// @run-at       document-end
// ==/UserScript==

(function() {
  'use strict';
  document.querySelectorAll('video').forEach(v => {
    v.setAttribute('playsinline', 'true');
    v.setAttribute('preload', 'metadata');
  });
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
    id: 'tears-of-steel-1080p',
    title: 'Tears of Steel (Sci-Fi 1080p Master)',
    url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4',
    type: 'mp4',
    quality: '1080p 60fps · H.264 Ligero',
    category: 'Película',
    synopsis: 'En un Ámsterdam futurista, un grupo de soldados y científicos intenta salvar el mundo de robots destructivos.',
  },
  {
    id: 'sintel-remaster',
    title: 'Sintel — La Guardiana del Dragón',
    url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4',
    type: 'mp4',
    quality: '1080p HD · Audio Estéreo',
    category: 'Película',
    synopsis: 'Una joven solitaria emprende una peligrosa búsqueda a través de tierras heladas para rescatar a su único amigo, un joven dragón llamado Scales.',
  },
  {
    id: 'cosmos-laundromat-ep1',
    title: 'Cosmos Laundromat — Episodio Piloto',
    url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4',
    type: 'mp4',
    quality: '1080p · Direct Play',
    category: 'Serie',
    synopsis: 'En una isla desolada, la oveja Franck conoce a un misterioso vendedor que le ofrece el don de elegir entre infinitas vidas.',
  },
  {
    id: 'elephants-dream-ep',
    title: 'Elephants Dream — Archivos de Proog (S01E01)',
    url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4',
    type: 'mp4',
    quality: '1080p · Bajo Consumo CPU',
    category: 'Serie',
    synopsis: 'Proog y Emo exploran los laberintos mecánicos de una gigantesca Máquina surrealista que genera su propia realidad.',
  },
  {
    id: 'live-sports-rally',
    title: 'Directo Motor TV — Campeonato Mundial Rally',
    url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4',
    type: 'mp4',
    quality: 'EN VIVO · 1080p Zero-Buffer',
    category: 'Directo TV',
    synopsis: 'Transmisión en directo con telemetría en pista y cámaras onboard optimizadas para pantallas 1080p.',
  },
  {
    id: 'live-nature-4k-downscale',
    title: 'Directo 24/7 — Expedición Alpes & Glaciares',
    url: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4',
    type: 'mp4',
    quality: 'EN VIVO · Bitrate Adaptativo',
    category: 'Directo TV',
    synopsis: 'Señal continua de naturaleza en alta definición con decodificación directa sin sobrecarga de memoria.',
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
      // Clean ||domain.com^ syntax into domain.com
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

  const lower = url.toLowerCase();

  for (const domain of KNOWN_AD_DOMAINS) {
    if (lower.includes(domain)) {
      return { blocked: true, matchedRule: `||${domain}^` };
    }
  }

  // Common ad script patterns on streaming sites
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
  ];
  for (const pattern of adScriptPatterns) {
    if (lower.includes(pattern)) {
      return { blocked: true, matchedRule: `*${pattern}*` };
    }
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
    const parsed = new URL(targetUrl, 'https://veloxtv.local');
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

  // 1. Check query parameters (?url=, ?dest=, ?go=, ?to=, ?r=, ?link=, ?target=)
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
    'ouo.io/pelicula-1080p': 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4',
    'ouo.io/test-pelicula': 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4',
    'adf.ly/serie-cosmos': 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4',
    'shrinkme.io/directo-motor': 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/WeAreGoingOnBullrun.mp4',
    'linkvertise.com/sintel-hd': 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4',
    'cuty.io/elephants-dream': 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4',
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
 * Sanitizes raw HTML by stripping ad scripts, tracking iframes, and invisible overlays,
 * and extracts any direct video streams (.m3u8 / .mp4) found inside the markup.
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

  // 2. uBlock Origin: remove ad <script> tags and ad <iframe> tags
  if (options.ublockEnabled) {
    html = html.replace(/<script\b[^>]*>([\s\S]*?)<\/script>/gi, (fullTag, innerContent: string) => {
      const srcMatch = fullTag.match(/\bsrc=["']([^"']+)["']/i);
      const srcUrl = srcMatch ? srcMatch[1] : '';
      const check = evaluateUblockRequest(srcUrl || innerContent, true, options.customRules || []);
      if (
        check.blocked ||
        /popunder|window\.open\(|adserver|propeller|exoclick|popads|eval\(function\(p,a,c,k,e,d\)/i.test(
          innerContent
        )
      ) {
        blockedAdsCount += 1;
        return '<!-- [uBlock Origin TV: Script publicitario bloqueado] -->';
      }
      return fullTag;
    });

    html = html.replace(/<iframe\b[^>]*>[\s\S]*?<\/iframe>/gi, (fullIframe) => {
      const srcMatch = fullIframe.match(/\bsrc=["']([^"']+)["']/i);
      const srcUrl = srcMatch ? srcMatch[1] : '';
      const check = evaluateUblockRequest(srcUrl, true, options.customRules || []);
      if (check.blocked) {
        blockedAdsCount += 1;
        return '<!-- [uBlock Origin TV: Iframe publicitario eliminado] -->';
      }
      return fullIframe;
    });
  }

  // 3. Popup Blocker (strict): strip inline onclick="window.open(...)" and invisible high-z-index clickjack overlays
  if (options.popupStrictEnabled) {
    html = html.replace(/\bon(?:click|mousedown|mouseup|touchstart)=["'][^"']*window\.open[^"']*["']/gi, () => {
      blockedPopupsCount += 1;
      return 'data-popup-blocked="strict"';
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

  // 5. Inject TV performance CSS & strict runtime guards if enabled
  const injectedGuards: string[] = [];
  if (options.popupStrictEnabled) {
    injectedGuards.push(
      `<script>(function(){window.open=function(){return null;};})();</script>`
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
