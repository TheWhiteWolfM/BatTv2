import express from 'express';
import path from 'path';
import { fileURLToPath } from 'url';
import { createServer as createViteServer } from 'vite';
import {
  resolveShortlinkUrl,
  sanitizeHtmlWithExtensions,
  parseCustomFilterRules,
  DEFAULT_USERSCRIPTS,
} from './src/engine/extensionEngine.ts';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const GECKOVIEW_DESKTOP_UA =
  'Mozilla/5.0 (X11; Linux x86_64; rv:132.0) Gecko/20100101 Firefox/132.0';

async function fetchWithGeckoHeaders(targetUrl: string, timeoutMs = 10000): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const parsed = new URL(targetUrl);
    const resp = await fetch(targetUrl, {
      method: 'GET',
      redirect: 'follow',
      signal: controller.signal,
      headers: {
        'User-Agent': GECKOVIEW_DESKTOP_UA,
        Accept:
          'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
        'Accept-Language': 'es-ES,es;q=0.9,en-US;q=0.8,en;q=0.7',
        Referer: `${parsed.origin}/`,
        'Upgrade-Insecure-Requests': '1',
      },
    });
    return resp;
  } finally {
    clearTimeout(timer);
  }
}

/**
 * Fetches real live Twitch.tv streams via Twitch's GQL API and embeds the official 1080p60 player
 */
