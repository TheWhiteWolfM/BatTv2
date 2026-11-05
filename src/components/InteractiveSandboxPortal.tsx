import React, { useState } from 'react';
import {
  Play,
  ShieldCheck,
  Ban,
  Zap,
  ExternalLink,
  AlertTriangle,
  CheckCircle2,
  Tv,
  Film,
  Sparkles,
} from 'lucide-react';
import {
  BUILTIN_MEDIA_CATALOG,
  DetectedStream,
  resolveShortlinkUrl,
  evaluatePopupAttempt,
} from '../engine/extensionEngine';

interface InteractiveSandboxPortalProps {
  ublockEnabled: boolean;
  popupStrictEnabled: boolean;
  violentmonkeyEnabled: boolean;
  onPlayStream: (stream: DetectedStream) => void;
  onRecordEvent: (
    extension: 'ublock' | 'popup-strict' | 'violentmonkey',
    targetOrRule: string,
    ramSavedKb: number
  ) => void;
  onOpenExtensionsModal: (tab: 'ublock' | 'popup-strict' | 'violentmonkey') => void;
}

export const InteractiveSandboxPortal: React.FC<InteractiveSandboxPortalProps> = ({
  ublockEnabled,
  popupStrictEnabled,
  violentmonkeyEnabled,
  onPlayStream,
  onRecordEvent,
  onOpenExtensionsModal,
}) => {
  const [lastActionFeedback, setLastActionFeedback] = useState<{
    title: string;
    detail: string;
    extension: 'ublock' | 'popup-strict' | 'violentmonkey';
  } | null>(null);

  const shortlinkTestCards = [
    {
      id: 'short-ouo',
      shortUrl: 'https://ouo.io/pelicula-1080p',
      label: 'Película: Tears of Steel (1080p)',
      normalWait: '15s de espera + 2 captchas + 3 popups',
      stream: BUILTIN_MEDIA_CATALOG[0],
    },
    {
      id: 'short-adfly',
      shortUrl: 'https://adf.ly/serie-cosmos',
      label: 'Serie: Cosmos Laundromat (S01E01)',
      normalWait: '10s de espera + redirección adserver',
      stream: BUILTIN_MEDIA_CATALOG[2],
    },
    {
      id: 'short-shrinkme',
      shortUrl: 'https://shrinkme.io/directo-motor',
      label: 'Directo TV: Campeonato Mundial Rally',
      normalWait: '20s de espera + 4 capas invisibles',
      stream: BUILTIN_MEDIA_CATALOG[4],
    },
    {
      id: 'short-linkvertise',
      shortUrl: 'https://linkvertise.com/sintel-hd',
      label: 'Película: Sintel Remaster (1080p)',
      normalWait: 'Paso obligatorio de anuncios + temporizador',
      stream: BUILTIN_MEDIA_CATALOG[1],
    },
  ];

  const handleShortlinkClick = (card: (typeof shortlinkTestCards)[number]) => {
    // 1. Popup Blocker (strict) blocks the accompanying popup trap
    if (popupStrictEnabled) {
      const popCheck = evaluatePopupAttempt(
        'https://popads.net/popunder-casino',
        'window.open',
        true
      );
      if (!popCheck.allowed) {
        onRecordEvent(
          'popup-strict',
          `Bloqueado window.open() en ${card.shortUrl}`,
          340
        );
      }
    }

    // 2. Violentmonkey resolves the shortlink in 0ms
    const res = resolveShortlinkUrl(card.shortUrl, violentmonkeyEnabled);
    if (res.bypassed) {
      onRecordEvent(
        'violentmonkey',
        `Salto 0ms: ${card.shortUrl} -> ${res.finalUrl}`,
        520
      );
      setLastActionFeedback({
        title: `Violentmonkey saltó ${res.shortenerHost} en 0ms`,
        detail: `Se evitó: ${card.normalWait}. Abriendo stream 1080p directo: ${card.label}`,
        extension: 'violentmonkey',
      });
      onPlayStream(card.stream);
    } else {
      setLastActionFeedback({
        title: 'Violentmonkey está desactivado',
        detail: 'Activa Violentmonkey para saltar automáticamente la cuenta regresiva del acortador.',
        extension: 'violentmonkey',
      });
    }
  };

  const handleSimulateClickjackTrap = () => {
    if (popupStrictEnabled) {
      onRecordEvent(
        'popup-strict',
        'Eliminada capa invisible clickjacking (z-index: 999999) + bloqueado tab-under a 1xbet',
        450
      );
      if (ublockEnabled) {
        onRecordEvent('ublock', '||onclickalgo.com/popunder.js^', 380);
      }
      setLastActionFeedback({
        title: 'Popup Blocker (strict) + uBlock Origin neutralizaron la trampa',
        detail:
          'Se destruyó la capa transparente sobre el botón Play y se bloqueó la apertura de 2 ventanas emergentes.',
        extension: 'popup-strict',
      });
    } else {
      setLastActionFeedback({
        title: 'Advertencia: Popup Blocker (strict) está pausado',
        detail: 'Un sitio de streaming habría abierto una ventana emergente no deseada.',
        extension: 'popup-strict',
      });
    }
  };

  return (
    <div className="bg-[#111827] border border-slate-800 rounded-xl p-6 space-y-6">
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4 pb-5 border-b border-slate-800">
        <div>
          <h2 className="text-lg font-bold text-slate-100">
            Laboratorio en Vivo: Prueba las 3 Extensiones con el Mando de Google TV
          </h2>
          <p className="text-xs text-slate-400 mt-1">
            Haz clic en cualquier enlace acortado de película, serie o directo para comprobar cómo Violentmonkey salta la espera en 0ms y Popup Blocker (strict) bloquea las ventanas trampa.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-2 shrink-0">
          <button
            type="button"
            onClick={handleSimulateClickjackTrap}
            className="px-3.5 py-2 rounded-lg bg-amber-500/15 border border-amber-500/40 hover:bg-amber-500/25 text-amber-300 text-xs font-semibold transition-colors whitespace-nowrap"
          >
            Simular Clic en Reproductor con Capa Trampa
          </button>
          <button
            type="button"
            onClick={() => onOpenExtensionsModal('violentmonkey')}
            className="px-3.5 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold transition-colors whitespace-nowrap"
          >
            Inspeccionar Script Anti-Acortadores
          </button>
        </div>
      </div>

      {/* Live Action Notification */}
      {lastActionFeedback && (
        <div className="p-4 rounded-lg bg-[#0B0F17] border border-emerald-700/60 flex items-start justify-between gap-4">
          <div className="flex items-start gap-3">
            <CheckCircle2 className="w-5 h-5 text-emerald-400 shrink-0 mt-0.5" />
            <div>
              <div className="text-sm font-bold text-emerald-300">
                {lastActionFeedback.title}
              </div>
              <p className="text-xs text-slate-300 mt-0.5">{lastActionFeedback.detail}</p>
            </div>
          </div>
          <button
            type="button"
            onClick={() => setLastActionFeedback(null)}
            className="text-xs text-slate-400 hover:text-slate-200"
          >
            Ocultar
          </button>
        </div>
      )}

      {/* Grid of Shortened Streaming Links */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {shortlinkTestCards.map((card) => (
          <div
            key={card.id}
            className="p-4 rounded-xl bg-[#0B0F17] border border-slate-800 hover:border-slate-700 transition-colors flex flex-col justify-between gap-4"
          >
            <div className="space-y-1.5">
              <div className="flex items-center justify-between gap-2">
                <span className="text-xs font-mono text-[#38BDF8] truncate">
                  {card.shortUrl}
                </span>
                <span className="text-xs text-emerald-400 font-medium shrink-0">
                  Bypass 0ms Activo
                </span>
              </div>
              <h3 className="text-sm font-bold text-slate-100">{card.label}</h3>
              <p className="text-xs text-slate-400">
                Sin extensión: {card.normalWait} · Con Violentmonkey + uBlock: Reproducción directa inmediata.
              </p>
            </div>

            <div className="flex items-center justify-between pt-2 border-t border-slate-800/80">
              <span className="text-xs text-slate-500">
                {card.stream.category} · {card.stream.quality}
              </span>
              <button
                type="button"
                onClick={() => handleShortlinkClick(card)}
                className="px-3.5 py-2 rounded-lg bg-[#38BDF8] hover:bg-sky-300 text-slate-950 text-xs font-bold flex items-center gap-1.5 transition-colors whitespace-nowrap"
              >
                <Play className="w-3.5 h-3.5" />
                <span>Saltar Acortador y Ver Ahora</span>
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
