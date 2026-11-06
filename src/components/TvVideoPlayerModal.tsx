import React, { useRef, useState } from 'react';
import {
  Play,
  Pause,
  RotateCcw,
  FastForward,
  Maximize2,
  Minimize2,
  Volume2,
  VolumeX,
  Film,
  Tv,
  Zap,
  ArrowLeft,
  Check,
} from 'lucide-react';
import {
  DetectedStream,
  BUILTIN_MEDIA_CATALOG,
  SUPPORTED_VIDEO_SERVERS,
  VideoServerProfile,
  resolveShortlinkUrl,
} from '../engine/extensionEngine';

interface TvVideoPlayerViewProps {
  activeStream: DetectedStream;
  detectedPageStreams: DetectedStream[];
  onSelectStream: (stream: DetectedStream) => void;
  onBackToBrowser: () => void;
  lowRamMode: boolean;
  onRecordBypass: (shortUrl: string, finalUrl: string, method: string) => void;
}

export const TvVideoPlayerView: React.FC<TvVideoPlayerViewProps> = ({
  activeStream,
  detectedPageStreams,
  onSelectStream,
  onBackToBrowser,
  lowRamMode,
  onRecordBypass,
}) => {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const playerContainerRef = useRef<HTMLDivElement | null>(null);
  const [isPlaying, setIsPlaying] = useState(true);
  const [isMuted, setIsMuted] = useState(false);
  const [customStreamInput, setCustomStreamInput] = useState('');
  const [aspectFit, setAspectFit] = useState<'contain' | 'cover'>('contain');
  const [categoryFilter, setCategoryFilter] = useState<'Todos' | 'Película' | 'Serie' | 'Directo TV'>('Todos');

  // Auto-Fullscreen when player loads (enabled by default as requested)
  const [autoFullscreenOnLoad, setAutoFullscreenOnLoad] = useState<boolean>(true);
  const [isCinemaFullscreen, setIsCinemaFullscreen] = useState<boolean>(false);
  const [autoFsTriggeredNotice, setAutoFsTriggeredNotice] = useState<boolean>(false);
  const [selectedServerProfile, setSelectedServerProfile] = useState<VideoServerProfile>(
    SUPPORTED_VIDEO_SERVERS[0]
  );

  const allStreams = [...detectedPageStreams, ...BUILTIN_MEDIA_CATALOG];
  const filteredStreams =
    categoryFilter === 'Todos'
      ? allStreams
      : allStreams.filter((s) => s.category === categoryFilter);

  const togglePlay = () => {
    const v = videoRef.current;
    if (!v) return;
    if (v.paused) {
      v.play().catch(() => {});
      setIsPlaying(true);
    } else {
      v.pause();
      setIsPlaying(false);
    }
  };

  const seekRelative = (seconds: number) => {
    const v = videoRef.current;
    if (!v) return;
    v.currentTime = Math.max(0, v.currentTime + seconds);
  };

  const toggleMute = () => {
    const v = videoRef.current;
    if (!v) return;
    v.muted = !v.muted;
    setIsMuted(v.muted);
  };

  const toggleBottomRightFullscreen = () => {
    const el = playerContainerRef.current;
    const nextState = !isCinemaFullscreen;
    setIsCinemaFullscreen(nextState);

    if (el) {
      if (nextState && !document.fullscreenElement && el.requestFullscreen) {
        el.requestFullscreen().catch(() => {});
      } else if (!nextState && document.fullscreenElement) {
        document.exitFullscreen().catch(() => {});
      }
    }
  };

  const handleServerQuickSwitch = (srv: VideoServerProfile, idx: number) => {
    setSelectedServerProfile(srv);
    const targetCatalogItem = BUILTIN_MEDIA_CATALOG[idx % BUILTIN_MEDIA_CATALOG.length];
    onSelectStream({
      ...targetCatalogItem,
      id: `srv-${srv.id}-${Date.now()}`,
      title: `${srv.name} — ${targetCatalogItem.title.split('—')[1]?.trim() || 'Stream 1080p'}`,
      quality: `${srv.maxResolution} · ${srv.fps}fps · ${srv.hardwareDecodeCodec}`,
    });
  };

  const handleLoadCustomStream = (e: React.FormEvent) => {
    e.preventDefault();
    const raw = customStreamInput.trim();
    if (!raw) return;

    const bypass = resolveShortlinkUrl(raw, true);
    const finalUrl = bypass.bypassed ? bypass.finalUrl : raw;
    if (bypass.bypassed) {
      onRecordBypass(raw, finalUrl, bypass.method);
    }

    onSelectStream({
      id: `custom-${Date.now()}`,
      title: bypass.bypassed
        ? `Stream Desbloqueado (${bypass.shortenerHost})`
        : 'Stream Directo 1080p 60fps',
      url: finalUrl,
      type: finalUrl.includes('.m3u8') ? 'hls' : 'mp4',
      quality: '1080p 60fps · Hardware Decode',
      category: 'Detectado en Página',
      synopsis: `Origen: ${finalUrl}`,
    });
    setCustomStreamInput('');
  };

  return (
    <div className="space-y-4">
      {/* Top Bar Inside Player View */}
      <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-3 bg-[#14102B] border border-[#594696] rounded-2xl p-4">
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={onBackToBrowser}
            className="px-3.5 py-2 rounded-xl bg-[#1C163B] hover:bg-[#271F52] border border-[#594696] text-white text-xs font-bold flex items-center gap-2 transition-colors whitespace-nowrap shrink-0"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Volver al Navegador</span>
          </button>
          <div>
            <h2 className="text-sm sm:text-base font-bold text-white">{activeStream.title}</h2>
            <div className="text-xs text-slate-300">
              <span>{activeStream.category}</span>
              <span aria-hidden="true"> · </span>
              <span className="text-[#67D5C8] font-semibold">{activeStream.quality}</span>
              <span aria-hidden="true"> · </span>
              <span>
                {lowRamMode
                  ? 'Motor GeckoView 1080p 60fps (Zero-Lag Hardware Buffer)'
                  : 'Reproductor HTML5 Nativo'}
              </span>
            </div>
          </div>
        </div>

        {/* Auto-Fullscreen Toggle + Direct URL Loader */}
        <div className="flex flex-wrap items-center gap-2.5">
          <button
            type="button"
            onClick={() => setAutoFullscreenOnLoad(!autoFullscreenOnLoad)}
            className={`px-3 py-2 rounded-xl text-xs font-bold border flex items-center gap-1.5 transition-colors ${
              autoFullscreenOnLoad
                ? 'bg-emerald-500/20 border-emerald-400 text-emerald-300'
                : 'bg-[#0D0E12] border-[#594696] text-slate-400'
            }`}
          >
            <Check className="w-3.5 h-3.5" />
            <span>
              Auto-Pantalla Completa (Inferior Derecha): {autoFullscreenOnLoad ? 'ACTIVO' : 'OFF'}
            </span>
          </button>

          <form onSubmit={handleLoadCustomStream} className="flex items-center gap-2">
            <label htmlFor="direct-stream-url" className="sr-only">
              Pegar enlace MP4, M3U8 o acortador
            </label>
            <input
              id="direct-stream-url"
              type="text"
              value={customStreamInput}
              onChange={(e) => setCustomStreamInput(e.target.value)}
              placeholder="Pegar VOE, Streamtape, .m3u8 o acortador..."
              className="w-48 sm:w-64 rounded-xl bg-[#070812] border border-[#594696] px-3 py-2 text-xs font-mono text-white"
            />
            <button
              type="submit"
              className="px-3.5 py-2 rounded-xl bg-[#6C47FF] hover:bg-[#7C5CFF] text-white text-xs font-bold whitespace-nowrap shrink-0"
            >
              Cargar 1080p
            </button>
          </form>
        </div>
      </div>

      {/* Multi-Server 1080p 60fps Optimizer Bar (VOE, Streamtape, Streamwish, OK.ru, Byse, Primeload, KissKH, Twitch) */}
      <div className="bg-[#14102B] border border-[#3B2E73] rounded-2xl p-3 flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 sm:pb-0">
          <span className="text-xs font-bold text-slate-300 mr-1 shrink-0">
            Servidores 1080p 60fps:
          </span>
          {SUPPORTED_VIDEO_SERVERS.map((srv, idx) => {
            const isSelected = selectedServerProfile.id === srv.id;
            return (
              <button
                key={srv.id}
                type="button"
                onClick={() => handleServerQuickSwitch(srv, idx)}
                className={`px-3 py-1.5 rounded-xl text-xs font-bold transition-colors shrink-0 ${
                  isSelected
                    ? 'bg-[#6C47FF] text-white border border-[#A78BFA]'
                    : 'bg-[#0D0E12] text-slate-300 border border-[#3B2E73] hover:border-[#67D5C8]'
                }`}
              >
                {srv.name.split(' ')[0]}
              </button>
            );
          })}
        </div>
        <div className="text-[11px] font-mono text-[#67D5C8] flex items-center gap-1.5">
          <Zap className="w-3.5 h-3.5" />
          <span>{selectedServerProfile.hardwareDecodeCodec}</span>
        </div>
      </div>

      {/* Main 16:9 Cinema Stage + Side Channel/Movie Switcher */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-5 items-stretch">
        {/* Video Viewport (Expands to fixed 100vw/100vh when Auto-Fullscreen triggers!) */}
        <div
          ref={playerContainerRef}
          className={
            isCinemaFullscreen
              ? 'fixed inset-0 z-50 bg-black flex flex-col justify-between'
              : 'lg:col-span-8 bg-black border border-[#594696] rounded-2xl overflow-hidden flex flex-col justify-between'
          }
        >
          <div className="relative flex-1 aspect-video w-full bg-[#070A10] flex items-center justify-center overflow-hidden group">
            <video
              ref={videoRef}
              key={activeStream.url}
              src={activeStream.url}
              autoPlay
              muted={isMuted}
              playsInline
              controls
              onPlay={() => setIsPlaying(true)}
              onPause={() => setIsPlaying(false)}
              onLoadedData={(e) => {
                if (e.currentTarget.currentTime === 0) {
                  e.currentTarget.currentTime = 1.5;
                }
                // Automatically trigger bottom-right Fullscreen when video loads well!
                if (autoFullscreenOnLoad) {
                  setIsCinemaFullscreen(true);
                  setAutoFsTriggeredNotice(true);
                  setTimeout(() => setAutoFsTriggeredNotice(false), 3500);
                  if (playerContainerRef.current?.requestFullscreen && !document.fullscreenElement) {
                    playerContainerRef.current.requestFullscreen().catch(() => {});
                  }
                }
              }}
              className={`w-full h-full ${aspectFit === 'cover' ? 'object-cover' : 'object-contain'}`}
            />

            {/* Clean Google TV On-Screen Display (OSD) Info Header */}
            <div className="pointer-events-none absolute top-0 inset-x-0 p-4 bg-gradient-to-b from-black/85 via-black/45 to-transparent flex items-center justify-between gap-4">
              <div>
                <div className="text-xs font-semibold text-[#67D5C8]">
                  {selectedServerProfile.name} · {selectedServerProfile.maxResolution} · {selectedServerProfile.fps}fps
                </div>
                <div className="text-sm sm:text-base font-bold text-white">
                  {activeStream.title}
                </div>
              </div>
              <div className="text-xs font-mono text-emerald-400">
                uBlock + Anti-Popup + 1080p60 Activo
              </div>
            </div>

            {/* Bottom-Right Auto-Fullscreen Notification & Prominent Bottom-Right Fullscreen Button */}
            <div className="absolute bottom-14 right-4 z-20 flex items-center gap-2">
              {autoFsTriggeredNotice && (
                <div className="px-3 py-1.5 rounded-xl bg-emerald-500/95 text-slate-950 text-xs font-extrabold shadow-xl flex items-center gap-1.5">
                  <Check className="w-4 h-4" />
                  <span>Auto-Pantalla Completa Activada (1080p 60fps)</span>
                </div>
              )}
              <button
                type="button"
                onClick={toggleBottomRightFullscreen}
                aria-label="Botón de Pantalla Completa (Esquina Inferior Derecha)"
                title="Pantalla Completa (Esquina Inferior Derecha)"
                className="px-3.5 py-2 rounded-xl bg-[#14102B]/90 hover:bg-[#6C47FF] border border-[#67D5C8] text-white text-xs font-bold shadow-2xl flex items-center gap-1.5 transition-colors"
              >
                {isCinemaFullscreen ? (
                  <>
                    <Minimize2 className="w-4 h-4 text-[#67D5C8]" />
                    <span>Salir de Pantalla Completa</span>
                  </>
                ) : (
                  <>
                    <Maximize2 className="w-4 h-4 text-[#67D5C8]" />
                    <span>⛶ Pantalla Completa (Auto)</span>
                  </>
                )}
              </button>
            </div>
          </div>

          {/* D-Pad Remote Control Action Bar (Bottom-Right Fullscreen Button sits at the far right) */}
          <div className="p-3.5 bg-[#14102B] border-t border-[#3B2E73] flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={togglePlay}
                className="px-4 py-2 rounded-xl bg-[#67D5C8] hover:bg-teal-300 text-slate-950 text-xs font-bold flex items-center gap-1.5 transition-colors whitespace-nowrap"
              >
                {isPlaying ? <Pause className="w-4 h-4" /> : <Play className="w-4 h-4" />}
                <span>{isPlaying ? 'Pausar (OK)' : 'Reproducir (OK)'}</span>
              </button>

              <button
                type="button"
                onClick={() => seekRelative(-10)}
                className="px-3 py-2 rounded-xl bg-[#1C163B] hover:bg-[#271F52] text-slate-200 text-xs font-medium flex items-center gap-1 transition-colors whitespace-nowrap"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                <span>-10s</span>
              </button>

              <button
                type="button"
                onClick={() => seekRelative(10)}
                className="px-3 py-2 rounded-xl bg-[#1C163B] hover:bg-[#271F52] text-slate-200 text-xs font-medium flex items-center gap-1 transition-colors whitespace-nowrap"
              >
                <FastForward className="w-3.5 h-3.5" />
                <span>+10s</span>
              </button>

              <button
                type="button"
                onClick={toggleMute}
                aria-label={isMuted ? 'Activar sonido' : 'Silenciar audio'}
                className="px-3 py-2 rounded-xl bg-[#1C163B] hover:bg-[#271F52] text-slate-200 text-xs font-medium flex items-center gap-1 transition-colors whitespace-nowrap"
              >
                {isMuted ? <VolumeX className="w-4 h-4 text-rose-400" /> : <Volume2 className="w-4 h-4" />}
                <span>{isMuted ? 'Silenciado' : 'Audio 100%'}</span>
              </button>
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setAspectFit(aspectFit === 'contain' ? 'cover' : 'contain')}
                className="px-3 py-2 rounded-xl bg-[#1C163B] hover:bg-[#271F52] text-slate-200 text-xs font-medium whitespace-nowrap"
              >
                Escala: {aspectFit === 'contain' ? '16:9 Original' : 'Zoom TV'}
              </button>

              <button
                type="button"
                onClick={toggleBottomRightFullscreen}
                className="px-3.5 py-2 rounded-xl bg-[#6C47FF] hover:bg-[#7C5CFF] text-white text-xs font-bold flex items-center gap-1.5 whitespace-nowrap"
              >
                {isCinemaFullscreen ? (
                  <>
                    <Minimize2 className="w-3.5 h-3.5" />
                    <span>Restaurar Vista</span>
                  </>
                ) : (
                  <>
                    <Maximize2 className="w-3.5 h-3.5" />
                    <span>Pantalla Completa (Inferior Derecha)</span>
                  </>
                )}
              </button>
            </div>
          </div>
        </div>

        {/* Side Catalog: Películas, Series & Directos (4 cols on desktop) */}
        <div className="lg:col-span-4 bg-[#14102B] border border-[#594696] rounded-2xl p-4 flex flex-col justify-between space-y-4">
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold text-white">
                Servidores Activos & Directos 1080p60
              </h3>
              <span className="text-xs text-[#67D5C8] font-mono tabular-nums">
                {filteredStreams.length} streams
              </span>
            </div>

            {/* Interactive Category Filter */}
            <div className="flex items-center gap-1 p-1 bg-[#070812] rounded-xl border border-[#3B2E73] overflow-x-auto">
              {(['Todos', 'Película', 'Serie', 'Directo TV'] as const).map((cat) => (
                <button
                  key={cat}
                  type="button"
                  onClick={() => setCategoryFilter(cat)}
                  className={`px-2.5 py-1.5 rounded-lg text-xs font-semibold transition-colors whitespace-nowrap shrink-0 ${
                    categoryFilter === cat
                      ? 'bg-[#6C47FF] text-white'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  {cat}
                </button>
              ))}
            </div>

            {/* Stream List */}
            <div className="space-y-2 max-h-[340px] overflow-y-auto pr-1">
              {filteredStreams.map((item) => {
                const isSelected = item.url === activeStream.url && item.id === activeStream.id;
                return (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => onSelectStream(item)}
                    className={`w-full text-left p-3 rounded-xl border transition-colors flex items-start justify-between gap-3 ${
                      isSelected
                        ? 'bg-[#1C163B] border-[#67D5C8] text-white'
                        : 'bg-[#070812]/80 border-[#2D2459] text-slate-300 hover:border-[#6C47FF]'
                    }`}
                  >
                    <div className="min-w-0">
                      <div className="text-xs font-bold text-white truncate">
                        {item.title}
                      </div>
                      {item.synopsis && (
                        <p className="text-xs text-slate-400 line-clamp-2 mt-0.5">
                          {item.synopsis}
                        </p>
                      )}
                      <div className="text-[11px] text-[#67D5C8] mt-1.5 font-mono">
                        <span>{item.category}</span>
                        <span aria-hidden="true"> · </span>
                        <span>{item.quality}</span>
                      </div>
                    </div>
                    <div className="shrink-0 mt-0.5">
                      {item.category === 'Directo TV' ? (
                        <Tv className="w-4 h-4 text-[#67D5C8]" />
                      ) : (
                        <Film className="w-4 h-4 text-slate-400" />
                      )}
                    </div>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="pt-3 border-t border-[#2D2459] text-xs text-slate-300 flex items-center justify-between">
            <span>Decodificador Hardware 1080p 60fps</span>
            <span className="font-mono text-emerald-400 flex items-center gap-1">
              <Zap className="w-3.5 h-3.5" /> 0 Lag / 0 Drops
            </span>
          </div>
        </div>
      </div>
    </div>
  );
};