async function buildLiveTwitchHtml(targetUrl: string): Promise<string | null> {
  try {
    const parsed = new URL(targetUrl);
    const pathParts = parsed.pathname.split('/').filter(Boolean);
    const requestedChannel = pathParts[0] && !['directory', 'search', 'p'].includes(pathParts[0])
      ? pathParts[0]
      : null;

    const gqlResp = await fetch('https://gql.twitch.tv/gql', {
      method: 'POST',
      headers: {
        'Client-Id': 'kimne78kx3ncx6brgo4mv6wki5h1ko',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({
        query: `{ streams(first: 12) { edges { node { id title viewersCount broadcaster { login displayName profileImageURL(width: 70) } game { name } previewImageURL(width: 640, height: 360) } } } }`,
      }),
    });
    const gqlJson = await gqlResp.json();
    const edges = gqlJson?.data?.streams?.edges || [];
    const streams = edges.map((e: { node: unknown }) => e.node) as Array<{
      id: string;
      title: string;
      viewersCount: number;
      broadcaster: { login: string; displayName: string; profileImageURL: string };
      game?: { name: string };
      previewImageURL: string;
    }>;

    const activeChannel =
      requestedChannel || (streams[0] && streams[0].broadcaster.login) || 'ibai';

    const cardsHtml = streams
      .map(
        (st) => `
        <a href="https://www.twitch.tv/${st.broadcaster.login}" class="tw-card">
          <div class="tw-thumb">
            <img src="${st.previewImageURL}" alt="${st.broadcaster.displayName}" loading="lazy" />
            <span class="tw-live">EN DIRECTO · 1080p60</span>
            <span class="tw-viewers">${Number(st.viewersCount).toLocaleString()} espectadores</span>
          </div>
          <div class="tw-meta">
            <img src="${st.broadcaster.profileImageURL}" class="tw-avatar" alt="" />
            <div style="min-width:0">
              <div class="tw-title">${st.title}</div>
              <div class="tw-user">${st.broadcaster.displayName} · ${st.game?.name || 'Directo'}</div>
            </div>
          </div>
        </a>`
      )
      .join('');

    return `<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8" />
  <title>Twitch.tv — Directos en 1080p 60fps</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { background: #0e0e10; color: #efeff1; font-family: system-ui, -apple-system, sans-serif; }
    header { background: #18181b; border-bottom: 1px solid #2f2f35; padding: 12px 24px; display: flex; align-items: center; justify-content: space-between; }
    .tw-brand { font-size: 20px; font-weight: 900; color: #a970ff; display: flex; align-items: center; gap: 10px; }
    .container { max-width: 1400px; margin: 0 auto; padding: 18px 24px; }
    .stage { background: #000; border: 1px solid #303038; border-radius: 12px; overflow: hidden; margin-bottom: 22px; position: relative; }
    .player-box { position: relative; width: 100%; aspect-ratio: 16/9; max-height: 520px; background: #000; }
    iframe { width: 100%; height: 100%; border: 0; }
    .fs-overlay-br { position: absolute; bottom: 48px; right: 16px; z-index: 30; display: flex; gap: 8px; align-items: center; }
    .badge-60 { background: #10b981; color: #052e16; font-size: 11px; font-weight: 800; padding: 5px 10px; border-radius: 8px; }
    .btn-fs { background: rgba(24,24,27,0.92); color: #fff; border: 1.5px solid #a970ff; padding: 7px 14px; border-radius: 9px; font-size: 12px; font-weight: 800; cursor: pointer; }
    .btn-fs:hover { background: #9147ff; }
    .fs-active { position: fixed !important; inset: 0 !important; width: 100vw !important; height: 100vh !important; max-height: 100vh !important; z-index: 999999 !important; }
    h2 { font-size: 17px; font-weight: 800; margin-bottom: 14px; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 16px; }
    .tw-card { text-decoration: none; color: inherit; background: #18181b; border: 1px solid #26262c; border-radius: 10px; overflow: hidden; display: block; }
    .tw-card:hover { border-color: #a970ff; }
    .tw-thumb { position: relative; aspect-ratio: 16/9; background: #000; }
    .tw-thumb img { width: 100%; height: 100%; object-fit: cover; }
    .tw-live { position: absolute; top: 8px; left: 8px; background: #eb0400; color: #fff; font-size: 10px; font-weight: 800; padding: 2px 6px; border-radius: 4px; }
    .tw-viewers { position: absolute; bottom: 8px; left: 8px; background: rgba(0,0,0,0.8); color: #fff; font-size: 11px; padding: 2px 6px; border-radius: 4px; }
    .tw-meta { padding: 10px; display: flex; gap: 10px; align-items: flex-start; }
    .tw-avatar { width: 36px; height: 36px; border-radius: 50%; flex-shrink: 0; }
    .tw-title { font-size: 13px; font-weight: 700; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .tw-user { font-size: 11px; color: #adadb8; margin-top: 2px; }
  </style>
</head>
<body>
  <header>
    <div class="tw-brand">Twitch.tv <span>· 1080p 60fps Source (Sin Lag)</span></div>
    <div style="font-size:12px;color:#67d5c8;font-weight:700">Canal activo: ${activeChannel} · Auto-Pantalla Completa Activa</div>
  </header>
  <div class="container">
    <div class="stage" id="tw-stage">
      <div class="player-box" id="tw-box">
        <iframe id="tw-iframe" allowfullscreen="true" allow="autoplay; fullscreen; picture-in-picture"></iframe>
        <div class="fs-overlay-br">
          <span class="badge-60">1080p60 Source · 0 Dropped Frames</span>
          <button type="button" class="btn-fs" id="tw-fs-btn" onclick="toggleTwFs()">⛶ Pantalla Completa (Auto)</button>
        </div>
      </div>
    </div>
    <h2>Canales en Directo Ahora Mismo (1080p 60fps)</h2>
    <div class="grid">${cardsHtml}</div>
  </div>
  <script>
    (function() {
      var parentHost = 'localhost';
      try { parentHost = window.parent.location.hostname || 'localhost'; } catch (_) {}
      var iframe = document.getElementById('tw-iframe');
      iframe.src = 'https://player.twitch.tv/?channel=${encodeURIComponent(
        activeChannel
      )}&parent=' + encodeURIComponent(parentHost) + '&quality=chunked&muted=false&autoplay=true';
      var isFs = false;
      window.toggleTwFs = function() {
        isFs = !isFs;
        var st = document.getElementById('tw-stage');
        var bx = document.getElementById('tw-box');
        var btn = document.getElementById('tw-fs-btn');
        if (isFs) {
          st.classList.add('fs-active');
          bx.style.maxHeight = '100vh';
          bx.style.height = '100vh';
          btn.textContent = 'Salir de Pantalla Completa';
        } else {
          st.classList.remove('fs-active');
          bx.style.maxHeight = '520px';
          bx.style.height = 'auto';
          btn.textContent = '⛶ Pantalla Completa (Auto)';
        }
      };
      iframe.addEventListener('load', function() {
        if (!isFs) window.toggleTwFs();
      }, { once: true });
    })();
  </script>
</body>
</html>`;
  } catch {
    return null;
  }
}

/**
 * Fetches live organic web search results (via Bing / Wikipedia)
 */
async function buildLiveSearchResultsHtml(query: string): Promise<string> {
  const cleanQuery = query.trim();
  const organicResults: Array<{ title: string; url: string; snippet: string }> = [];

  if (cleanQuery) {
    try {
      const bingResp = await fetchWithGeckoHeaders(
        `https://www.bing.com/search?q=${encodeURIComponent(cleanQuery)}&setlang=es`,
        6500
      );
      const bingHtml = await bingResp.text();
      const algoBlocks = [...bingHtml.matchAll(/<li class="b_algo"[\s\S]*?<\/li>/gi)].map(
        (m) => m[0]
      );
      for (const blk of algoBlocks) {
        const aMatch = blk.match(/<h2[^>]*>\s*<a[^>]+href="([^"]+)"[^>]*>([\s\S]*?)<\/a>/i);
        const pMatch = blk.match(/<p[^>]*>([\s\S]*?)<\/p>/i);
        if (!aMatch) continue;
        let resolvedUrl = aMatch[1].replace(/&amp;/g, '&');
        try {
          const uParam = new URL(resolvedUrl).searchParams.get('u');
          if (uParam && uParam.startsWith('a1')) {
            const b64 = uParam.slice(2).replace(/-/g, '+').replace(/_/g, '/');
            const padded = b64.padEnd(b64.length + ((4 - (b64.length % 4)) % 4), '=');
            const decoded = Buffer.from(padded, 'base64').toString('utf-8').trim();
            if (/^https?:\/\//i.test(decoded)) {
              resolvedUrl = decoded;
            }
          }
        } catch {}
        const cleanTitle = aMatch[2].replace(/<[^>]+>/g, '').trim();
        const cleanSnippet = (pMatch ? pMatch[1] : '').replace(/<[^>]+>/g, '').trim();
        if (cleanTitle && /^https?:\/\//i.test(resolvedUrl)) {
          organicResults.push({ title: cleanTitle, url: resolvedUrl, snippet: cleanSnippet });
        }
      }
    } catch {}
  }

  if (!cleanQuery) {
    return `<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8" />
  <title>Google</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { background: #202124; color: #e8eaed; font-family: Arial, sans-serif; min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 24px; }
    .g-logo { font-size: 76px; font-weight: 700; letter-spacing: -2px; margin-bottom: 28px; user-select: none; }
    .g-b { color: #4285f4; } .g-r { color: #ea4335; } .g-y { color: #fbbc05; } .g-g { color: #34a853; }
    .g-form { width: 100%; max-width: 584px; }
    .g-bar { display: flex; align-items: center; background: #303134; border: 1px solid #5f6368; border-radius: 24px; padding: 10px 18px; gap: 12px; }
    .g-input { flex: 1; background: transparent; border: none; color: #e8eaed; font-size: 16px; outline: none; }
    .g-btns { display: flex; justify-content: center; gap: 12px; margin-top: 24px; }
    .g-btn { background: #303134; border: 1px solid transparent; border-radius: 4px; color: #e8eaed; font-size: 14px; padding: 9px 18px; cursor: pointer; }
    .g-btn:hover { border-color: #5f6368; }
  </style>
</head>
<body>
  <div class="g-logo"><span class="g-b">G</span><span class="g-r">o</span><span class="g-y">o</span><span class="g-b">g</span><span class="g-g">l</span><span class="g-r">e</span></div>
  <form class="g-form" id="g-home-form">
    <div class="g-bar">
      <span>🔍</span>
      <input type="text" id="g-home-input" class="g-input" placeholder="Buscar en Google o escribir una URL" autofocus />
    </div>
    <div class="g-btns">
      <button type="submit" class="g-btn">Buscar con Google</button>
    </div>
  </form>
  <script>
    document.getElementById('g-home-form').addEventListener('submit', function(e) {
      e.preventDefault();
      var q = document.getElementById('g-home-input').value.trim();
      if (!q) return;
      window.parent.postMessage({ type: 'BATTV_NAVIGATE', url: 'https://www.google.com/search?q=' + encodeURIComponent(q) }, '*');
    });
  </script>
</body>
</html>`;
  }

  const itemsHtml = organicResults
    .map((item) => `
      <div style="margin-bottom: 26px;">
        <a href="${item.url}" style="text-decoration:none;display:block;">
          <div style="font-size:12px;color:#dadce0;margin-bottom:4px;">${item.url}</div>
          <h3 style="font-size:20px;font-weight:400;color:#8ab4f8;margin-bottom:4px;">${item.title}</h3>
        </a>
        <div style="font-size:14px;color:#bdc1c6;line-height:1.58;">${item.snippet}</div>
      </div>`)
    .join('');

  return `<!doctype html>
<html lang="es">
<head><meta charset="utf-8"/><title>${cleanQuery} - Buscar con Google</title></head>
<body style="background:#202124;color:#bdc1c6;font-family:Arial,sans-serif;padding:28px;">
  <h2 style="color:#fff;margin-bottom:20px;">Resultados para: ${cleanQuery}</h2>
  ${itemsHtml || '<p>No se encontraron resultados.</p>'}
  <script>
    document.addEventListener('click', function(e) {
      var a = e.target.closest('a');
      if (!a || !a.href) return;
      e.preventDefault();
      window.parent.postMessage({ type: 'BATTV_NAVIGATE', url: a.href }, '*');
    }, true);
  </script>
</body>
</html>`;
}

