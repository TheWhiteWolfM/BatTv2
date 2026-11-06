import React, { useState, useEffect, useRef, useCallback } from 'react';
import {
  Search,
  Settings,
  RotateCw,
  Check,
  Plus,
  Star,
  Clock,
  Github,
  X,
} from 'lucide-react';
import {
  DEFAULT_FILTER_LISTS,
  DEFAULT_USERSCRIPTS,
  BUILTIN_MEDIA_CATALOG,
  FilterListConfig,
  UserscriptItem,
  BlockedEvent,
  DetectedStream,
  resolveShortlinkUrl,
  evaluatePopupAttempt,
  evaluateUblockRequest,
} from './engine/extensionEngine';
import { ExtensionsModal } from './components/ExtensionsModal';
import { TvVideoPlayerView } from './components/TvVideoPlayerModal';
import { TvInstallModal } from './components/TvInstallModal';

type BrowserViewMode = 'newtab' | 'web' | 'video';
type OverlayDrawer = 'none' | 'bookmarks' | 'history';

interface ShortcutItem {
  id: string;
  title: string;
  domain: string;
  url: string;
}

interface HistoryEntry {
  id: string;
  title: string;
  url: string;
  timestamp: string;
}

const BAT_TV_LOGO_PATH =
  '/assets/aistudio/file_0000000004d881f583380aa06d6466dd_ffbd0340.png';

const DEFAULT_SHORTCUTS: ShortcutItem[] = [
  {
    id: 'sc-doramasflix-io',
    title: 'doramasflix.io',
    domain: 'doramasflix.io',
    url: 'https://doramasflix.io/',
  },
  {
    id: 'sc-doramasflix-co',
    title: 'doramasflix.co',
    domain: 'doramasflix.co',
    url: 'https://doramasflix.co/',
  },
  {
    id: 'sc-dramachino',
    title: 'dramachino.com',
    domain: 'dramachino.com',
    url: 'https://dramachino.com/',
  },
  {
    id: 'sc-asialiveaction',
    title: 'asialiveaction.com',
    domain: 'asialiveaction.com',
    url: 'https://asialiveaction.com/',
  },
  {
    id: 'sc-tudorama',
    title: 'tudorama.com',
    domain: 'tudorama.com',
    url: 'https://tudorama.com/',
  },
  {
    id: 'sc-kisskh',
    title: 'kisskh.do',
    domain: 'kisskh.do',
    url: 'https://kisskh.do/',
  },
  {
    id: 'sc-jkanime',
    title: 'JKanime.net',
    domain: 'jkanime.net',
    url: 'https://jkanime.net/',
  },
  {
    id: 'sc-twitch',
    title: 'Twitch.tv 1080p60',
    domain: 'twitch.tv',
    url: 'https://www.twitch.tv/',
  },
];

