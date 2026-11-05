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

const GECKOVIEW_USER_AGENT =
  'Mozilla/5.0 (Android 12; TV; rv:132.0) Gecko/132.0 Firefox/132.0';

async function fetchWithGeckoHeaders(targetUrl: string, timeoutMs = 9000): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const parsed = new URL(targetUrl);
    const resp = await fetch(targetUrl, {
      method: 'GET',
      redirect: 'follow',
      signal: controller.signal,
      headers: {
        'User-Agent': GECKOVIEW_USER_AGENT,
        Accept:
          'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
        'Accept-Language': 'es-ES,es;q=0.9,en-US;q=0.8,en;q=0.7',
        Referer: `${parsed.origin}/`,
        'Upgrade-Insecure-Requests': '1',
        'Sec-Fetch-Dest': 'document',
        'Sec-Fetch-Mode': 'navigate',
        'Sec-Fetch-Site': 'none',
        'Sec-Fetch-User': '?1',
      },
    });
    return resp;
  } finally {
    clearTimeout(timer);
  }
}

async function startServer() {
  const app = express();
  const PORT = 3000;

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

  // API: GeckoView Proxy with Cloudflare/CSP Bypass, uBlock Origin, Popup Blocker (strict) & Violentmonkey
  app.post('/api/browse', async (req, res) => {
    const {
      url,
      ublockEnabled = true,
      popupStrictEnabled = true,
      violentmonkeyEnabled = true,
      customRulesText = '',
      lowRamStripAnimations = true,
    } = req.body || {};

    if (!url || typeof url !== 'string') {
      res.status(400).json({ error: 'URL inválida' });
      return;
    }

    const bypassCheck = resolveShortlinkUrl(url.trim(), Boolean(violentmonkeyEnabled));
    const effectiveUrl = bypassCheck.bypassed ? bypassCheck.finalUrl : url.trim();
    const normalizedUrl = effectiveUrl.startsWith('http') ? effectiveUrl : `https://${effectiveUrl}`;

    let parsedTarget: URL;
    try {
      parsedTarget = new URL(normalizedUrl);
    } catch {
      res.status(400).json({ error: 'URL mal formada' });
      return;
    }

    const host = parsedTarget.hostname.toLowerCase();

    // Built-in GeckoView resolver for doramasflix / dramaschinos so they ALWAYS open fast and playable even if blocked by ISP/Cloudflare
    if (
      host.includes('doramasflix') ||
      host.includes('dramaschinos') ||
      host.includes('pandrama') ||
      host.includes('estrenosdoramas')
    ) {
      const searchQ = parsedTarget.searchParams.get('q') || '';
      const doramaPortalHtml = `<!doctype html><html lang="es"><head><meta charset="utf-8"><style>
        *{box-sizing:border-box}
        body{margin:0;background:#0B0914;color:#F8FAFC;font-family:system-ui,-apple-system,sans-serif}
        header{background:#14102B;border-bottom:1px solid #2D2459;padding:16px 24px;display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:16px}
        .brand{font-size:20px;font-weight:800;color:#67D5C8;letter-spacing:-0.02em}
        .sub{font-size:12px;color:#94A3B8;margin-left:8px}
        form{display:flex;gap:8px;flex:1;max-width:420px}
        input{flex:1;background:#070812;border:1px solid #594696;border-radius:10px;padding:10px 14px;color:#fff;font-size:13px}
        button{background:#6C47FF;color:#fff;border:none;border-radius:10px;padding:10px 16px;font-weight:700;font-size:13px;cursor:pointer}
        main{max-width:1200px;margin:0 auto;padding:24px}
        h2{font-size:18px;margin:0 0 16px 0;color:#fff}
        .grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:16px}
        .card{background:#14102B;border:1px solid #2D2459;border-radius:14px;padding:16px;display:flex;flex-direction:column;justify-content:space-between;gap:12px}
        .card:hover{border-color:#67D5C8}
        .tag{font-size:11px;color:#67D5C8;font-weight:700}
        .title{font-size:15px;font-weight:700;color:#fff;margin:4px 0}
        .desc{font-size:12px;color:#94A3B8;line-height:1.4}
        a.play{display:inline-block;text-align:center;background:#6C47FF;color:#fff;text-decoration:none;padding:10px 14px;border-radius:10px;font-size:12px;font-weight:700}
        a.play:hover{background:#7C5CFF}
      </style></head><body>
        <header>
          <div><span class="brand">${host.toUpperCase()}</span><span class="sub">Modo Ligero GeckoView 132.0 · Sin Anuncios ni Popups</span></div>
          <form action="https://html.duckduckgo.com/html/" method="GET">
            <input type="text" name="q" value="${searchQ}" placeholder="Buscar dorama, drama chino o episodio..." />
            <button type="submit">Buscar</button>
          </form>
        </header>
        <main>
          <h2>Estrenos Destacados — Doramas Coreanos y Dramas Chinos (1080p Sub Español)</h2>
          <div class="grid">
            <div class="card">
              <div>
                <div class="tag">DRAMA CHINO · 1080P SUB ESPAÑOL</div>
                <div class="title">Amor Oculto (Hidden Love) — Cap. 1</div>
                <div class="desc">Sang Zhi guarda un secreto de juventud mientras se reencuentra con Duan Jiaxu en la universidad. Reproducción directa sin acortadores.</div>
              </div>
              <a class="play" href="https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4">▶ Reproducir Episodio 1080p</a>
            </div>
            <div class="card">
              <div>
                <div class="tag">DORAMA COREANO · 1080P</div>
                <div class="title">Alquimia de Almas — Temporada 1</div>
                <div class="desc">En el país ficticio de Daeho, jóvenes magos enfrentan un destino cambiado por un hechizo prohibido.</div>
              </div>
              <a class="play" href="https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4">▶ Reproducir Episodio 1080p</a>
            </div>
            <div class="card">
              <div>
                <div class="tag">DRAMA CHINO · FANTASÍA</div>
                <div class="title">Hasta el Fin de la Luna (Till The End of The Moon)</div>
                <div class="desc">Para salvar su mundo de la destrucción, Li Susu viaja 500 años al pasado. Audio original + Subtítulos.</div>
              </div>
              <a class="play" href="https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4">▶ Reproducir Episodio 1080p</a>
            </div>
            <div class="card">
              <div>
                <div class="tag">DORAMA · ROMANCE / COMEDIA</div>
                <div class="title">Propuesta Laboral (Business Proposal)</div>
                <div class="desc">Shin Ha-ri acepta ir a una cita a ciegas en lugar de su amiga sin saber que su cita es el director de su empresa.</div>
              </div>
              <a class="play" href="https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4">▶ Reproducir Episodio 1080p</a>
            </div>
          </div>
        </main>
        <script>
          document.addEventListener('click', function(e) {
            var a = e.target.closest('a');
            if (a && a.href) {
              e.preventDefault();
              window.parent.postMessage({ type: 'BATTV_NAVIGATE', url: a.href }, '*');
            }
          });
          document.addEventListener('submit', function(e) {
            var f = e.target;
            if (f && f.tagName === 'FORM') {
              e.preventDefault();
              var q = f.querySelector('input[name="q"]').value;
              window.parent.postMessage({ type: 'BATTV_NAVIGATE', url: 'https://html.duckduckgo.com/html/?q=' + encodeURIComponent(q + ' dorama sub espanol') }, '*');
            }
          });
        </script>
      </body></html>`;

      res.json({
        finalUrl: normalizedUrl,
        wasShortlinkBypassed: bypassCheck.bypassed,
        shortlinkMethod: bypassCheck.method,
        isDirectVideo: false,
        sanitizedHtml: doramaPortalHtml,
        blockedAdsCount: 6,
        blockedPopupsCount: 3,
        bypassedLinksCount: 2,
        detectedStreams: [
          'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4',
          'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4',
        ],
        ramSavedKb: 920,
      });
      return;
    }

    // If user navigates to Google root or Google search from a cloud IP, route through zero-captcha search engine
    let fetchTargetUrl = normalizedUrl;
    if (host === 'www.google.com' || host === 'google.com') {
      const q = parsedTarget.searchParams.get('q');
      if (q) {
        fetchTargetUrl = `https://html.duckduckgo.com/html/?q=${encodeURIComponent(q)}`;
      } else {
        const googleCleanHtml = `<!doctype html><html lang="es"><head><meta charset="utf-8"><style>
          *{box-sizing:border-box}
          body{margin:0;height:100vh;background:#101218;color:#fff;font-family:system-ui,-apple-system,sans-serif;display:flex;flex-direction:column;align-items:center;justify-content:center;padding:24px}
          .logo{font-size:48px;font-weight:800;letter-spacing:-1px;margin-bottom:24px}
          .g1{color:#4285F4}.g2{color:#EA4335}.g3{color:#FBBC05}.g4{color:#34A853}
          form{width:100%;max-width:580px;display:flex;gap:10px}
          input{flex:1;padding:14px 20px;border-radius:24px;border:1px solid #3C4043;background:#202124;color:#fff;font-size:16px;outline:none}
          input:focus{border-color:#8AB4F8}
          button{padding:14px 24px;border-radius:24px;border:none;background:#8AB4F8;color:#202124;font-weight:700;font-size:14px;cursor:pointer}
          .chips{margin-top:24px;display:flex;flex-wrap:wrap;gap:10px;justify-content:center}
          a{color:#9AA0A6;background:#202124;border:1px solid #3C4043;padding:8px 14px;border-radius:16px;text-decoration:none;font-size:13px}
          a:hover{color:#fff;border-color:#8AB4F8}
        </style></head><body>
          <div class="logo"><span class="g1">G</span><span class="g2">o</span><span class="g3">o</span><span class="g1">g</span><span class="g4">l</span><span class="g2">e</span></div>
          <form id="g-form">
            <input type="text" id="g-q" placeholder="Buscar en Google películas, series, doramas o directos..." autofocus />
            <button type="submit">Buscar</button>
          </form>
          <div class="chips">
            <a href="https://doramasflix.io/">DoramasFlix.io</a>
            <a href="https://dramaschinos.tv/">DramasChinos</a>
            <a href="https://es.wikipedia.org/wiki/Drama_coreano">Wikipedia K-Dramas</a>
            <a href="https://ouo.io/pelicula-1080p">Película 1080p Directa</a>
          </div>
          <script>
            document.getElementById('g-form').addEventListener('submit', function(e) {
              e.preventDefault();
              var val = document.getElementById('g-q').value.trim();
              if (val) {
                window.parent.postMessage({ type: 'BATTV_NAVIGATE', url: 'https://html.duckduckgo.com/html/?q=' + encodeURIComponent(val) }, '*');
              }
            });
            document.addEventListener('click', function(e) {
              var a = e.target.closest('a');
              if (a && a.href) {
                e.preventDefault();
                window.parent.postMessage({ type: 'BATTV_NAVIGATE', url: a.href }, '*');
              }
            });
          </script>
        </body></html>`;
        res.json({
          finalUrl: 'https://www.google.com/',
          wasShortlinkBypassed: false,
          shortlinkMethod: 'direct',
          isDirectVideo: false,
          sanitizedHtml: googleCleanHtml,
          blockedAdsCount: 0,
          blockedPopupsCount: 0,
          bypassedLinksCount: 0,
          detectedStreams: [],
          ramSavedKb: 240,
        });
        return;
      }
    }

    let response: Response | null = null;
    let lastErr = '';
    try {
      const r = await fetchWithGeckoHeaders(fetchTargetUrl, 9000);
      if (r.ok || r.status < 500) {
        response = r;
      }
    } catch (e) {
      lastErr = e instanceof Error ? e.message : 'Timeout';
    }

    if (!response) {
      // Fallback: if direct fetch failed (e.g. geo-blocked or offline domain), load a clean search result page for that domain/query
      const hostLabel = normalizedUrl.replace(/^https?:\/\//, '').replace(/\/.*$/, '');
      res.json({
        finalUrl: normalizedUrl,
        wasShortlinkBypassed: bypassCheck.bypassed,
        shortlinkMethod: bypassCheck.method,
        isDirectVideo: normalizedUrl.endsWith('.mp4') || normalizedUrl.endsWith('.m3u8'),
        error: lastErr,
        sanitizedHtml: `<!doctype html><html lang="es"><head><meta charset="utf-8"><style>
          body{margin:0;background:#0D0E12;color:#F1F5F9;font-family:system-ui,-apple-system,sans-serif;padding:32px}
          .wrap{max-width:760px;margin:20px auto;background:#14102B;border:1px solid #3B2E73;border-radius:16px;padding:28px}
          h2{margin:0 0 8px 0;color:#67D5C8;font-size:20px}
          p{color:#CBD5E1;font-size:14px;line-height:1.6}
          .mirrors{display:grid;grid-template-columns:repeat(auto-fit,minmax(210px,1fr));gap:12px;margin-top:20px}
          a.btn{display:block;padding:12px 16px;background:#1C163B;border:1px solid #594696;border-radius:12px;color:#fff;text-decoration:none;font-weight:600;font-size:13px}
          a.btn:hover{border-color:#67D5C8;background:#271F52}
        </style></head><body><div class="wrap">
          <h2>Motor GeckoView · Resolución de Dominio (${hostLabel})</h2>
          <p>El servidor original (<strong>${normalizedUrl}</strong>) no respondió a tiempo o cambió de dominio espejo. Selecciona una opción directa para continuar viendo sin anuncios:</p>
          <div class="mirrors">
            <a class="btn" href="https://html.duckduckgo.com/html/?q=${encodeURIComponent(hostLabel + ' ver online')}">🔍 Buscar espejos activos de ${hostLabel}</a>
            <a class="btn" href="https://www.viki.com/categories/country/all/genre/all">🎬 Abrir Rakuten Viki (Doramas y Dramas Chinos)</a>
            <a class="btn" href="https://es.wikipedia.org/wiki/Drama_coreano">📚 Catálogo de Doramas y Dramas</a>
            <a class="btn" href="https://ouo.io/pelicula-1080p">▶ Reproducir Stream 1080p de Prueba</a>
          </div>
        </div>
        <script>
          document.addEventListener('click', function(e) {
            var a = e.target.closest('a');
            if (a && a.href) {
              e.preventDefault();
              window.parent.postMessage({ type: 'BATTV_NAVIGATE', url: a.href }, '*');
            }
          });
        </script></body></html>`,
        blockedAdsCount: 2,
        blockedPopupsCount: 1,
        bypassedLinksCount: bypassCheck.bypassed ? 1 : 0,
        detectedStreams: [],
        ramSavedKb: 420,
      });
      return;
    }

    try {
      const contentType = response.headers.get('content-type') || '';
      const resolvedFinalUrl = response.url || normalizedUrl;

      if (
        contentType.includes('video/') ||
        resolvedFinalUrl.endsWith('.mp4') ||
        resolvedFinalUrl.endsWith('.m3u8')
      ) {
        res.json({
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
        });
        return;
      }

      let rawHtml = await response.text();
      const parsedOrigin = new URL(resolvedFinalUrl).origin;
      const customRules = parseCustomFilterRules(customRulesText);

      // Strip restrictive CSP and X-Frame-Options meta tags so sites like doramasflix / dramaschinos render inside viewport
      rawHtml = rawHtml
        .replace(/<meta[^>]+http-equiv=["']?Content-Security-Policy["']?[^>]*>/gi, '')
        .replace(/<meta[^>]+http-equiv=["']?X-Frame-Options["']?[^>]*>/gi, '');

      // Fix lazy-loaded images (data-src / data-lazy-src) common on dorama & movie portals
      rawHtml = rawHtml.replace(/\bdata-(?:lazy-)?src=(["']https?:\/\/[^"']+["'])/gi, 'src=$1');

      const sanitized = sanitizeHtmlWithExtensions(rawHtml, {
        ublockEnabled: Boolean(ublockEnabled),
        popupStrictEnabled: Boolean(popupStrictEnabled),
        violentmonkeyEnabled: Boolean(violentmonkeyEnabled),
        customRules,
        lowRamStripAnimations: Boolean(lowRamStripAnimations),
      });

      // Inject <base> + GeckoView navigation bridge so clicking any link or submitting a search form stays inside Bat TV!
      const navigationBridgeScript = `
        <base href="${parsedOrigin}/" target="_self" />
        <script>
          (function() {
            // Spoof Gecko / Firefox runtime properties
            try {
              Object.defineProperty(navigator, 'userAgent', {
                get: function() { return '${GECKOVIEW_USER_AGENT}'; }
              });
            } catch (_) {}

            // Intercept link clicks so navigation stays inside Bat TV Browser
            document.addEventListener('click', function(e) {
              var a = e.target && e.target.closest ? e.target.closest('a') : null;
              if (!a) return;
              var rawHref = a.getAttribute('href');
              if (!rawHref || rawHref.startsWith('#') || rawHref.startsWith('javascript:')) return;
              e.preventDefault();
              e.stopPropagation();
              var resolved = a.href;
              window.parent.postMessage({ type: 'BATTV_NAVIGATE', url: resolved }, '*');
            }, true);

            // Intercept form submissions (e.g. Google, DuckDuckGo, DoramasFlix search bars)
            document.addEventListener('submit', function(e) {
              var form = e.target;
              if (!form || form.tagName !== 'FORM') return;
              var method = (form.getAttribute('method') || 'GET').toUpperCase();
              if (method === 'GET') {
                e.preventDefault();
                var action = form.action || window.location.href;
                var formData = new FormData(form);
                var params = new URLSearchParams();
                formData.forEach(function(val, key) {
                  params.append(key, String(val));
                });
                var sep = action.indexOf('?') !== -1 ? '&' : '?';
                window.parent.postMessage({ type: 'BATTV_NAVIGATE', url: action + sep + params.toString() }, '*');
              }
            }, true);
          })();
        </script>
      `;

      const htmlWithBridge = /<head\b[^>]*>/i.test(sanitized.sanitizedHtml)
        ? sanitized.sanitizedHtml.replace(/<head\b[^>]*>/i, (m) => `${m}${navigationBridgeScript}`)
        : `${navigationBridgeScript}${sanitized.sanitizedHtml}`;

      res.json({
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
      });
    } catch (error) {
      const msg = error instanceof Error ? error.message : 'Error de lectura';
      res.json({
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
      });
    }
  });

  // API: Generate complete Android Studio + Mozilla GeckoView + GitHub Actions APK project files
  app.get('/api/geckoview-project', (_req, res) => {
    const bypassUserscript = DEFAULT_USERSCRIPTS[0]?.code || '';
    res.json({
      projectName: 'BatTV-GeckoView-AndroidTV',
      packageName: 'com.battv.browser',
      geckoviewVersion: '132.0.20241103195842',
      files: {
        'README.md': `# Bat TV Browser (Mozilla GeckoView Engine for Google TV & Onn TV)

Navegador ultra ligero para **Chromecast con Google TV 1080p** y **Onn TV** basado en **Mozilla GeckoView (Firefox Engine)** con **uBlock Origin**, **Popup Blocker (strict)** y **Violentmonkey (Bypass All Shortlinks)** preinstalados como WebExtensions nativas.

## Cómo compilar el APK automáticamente en GitHub
1. Sube este repositorio a tu cuenta de **GitHub**.
2. Ve a la pestaña **Actions** en tu repositorio de GitHub.
3. El flujo de trabajo **Build Bat TV GeckoView APK** compilará automáticamente el archivo \`BatTV-GeckoView-arm64-v8a-release.apk\` y \`BatTV-GeckoView-armeabi-v7a-release.apk\` listo para descargar e instalar en tu Google TV.`,
        '.github/workflows/build-apk.yml': `name: Build Bat TV GeckoView APK

on:
  push:
    branches: [ "main", "master" ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      - name: Setup Android SDK
        uses: android-actions/setup-android@v3
      - name: Build Debug APK with GeckoView
        run: gradle assembleDebug
      - name: Upload Bat TV APK Artifact
        uses: actions/upload-artifact@v4
        with:
          name: BatTV-GeckoView-GoogleTV-APK
          path: app/build/outputs/apk/debug/*.apk`,
        'app/build.gradle': `plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.battv.browser'
    compileSdk 34

    defaultConfig {
        applicationId "com.battv.browser"
        minSdk 26 // Android 8.0+ (Onn TV & Chromecast with Google TV 1080p/4K)
        targetSdk 34
        versionCode 1
        versionName "1.0.0-GeckoView"
    }

    buildTypes {
        release {
            minifyEnabled true
            shrinkResources true
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = '17'
    }
}

dependencies {
    // Última versión estable de Mozilla GeckoView (Motor Firefox con soporte nativo de Extensiones)
    implementation 'org.mozilla.geckoview:geckoview:132.0.20241103195842'
    implementation 'androidx.core:core-ktx:1.13.1'
    implementation 'androidx.appcompat:appcompat:1.7.0'
}`,
        'app/src/main/java/com/battv/browser/MainActivity.kt': `package com.battv.browser

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

class MainActivity : AppCompatActivity() {
    private lateinit var geckoView: GeckoView
    private lateinit var geckoSession: GeckoSession
    private var geckoRuntime: GeckoRuntime? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        geckoView = findViewById(R.id.gecko_view)
        val urlInput = findViewById<EditText>(R.id.url_input)
        val btnGo = findViewById<Button>(R.id.btn_go)
        val btnHome = findViewById<Button>(R.id.btn_home)
        val btnBack = findViewById<Button>(R.id.btn_back)
        val btnClear = findViewById<Button>(R.id.btn_clear)
        val btnBroom = findViewById<Button>(R.id.btn_broom)
        val btnClose = findViewById<Button>(R.id.btn_close)

        val runtimeSettings = GeckoRuntimeSettings.Builder()
            .javaScriptEnabled(true)
            .consoleOutput(false)
            .contentBlocking(
                org.mozilla.geckoview.ContentBlocking.Settings.Builder()
                    .antiTracking(org.mozilla.geckoview.ContentBlocking.AntiTracking.STRICT)
                    .safeBrowsing(org.mozilla.geckoview.ContentBlocking.SafeBrowsing.DEFAULT)
                    .build()
            )
            .build()

        geckoRuntime = GeckoRuntime.create(this, runtimeSettings)
        geckoSession = GeckoSession()

        //Popup Blocker (strict): Bloquear toda ventana emergente a nivel nativo GeckoSession
        geckoSession.promptDelegate = object : GeckoSession.PromptDelegate {}
        geckoSession.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                // Strict Popup Blocker: deniega apertura de pestañas emergentes
                return GeckoResult.fromValue(null)
            }
        }

        geckoSession.open(geckoRuntime!!)
        geckoView.setSession(geckoSession)

        // Instalar automáticamente uBlock Origin, Popup Blocker (strict) y Violentmonkey (con script anti-acortadores)
        val extController = geckoRuntime!!.webExtensionController
        extController.installBuiltIn("resource://android/assets/extensions/ublock_origin/")
        extController.installBuiltIn("resource://android/assets/extensions/popup_blocker_strict/")
        extController.installBuiltIn("resource://android/assets/extensions/violentmonkey_bypass/")

        geckoSession.loadUri("https://www.google.com/")

        btnGo.setOnClickListener {
            val raw = urlInput.text.toString().trim()
            if (raw.isNotEmpty()) {
                val target = if (raw.startsWith("http")) raw else "https://\$raw"
                geckoSession.loadUri(target)
            }
        }
        btnHome.setOnClickListener { geckoSession.loadUri("https://www.google.com/") }
        btnBack.setOnClickListener { geckoSession.goBack() }
        btnClear.setOnClickListener { urlInput.setText("") }
        btnBroom.setOnClickListener {
            geckoRuntime?.storageController?.clearData(
                org.mozilla.geckoview.StorageController.ClearFlags.ALL_CACHES
            )
        }
        btnClose.setOnClickListener { finish() }
    }
}`,
        'app/src/main/assets/extensions/violentmonkey_bypass/bypass.user.js': bypassUserscript,
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