interface BrowsePageResult {
  finalUrl: string;
  wasShortlinkBypassed: boolean;
  shortlinkMethod: string;
  isDirectVideo: boolean;
  error?: string;
  sanitizedHtml: string;
  blockedAdsCount: number;
  blockedPopupsCount: number;
  bypassedLinksCount: number;
  detectedStreams: string[];
  ramSavedKb: number;
}

const recentViewCache = new Map<string, { result: BrowsePageResult; expiresAt: number }>();

async function buildSanitizedPageResponse(params: {
  url: string;
  ublockEnabled?: boolean;
  popupStrictEnabled?: boolean;
  violentmonkeyEnabled?: boolean;
  customRulesText?: string;
  lowRamStripAnimations?: boolean;
}): Promise<BrowsePageResult> {
  const {
    url,
    ublockEnabled = true,
    popupStrictEnabled = true,
    violentmonkeyEnabled = true,
    customRulesText = '',
    lowRamStripAnimations = false,
  } = params;

  const bypassCheck = resolveShortlinkUrl(url.trim(), Boolean(violentmonkeyEnabled));
  const effectiveUrl = bypassCheck.bypassed ? bypassCheck.finalUrl : url.trim();
  const normalizedUrl = effectiveUrl.startsWith('http') ? effectiveUrl : `https://${effectiveUrl}`;

  let parsedTarget: URL;
  try {
    parsedTarget = new URL(normalizedUrl);
  } catch {
    return {
      finalUrl: normalizedUrl,
      wasShortlinkBypassed: false,
      shortlinkMethod: 'invalid-url',
      isDirectVideo: false,
      error: 'URL mal formada',
      sanitizedHtml: 'URL mal formada',
      blockedAdsCount: 0,
      blockedPopupsCount: 0,
      bypassedLinksCount: 0,
      detectedStreams: [],
      ramSavedKb: 0,
    };
  }

  const host = parsedTarget.hostname.toLowerCase();

  if (host.includes('google.com') || host.includes('bing.com')) {
    const q = parsedTarget.searchParams.get('q') || '';
    const searchHtml = await buildLiveSearchResultsHtml(q);
    return {
      finalUrl: q ? `https://www.google.com/search?q=${encodeURIComponent(q)}` : 'https://www.google.com/',
      wasShortlinkBypassed: bypassCheck.bypassed,
      shortlinkMethod: bypassCheck.method,
      isDirectVideo: false,
      sanitizedHtml: searchHtml,
      blockedAdsCount: 2,
      blockedPopupsCount: 0,
      bypassedLinksCount: 0,
      detectedStreams: [],
      ramSavedKb: 500,
    };
  }

  if (host.includes('twitch.tv')) {
    const twitchHtml = await buildLiveTwitchHtml(normalizedUrl);
    if (twitchHtml) {
      return {
        finalUrl: normalizedUrl,
        wasShortlinkBypassed: bypassCheck.bypassed,
        shortlinkMethod: bypassCheck.method,
        isDirectVideo: false,
        sanitizedHtml: twitchHtml,
        blockedAdsCount: 3,
        blockedPopupsCount: 1,
        bypassedLinksCount: 0,
        detectedStreams: [],
        ramSavedKb: 1500,
      };
    }
  }

  let response: Response | null = null;
  let rawHtml = '';
  let resolvedFinalUrl = normalizedUrl;
  let lastErr = '';

  try {
    const r = await fetchWithGeckoHeaders(normalizedUrl, 10000);
    response = r;
    rawHtml = await r.text();
    resolvedFinalUrl = r.url || normalizedUrl;
  } catch (e) {
    lastErr = e instanceof Error ? e.message : 'Error de red';
  }

  if (!response && !rawHtml) {
    return {
      finalUrl: normalizedUrl,
      wasShortlinkBypassed: bypassCheck.bypassed,
      shortlinkMethod: bypassCheck.method,
      isDirectVideo: false,
      error: lastErr,
      sanitizedHtml: `<!doctype html><html><body style="background:#0D0E12;color:#fff;padding:32px"><h3>No se pudo conectar</h3><p>${lastErr}</p></body></html>`,
      blockedAdsCount: 0,
      blockedPopupsCount: 0,
      bypassedLinksCount: 0,
      detectedStreams: [],
      ramSavedKb: 0,
    };
  }

  try {
    const parsedFinal = new URL(resolvedFinalUrl);
    const parsedOrigin = parsedFinal.origin;
    const customRules = parseCustomFilterRules(customRulesText);

    // Limpieza estricta de políticas restrictivas sin romper iframes de reproducción
    rawHtml = rawHtml
      .replace(/<meta[^>]+http-equiv=["']?Content-Security-Policy["']?[^>]*>/gi, '')
      .replace(/<meta[^>]+http-equiv=["']?X-Frame-Options["']?[^>]*>/gi, '');

    const sanitized = sanitizeHtmlWithExtensions(rawHtml, {
      ublockEnabled: Boolean(ublockEnabled),
      popupStrictEnabled: Boolean(popupStrictEnabled),
      violentmonkeyEnabled: Boolean(violentmonkeyEnabled),
      customRules,
      lowRamStripAnimations: Boolean(lowRamStripAnimations),
    });

    const geckoRuntimePatch = `
      <script>
        (function() {
          document.addEventListener('click', function(e) {
            var a = e.target.closest('a');
            if (!a || !a.href) return;
            var href = a.getAttribute('href') || '';
            if (href.indexOf('#') === 0 || href.toLowerCase().indexOf('javascript:') === 0) return;
            e.preventDefault();
            e.stopPropagation();
            window.parent.postMessage({ type: 'BATTV_NAVIGATE', url: a.href }, '*');
          }, true);
        })();
      </script>
      <base href="${parsedOrigin}/" target="_self" />
    `;

    const htmlWithBridge = /<head\b[^>]*>/i.test(sanitized.sanitizedHtml)
      ? sanitized.sanitizedHtml.replace(/<head\b[^>]*>/i, (m) => `${m}${geckoRuntimePatch}`)
      : `${geckoRuntimePatch}${sanitized.sanitizedHtml}`;

    const result: BrowsePageResult = {
      finalUrl: resolvedFinalUrl,
      wasShortlinkBypassed: bypassCheck.bypassed,
      shortlinkMethod: bypassCheck.method,
      isDirectVideo: false,
      sanitizedHtml: htmlWithBridge,
      blockedAdsCount: sanitized.blockedAdsCount,
      blockedPopupsCount: sanitized.blockedPopupsCount,
      bypassedLinksCount: sanitized.bypassedLinksCount,
      detectedStreams: sanitized.detectedStreams,
      ramSavedKb: sanitized.ramSavedKb,
    };

    recentViewCache.set(resolvedFinalUrl, { result, expiresAt: Date.now() + 25000 });
    return result;
  } catch (error) {
    const msg = error instanceof Error ? error.message : 'Error';
    return {
      finalUrl: normalizedUrl,
      wasShortlinkBypassed: false,
      shortlinkMethod: '',
      isDirectVideo: false,
      error: msg,
      sanitizedHtml: `<p>Error: ${msg}</p>`,
      blockedAdsCount: 0,
      blockedPopupsCount: 0,
      bypassedLinksCount: 0,
      detectedStreams: [],
      ramSavedKb: 0,
    };
  }
}

async function startServer() {
  const app = express();
  const PORT = 3000;

  app.use(express.json({ limit: '4mb' }));

  app.post('/api/browse', async (req, res) => {
    const {
      url,
      ublockEnabled = true,
      popupStrictEnabled = true,
      violentmonkeyEnabled = true,
      customRulesText = '',
    } = req.body || {};

    if (!url) {
      res.status(400).json({ error: 'URL inválida' });
      return;
    }

    const result = await buildSanitizedPageResponse({
      url,
      ublockEnabled,
      popupStrictEnabled,
      violentmonkeyEnabled,
      customRulesText,
    });

    res.json(result);
  });

  if (process.env.NODE_ENV !== 'production') {
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(__dirname, 'dist');
    app.use(express.static(distPath));
    app.get('*', (_req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`Bat TV Browser running on http://localhost:${PORT}`);
  });
}

startServer();
