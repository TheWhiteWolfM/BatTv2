import React, { useEffect, useState } from 'react';
import { Download, Tv, Copy, Check, X, FileCode2, Sparkles, Github } from 'lucide-react';
import { DEFAULT_USERSCRIPTS } from '../engine/extensionEngine';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed'; platform: string }>;
}

export function usePWAInstall() {
  const [deferredPrompt, setDeferredPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [isInstalled, setIsInstalled] = useState(false);

  useEffect(() => {
    const isStandalone =
      window.matchMedia('(display-mode: standalone)').matches ||
      (window.navigator as unknown as { standalone?: boolean }).standalone === true;
    setIsInstalled(isStandalone);

    const handleBeforeInstallPrompt = (e: Event) => {
      e.preventDefault();
      setDeferredPrompt(e as BeforeInstallPromptEvent);
    };

    const handleAppInstalled = () => {
      setIsInstalled(true);
      setDeferredPrompt(null);
    };

    window.addEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
    window.addEventListener('appinstalled', handleAppInstalled);

    return () => {
      window.removeEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
      window.removeEventListener('appinstalled', handleAppInstalled);
    };
  }, []);

  const install = async () => {
    if (!deferredPrompt) return false;
    await deferredPrompt.prompt();
    const { outcome } = await deferredPrompt.userChoice;
    if (outcome === 'accepted') {
      setIsInstalled(true);
      setDeferredPrompt(null);
      return true;
    }
    return false;
  };

  return {
    isInstallable: !!deferredPrompt,
    isInstalled,
    install,
  };
}

interface TvInstallModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const TvInstallModal: React.FC<TvInstallModalProps> = ({ isOpen, onClose }) => {
  const { isInstallable, isInstalled, install } = usePWAInstall();
  const [copiedUrl, setCopiedUrl] = useState(false);
  const [downloadedLauncher, setDownloadedLauncher] = useState(false);
  const [downloadedGecko, setDownloadedGecko] = useState(false);
  const [downloadedUserscript, setDownloadedUserscript] = useState(false);

  if (!isOpen) return null;

  const appUrl =
    typeof window !== 'undefined'
      ? window.location.origin
      : 'https://ais-pre-eiywtyzmhw5aipfwkeab4u-210152158410.us-east1.run.app';

  const handleCopyUrl = () => {
    navigator.clipboard?.writeText(appUrl).catch(() => {});
    setCopiedUrl(true);
    setTimeout(() => setCopiedUrl(false), 2500);
  };

  const handleDownloadStandaloneTvLauncher = () => {
    const launcherHtml = `<!doctype html>
<html lang="es">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
  <title>Bat TV — GeckoView Edition</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body, html { width: 100%; height: 100%; background: #0D0E12; color: #F1F5F9; font-family: system-ui, sans-serif; overflow: hidden; }
    iframe { width: 100%; height: 100%; border: none; display: block; }
  </style>
</head>
<body>
  <iframe src="${appUrl}" allow="autoplay; fullscreen" allowfullscreen></iframe>
</body>
</html>`;

    const blob = new Blob([launcherHtml], { type: 'text/html;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'BatTV_GoogleTV_Launcher.html';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    setDownloadedLauncher(true);
    setTimeout(() => setDownloadedLauncher(false), 3000);
  };

  const handleDownloadGeckoViewProject = async () => {
    try {
      const resp = await fetch('/api/geckoview-project');
      const data = await resp.json();
      const blob = new Blob([JSON.stringify(data, null, 2)], {
        type: 'application/json;charset=utf-8',
      });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'BatTV-GeckoView-132-AndroidTV-GitHub.json';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      setDownloadedGecko(true);
      setTimeout(() => setDownloadedGecko(false), 3000);
    } catch {
      // ignore
    }
  };

  const handleDownloadUserscriptFile = () => {
    const bypassScript = DEFAULT_USERSCRIPTS[0]?.code || '';
    const blob = new Blob([bypassScript], { type: 'text/javascript;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'BatTV_Bypass_All_Shortlinks.user.js';
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
    setDownloadedUserscript(true);
    setTimeout(() => setDownloadedUserscript(false), 3000);
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="tv-install-modal-title"
    >
      <div className="w-full max-w-4xl bg-[#14102B] border border-[#594696] rounded-2xl overflow-hidden flex flex-col max-h-[90vh]">
        <div className="flex items-center justify-between px-6 py-4 border-b border-[#2D2459] bg-[#0F0B24]">
          <div className="flex items-center gap-3">
            <Github className="w-5 h-5 text-[#67D5C8]" />
            <div>
              <h2 id="tv-install-modal-title" className="text-lg font-bold text-white">
                Exportar a GitHub y Descargar Bat TV (Motor GeckoView 132.0)
              </h2>
              <p className="text-xs text-slate-400">
                Listo para subir a GitHub con flujo automático de GitHub Actions y paquete nativo Mozilla GeckoView
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar guía de GitHub y TV"
            className="px-3 py-2 rounded-xl bg-[#1C163B] hover:bg-[#271F52] border border-[#594696] text-slate-200 text-xs font-semibold flex items-center gap-1.5"
          >
            <X className="w-4 h-4" />
            <span>Cerrar</span>
          </button>
        </div>

        <div className="p-6 overflow-y-auto space-y-6">
          {/* GitHub Export Instructions */}
          <div className="p-4 rounded-xl bg-[#0D0E12] border border-[#67D5C8]/60 space-y-2">
            <div className="text-xs font-bold text-[#67D5C8] flex items-center gap-2">
              <Github className="w-4 h-4" />
              <span>Cómo subirlo a tu cuenta de GitHub en 1 clic:</span>
            </div>
            <p className="text-xs text-slate-300 leading-relaxed">
              Por seguridad, yo no tengo acceso directo a las contraseñas de tu cuenta personal de GitHub, pero ya dejé creados en este proyecto la carpeta <code className="text-white">/android-geckoview</code> y el flujo automático <code className="text-white">.github/workflows/build-apk.yml</code>. Solo haz clic en el <strong>icono de GitHub / Exportar</strong> en la esquina superior derecha de esta ventana de Google AI Studio para conectarlo y subirlo directo a tu repositorio de GitHub, o descarga los paquetes aquí abajo:
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="p-4 rounded-xl bg-[#0D0E12] border border-[#3B2E73] flex flex-col justify-between gap-3">
              <div>
                <div className="text-xs font-semibold text-[#67D5C8]">
                  1. Código Nativo GeckoView 132.0
                </div>
                <h3 className="text-sm font-bold text-white mt-1">
                  Proyecto Android + GitHub Actions
                </h3>
                <p className="text-xs text-slate-400 mt-1">
                  Incluye <code className="text-slate-200">org.mozilla.geckoview:geckoview:132.0</code> con uBlock Origin, Popup Blocker (strict) y Violentmonkey integrados.
                </p>
              </div>
              <button
                type="button"
                onClick={handleDownloadGeckoViewProject}
                className="w-full py-2.5 px-3 rounded-xl bg-[#6C47FF] hover:bg-[#7C5CFF] text-white text-xs font-bold flex items-center justify-center gap-1.5 transition-colors"
              >
                {downloadedGecko ? <Check className="w-4 h-4" /> : <Github className="w-4 h-4" />}
                <span>
                  {downloadedGecko ? '¡Proyecto Descargado!' : 'Descargar Fuente GeckoView'}
                </span>
              </button>
            </div>

            <div className="p-4 rounded-xl bg-[#0D0E12] border border-[#3B2E73] flex flex-col justify-between gap-3">
              <div>
                <div className="text-xs font-semibold text-pink-300">
                  2. Lanzador Directo para TV
                </div>
                <h3 className="text-sm font-bold text-white mt-1">
                  Archivo Portátil Bat TV (.html)
                </h3>
                <p className="text-xs text-slate-400 mt-1">
                  Descarga el lanzador ligero de 2 KB o instala la PWA para abrirlo directamente en tu Chromecast 1080p u Onn TV.
                </p>
              </div>
              {isInstallable && !isInstalled ? (
                <button
                  type="button"
                  onClick={install}
                  className="w-full py-2.5 px-3 rounded-xl bg-[#3D143C] hover:bg-[#521B50] border border-[#C84B9E] text-pink-100 text-xs font-bold flex items-center justify-center gap-1.5 transition-colors"
                >
                  <Sparkles className="w-4 h-4" />
                  <span>Instalar App Ahora</span>
                </button>
              ) : (
                <button
                  type="button"
                  onClick={handleDownloadStandaloneTvLauncher}
                  className="w-full py-2.5 px-3 rounded-xl bg-[#3D143C] hover:bg-[#521B50] border border-[#C84B9E] text-pink-100 text-xs font-bold flex items-center justify-center gap-1.5 transition-colors"
                >
                  {downloadedLauncher ? <Check className="w-4 h-4" /> : <Download className="w-4 h-4" />}
                  <span>
                    {downloadedLauncher ? '¡Lanzador Descargado!' : 'Descargar Lanzador TV (.html)'}
                  </span>
                </button>
              )}
            </div>

            <div className="p-4 rounded-xl bg-[#0D0E12] border border-[#3B2E73] flex flex-col justify-between gap-3">
              <div>
                <div className="text-xs font-semibold text-emerald-400">
                  3. Script Violentmonkey
                </div>
                <h3 className="text-sm font-bold text-white mt-1">
                  Bypass All Shortlinks (.user.js)
                </h3>
                <p className="text-xs text-slate-400 mt-1">
                  Descarga el userscript independiente para importarlo en Firefox / GeckoView con Violentmonkey.
                </p>
              </div>
              <button
                type="button"
                onClick={handleDownloadUserscriptFile}
                className="w-full py-2.5 px-3 rounded-xl bg-[#1C163B] hover:bg-[#271F52] border border-[#594696] text-slate-100 text-xs font-bold flex items-center justify-center gap-1.5 transition-colors"
              >
                {downloadedUserscript ? (
                  <Check className="w-4 h-4 text-emerald-400" />
                ) : (
                  <FileCode2 className="w-4 h-4" />
                )}
                <span>
                  {downloadedUserscript ? '¡Script Descargado!' : 'Descargar Script (.user.js)'}
                </span>
              </button>
            </div>
          </div>

          <div className="p-4 rounded-xl bg-[#0D0E12] border border-[#594696] space-y-2">
            <div className="text-xs font-semibold text-slate-300">
              URL Pública de Bat TV (para Downloader en Google TV o generar APK en PWABuilder.com):
            </div>
            <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2">
              <input
                type="text"
                readOnly
                value={appUrl}
                aria-label="Enlace directo de Bat TV"
                className="flex-1 rounded-xl bg-[#14102B] border border-[#3B2E73] px-3.5 py-2 text-xs font-mono text-[#67D5C8]"
              />
              <button
                type="button"
                onClick={handleCopyUrl}
                className="px-4 py-2 rounded-xl bg-[#6C47FF] hover:bg-[#7C5CFF] text-white text-xs font-bold flex items-center justify-center gap-1.5 whitespace-nowrap"
              >
                {copiedUrl ? <Check className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                <span>{copiedUrl ? '¡Copiado!' : 'Copiar URL'}</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
