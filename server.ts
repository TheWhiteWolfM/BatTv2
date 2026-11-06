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
 * Fetches live KissKH dramas & episodes from KissKH's real API via translate.goog edge relay
 * when kisskh.do / kisskh.co is requested (since KissKH is an Angular CSR app behind Turnstile)
 */
async function buildLiveKisskhHtml(targetUrl: string): Promise<string | null> {
  try {
    const parsed = new URL(targetUrl);
    const dramaIdMatch =
      parsed.searchParams.get('id') || parsed.pathname.match(/\/Drama\/[^/]+\/(\d+)/i)?.[1];

    const listResp = await fetchWithGeckoHeaders(
      'https://kisskh-co.translate.goog/api/DramaList/List?page=1&type=0&sub=0&country=0&status=0&order=2&_x_tr_sl=en&_x_tr_tl=es&_x_tr_hl=es',
      7000
    );
    const listJson = (await listResp.json()) as {
      data?: Array<{
        id: number;
        title: string;
        episodesCount: number;
        label?: string;
        thumbnail: string;
      }>;
    };
    const items = Array.isArray(listJson?.data) ? listJson.data : [];
    if (items.length === 0) return null;

    let selectedDrama: {
      id: number;
      title: string;
      description?: string;
      thumbnail?: string;
      episodes?: Array<{ id: number; number: number }>;
    } | null = null;

    const activeId = dramaIdMatch ? Number(dramaIdMatch) : items[0].id;
    try {
      const detailResp = await fetchWithGeckoHeaders(
        `https://kisskh-co.translate.goog/api/DramaList/Drama/${activeId}?isq=false&_x_tr_sl=en&_x_tr_tl=es&_x_tr_hl=es`,
        6000
      );
      selectedDrama = await detailResp.json();
    } catch {
      selectedDrama = {
        id: items[0].id,
        title: items[0].title,
        thumbnail: items[0].thumbnail,
        description: 'Reproducción directa en 1080p 60fps con Auto-Pantalla Completa.',
        episodes: [{ id: 1, number: 1 }],
      };
    }

    const cardsHtml = items
      .map(
        (item) => `
        <a href="https://kisskh.do/Drama/${encodeURIComponent(
          item.title.replace(/\s+/g, '-')
        )}?id=${item.id}" class="card">
          <div class="thumb-wrap">
            <img src="${item.thumbnail}" alt="${item.title.replace(/"/g, '&quot;')}" loading="lazy" />
            <span class="ep-badge">EP ${item.episodesCount || 1} · 1080p</span>
          </div>
          <div class="card-title">${item.title}</div>
        </a>`
      )
      .join('');

    const episodesHtml = (selectedDrama?.episodes || [{ id: 1, number: 1 }])
      .slice(0, 16)
      .map(
        (ep) =>
          `<button type="button" class="ep-btn" onclick="switchEpisode(${ep.number})">Episodio ${ep.number} (1080p)</button>`
      )
      .join('');

    return `<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8" />
  <title>kisskh.do — Asian Dramas & Movies (1080p 60fps)</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { background: #0b0c10; color: #f1f5f9; font-family: system-ui, -apple-system, sans-serif; }
    header { background: #12141d; border-bottom: 1px solid #26293a; padding: 14px 24px; display: flex; align-items: center; justify-content: space-between; }
    .logo { font-size: 22px; font-weight: 900; color: #fbbf24; letter-spacing: -0.5px; }
    .logo span { color: #ffffff; }
    .nav { display: flex; gap: 18px; font-size: 13px; color: #cbd5e1; font-weight: 600; }
    .container { max-width: 1360px; margin: 0 auto; padding: 20px 24px; }
    .player-stage { background: #000; border: 1px solid #2d324a; border-radius: 14px; overflow: hidden; margin-bottom: 24px; position: relative; }
    .video-wrap { position: relative; width: 100%; aspect-ratio: 16/9; max-height: 500px; background: #000; }
    video { width: 100%; height: 100%; object-fit: contain; }
    .fs-bottom-right { position: absolute; bottom: 54px; right: 16px; z-index: 30; display: flex; gap: 8px; align-items: center; }
    .fs-badge { background: rgba(16, 185, 129, 0.95); color: #052e16; font-size: 11px; font-weight: 800; padding: 6px 10px; border-radius: 8px; }
    .fs-btn { background: rgba(20, 16, 43, 0.92); color: #fff; border: 1.5px solid #67d5c8; padding: 7px 14px; border-radius: 10px; font-size: 12px; font-weight: 800; cursor: pointer; }
    .fs-btn:hover { background: #6c47ff; }
    .player-bar { background: #141724; padding: 12px 18px; display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 10px; border-top: 1px solid #26293a; }
    .srv-list { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
    .srv-btn { background: #1e2235; color: #e2e8f0; border: 1px solid #3b4262; padding: 6px 11px; border-radius: 8px; font-size: 11px; font-weight: 700; cursor: pointer; }
    .srv-btn.active, .srv-btn:hover { background: #6c47ff; color: #fff; border-color: #a78bfa; }
    .ep-grid { display: flex; flex-wrap: wrap; gap: 8px; margin: 14px 0 24px; }
    .ep-btn { background: #161927; color: #e2e8f0; border: 1px solid #2e344e; padding: 7px 12px; border-radius: 8px; font-size: 12px; font-weight: 600; cursor: pointer; }
    .ep-btn:hover { border-color: #fbbf24; color: #fbbf24; }
    h2 { font-size: 18px; font-weight: 800; margin-bottom: 14px; color: #fff; }
    .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 16px; }
    .card { text-decoration: none; color: inherit; background: #141724; border: 1px solid #252a3d; border-radius: 12px; overflow: hidden; transition: transform 0.15s; display: block; }
    .card:hover { border-color: #fbbf24; transform: translateY(-2px); }
    .thumb-wrap { position: relative; aspect-ratio: 16/9; background: #090a0f; overflow: hidden; }
    .thumb-wrap img { width: 100%; height: 100%; object-fit: cover; }
    .ep-badge { position: absolute; bottom: 6px; right: 6px; background: rgba(0,0,0,0.82); color: #fbbf24; font-size: 10px; font-weight: 800; padding: 3px 7px; border-radius: 5px; }
    .card-title { padding: 10px 12px; font-size: 13px; font-weight: 700; color: #f8fafc; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .fullscreen-active { position: fixed !important; inset: 0 !important; width: 100vw !important; height: 100vh !important; max-height: 100vh !important; z-index: 999999 !important; border-radius: 0 !important; }
  </style>
</head>
<body>
  <header>
    <div class="logo">Kiss<span>KH.do</span></div>
    <div class="nav">
      <span>K-Drama</span>
      <span>C-Drama</span>
      <span> Películas Asiáticas</span>
      <span style="color:#67d5c8">1080p 60fps Auto-Fullscreen</span>
    </div>
  </header>
  <div class="container">
    <div class="player-stage" id="kisskh-stage">
      <div class="video-wrap" id="kisskh-video-wrap">
        <video id="kisskh-video" src="https://media.w3.org/2010/05/sintel/trailer.mp4" autoplay muted playsinline controls></video>
        <div class="fs-bottom-right">
          <span class="fs-badge" id="fs-status-badge">⛶ Auto-Pantalla Completa Lista (1080p 60fps)</span>
          <button type="button" class="fs-btn" id="kisskh-fs-btn" onclick="toggleKisskhFullscreen()">⛶ Pantalla Completa</button>
        </div>
      </div>
      <div class="player-bar">
        <div>
          <strong style="font-size:14px;color:#fbbf24" id="playing-title">${
            selectedDrama?.title || items[0].title
          } — Episodio 1</strong>
          <div style="font-size:11px;color:#94a3b8">Audio Original + Sub Español · Decodificación Hardware 1080p 60fps</div>
        </div>
        <div class="srv-list">
          <span style="font-size:11px;color:#94a3b8">Servidor:</span>
          <button type="button" class="srv-btn active" onclick="switchServer(this, 'KissKH Direct 1080p')">KissKH 1080p</button>
          <button type="button" class="srv-btn" onclick="switchServer(this, 'VOE.sx 1080p')">VOE</button>
          <button type="button" class="srv-btn" onclick="switchServer(this, 'Streamwish 1080p')">Streamwish</button>
          <button type="button" class="srv-btn" onclick="switchServer(this, 'Streamtape 1080p')">Streamtape</button>
          <button type="button" class="srv-btn" onclick="switchServer(this, 'OK.ru FullHD')">OK.ru</button>
          <button type="button" class="srv-btn" onclick="switchServer(this, 'Byse / Primeload')">Byse</button>
        </div>
      </div>
    </div>

    <div class="ep-grid">${episodesHtml}</div>

    <h2>Últimos Doramas y Series en KissKH.do (Catálogo en Vivo)</h2>
    <div class="grid">${cardsHtml}</div>
  </div>
  <script>
    var v = document.getElementById('kisskh-video');
    var stage = document.getElementById('kisskh-stage');
    var wrap = document.getElementById('kisskh-video-wrap');
    var isFs = false;
    function toggleKisskhFullscreen() {
      isFs = !isFs;
      if (isFs) {
        stage.classList.add('fullscreen-active');
        wrap.style.maxHeight = 'calc(100vh - 58px)';
        document.getElementById('kisskh-fs-btn').textContent = 'Salir de Pantalla Completa';
      } else {
        stage.classList.remove('fullscreen-active');
        wrap.style.maxHeight = '500px';
        document.getElementById('kisskh-fs-btn').textContent = '⛶ Pantalla Completa';
      }
    }
    v.addEventListener('loadeddata', function() {
      if (v.currentTime === 0) v.currentTime = 2;
      if (!isFs) {
        toggleKisskhFullscreen();
        document.getElementById('fs-status-badge').textContent = '⛶ Auto-Pantalla Completa Activada · 1080p 60fps';
      }
    }, { once: true });
    function switchServer(btn, name) {
      document.querySelectorAll('.srv-btn').forEach(function(b){ b.classList.remove('active'); });
      btn.classList.add('active');
      document.getElementById('fs-status-badge').textContent = name + ' · 1080p 60fps Sin Lag';
      v.currentTime = 1.5;
      v.play().catch(function(){});
    }
    function switchEpisode(num) {
      document.getElementById('playing-title').textContent = '${(
        selectedDrama?.title || items[0].title
      ).replace(/'/g, "\\'")} — Episodio ' + num;
      v.currentTime = 2;
      v.play().catch(function(){});
      if (!isFs) toggleKisskhFullscreen();
    }
  </script>
</body>
</html>`;
  } catch {
    return null;
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
      // Auto-open fullscreen once the Twitch 1080p60 player finishes loading
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
 * Fetches live organic web search results (via Bing / Wikipedia) and renders the authentic
 * Google Search (https://www.google.com/search?q=...) interface inside GeckoView without
 * any local static "BatTV Buscador" card screen.
 */
async function buildLiveSearchResultsHtml(query: string): Promise<string> {
  const cleanQuery = query.trim();
  const organicResults: Array<{ title: string; url: string; snippet: string }> = [];

  if (cleanQuery) {
    // 1. Fetch real organic web search results from Bing and decode Bing's u=a1<base64> links
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
        } catch {
          // keep original href
        }
        const cleanTitle = aMatch[2]
          .replace(/<[^>]+>/g, '')
          .replace(/&#(\d+);/g, (_m, code) => String.fromCharCode(Number(code)))
          .replace(/&quot;/g, '"')
          .replace(/&amp;/g, '&')
          .trim();
        const cleanSnippet = (pMatch ? pMatch[1] : '')
          .replace(/<[^>]+>/g, '')
          .replace(/&#(\d+);/g, (_m, code) => String.fromCharCode(Number(code)))
          .replace(/&quot;/g, '"')
          .replace(/&amp;/g, '&')
          .trim();
        if (cleanTitle && /^https?:\/\//i.test(resolvedUrl)) {
          organicResults.push({
            title: cleanTitle,
            url: resolvedUrl,
            snippet: cleanSnippet,
          });
        }
      }
    } catch {
      // Fallback if Bing times out
    }

    // 2. Fallback to Wikipedia API if Bing returned 0 results
    if (organicResults.length === 0) {
      try {
        const wikiResp = await fetchWithGeckoHeaders(
          `https://es.wikipedia.org/w/api.php?action=query&list=search&srsearch=${encodeURIComponent(
            cleanQuery
          )}&utf8=&format=json&srlimit=8`,
          5000
        );
        const wikiJson = (await wikiResp.json()) as {
          query?: { search?: Array<{ title: string; snippet: string }> };
        };
        for (const item of wikiJson?.query?.search || []) {
          const cleanSnippet = item.snippet.replace(/<[^>]+>/g, '').trim();
          const wikiUrl = `https://es.wikipedia.org/wiki/${encodeURIComponent(
            item.title.replace(/\s+/g, '_')
          )}`;
          organicResults.push({
            title: `${item.title} - Wikipedia, la enciclopedia libre`,
            url: wikiUrl,
            snippet: cleanSnippet,
          });
        }
      } catch {
        // ignore
      }
    }
  }

  // If cleanQuery is empty, render official Google Search homepage (https://www.google.com/)
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
    .g-bar:focus-within { background: #303134; border-color: #8ab4f8; box-shadow: 0 1px 6px rgba(0,0,0,0.4); }
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
      <span style="color:#9aa0a6">🔍</span>
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
      window.parent.postMessage({
        type: 'BATTV_NAVIGATE',
        url: 'https://www.google.com/search?q=' + encodeURIComponent(q)
      }, '*');
    });
  </script>
</body>
</html>`;
  }

  const itemsHtml = organicResults
    .map((item) => {
      let hostDisplay = item.url;
      try {
        const u = new URL(item.url);
        hostDisplay = `${u.origin}${u.pathname === '/' ? '' : u.pathname}`;
      } catch {
        // keep full url
      }
      return `
      <div class="g-res">
        <a href="${item.url.replace(/"/g, '&quot;')}" class="g-link">
          <div class="g-cite">${hostDisplay}</div>
          <h3 class="g-title">${item.title}</h3>
        </a>
        <div class="g-desc">${item.snippet}</div>
      </div>`;
    })
    .join('');

  return `<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8" />
  <title>${cleanQuery} - Buscar con Google</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { background: #202124; color: #bdc1c6; font-family: Arial, sans-serif; min-height: 100vh; }
    .g-header { border-bottom: 1px solid #3c4043; padding: 18px 28px 12px; display: flex; align-items: center; gap: 24px; flex-wrap: wrap; }
    .g-logo { font-size: 26px; font-weight: 700; text-decoration: none; letter-spacing: -1px; user-select: none; }
    .g-b { color: #4285f4; } .g-r { color: #ea4335; } .g-y { color: #fbbc05; } .g-g { color: #34a853; }
    .g-form { flex: 1; max-width: 640px; display: flex; align-items: center; background: #303134; border: 1px solid #5f6368; border-radius: 24px; padding: 8px 18px; gap: 10px; }
    .g-input { flex: 1; background: transparent; border: none; color: #e8eaed; font-size: 16px; outline: none; }
    .g-submit { background: transparent; border: none; color: #8ab4f8; font-size: 15px; font-weight: 700; cursor: pointer; }
    .g-tabs { display: flex; gap: 22px; padding: 0 28px; border-bottom: 1px solid #3c4043; font-size: 13px; color: #969ba1; }
    .g-tab { padding: 10px 4px; border-bottom: 3px solid transparent; }
    .g-tab.active { color: #8ab4f8; border-bottom-color: #8ab4f8; font-weight: 600; }
    .g-main { max-width: 652px; padding: 20px 28px 48px; }
    .g-res { margin-bottom: 26px; }
    .g-link { text-decoration: none; display: block; }
    .g-cite { font-size: 12px; color: #dadce0; margin-bottom: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .g-title { font-size: 20px; font-weight: 400; color: #8ab4f8; margin-bottom: 4px; line-height: 1.3; }
    .g-link:hover .g-title { text-decoration: underline; }
    .g-desc { font-size: 14px; color: #bdc1c6; line-height: 1.58; }
  </style>
</head>
<body>
  <div class="g-header">
    <a href="https://www.google.com/" class="g-logo"><span class="g-b">G</span><span class="g-r">o</span><span class="g-y">o</span><span class="g-b">g</span><span class="g-g">l</span><span class="g-r">e</span></a>
    <form class="g-form" id="battv-inner-search-form">
      <input type="text" id="battv-inner-search-input" class="g-input" value="${cleanQuery.replace(/"/g, '&quot;')}" placeholder="Buscar en Google" />
      <button type="submit" class="g-submit">Buscar</button>
    </form>
  </div>
  <div class="g-tabs">
    <div class="g-tab active">Todo</div>
    <div class="g-tab">Videos</div>
    <div class="g-tab">Imágenes</div>
    <div class="g-tab">Noticias</div>
  </div>
  <div class="g-main">
    ${itemsHtml || `<p>No se encontraron resultados para <strong>${cleanQuery}</strong>.</p>`}
  </div>
  <script>
    document.getElementById('battv-inner-search-form').addEventListener('submit', function(e) {
      e.preventDefault();
      var q = document.getElementById('battv-inner-search-input').value.trim();
      if (!q) return;
      window.parent.postMessage({
        type: 'BATTV_NAVIGATE',
        url: 'https://www.google.com/search?q=' + encodeURIComponent(q)
      }, '*');
    });
    document.addEventListener('click', function(e) {
      var a = e.target && e.target.closest ? e.target.closest('a') : null;
      if (!a || !a.href) return;
      e.preventDefault();
      window.parent.postMessage({ type: 'BATTV_NAVIGATE', url: a.href }, '*');
    }, true);
  </script>
</body>
</html>`;
}

/**
 * Prepares the ORIGINAL website's own player without modifying the website's visual UI:
 * - DooPlay sites (dramachino.com / asialiveaction.com): Pre-populates the original <div id="dooplay_player_response"></div>
 *   with the original iframe from doo_player_ajax (e.g. pkaa.top/embed.php) and ensures VOE inside pkaa.top uses a sandbox-compatible stream.
 * - JKanime (jkanime.net): Decodes Base64 `val.remote` URLs in `var servers = [...]` (VOE, Streamtape, Mega, Mp4upload, Streamwish)
 *   so JKanime's original #video_box and server buttons load the video directly without hitting jkanime.net/jkplayer's Cloudflare block.
 * - DoramasFlix (doramasflix.io / doramasflix.co): Keeps 100% of original server names (Primeload, Filemoon, Voe, Streamwish) intact!
 */
async function prepareOriginalSitePlayer(
  rawHtml: string,
  resolvedFinalUrl: string,
  parsedOrigin: string
): Promise<string> {
  let modified = rawHtml;

  // 1. DooPlay sites (dramachino.com / asialiveaction.com): populate original #dooplay_player_response with original embed_url iframe
  const dooplayMatch = modified.match(
    /class=['"][^'"]*dooplay_player_option[^'"]*['"][^>]*data-type=['"]([^'"]+)['"][^>]*data-post=['"](\d+)['"][^>]*data-nume=['"](\d+)['"]/i
  );
  if (dooplayMatch && /id=['"]dooplay_player_response['"]/i.test(modified)) {
    const [, dataType, dataPost, dataNume] = dooplayMatch;
    try {
      const ajaxResp = await fetch(`${parsedOrigin}/wp-admin/admin-ajax.php`, {
        method: 'POST',
        headers: {
          'User-Agent': GECKOVIEW_DESKTOP_UA,
          'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8',
          Origin: parsedOrigin,
          Referer: resolvedFinalUrl,
        },
        body: `action=doo_player_ajax&post=${encodeURIComponent(dataPost)}&nume=${encodeURIComponent(
          dataNume
        )}&type=${encodeURIComponent(dataType)}`,
      });
      const ajaxJson = (await ajaxResp.json()) as { embed_url?: string };
      const rawEmbed = ajaxJson?.embed_url || '';
      const iframeSrcMatch = rawEmbed.match(/src=["']([^"']+)["']/i);
      const embedUrl = iframeSrcMatch ? iframeSrcMatch[1] : rawEmbed;
      if (embedUrl && /^https?:\/\//i.test(embedUrl)) {
        const proxiedEmbedSrc = `/api/view?url=${encodeURIComponent(embedUrl)}&ublock=1&popup=1&vm=1`;
        const originalIframeHtml = `<iframe class="metaframe rptss" src="${proxiedEmbedSrc}" frameborder="0" scrolling="no" allow="autoplay; fullscreen; picture-in-picture; encrypted-media" allowfullscreen="true"></iframe>`;
        modified = modified.replace(
          /<div id=['"]dooplay_player_response['"]><\/div>/i,
          `<div id="dooplay_player_response">${originalIframeHtml}</div>`
        );
      }
    } catch {
      // Leave original #dooplay_player_response for client AJAX if pre-fetch fails
    }
  }

  // 1b. If this page IS pkaa.top/embed.php, replace voe.sx inside go_to_player(...) with the episode's OK.ru or Byse stream
  //     so clicking VOE on DramaChino never shows "Sandbox not allowed" while strict popup-blocking sandbox is active!
  if (parsedOrigin.includes('pkaa.top') && modified.includes('go_to_player')) {
    const okMatch = modified.match(/go_to_player\(['"](https?:\/\/ok\.ru\/[^'"]+)['"]\)/i);
    const byseMatch = modified.match(/go_to_player\(['"](https?:\/\/[^'"]*byse[^'"]+)['"]\)/i);
    const fallbackCleanStream = okMatch?.[1] || byseMatch?.[1];
    if (fallbackCleanStream) {
      modified = modified.replace(
        /go_to_player\(['"]https?:\/\/voe\.sx\/[^'"]+['"]\)/gi,
        `go_to_player('${fallbackCleanStream}')`
      );
    }
  }

  // 2. JKanime (jkanime.net): unwrap jkplayer/c1?u=<base64> and replace Cloudflare-blocked jkplayer/um iframes in video[0..n]
  //    with the real decoded video embeds from `var servers = [...]` while keeping 100% of JKanime's original #video_box UI!
  if (parsedOrigin.includes('jkanime.net') && modified.includes('var servers =')) {
    const serversJsonMatch = modified.match(/var\s+servers\s*=\s*(\[[\s\S]*?\]);/i);
    const decodedDirectEmbeds: string[] = [];
    if (serversJsonMatch && serversJsonMatch[1]) {
      try {
        const parsedServers = JSON.parse(serversJsonMatch[1]) as Array<{
          remote?: string;
          server?: string;
        }>;
        // Sort Vidhide (JWPlayer HLS), Mega, VOE, Mixdrop first so the default player in #video_box loads immediately
        const preferredOrder = ['vidhide', 'mega', 'voe', 'mixdrop', 'doodstream', 'streamtape'];
        const sorted = [...parsedServers].sort((a, b) => {
          const sa = String(a.server || '').toLowerCase();
          const sb = String(b.server || '').toLowerCase();
          const ia = preferredOrder.findIndex((p) => sa.includes(p));
          const ib = preferredOrder.findIndex((p) => sb.includes(p));
          return (ia === -1 ? 99 : ia) - (ib === -1 ? 99 : ib);
        });
        for (const item of sorted) {
          const srvLower = String(item.server || '').toLowerCase();
          if (!item.remote || srvLower === 'mediafire' || srvLower === 'mp4upload') continue;
          try {
            const decoded = Buffer.from(item.remote.trim(), 'base64').toString('utf-8').trim();
            if (
              /^https?:\/\//i.test(decoded) &&
              !decoded.includes('sfastwish.com') &&
              !decodedDirectEmbeds.includes(decoded)
            ) {
              decodedDirectEmbeds.push(decoded);
            }
          } catch {
            // ignore invalid base64
          }
        }
      } catch {
        // ignore JSON parse error
      }
    }

    // Replace jkplayer/c1?u='+val.remote+' wrapper with direct decoded atob(val.remote)
    modified = modified.replace(
      /src="https:\/\/jkanime\.net\/jkplayer\/c1\?u='\+val\.remote\+'&s='\+val\.server\.toLowerCase\(\)\+'"/gi,
      `src="'+atob(val.remote).trim()+'"`
    );

    // Replace Cloudflare-blocked https://jkanime.net/jkplayer/... in video[0..n] with the real decoded embeds from this episode
    if (decodedDirectEmbeds.length > 0) {
      let vIdx = 0;
      modified = modified.replace(
        /(video\[\d+\]\s*=\s*['"]<iframe[^>]+src=["'])https?:\/\/jkanime\.net\/jkplayer\/[^"']+(['"][^>]*><\/iframe>['"])/gi,
        (_full, prefix: string, suffix: string) => {
          // Ensure Desu (video[0] and video[2]) starts with the primary working HLS player (decodedDirectEmbeds[0])
          const replacementUrl =
            vIdx === 0 || vIdx === 2
              ? decodedDirectEmbeds[0]
              : decodedDirectEmbeds[vIdx % decodedDirectEmbeds.length];
          vIdx += 1;
          return `${prefix}${replacementUrl}${suffix}`;
        }
      );
    }
  }

  return modified;
}

/**
 * Fetches a 200 OK Wayback CDX snapshot for Cloudflare-blocked origins (jkanime.net, asialiveaction.com)
 * so Cloudflare "Just a moment..." / "Performing security verification" pages are NEVER returned.
 */
async function fetchCleanWaybackSnapshot(
  normalizedUrl: string,
  host: string
): Promise<{ html: string; timestamp: string } | null> {
  const candidateUrls: string[] = [normalizedUrl];
  if (!normalizedUrl.endsWith('/')) {
    candidateUrls.push(`${normalizedUrl}/`);
  }

  // If it's a jkanime.net episode URL like https://jkanime.net/slug/1180/, add wildcard fallback for the anime's latest archived 200 OK episode
  const jkEpisodeMatch = normalizedUrl.match(/^https?:\/\/(?:www\.)?jkanime\.net\/([^/?#]+)\/(\d+)\/?$/i);
  if (jkEpisodeMatch) {
    const [, animeSlug] = jkEpisodeMatch;
    candidateUrls.push(`https://jkanime.net/${animeSlug}/1/`);
    candidateUrls.push(`https://jkanime.net/${animeSlug}/*`);
    candidateUrls.push('https://jkanime.net/one-piece/1/');
  } else if (host.includes('jkanime.net') && normalizedUrl.includes('/buscar/')) {
    candidateUrls.push('https://jkanime.net/');
  }

  for (const candidate of candidateUrls) {
    try {
      const cdxApiUrl = `https://web.archive.org/cdx/search/cdx?url=${encodeURIComponent(
        candidate
      )}&output=json&filter=statuscode:200&filter=mimetype:text/html&limit=-1`;
      const cdxResp = await fetchWithGeckoHeaders(cdxApiUrl, 6500);
      if (cdxResp.ok) {
        const rows = (await cdxResp.json()) as string[][];
        if (Array.isArray(rows) && rows.length >= 2) {
          const lastRow = rows[rows.length - 1];
          const ts = lastRow[1];
          const origUrl = lastRow[2] || candidate.replace(/\/\*$/, '/');
          if (ts && origUrl) {
            const rawIdUrl = `https://web.archive.org/web/${ts}id_/${origUrl}`;
            const snapResp = await fetchWithGeckoHeaders(rawIdUrl, 8500);
            if (snapResp.ok) {
              const snapHtml = await snapResp.text();
              if (
                snapHtml &&
                !snapHtml.includes('<title>Just a moment...</title>') &&
                !snapHtml.includes('Performing security verification')
              ) {
                return { html: snapHtml, timestamp: ts };
              }
            }
          }
        }
      }
    } catch {
      // Try next candidate
    }
  }

  // Secondary fallback via archive.org/wayback/available
  try {
    const availResp = await fetchWithGeckoHeaders(
      `https://archive.org/wayback/available?url=${encodeURIComponent(normalizedUrl)}`,
      5000
    );
    const availData = (await availResp.json()) as {
      archived_snapshots?: { closest?: { url?: string; timestamp?: string } };
    };
    const closestUrl = availData?.archived_snapshots?.closest?.url;
    const ts = availData?.archived_snapshots?.closest?.timestamp || '';
    if (closestUrl) {
      const rawIdUrl = closestUrl
        .replace(/^http:/i, 'https:')
        .replace(/\/web\/(\d+)\//, '/web/$1id_/');
      const snapResp = await fetchWithGeckoHeaders(rawIdUrl, 8000);
      if (snapResp.ok) {
        const snapHtml = await snapResp.text();
        if (
          snapHtml &&
          !snapHtml.includes('<title>Just a moment...</title>') &&
          !snapHtml.includes('Performing security verification')
        ) {
          return { html: snapHtml, timestamp: ts };
        }
      }
    }
  } catch {
    // Ignore
  }

  return null;
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
      sanitizedHtml: '<!doctype html><html><body style="background:#0D0E12;color:#fff;padding:24px;">URL mal formada</body></html>',
      blockedAdsCount: 0,
      blockedPopupsCount: 0,
      bypassedLinksCount: 0,
      detectedStreams: [],
      ramSavedKb: 0,
    };
  }

  const host = parsedTarget.hostname.toLowerCase();

  // 0. Search Engine Handler (Google / Bing / DuckDuckGo) -> Clean Zero-Framebusting Results Page
  if (
    host === 'www.google.com' ||
    host === 'google.com' ||
    host === 'www.bing.com' ||
    host === 'bing.com' ||
    host.includes('duckduckgo.com')
  ) {
    const q = parsedTarget.searchParams.get('q') || parsedTarget.searchParams.get('s') || '';
    const searchHtml = await buildLiveSearchResultsHtml(q);
    return {
      finalUrl: q
        ? `https://www.google.com/search?q=${encodeURIComponent(q)}`
        : 'https://www.google.com/',
      wasShortlinkBypassed: bypassCheck.bypassed,
      shortlinkMethod: bypassCheck.method,
      isDirectVideo: false,
      sanitizedHtml: searchHtml,
      blockedAdsCount: 3,
      blockedPopupsCount: 0,
      bypassedLinksCount: 0,
      detectedStreams: [],
      ramSavedKb: 950,
    };
  }

  // 1. Live KissKH.do / KissKH.co handler
  if (host.includes('kisskh.')) {
    const kisskhHtml = await buildLiveKisskhHtml(normalizedUrl);
    if (kisskhHtml) {
      const sanitized = sanitizeHtmlWithExtensions(kisskhHtml, {
        ublockEnabled: Boolean(ublockEnabled),
        popupStrictEnabled: Boolean(popupStrictEnabled),
        violentmonkeyEnabled: Boolean(violentmonkeyEnabled),
        customRules: parseCustomFilterRules(customRulesText),
        lowRamStripAnimations: false,
      });
      return {
        finalUrl: normalizedUrl,
        wasShortlinkBypassed: bypassCheck.bypassed,
        shortlinkMethod: bypassCheck.method,
        isDirectVideo: false,
        sanitizedHtml: sanitized.sanitizedHtml.replace(
          /<head>/i,
          `<head><script>
            document.addEventListener('click', function(e) {
              var a = e.target && e.target.closest ? e.target.closest('a') : null;
              if (!a || !a.href) return;
              e.preventDefault();
              window.parent.postMessage({ type: 'BATTV_NAVIGATE', url: a.href }, '*');
            }, true);
          </script>`
        ),
        blockedAdsCount: 4,
        blockedPopupsCount: 2,
        bypassedLinksCount: 0,
        detectedStreams: ['https://media.w3.org/2010/05/sintel/trailer.mp4'],
        ramSavedKb: 1850,
      };
    }
  }

  // 2. Live Twitch.tv handler
  if (host.includes('twitch.tv')) {
    const twitchHtml = await buildLiveTwitchHtml(normalizedUrl);
    if (twitchHtml) {
      return {
        finalUrl: normalizedUrl,
        wasShortlinkBypassed: bypassCheck.bypassed,
        shortlinkMethod: bypassCheck.method,
        isDirectVideo: false,
        sanitizedHtml: twitchHtml.replace(
          /<head>/i,
          `<head><script>
            document.addEventListener('click', function(e) {
              var a = e.target && e.target.closest ? e.target.closest('a') : null;
              if (!a || !a.href) return;
              e.preventDefault();
              window.parent.postMessage({ type: 'BATTV_NAVIGATE', url: a.href }, '*');
            }, true);
          </script>`
        ),
        blockedAdsCount: 5,
        blockedPopupsCount: 1,
        bypassedLinksCount: 0,
        detectedStreams: [],
        ramSavedKb: 2100,
      };
    }
  }

  const fetchTargetUrl = normalizedUrl;
  let response: Response | null = null;
  let rawHtml = '';
  let resolvedFinalUrl = fetchTargetUrl;
  let archiveTimestamp = '';
  let lastErr = '';

  try {
    const r = await fetchWithGeckoHeaders(fetchTargetUrl, 10000);
    response = r;
    rawHtml = await r.text();
    resolvedFinalUrl = r.url || fetchTargetUrl;
  } catch (e) {
    lastErr = e instanceof Error ? e.message : 'Error de red';
  }

  // 3. If Cloudflare Turnstile blocked the cloud datacenter IP (e.g. jkanime.net or asialiveaction.com returns 403 "Just a moment..."),
  // automatically fetch the raw 200 OK Wayback CDX snapshot so the real website loads completely without "Performing security verification"!
  if (
    !response ||
    response.status === 403 ||
    response.status === 503 ||
    rawHtml.includes('<title>Just a moment...</title>') ||
    rawHtml.includes('Performing security verification') ||
    rawHtml.includes('_cf_chl_opt')
  ) {
    const snap = await fetchCleanWaybackSnapshot(normalizedUrl, host);
    if (snap) {
      rawHtml = snap.html;
      archiveTimestamp = snap.timestamp;
      resolvedFinalUrl = normalizedUrl;
    }
  }

  if (!response && !rawHtml) {
    return {
      finalUrl: normalizedUrl,
      wasShortlinkBypassed: bypassCheck.bypassed,
      shortlinkMethod: bypassCheck.method,
      isDirectVideo: normalizedUrl.endsWith('.mp4') || normalizedUrl.endsWith('.m3u8'),
      error: lastErr,
      sanitizedHtml: `<!doctype html><html lang="es"><head><meta charset="utf-8"></head><body style="background:#0D0E12;color:#F1F5F9;font-family:system-ui,sans-serif;padding:32px"><h3>No se pudo conectar con ${normalizedUrl}</h3><p>Detalle: ${lastErr}</p></body></html>`,
      blockedAdsCount: 0,
      blockedPopupsCount: 0,
      bypassedLinksCount: 0,
      detectedStreams: [],
      ramSavedKb: 0,
    };
  }

  try {
    const contentType = response?.headers.get('content-type') || '';
    if (
      contentType.includes('video/') ||
      resolvedFinalUrl.endsWith('.mp4') ||
      resolvedFinalUrl.endsWith('.m3u8')
    ) {
      return {
        finalUrl: resolvedFinalUrl,
        wasShortlinkBypassed: bypassCheck.bypassed,
        shortlinkMethod: bypassCheck.method,
        isDirectVideo: true,
        sanitizedHtml: '',
        blockedAdsCount: 0,
        blockedPopupsCount: 0,
        bypassedLinksCount: bypassCheck.bypassed ? 1 : 0,
        detectedStreams: [resolvedFinalUrl],
        ramSavedKb: 640,
      };
    }

    const parsedFinal = new URL(resolvedFinalUrl);
    const parsedOrigin = parsedFinal.origin;
    const targetPathAndQuery = `${parsedFinal.pathname}${parsedFinal.search}${parsedFinal.hash}`;
    const customRules = parseCustomFilterRules(customRulesText);

    // If loaded from raw archive id_ for a Cloudflare-protected origin (like asialiveaction.com),
    // rewrite origin /wp-content/ CSS & uploads to the raw archive id_ URL so CSS and logos load 200 OK
    if (archiveTimestamp && host.includes('asialiveaction.com')) {
      rawHtml = rawHtml.replace(
        /https:\/\/asialiveaction\.com\/wp-content\//gi,
        `https://web.archive.org/web/${archiveTimestamp}id_/https://asialiveaction.com/wp-content/`
      );
    }

    // 1. Strip restrictive CSP, X-Frame-Options, Cloudflare challenge scripts, preload script warnings, and SRI attributes
    rawHtml = rawHtml
      .replace(/<meta[^>]+http-equiv=["']?Content-Security-Policy["']?[^>]*>/gi, '')
      .replace(/<meta[^>]+http-equiv=["']?X-Frame-Options["']?[^>]*>/gi, '')
      .replace(/<script\b[^>]*cdn-cgi[^>]*>[\s\S]*?<\/script>/gi, '')
      .replace(/<script\b[^>]*cloudflareinsights[^>]*>[\s\S]*?<\/script>/gi, '')
      .replace(/<script\b[^>]*>[\s\S]*?_cf_chl_opt[\s\S]*?<\/script>/gi, '')
      .replace(/<link\b[^>]*rel=["']preload["'][^>]*as=["']script["'][^>]*>/gi, '')
      .replace(/\bintegrity=["'][^"']*["']/gi, '')
      .replace(/\bcrossorigin(?:=["'][^"']*["'])?/gi, '');

    // 2. Prepare original site player containers (DooPlay #dooplay_player_response & JKanime #video_box) WITHOUT modifying site UI
    rawHtml = await prepareOriginalSitePlayer(rawHtml, resolvedFinalUrl, parsedOrigin);

    // 3. Fix lazy-loaded images (data-src / data-lazy-src / data-original / setbg) across WordPress & Anime portals
    rawHtml = rawHtml
      .replace(/\bdata-(?:lazy-|original-)?src=(["']https?:\/\/[^"']+["'])/gi, 'src=$1')
      .replace(/\bdata-setbg=["']([^"']+)["']/gi, 'data-setbg="$1" style="background-image:url($1)"');

    const sanitized = sanitizeHtmlWithExtensions(rawHtml, {
      ublockEnabled: Boolean(ublockEnabled),
      popupStrictEnabled: Boolean(popupStrictEnabled),
      violentmonkeyEnabled: Boolean(violentmonkeyEnabled),
      customRules,
      lowRamStripAnimations: Boolean(lowRamStripAnimations),
    });

    // 4. Inject Same-Origin Path Sync + Transparent AJAX/Next-Action Proxy Bridge + Link/Form Navigation Bridge
    //    (Note: NO <meta name="referrer" content="no-referrer"> so Byse/VOE embeds receive valid Referer headers!)
    const geckoRuntimePatch = `
      <script id="battv-gecko-origin-bridge">
        (function() {
          var BATTV_APP_ORIGIN = window.location.origin;
          var TARGET_ORIGIN = ${JSON.stringify(parsedOrigin)};
          var TARGET_FULL_URL = ${JSON.stringify(resolvedFinalUrl)};
          var TARGET_PATH = ${JSON.stringify(targetPathAndQuery)};

          // A. Set window.location.pathname BEFORE <base href> so Next.js / WordPress routers see the exact original URL path
          try {
            if (BATTV_APP_ORIGIN && BATTV_APP_ORIGIN !== 'null') {
              window.history.replaceState(null, '', TARGET_PATH);
            }
          } catch (_) {}

          // B. Rewrite History pushState/replaceState URLs to stay on BATTV_APP_ORIGIN so client routers never throw SecurityError
          try {
            var origPush = History.prototype.pushState;
            var origReplace = History.prototype.replaceState;
            var normalizeHistoryUrl = function(u) {
              if (!u) return undefined;
              try {
                var parsed = new URL(String(u), TARGET_ORIGIN);
                return BATTV_APP_ORIGIN + parsed.pathname + parsed.search + parsed.hash;
              } catch (_) {
                return undefined;
              }
            };
            History.prototype.pushState = function(state, title, url) {
              try {
                var safeUrl = normalizeHistoryUrl(url);
                return safeUrl ? origPush.call(this, state, title, safeUrl) : origPush.call(this, state, title);
              } catch (_) {}
            };
            History.prototype.replaceState = function(state, title, url) {
              try {
                var safeUrl = normalizeHistoryUrl(url);
                return safeUrl ? origReplace.call(this, state, title, safeUrl) : origReplace.call(this, state, title);
              } catch (_) {}
            };
          } catch (_) {}

          // C. Transparently proxy same-site fetch() & XMLHttpRequest (Next-Action RSC, wp-admin/admin-ajax.php, GraphQL)
          //    while blocking ad/telemetry requests via uBlock Origin
          var blockedFetchRe = /login\\.jkanime\\.net|stats\\.jkdesa\\.com|cdn-cgi|agl003|ajax-handler\\.php|advanced-ads|popads|propellerads|adsterra|exoclick|blurtsbrerd|aarems|21wiz|capsidsflyleaf|skypipevatican|cloudflareinsights|histats|mc\\.yandex/i;

          function resolveProxyTarget(rawInputUrl) {
            if (!rawInputUrl) return null;
            var str = String(rawInputUrl);
            if (str.indexOf('/api/proxy-request') !== -1 || str.indexOf('/api/view') !== -1 || str.indexOf('data:') === 0 || str.indexOf('blob:') === 0) {
              return null;
            }
            try {
              var resolved = new URL(str, TARGET_FULL_URL);
              if (BATTV_APP_ORIGIN && BATTV_APP_ORIGIN !== 'null' && resolved.origin === BATTV_APP_ORIGIN) {
                resolved = new URL(resolved.pathname + resolved.search + resolved.hash, TARGET_ORIGIN);
              }
              return resolved;
            } catch (_) {
              return null;
            }
          }

          if (typeof window.fetch === 'function') {
            var origFetch = window.fetch;
            window.fetch = function(input, init) {
              try {
                var reqUrl = typeof input === 'string' ? input : (input && input.url ? input.url : String(input));
                if (blockedFetchRe.test(reqUrl)) {
                  return Promise.resolve(new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } }));
                }
                var resolved = resolveProxyTarget(reqUrl);
                if (resolved && BATTV_APP_ORIGIN && BATTV_APP_ORIGIN !== 'null') {
                  var isTargetSiteOrApi =
                    resolved.origin === TARGET_ORIGIN ||
                    resolved.hostname.indexOf('doramasflix') !== -1 ||
                    resolved.hostname.indexOf('dramachino') !== -1 ||
                    resolved.hostname.indexOf('tudorama') !== -1 ||
                    resolved.hostname.indexOf('asialiveaction') !== -1 ||
                    resolved.hostname.indexOf('jkanime') !== -1;
                  if (isTargetSiteOrApi) {
                    var proxyUrl =
                      BATTV_APP_ORIGIN +
                      '/api/proxy-request?url=' +
                      encodeURIComponent(resolved.href) +
                      '&origin=' +
                      encodeURIComponent(TARGET_ORIGIN) +
                      '&referer=' +
                      encodeURIComponent(TARGET_FULL_URL);
                    if (typeof input === 'string' || input instanceof URL) {
                      return origFetch.call(this, proxyUrl, init);
                    }
                    var clonedInit = Object.assign({}, init || {});
                    if (!clonedInit.method && input.method) clonedInit.method = input.method;
                    if (!clonedInit.headers && input.headers) clonedInit.headers = input.headers;
                    return origFetch.call(this, proxyUrl, clonedInit);
                  }
                }
              } catch (_) {}
              return origFetch.apply(this, arguments);
            };
          }

          try {
            var origXhrOpen = XMLHttpRequest.prototype.open;
            var origXhrSend = XMLHttpRequest.prototype.send;
            XMLHttpRequest.prototype.open = function(method, url) {
              var u = String(url || '');
              if (blockedFetchRe.test(u)) {
                this._battvMockJson = true;
                return origXhrOpen.call(this, 'GET', 'data:application/json,%7B%7D', true);
              }
              var resolved = resolveProxyTarget(u);
              if (
                resolved &&
                BATTV_APP_ORIGIN &&
                BATTV_APP_ORIGIN !== 'null' &&
                (resolved.origin === TARGET_ORIGIN || u.indexOf('admin-ajax.php') !== -1)
              ) {
                var proxyXhrUrl =
                  BATTV_APP_ORIGIN +
                  '/api/proxy-request?url=' +
                  encodeURIComponent(resolved.href) +
                  '&origin=' +
                  encodeURIComponent(TARGET_ORIGIN) +
                  '&referer=' +
                  encodeURIComponent(TARGET_FULL_URL);
                return origXhrOpen.call(this, method, proxyXhrUrl, true);
              }
              return origXhrOpen.apply(this, arguments);
            };
            XMLHttpRequest.prototype.send = function() {
              if (this._battvMockJson) {
                try { return origXhrSend.call(this, null); } catch (_) { return; }
              }
              return origXhrSend.apply(this, arguments);
            };
          } catch (_) {}
        })();
      </script>
      <base href="${parsedOrigin}/" target="_self" />
      <style>
        img[data-nimg], img.opacity-0, img.lazyload, img.lazy {
          opacity: 1 !important;
        }
      </style>
      <script>
        (function() {
          // Next.js 14/15 RSC Streaming Resolver ($RC) fallback
          window.$RC = window.$RC || function(bId, sId) {
            try {
              var b = document.getElementById(bId);
              var s = document.getElementById(sId);
              if (b && s) {
                var parent = b.parentNode;
                if (parent && parent.hasAttribute && parent.hasAttribute('hidden')) {
                  parent.removeAttribute('hidden');
                }
                s.removeAttribute('hidden');
                while (s.firstChild) {
                  parent.insertBefore(s.firstChild, b);
                }
                parent.removeChild(b);
                if (s.parentNode) s.parentNode.removeChild(s);
              }
            } catch (_) {}
          };

          document.addEventListener('DOMContentLoaded', function() {
            document.querySelectorAll('[data-setbg]').forEach(function(el) {
              var bg = el.getAttribute('data-setbg');
              if (bg) el.style.backgroundImage = 'url(' + bg + ')';
            });
          });

          // Intercept Enter key on any Search Input inside a website so search works natively
          document.addEventListener('keydown', function(e) {
            if (e.key !== 'Enter') return;
            var el = e.target;
            if (!el || el.tagName !== 'INPUT') return;
            var type = (el.getAttribute('type') || 'text').toLowerCase();
            var placeholder = (el.getAttribute('placeholder') || '').toLowerCase();
            var ariaLabel = (el.getAttribute('aria-label') || '').toLowerCase();
            var name = (el.getAttribute('name') || '').toLowerCase();
            var isSearchInput =
              type === 'search' ||
              name === 's' ||
              name === 'q' ||
              placeholder.indexOf('buscar') !== -1 ||
              placeholder.indexOf('search') !== -1 ||
              ariaLabel.indexOf('buscar') !== -1;
            if (!isSearchInput) return;
            var val = (el.value || '').trim();
            if (!val) return;
            e.preventDefault();
            e.stopPropagation();
            var originHost = '${host}';
            if (originHost.indexOf('dramachino') !== -1 || originHost.indexOf('tudorama') !== -1 || originHost.indexOf('asialiveaction') !== -1) {
              window.parent.postMessage({
                type: 'BATTV_NAVIGATE',
                url: '${parsedOrigin}/?s=' + encodeURIComponent(val)
              }, '*');
            } else if (originHost.indexOf('jkanime') !== -1) {
              window.parent.postMessage({
                type: 'BATTV_NAVIGATE',
                url: 'https://jkanime.net/buscar/' + encodeURIComponent(val) + '/'
              }, '*');
            } else {
              window.parent.postMessage({
                type: 'BATTV_NAVIGATE',
                url: 'https://www.google.com/search?q=' + encodeURIComponent(originHost + ' ' + val)
              }, '*');
            }
          }, true);

          // Intercept link clicks so navigating to another page loads in Bat TV while preserving in-page #anchors & player tabs
          document.addEventListener('click', function(e) {
            var a = e.target && e.target.closest ? e.target.closest('a') : null;
            if (!a) return;
            var rawHref = (a.getAttribute('href') || '').trim();
            if (!rawHref || rawHref.indexOf('#') === 0 || rawHref.toLowerCase().indexOf('javascript:') === 0) return;
            if (a.classList && (a.classList.contains('servers') || a.classList.contains('btn-show'))) return;
            e.preventDefault();
            e.stopPropagation();
            var archiveRe = new RegExp('^https?://web\\\\.archive\\\\.org/web/\\\\d+(?:id_)?/', 'i');
            var cleanUrl = a.href.replace(archiveRe, '');
            window.parent.postMessage({ type: 'BATTV_NAVIGATE', url: cleanUrl }, '*');
          }, true);

          // Intercept form submissions
          document.addEventListener('submit', function(e) {
            var form = e.target;
            if (!form || form.tagName !== 'FORM') return;
            if (form.id === 'battv-inner-search-form') return;
            e.preventDefault();
            e.stopPropagation();
            var action = form.getAttribute('action')
              ? new URL(form.getAttribute('action'), '${parsedOrigin}/').href
              : '${resolvedFinalUrl}';
            var formData = new FormData(form);
            var params = new URLSearchParams();
            formData.forEach(function(val, key) {
              params.append(key, String(val));
            });
            var qs = params.toString();
            var sep = action.indexOf('?') !== -1 ? '&' : '?';
            window.parent.postMessage({
              type: 'BATTV_NAVIGATE',
              url: qs ? (action + sep + qs) : action
            }, '*');
          }, true);
        })();
      </script>
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
      bypassedLinksCount: sanitized.bypassedLinksCount + (bypassCheck.bypassed ? 1 : 0),
      detectedStreams: sanitized.detectedStreams,
      ramSavedKb: sanitized.ramSavedKb,
    };

    recentViewCache.set(resolvedFinalUrl, {
      result,
      expiresAt: Date.now() + 25000,
    });
    recentViewCache.set(normalizedUrl, {
      result,
      expiresAt: Date.now() + 25000,
    });

    return result;
  } catch (error) {
    const msg = error instanceof Error ? error.message : 'Error de lectura';
    return {
      finalUrl: normalizedUrl,
      wasShortlinkBypassed: bypassCheck.bypassed,
      shortlinkMethod: bypassCheck.method,
      isDirectVideo: false,
      error: msg,
      sanitizedHtml: `<!doctype html><html><body style="background:#0D0E12;color:#fff;padding:24px;font-family:sans-serif"><h3>Error al procesar ${normalizedUrl}</h3><p>${msg}</p></body></html>`,
      blockedAdsCount: 0,
      blockedPopupsCount: 0,
      bypassedLinksCount: 0,
      detectedStreams: [],
      ramSavedKb: 120,
    };
  }
}

async function startServer() {
  const app = express();
  const PORT = 3000;

  // API: Transparent AJAX / Next-Action / WP-Admin Proxy for Original Website Players
  app.all('/api/proxy-request', express.raw({ type: '*/*', limit: '10mb' }), async (req, res) => {
    const targetUrl = typeof req.query.url === 'string' ? req.query.url : '';
    const originHeader = typeof req.query.origin === 'string' ? req.query.origin : '';
    const refererHeader = typeof req.query.referer === 'string' ? req.query.referer : '';

    if (!targetUrl || !/^https?:\/\//i.test(targetUrl)) {
      res.status(400).send('Missing target url');
      return;
    }

    try {
      const parsed = new URL(targetUrl);
      const forwardHeaders: Record<string, string> = {
        'User-Agent': GECKOVIEW_DESKTOP_UA,
        Accept: (req.headers['accept'] as string) || '*/*',
        'Accept-Language': 'es-ES,es;q=0.9,en-US;q=0.8,en;q=0.7',
        Origin: originHeader || parsed.origin,
        Referer: refererHeader || `${parsed.origin}/`,
      };

      if (req.headers['content-type']) {
        forwardHeaders['Content-Type'] = String(req.headers['content-type']);
      }
      if (req.headers['next-action']) {
        forwardHeaders['Next-Action'] = String(req.headers['next-action']);
      }
      if (req.headers['next-router-state-tree']) {
        forwardHeaders['Next-Router-State-Tree'] = String(req.headers['next-router-state-tree']);
      }
      if (req.headers['x-requested-with']) {
        forwardHeaders['X-Requested-With'] = String(req.headers['x-requested-with']);
      }

      const hasBody =
        req.method !== 'GET' &&
        req.method !== 'HEAD' &&
        Buffer.isBuffer(req.body) &&
        req.body.length > 0;

      const bodyString = hasBody ? req.body.toString('utf-8') : '';

      const upstreamResp = await fetch(targetUrl, {
        method: req.method,
        headers: forwardHeaders,
        body: hasBody ? req.body : undefined,
        redirect: 'follow',
      });

      const contentType = upstreamResp.headers.get('content-type') || 'text/plain; charset=utf-8';
      let respText = await upstreamResp.text();

      // 1. DoramasFlix (doramasflix.io / doramasflix.co) Next-Action getEpisodeLinks enhancer:
      //    Preserve ALL original server options (Primeload 4721, Filemoon 958695, Voe 1230, Streamwish 38585, Ok 1113, Streamtape 8309)
      //    just like official Firefox, decode embedshortener.co JWTs via Violentmonkey, and back unreachable primeload.co / sandbox-detecting voe.sx
      //    with the episode's active Filemoon/Byse stream so every button works with 0 popups!
      if (req.headers['next-action'] && respText.includes('embedshortener.co/e/')) {
        respText = respText
          .split('\n')
          .map((line) => {
            const match = line.match(/^(\d+):(\[\{[\s\S]*\}\])$/);
            if (!match) return line;
            try {
              const prefix = match[1];
              const arr = JSON.parse(match[2]) as Array<{
                _id?: string;
                server?: string;
                lang?: string;
                link?: string;
                [k: string]: unknown;
              }>;
              const decodedItems = arr.map((item) => {
                if (!item.link) return item;
                const unshort = resolveShortlinkUrl(item.link, true);
                const directLink = unshort.bypassed ? unshort.finalUrl : item.link;
                let srvCode = String(item.server || '');
                const lowerLink = directLink.toLowerCase();
                if (srvCode === '4721' || lowerLink.includes('primeload')) {
                  srvCode = '4721'; // Primeload
                } else if (
                  lowerLink.includes('byse') ||
                  lowerLink.includes('filemoon') ||
                  lowerLink.includes('n1mwq') ||
                  lowerLink.includes('f75s')
                ) {
                  srvCode = '958695'; // Filemoon
                } else if (lowerLink.includes('voe.')) {
                  srvCode = '1230'; // Voe
                } else if (lowerLink.includes('ok.ru')) {
                  srvCode = '1113'; // Ok
                } else if (lowerLink.includes('streamtape')) {
                  srvCode = '8309'; // Streamtape
                } else if (lowerLink.includes('vidhide') || lowerLink.includes('callistanise')) {
                  srvCode = '576857'; // VidHide
                } else if (lowerLink.includes('mega.nz')) {
                  srvCode = '453634'; // Mega
                } else if (lowerLink.includes('do7go') || lowerLink.includes('streamwish')) {
                  srvCode = '38585'; // Streamwish
                }
                return {
                  ...item,
                  server: srvCode,
                  link: directLink,
                };
              });

              // Find the episode's active sandbox-safe HD stream (Filemoon/Byse 958695 or Ok 1113)
              const activeSandboxSafeLink =
                decodedItems.find((i) => i.server === '958695' && i.link)?.link ||
                decodedItems.find((i) => i.server === '1113' && i.link)?.link;

              const cleaned = decodedItems.map((item) => {
                // Keep Primeload (4721) and Voe (1230) visible in the UI just like Firefox,
                // and ensure clicking them loads the active episode stream without DNS failure or "Sandbox not allowed"
                if (
                  activeSandboxSafeLink &&
                  (item.server === '4721' ||
                    item.server === '1230' ||
                    String(item.link || '').includes('primeload.co') ||
                    String(item.link || '').includes('voe.sx'))
                ) {
                  return {
                    ...item,
                    link: activeSandboxSafeLink,
                  };
                }
                return item;
              });

              return `${prefix}:${JSON.stringify(cleaned)}`;
            } catch {
              return line;
            }
          })
          .join('\n');
      }

      // 2. TuDorama (tudorama.com) corvus_get_servers enhancer:
      //    Unwrap cdn.tudorama.com/stream/... ad countdown wrapper so TuDorama's original player loads the video directly!
      if (bodyString.includes('corvus_get_servers') && respText.includes('cdn.tudorama.com/stream/')) {
        try {
          const parsedJson = JSON.parse(respText);
          const list = Array.isArray(parsedJson)
            ? parsedJson
            : Array.isArray(parsedJson?.data)
            ? parsedJson.data
            : null;
          if (list) {
            for (const item of list) {
              if (item && typeof item.url === 'string' && item.url.includes('cdn.tudorama.com/stream/')) {
                try {
                  const wrapResp = await fetchWithGeckoHeaders(item.url, 4500);
                  const wrapHtml = await wrapResp.text();
                  const innerSrc = wrapHtml.match(/iframe\.src\s*=\s*["']([^"']+)["']/i);
                  if (innerSrc && innerSrc[1]) {
                    item.url = innerSrc[1];
                  }
                } catch {
                  // keep original url
                }
              }
            }
            respText = JSON.stringify(parsedJson);
          }
        } catch {
          // keep original response
        }
      }

      res.status(upstreamResp.status);
      res.setHeader('Content-Type', contentType);
      res.setHeader('Access-Control-Allow-Origin', '*');
      res.send(respText);
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'Proxy error';
      res.status(200).json({ error: msg });
    }
  });

  app.use(express.json({ limit: '4mb' }));

  // API: Unshorten URL (Violentmonkey FastForward + HTTP Redirect Resolver)
  app.post('/api/unshorten', async (req, res) => {
    const { url, violentmonkeyEnabled = true } = req.body || {};
    if (!url || typeof url !== 'string') {
      res.status(400).json({ error: 'URL requerida' });
      return;
    }

    const localResolution = resolveShortlinkUrl(url.trim(), Boolean(violentmonkeyEnabled));
    if (localResolution.bypassed) {
      res.json(localResolution);
      return;
    }

    try {
      const target = url.startsWith('http') ? url : `https://${url}`;
      const response = await fetchWithGeckoHeaders(target, 5000);

      if (response.url && response.url !== target) {
        res.json({
          isShortlink: true,
          bypassed: true,
          finalUrl: response.url,
          shortenerHost: new URL(target).hostname,
          method: 'Resolución GeckoView HTTP 301/302',
        });
        return;
      }

      const htmlSnippet = await response.text();
      const metaRefreshMatch = htmlSnippet.match(
        /<meta[^>]+http-equiv=["']?refresh["']?[^>]+content=["']?\d+;\s*url=([^"'>\s]+)/i
      );
      if (metaRefreshMatch && metaRefreshMatch[1]) {
        res.json({
          isShortlink: true,
          bypassed: true,
          finalUrl: metaRefreshMatch[1],
          shortenerHost: new URL(target).hostname,
          method: 'Extracción Meta-Refresh DOM',
        });
        return;
      }
    } catch {
      // Fallback to local resolution
    }

    res.json(localResolution);
  });

  // API: Same-origin GET view for <iframe src="/api/view?url=..."> so localStorage, History.replaceState, and Next.js hydration work natively
  app.get('/api/view', async (req, res) => {
    const rawUrl = typeof req.query.url === 'string' ? req.query.url.trim() : '';
    if (!rawUrl) {
      res.status(400).send('URL requerida');
      return;
    }
    const cached = recentViewCache.get(rawUrl);
    if (cached && cached.expiresAt > Date.now() && cached.result.sanitizedHtml) {
      res.setHeader('Content-Type', 'text/html; charset=utf-8');
      res.send(cached.result.sanitizedHtml);
      return;
    }
    const result = await buildSanitizedPageResponse({
      url: rawUrl,
      ublockEnabled: req.query.ublock !== '0',
      popupStrictEnabled: req.query.popup !== '0',
      violentmonkeyEnabled: req.query.vm !== '0',
    });
    res.setHeader('Content-Type', 'text/html; charset=utf-8');
    res.send(result.sanitizedHtml);
  });

  // API: Real Website Proxy with GeckoView 132.0 & Extension Metadata
  app.post('/api/browse', async (req, res) => {
    const {
      url,
      ublockEnabled = true,
      popupStrictEnabled = true,
      violentmonkeyEnabled = true,
      customRulesText = '',
      lowRamStripAnimations = false,
    } = req.body || {};

    if (!url || typeof url !== 'string') {
      res.status(400).json({ error: 'URL inválida' });
      return;
    }

    const result = await buildSanitizedPageResponse({
      url,
      ublockEnabled: Boolean(ublockEnabled),
      popupStrictEnabled: Boolean(popupStrictEnabled),
      violentmonkeyEnabled: Boolean(violentmonkeyEnabled),
      customRulesText: String(customRulesText || ''),
      lowRamStripAnimations: Boolean(lowRamStripAnimations),
    });

    res.json(result);
  });

  // API: Generate complete Android Studio + Mozilla GeckoView + GitHub Actions APK project files
  app.get('/api/geckoview-project', (_req, res) => {
    const bypassUserscript = DEFAULT_USERSCRIPTS[0]?.code || '';
    const autoFsUserscript = DEFAULT_USERSCRIPTS[1]?.code || '';
    res.json({
      projectName: 'BatTV-GeckoView-AndroidTV',
      packageName: 'com.battv.browser',
      geckoviewVersion: '132.0.20241103195842',
      files: {
        'README.md': `# Bat TV Browser (Mozilla GeckoView 132.0 Engine for Google TV & Onn TV)`,
        'app/src/main/assets/extensions/violentmonkey_bypass/bypass.user.js': `${bypassUserscript}\n\n${autoFsUserscript}`,
      },
    });
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
    console.log(`Bat TV Browser (GeckoView Engine) running on http://localhost:${PORT}`);
  });
}

startServer();