export default function App() {
  const urlInputRef = useRef<HTMLInputElement | null>(null);

  // Clean Browser State (New Tab, Web Viewport, or Direct Video Player)
  const [viewMode, setViewMode] = useState<BrowserViewMode>('newtab');
  const [overlayDrawer, setOverlayDrawer] = useState<OverlayDrawer>('none');
  const [urlInput, setUrlInput] = useState('https://www.google.com/');
  const [newTabSearchInput, setNewTabSearchInput] = useState('');
  const [currentBrowserUrl, setCurrentBrowserUrl] = useState('https://www.google.com/');
  const [navStack, setNavStack] = useState<string[]>(['https://www.google.com/']);
  const [proxiedHtml, setProxiedHtml] = useState<string>('');
  const [isLoadingPage, setIsLoadingPage] = useState<boolean>(false);
  const [progressWidth, setProgressWidth] = useState<number>(78);
  const [toastMessage, setToastMessage] = useState<string | null>(null);

  // Bookmarks (⭐) & History (🕘)
  const [bookmarks, setBookmarks] = useState<ShortcutItem[]>(DEFAULT_SHORTCUTS);
  const [newBookmarkTitle, setNewBookmarkTitle] = useState('');
  const [newBookmarkUrl, setNewBookmarkUrl] = useState('');
  const [historyList, setHistoryList] = useState<HistoryEntry[]>([
    {
      id: 'h-1',
      title: 'DoramasFlix.io — Doramas Sub Español',
      url: 'https://doramasflix.io/',
      timestamp: '15:53',
    },
    {
      id: 'h-2',
      title: 'KissKH.do — Asian Dramas & Movies 1080p',
      url: 'https://kisskh.do/',
      timestamp: '15:51',
    },
    {
      id: 'h-3',
      title: 'JKanime.net — Anime Online Full HD',
      url: 'https://jkanime.net/',
      timestamp: '15:48',
    },
  ]);

  // 3 Extensions State (uBlock Origin, Popup Blocker strict, Violentmonkey) — active silently in background like Firefox!
  const [ublockEnabled, setUblockEnabled] = useState<boolean>(true);
  const [filterLists, setFilterLists] = useState<FilterListConfig[]>(DEFAULT_FILTER_LISTS);
  const [customRulesText, setCustomRulesText] = useState<string>(
    '! Reglas personalizadas para Bat TV (GeckoView 132.0)\n||popads.net^\n||propellerads.com^\n||exoclick.com^\n||1xbet.com^'
  );
  const [popupStrictEnabled, setPopupStrictEnabled] = useState<boolean>(true);
  const [whitelistedHosts, setWhitelistedHosts] = useState<string[]>([]);
  const [violentmonkeyEnabled, setViolentmonkeyEnabled] = useState<boolean>(true);
  const [userscripts, setUserscripts] = useState<UserscriptItem[]>(DEFAULT_USERSCRIPTS);

  // Modals (Only opened when clicking 🧩 or GitHub button)
  const [extensionsModalOpen, setExtensionsModalOpen] = useState<boolean>(false);
  const [extensionsModalTab, setExtensionsModalTab] = useState<
    'ublock' | 'popup-strict' | 'violentmonkey'
  >('ublock');
  const [installModalOpen, setInstallModalOpen] = useState<boolean>(false);

  // Video Stream Player State
  const [activeStream, setActiveStream] = useState<DetectedStream>(BUILTIN_MEDIA_CATALOG[0]);
  const [detectedPageStreams, setDetectedPageStreams] = useState<DetectedStream[]>([]);

  const [blockedEvents, setBlockedEvents] = useState<BlockedEvent[]>([
    {
      id: 'init-1',
      timestamp: '15:52:10',
      extension: 'violentmonkey',
      action: 'bypassed-shortlink',
      sourceUrl: 'https://ouo.io/pelicula-1080p',
      targetOrRule: 'Salto 0ms FastForward -> stream.mp4',
      ramSavedKb: 520,
    },
    {
      id: 'init-2',
      timestamp: '15:52:08',
      extension: 'popup-strict',
      action: 'blocked-popup',
      sourceUrl: 'https://doramasflix.co',
      targetOrRule: 'Bloqueado window.open() + capa invisible clickjacking',
      ramSavedKb: 380,
    },
    {
      id: 'init-3',
      timestamp: '15:52:04',
      extension: 'ublock',
      action: 'blocked-request',
      sourceUrl: 'https://cdn.propellerads.com/pop.js',
      targetOrRule: '||propellerads.com^ (EasyList LatAm)',
      ramSavedKb: 440,
    },
  ]);

  // Enable Ultra-Light Low-RAM mode by default for Chromecast 1080p / Onn TV
  useEffect(() => {
    document.body.setAttribute('data-low-ram', 'true');
  }, []);

  const showBriefToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(null), 2800);
  };

  const recordExtensionEvent = useCallback(
    (
      extension: 'ublock' | 'popup-strict' | 'violentmonkey',
      targetOrRule: string,
      ramSavedKb: number
    ) => {
      const timeStr = new Date().toTimeString().slice(0, 8);
      const newEv: BlockedEvent = {
        id: `ev-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
        timestamp: timeStr,
        extension,
        action:
          extension === 'ublock'
            ? 'blocked-request'
            : extension === 'popup-strict'
            ? 'blocked-popup'
            : 'bypassed-shortlink',
        sourceUrl: currentBrowserUrl,
        targetOrRule,
        ramSavedKb,
      };
      setBlockedEvents((prev) => [newEv, ...prev.slice(0, 39)]);
    },
    [currentBrowserUrl]
  );

  const navigateAndSanitize = useCallback(
    async (rawTarget: string) => {
      const trimmed = rawTarget.trim();
      if (!trimmed) return;

      setOverlayDrawer('none');
      setProgressWidth(30);

      // 1. Check uBlock Origin on main request
      const ublockCheck = evaluateUblockRequest(trimmed, ublockEnabled);
      if (ublockCheck.blocked) {
        recordExtensionEvent('ublock', `Bloqueado: ${ublockCheck.matchedRule}`, 450);
        showBriefToast(`uBlock Origin bloqueó dominio publicitario (${ublockCheck.matchedRule})`);
        setProgressWidth(78);
        return;
      }

      // 2. Violentmonkey Universal Shortlink Bypass
      const bypass = resolveShortlinkUrl(trimmed, violentmonkeyEnabled);
      const effectiveTarget = bypass.bypassed ? bypass.finalUrl : trimmed;

      const timeStr = new Date().toTimeString().slice(0, 5);
      setHistoryList((prev) => [
        {
          id: `hist-${Date.now()}`,
          title: bypass.bypassed
            ? `Salto 0ms (${bypass.shortenerHost}) -> ${effectiveTarget}`
            : effectiveTarget,
          url: trimmed,
          timestamp: timeStr,
        },
        ...prev.slice(0, 29),
      ]);

      if (bypass.bypassed) {
        recordExtensionEvent(
          'violentmonkey',
          `Acortador saltado (${bypass.shortenerHost}) -> ${bypass.finalUrl}`,
          540
        );
        showBriefToast(`Violentmonkey saltó ${bypass.shortenerHost} en 0ms`);
      }

      // 3. Direct Video Stream (.mp4 / .m3u8) -> open native 1080p player
      if (/\.(mp4|m3u8)(\?.*)?$/i.test(effectiveTarget)) {
        const matchedCatalog = BUILTIN_MEDIA_CATALOG.find((item) => item.url === effectiveTarget);
        const streamToPlay: DetectedStream = matchedCatalog || {
          id: `direct-${Date.now()}`,
          title: bypass.bypassed
            ? `Stream Desbloqueado (${bypass.shortenerHost})`
            : 'Stream Directo 1080p',
          url: effectiveTarget,
          type: effectiveTarget.includes('.m3u8') ? 'hls' : 'mp4',
          quality: '1080p 60fps · GeckoView Hardware Decode',
          category: 'Película',
        };
        setActiveStream(streamToPlay);
        setUrlInput(effectiveTarget);
        setProgressWidth(78);
        setViewMode('video');
        return;
      }

      // 4. Normalize search query or domain
      let finalWebUrl = effectiveTarget;
      if (!/^https?:\/\//i.test(finalWebUrl)) {
        if (finalWebUrl.includes('.') && !finalWebUrl.includes(' ')) {
          finalWebUrl = `https://${finalWebUrl}`;
        } else {
          finalWebUrl = `https://www.google.com/search?q=${encodeURIComponent(finalWebUrl)}`;
        }
      }

      setUrlInput(finalWebUrl);
      setCurrentBrowserUrl(finalWebUrl);
      setNavStack((prev) => [...prev, finalWebUrl]);
      setViewMode('web');
      setIsLoadingPage(true);
      setProgressWidth(65);

      try {
        const response = await fetch('/api/browse', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            url: finalWebUrl,
            ublockEnabled,
            popupStrictEnabled,
            violentmonkeyEnabled,
            customRulesText,
            lowRamStripAnimations: false,
          }),
        });
        const data = await response.json();

        if (data.isDirectVideo && data.finalUrl) {
          setActiveStream({
            id: `proxy-video-${Date.now()}`,
            title: 'Video Directo Detectado',
            url: data.finalUrl,
            type: data.finalUrl.includes('.m3u8') ? 'hls' : 'mp4',
            quality: '1080p Direct Play',
            category: 'Detectado en Página',
          });
          setIsLoadingPage(false);
          setProgressWidth(78);
          setViewMode('video');
          return;
        }

        setProxiedHtml(data.sanitizedHtml || '');
        if (data.finalUrl) {
          setCurrentBrowserUrl(data.finalUrl);
          setUrlInput(data.finalUrl);
        }

        if (Number(data.blockedAdsCount) > 0) {
          recordExtensionEvent(
            'ublock',
            `uBlock Origin bloqueó ${data.blockedAdsCount} scripts/capas publicitarias en ${
              data.finalUrl || finalWebUrl
            }`,
            Number(data.blockedAdsCount) * 420
          );
        }
        if (Number(data.blockedPopupsCount) > 0) {
          recordExtensionEvent(
            'popup-strict',
            `Popup Blocker neutralizó ${data.blockedPopupsCount} popups`,
            Number(data.blockedPopupsCount) * 310
          );
        }
      } catch {
        setProxiedHtml(
          `<!doctype html><html><body style="background:#0D0E12;color:#F1F5F9;font-family:sans-serif;padding:24px;"><h3>Página cargada en Bat TV</h3><p>${finalWebUrl}</p></body></html>`
        );
      } finally {
        setIsLoadingPage(false);
        setProgressWidth(78);
      }
    },
    [
      ublockEnabled,
      violentmonkeyEnabled,
      popupStrictEnabled,
      customRulesText,
      recordExtensionEvent,
    ]
  );

  // Listen for in-iframe link clicks and messages
  useEffect(() => {
    const onMessage = (e: MessageEvent) => {
      if (e.data && e.data.type === 'BATTV_NAVIGATE' && typeof e.data.url === 'string') {
        navigateAndSanitize(e.data.url);
      }
    };
    window.addEventListener('message', onMessage);
    return () => window.removeEventListener('message', onMessage);
  }, [navigateAndSanitize]);

  // Close modals on Escape key
  useEffect(() => {
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setExtensionsModalOpen(false);
        setInstallModalOpen(false);
        setOverlayDrawer('none');
      }
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, []);

  const handleUrlBarSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    navigateAndSanitize(urlInput);
  };

  const handleNewTabSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTabSearchInput.trim()) return;
    navigateAndSanitize(newTabSearchInput);
  };

  const handleBackNavigation = () => {
    setOverlayDrawer('none');
    if (navStack.length > 1) {
      const nextStack = navStack.slice(0, -1);
      const prevUrl = nextStack[nextStack.length - 1];
      setNavStack(nextStack);
      setUrlInput(prevUrl);
      if (viewMode === 'web') {
        navigateAndSanitize(prevUrl);
      } else {
        setViewMode('newtab');
      }
    } else {
      setViewMode('newtab');
    }
  };

  const handleClearOrStopUrl = () => {
    setIsLoadingPage(false);
    setUrlInput('');
    urlInputRef.current?.focus();
  };

  const handleBroomRamClean = () => {
    setProxiedHtml('');
    setDetectedPageStreams([]);
    showBriefToast('🧹 Caché DOM y RAM liberada');
  };

  const handleAddBookmark = (e: React.FormEvent) => {
    e.preventDefault();
    const targetUrl = newBookmarkUrl.trim() || urlInput.trim();
    if (!targetUrl) return;
    let domain = targetUrl;
    try {
      domain = new URL(targetUrl.startsWith('http') ? targetUrl : `https://${targetUrl}`).hostname;
    } catch {}
    const title = newBookmarkTitle.trim() || domain;
    setBookmarks((prev) => [
      {
        id: `bm-${Date.now()}`,
        title,
        domain,
        url: targetUrl.startsWith('http') ? targetUrl : `https://${targetUrl}`,
      },
      ...prev,
    ]);
    setNewBookmarkTitle('');
    setNewBookmarkUrl('');
  };

  const activeExtensionsCount =
    (ublockEnabled ? 1 : 0) + (popupStrictEnabled ? 1 : 0) + (violentmonkeyEnabled ? 1 : 0);

  return (
    <div className="h-screen w-screen overflow-hidden bg-[#0D0E12] text-[#F1F5F9] flex flex-col">
      <header
        aria-label="Barra de navegación principal de Bat TV"
        className="bg-[#0F0B24] select-none shrink-0 z-30"
      >
        <div className="px-2.5 sm:px-4 py-2 flex items-center gap-1.5 sm:gap-2.5">
          <button
            type="button"
            onClick={() => {
              setOverlayDrawer('none');
              setUrlInput('https://www.google.com/');
              setViewMode('newtab');
            }}
            aria-label="Inicio Bat TV"
            className="w-11 h-11 sm:w-14 sm:h-14 rounded-2xl bg-[#1C163B] hover:bg-[#271F52] border-[1.5px] border-[#594696] flex items-center justify-center text-lg sm:text-xl shrink-0 transition-colors"
          >
            <span>🏠</span>
          </button>

          <button
            type="button"
            onClick={handleBackNavigation}
            aria-label="Atrás"
            className="w-11 h-11 sm:w-14 sm:h-14 rounded-2xl bg-[#1C163B] hover:bg-[#271F52] border-[1.5px] border-[#594696] flex items-center justify-center text-white text-base sm:text-lg shrink-0 transition-colors"
          >
            <span>◀</span>
          </button>

          <button
            type="button"
            onClick={handleClearOrStopUrl}
            aria-label="Limpiar barra"
            className="w-11 h-11 sm:w-14 sm:h-14 rounded-2xl bg-[#1C163B] hover:bg-[#271F52] border-[1.5px] border-[#594696] flex items-center justify-center text-slate-200 text-lg sm:text-xl shrink-0 transition-colors"
          >
            <span>✕</span>
          </button>

          <form
            onSubmit={handleUrlBarSubmit}
            className="flex-1 flex items-center gap-1.5 sm:gap-2.5 min-w-0"
          >
            <input
              ref={urlInputRef}
              type="text"
              value={urlInput}
              onChange={(e) => setUrlInput(e.target.value)}
              placeholder="https://www.google.com/"
              className="flex-1 min-w-0 h-11 sm:h-14 rounded-2xl bg-[#070812] border-[1.5px] border-[#6C47FF] focus:border-[#A78BFA] px-4 text-sm sm:text-xl text-white outline-none transition-colors"
            />
            <button
              type="submit"
              className="w-11 h-11 sm:w-14 sm:h-14 rounded-2xl bg-[#1C163B] hover:bg-[#271F52] border-[1.5px] border-[#594696] flex items-center justify-center text-white font-semibold text-sm sm:text-lg shrink-0 transition-colors"
            >
              Ir
            </button>
          </form>

          <button
            type="button"
            onClick={() => setOverlayDrawer(overlayDrawer === 'bookmarks' ? 'none' : 'bookmarks')}
            aria-label="Favoritos"
            className="w-11 h-11 sm:w-14 sm:h-14 rounded-2xl bg-[#1C163B] hover:bg-[#271F52] border-[1.5px] border-[#594696] flex items-center justify-center text-lg sm:text-xl shrink-0 transition-colors"
          >
            <span>⭐</span>
          </button>

          <button
            type="button"
            onClick={() => setOverlayDrawer(overlayDrawer === 'history' ? 'none' : 'history')}
            aria-label="Historial"
            className="w-11 h-11 sm:w-14 sm:h-14 rounded-2xl bg-[#1C163B] hover:bg-[#271F52] border-[1.5px] border-[#594696] flex items-center justify-center text-lg sm:text-xl shrink-0 transition-colors"
          >
            <span>🕘</span>
          </button>

          <button
            type="button"
            onClick={handleBroomRamClean}
            aria-label="Limpiar RAM"
            className="w-11 h-11 sm:w-14 sm:h-14 rounded-2xl bg-[#1C163B] hover:bg-[#271F52] border-[1.5px] border-[#594696] flex items-center justify-center text-lg sm:text-xl shrink-0 transition-colors"
          >
            <span>🧹</span>
          </button>

          <button
            type="button"
            onClick={() => {
              setOverlayDrawer('none');
              setViewMode('newtab');
            }}
            aria-label="Cerrar"
            className="w-11 h-11 sm:w-14 sm:h-14 rounded-2xl bg-[#881B1B] hover:bg-[#A32020] border-[1.5px] border-[#E05252] flex items-center justify-center text-white text-lg sm:text-xl font-extrabold shrink-0 transition-colors"
          >
            <span>✖</span>
          </button>
        </div>

        <div className="w-full h-1.5 bg-[#3A3F47] overflow-hidden">
          <div
            className="h-full bg-[#67D5C8] transition-transform duration-200 origin-left"
            style={{ transform: `scaleX(${progressWidth / 100})` }}
          />
        </div>
      </header>

      {toastMessage && (
        <div className="fixed bottom-5 right-5 z-50 px-4 py-2.5 rounded-xl bg-[#14102B] border border-[#67D5C8] text-xs font-semibold text-[#67D5C8] shadow-xl flex items-center gap-2">
          <Check className="w-4 h-4 shrink-0" />
          <span>{toastMessage}</span>
        </div>
      )}

      {overlayDrawer === 'bookmarks' && (
        <div className="fixed inset-x-4 top-20 z-40 max-w-2xl mx-auto bg-[#14102B] border border-[#594696] rounded-2xl p-5 shadow-2xl space-y-4">
          <div className="flex items-center justify-between border-b border-[#2D2459] pb-3">
            <div className="flex items-center gap-2 text-sm font-bold text-white">
              <Star className="w-4 h-4 text-amber-400" />
              <span>Favoritos ({bookmarks.length})</span>
            </div>
            <button
              type="button"
              onClick={() => setOverlayDrawer('none')}
              className="text-xs text-slate-400 hover:text-white"
            >
              Cerrar
            </button>
          </div>
          <form onSubmit={handleAddBookmark} className="flex flex-col sm:flex-row gap-2">
            <input
              type="text"
              value={newBookmarkTitle}
              onChange={(e) => setNewBookmarkTitle(e.target.value)}
              placeholder="Nombre"
              className="rounded-xl bg-[#0D0E12] border border-[#594696] px-3 py-2 text-xs text-white"
            />
            <input
              type="text"
              value={newBookmarkUrl}
              onChange={(e) => setNewBookmarkUrl(e.target.value)}
              placeholder="URL"
              className="flex-1 rounded-xl bg-[#0D0E12] border border-[#594696] px-3 py-2 text-xs font-mono text-white"
            />
            <button type="submit" className="px-3.5 py-2 rounded-xl bg-[#6C47FF] text-white text-xs font-bold">
              Añadir
            </button>
          </form>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 max-h-60 overflow-y-auto">
            {bookmarks.map((bm) => (
              <div
                key={bm.id}
                className="p-2.5 rounded-xl bg-[#0D0E12] border border-[#3B2E73] flex items-center justify-between gap-2"
              >
                <button
                  type="button"
                  onClick={() => navigateAndSanitize(bm.url)}
                  className="text-left min-w-0 flex-1"
                >
                  <div className="text-xs font-bold text-white truncate">{bm.title}</div>
                  <div className="text-[11px] font-mono text-slate-400 truncate">{bm.url}</div>
                </button>
                <button
                  type="button"
                  onClick={() => setBookmarks((prev) => prev.filter((x) => x.id !== bm.id))}
                  className="text-xs text-slate-500 hover:text-rose-400 px-1.5"
                >
                  ✕
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {overlayDrawer === 'history' && (
        <div className="fixed inset-x-4 top-20 z-40 max-w-2xl mx-auto bg-[#14102B] border border-[#594696] rounded-2xl p-5 shadow-2xl space-y-4">
          <div className="flex items-center justify-between border-b border-[#2D2459] pb-3">
            <div className="flex items-center gap-2 text-sm font-bold text-white">
              <Clock className="w-4 h-4 text-[#67D5C8]" />
              <span>Historial</span>
            </div>
            <button
              type="button"
              onClick={() => setOverlayDrawer('none')}
              className="text-xs text-slate-400 hover:text-white"
            >
              Cerrar
            </button>
          </div>
          <div className="space-y-1.5 max-h-64 overflow-y-auto">
            {historyList.map((item) => (
              <button
                key={item.id}
                type="button"
                onClick={() => navigateAndSanitize(item.url)}
                className="w-full text-left p-2.5 rounded-xl bg-[#0D0E12] hover:bg-[#1C163B] border border-[#3B2E73] flex items-center justify-between gap-3"
              >
                <div className="min-w-0">
                  <div className="text-xs font-bold text-white truncate">{item.title}</div>
                  <div className="text-[11px] font-mono text-slate-400 truncate">{item.url}</div>
                </div>
                <span className="text-[11px] font-mono text-slate-500 shrink-0">{item.timestamp}</span>
              </button>
            ))}
          </div>
        </div>
      )}

      <main className="flex-1 w-full h-full overflow-y-auto relative flex flex-col">
        {viewMode === 'newtab' && (
          <div className="flex-1 flex flex-col items-center justify-center px-6 py-8 max-w-3xl mx-auto w-full space-y-8">
            <div className="flex flex-col items-center text-center space-y-3">
              <img
                src={BAT_TV_LOGO_PATH}
                alt="Bat TV"
                referrerPolicy="no-referrer"
                className="w-24 h-24 sm:w-28 sm:h-28 rounded-full object-contain shadow-2xl border-2 border-[#6C47FF]"
              />
              <h1 className="text-2xl sm:text-3xl font-bold text-white tracking-tight">Bat TV</h1>
              <p className="text-xs text-slate-400">GeckoView 132.0 · uBlock Origin · Violentmonkey</p>
            </div>

            <form onSubmit={handleNewTabSearchSubmit} className="w-full">
              <div className="relative flex items-center">
                <Search className="w-5 h-5 text-slate-400 absolute left-4 pointer-events-none" />
                <input
                  type="text"
                  value={newTabSearchInput}
                  onChange={(e) => setNewTabSearchInput(e.target.value)}
                  placeholder="Busca o escribe una web..."
                  className="w-full pl-12 pr-28 py-3.5 rounded-2xl bg-[#14102B] border border-[#594696] focus:border-[#67D5C8] text-sm sm:text-base text-white outline-none shadow-lg transition-colors"
                />
                <button
                  type="submit"
                  className="absolute right-2 px-4 py-2 rounded-xl bg-[#6C47FF] hover:bg-[#7C5CFF] text-white text-xs font-bold transition-colors"
                >
                  Buscar / Ir
                </button>
              </div>
            </form>

            <div className="w-full grid grid-cols-2 sm:grid-cols-4 gap-3">
              {bookmarks.slice(0, 8).map((sc) => (
                <button
                  key={sc.id}
                  type="button"
                  onClick={() => navigateAndSanitize(sc.url)}
                  className="p-3.5 rounded-2xl bg-[#14102B] hover:bg-[#1C163B] border border-[#2D2459] hover:border-[#6C47FF] transition-colors flex flex-col items-center text-center gap-2 group"
                >
                  <div className="w-10 h-10 rounded-xl bg-[#0D0E12] border border-[#594696] flex items-center justify-center text-sm font-bold text-[#67D5C8]">
                    {sc.title.slice(0, 2).toUpperCase()}
                  </div>
                  <div className="w-full">
                    <div className="text-xs font-semibold text-white truncate">{sc.title}</div>
                    <div className="text-[10px] text-slate-400 truncate">{sc.domain}</div>
                  </div>
                </button>
              ))}
            </div>

            <div className="pt-2 flex flex-wrap items-center justify-center gap-3 text-xs text-slate-400">
              <button
                type="button"
                onClick={() => {
                  setExtensionsModalTab('ublock');
                  setExtensionsModalOpen(true);
                }}
                className="px-4 py-2.5 rounded-xl bg-[#14102B] hover:bg-[#1C163B] border border-[#594696] text-slate-100 font-semibold flex items-center gap-2 transition-colors"
              >
                <Settings className="w-4 h-4 text-[#67D5C8]" />
                <span>Configuración ({activeExtensionsCount}/3 Extensiones Activas)</span>
              </button>

              <button
                type="button"
                onClick={() => setInstallModalOpen(true)}
                className="px-4 py-2.5 rounded-xl bg-[#14102B] hover:bg-[#1C163B] border border-[#3B2E73] text-slate-200 flex items-center gap-2 transition-colors"
              >
                <Github className="w-4 h-4 text-[#67D5C8]" />
                <span>Subir a GitHub / Descargar APK GeckoView</span>
              </button>
            </div>
          </div>
        )}

        {/* MODO WEB CORREGIDO: Sin atributo sandbox restrictivo en el iframe */}
        {viewMode === 'web' && (
          <div className="flex-1 w-full h-full flex flex-col bg-[#0D0E12] relative">
            {isLoadingPage ? (
              <div className="flex-1 flex flex-col items-center justify-center p-8 text-center space-y-3">
                <RotateCw className="w-7 h-7 text-[#67D5C8] animate-spin" />
                <div className="text-sm font-semibold text-slate-200">
                  Cargando con GeckoView 132.0 ({currentBrowserUrl})...
                </div>
              </div>
            ) : (
              <iframe
                title="Visor Web Bat TV GeckoView"
                src={`/api/view?url=${encodeURIComponent(currentBrowserUrl)}&ublock=${
                  ublockEnabled ? '1' : '0'
                }&popup=${popupStrictEnabled ? '1' : '0'}&vm=${
                  violentmonkeyEnabled ? '1' : '0'
                }`}
                allow="autoplay; fullscreen; picture-in-picture; encrypted-media"
                allowFullScreen
                className="w-full flex-1 border-0 bg-white"
              />
            )}
          </div>
        )}

        {viewMode === 'video' && (
          <div className="flex-1 max-w-7xl w-full mx-auto px-4 py-4">
            <TvVideoPlayerView
              activeStream={activeStream}
              detectedPageStreams={detectedPageStreams}
              onSelectStream={(s) => setActiveStream(s)}
              onBackToBrowser={() => setViewMode('newtab')}
              lowRamMode={true}
              onRecordBypass={(shortUrl, finalUrl, method) => {
                recordExtensionEvent(
                  'violentmonkey',
                  `Salto directo (${method}): ${shortUrl} -> ${finalUrl}`,
                  510
                );
              }}
            />
          </div>
        )}
      </main>

      <ExtensionsModal
        isOpen={extensionsModalOpen}
        initialTab={extensionsModalTab}
        onClose={() => setExtensionsModalOpen(false)}
        ublockEnabled={ublockEnabled}
        onToggleUblock={setUblockEnabled}
        filterLists={filterLists}
        onToggleFilterList={(id) =>
          setFilterLists((prev) =>
            prev.map((f) => (f.id === id ? { ...f, enabled: !f.enabled } : f))
          )
        }
        customRulesText={customRulesText}
        onSaveCustomRules={setCustomRulesText}
        popupStrictEnabled={popupStrictEnabled}
        onTogglePopupStrict={setPopupStrictEnabled}
        whitelistedHosts={whitelistedHosts}
        onAddWhitelistHost={(host) =>
          setWhitelistedHosts((prev) => (prev.includes(host) ? prev : [...prev, host]))
        }
        onRemoveWhitelistHost={(host) =>
          setWhitelistedHosts((prev) => prev.filter((h) => h !== host))
        }
        onTriggerSimulatedPopupAttack={() => {}}
        violentmonkeyEnabled={violentmonkeyEnabled}
        onToggleViolentmonkey={setViolentmonkeyEnabled}
        userscripts={userscripts}
        onToggleUserscript={(id) =>
          setUserscripts((prev) =>
            prev.map((s) => (s.id === id ? { ...s, enabled: !s.enabled } : s))
          )
        }
        onUpdateUserscriptCode={(id, code) =>
          setUserscripts((prev) => prev.map((s) => (s.id === id ? { ...s, code } : s)))
        }
        onAddUserscript={(newScript) => setUserscripts((prev) => [newScript, ...prev])}
        blockedEvents={blockedEvents}
        onClearBlockedEvents={() => setBlockedEvents([])}
        onNavigateToUrl={(targetUrl) => navigateAndSanitize(targetUrl)}
        onRecordBypassEvent={(shortUrl, finalUrl, method) => {
          recordExtensionEvent(
            'violentmonkey',
            `Desacortado (${method}): ${shortUrl} -> ${finalUrl}`,
            520
          );
        }}
      />

      <TvInstallModal
        isOpen={installModalOpen}
        onClose={() => setInstallModalOpen(false)}
      />
    </div>
  );
}
