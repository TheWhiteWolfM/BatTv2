import React, { useRef, useState } from 'react';
import {
  Play,
  Pause,
  RotateCcw,
  FastForward,
  Maximize2,
  Volume2,
  VolumeX,
  Film,
  Tv,
  Zap,
  ArrowLeft,
} from 'lucide-react';
import {
  DetectedStream,
  BUILTIN_MEDIA_CATALOG,
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

  const toggleFullscreen = () => {
    const el = playerContainerRef.current;
    if (!el) return;
    if (document.fullscreenElement) {
      document.exitFullscreen().catch(() => {});
    } else {
      el.requestFullscreen().catch(() => {});
    }
  };

  const handleLoadCustomStream = (e: React.FormEvent) => {
    e.preventDefault();
    const raw = customStreamInput.trim();
    if (!raw) return;

    // Automatically run Violentmonkey shortlink bypass if user pasted an ouo.io / adf.ly / linkvertise link!
    const bypass = resolveShortlinkUrl(raw, true);
    const finalUrl = bypass.bypassed ? bypass.finalUrl : raw;
    if (bypass.bypassed) {
      onRecordBypass(raw, finalUrl, bypass.method);
    }

    onSelectStream({
      id: `custom-${Date.now()}`,
      title: bypass.bypassed
        ? `Stream Desbloqueado (${bypass.shortenerHost})`
        : 'Stream Directo Personalizado',
      url: finalUrl,
      type: finalUrl.includes('.m3u8') ? 'hls' : 'mp4',
      quality: '1080p Directo · Hardware Decode',
      category: 'Detectado en Página',
      synopsis: `Origen: ${finalUrl}`,
    });
    setCustomStreamInput('');
  };

  return (
    <div className="space-y-6">
      {/* Top Bar Inside Player View */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 bg-[#111827] border border-slate-800 rounded-xl p-4">
        <div className="flex items-center gap-3">
          <button
            type="button"
            onClick={onBackToBrowser}
            className="px-3.5 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-100 text-xs font-semibold flex items-center gap-2 transition-colors whitespace-nowrap shrink-0"
          >
            <ArrowLeft className="w-4 h-4" />
            <span>Volver al Navegador</span>
          </button>
          <div>
            <h2 className="text-base font-bold text-slate-100">{activeStream.title}</h2>
            <div className="text-xs text-slate-400">
              <span>{activeStream.category}</span>
              <span aria-hidden="true"> · </span>
              <span>{activeStream.quality}</span>
              <span aria-hidden="true"> · </span>
              <span>
                {lowRamMode
                  ? 'Decodificación Directa Onn/Chromecast 1080p (Sin DOM Web en segundo plano)'
                  : 'Reproductor HTML5 Nativo'}
              </span>
            </div>
          </div>
        </div>

        {/* Direct URL or Shortened Stream Loader */}
        <form onSubmit={handleLoadCustomStream} className="flex items-center gap-2 w-full sm:w-auto">
          <label htmlFor="direct-stream-url" className="sr-only">
            Pegar enlace MP4, M3U8 o acortador
          </label>
          <input
            id="direct-stream-url"
            type="text"
            value={customStreamInput}
            onChange={(e) => setCustomStreamInput(e.target.value)}
            placeholder="Pegar enlace .mp4, .m3u8 o acortador (ouo.io/pelicula-1080p)..."
            className="w-full sm:w-72 rounded-lg bg-[#0B0F17] border border-slate-700 px-3 py-2 text-xs font-mono text-slate-100"
          />
          <button
            type="submit"
            className="px-3.5 py-2 rounded-lg bg-[#38BDF8] hover:bg-sky-300 text-slate-950 text-xs font-semibold whitespace-nowrap shrink-0"
          >
            Reproducir Directo
          </button>
        </form>
      </div>

      {/* Main 16:9 Cinema Stage + Side Channel/Movie Switcher */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 items-stretch">
        {/* Video Viewport (8 cols on desktop) */}
        <div
          ref={playerContainerRef}
          className="lg:col-span-8 bg-black border border-slate-800 rounded-xl overflow-hidden flex flex-col justify-between"
        >
          <div className="relative aspect-video w-full bg-[#070A10] flex items-center justify-center overflow-hidden group">
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
                // Ensure first frame is visible immediately
                if (e.currentTarget.currentTime === 0) {
                  e.currentTarget.currentTime = 1.5;
                }
              }}
              className={`w-full h-full ${aspectFit === 'cover' ? 'object-cover' : 'object-contain'}`}
            />

            {/* Clean Google TV On-Screen Display (OSD) Info Header */}
            <div className="pointer-events-none absolute top-0 inset-x-0 p-4 bg-gradient-to-b from-black/85 via-black/45 to-transparent flex items-center justify-between gap-4">
              <div>
                <div className="text-xs font-semibold text-[#38BDF8]">
                  {activeStream.category} · {activeStream.quality}
                </div>
                <div className="text-sm sm:text-base font-bold text-white">
                  {activeStream.title}
                </div>
              </div>
              <div className="text-xs font-mono text-emerald-400">
                escudo uBlock + Anti-Popup Activo
              </div>
            </div>
          </div>

          {/* D-Pad Remote Control Action Bar */}
          <div className="p-4 bg-[#111827] border-t border-slate-800 flex flex-wrap items-center justify-between gap-3">
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={togglePlay}
                className="px-4 py-2 rounded-lg bg-[#38BDF8] hover:bg-sky-300 text-slate-950 text-xs font-bold flex items-center gap-1.5 transition-colors whitespace-nowrap"
              >
                {isPlaying ? <Pause className="w-4 h-4" /> : <Play className="w-4 h-4" />}
                <span>{isPlaying ? 'Pausar (OK)' : 'Reproducir (OK)'}</span>
              </button>

              <button
                type="button"
                onClick={() => seekRelative(-10)}
                className="px-3 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium flex items-center gap-1 transition-colors whitespace-nowrap"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                <span>-10s</span>
              </button>

              <button
                type="button"
                onClick={() => seekRelative(10)}
                className="px-3 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium flex items-center gap-1 transition-colors whitespace-nowrap"
              >
                <FastForward className="w-3.5 h-3.5" />
                <span>+10s</span>
              </button>

              <button
                type="button"
                onClick={toggleMute}
                aria-label={isMuted ? 'Activar sonido' : 'Silenciar audio'}
                className="px-3 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium flex items-center gap-1 transition-colors whitespace-nowrap"
              >
                {isMuted ? <VolumeX className="w-4 h-4 text-rose-400" /> : <Volume2 className="w-4 h-4" />}
                <span>{isMuted ? 'Silenciado' : 'Audio 100%'}</span>
              </button>
            </div>

            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={() => setAspectFit(aspectFit === 'contain' ? 'cover' : 'contain')}
                className="px-3 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium whitespace-nowrap"
              >
                Escala: {aspectFit === 'contain' ? '16:9 Original' : 'Zoom Pantalla Completa'}
              </button>

              <button
                type="button"
                onClick={toggleFullscreen}
                className="px-3.5 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-100 text-xs font-semibold flex items-center gap-1.5 whitespace-nowrap"
              >
                <Maximize2 className="w-3.5 h-3.5" />
                <span>Pantalla Completa TV</span>
              </button>
            </div>
          </div>
        </div>

        {/* Side Catalog: Películas, Series & Directos (4 cols on desktop) */}
        <div className="lg:col-span-4 bg-[#111827] border border-slate-800 rounded-xl p-4 flex flex-col justify-between space-y-4">
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <h3 className="text-sm font-bold text-slate-100">
                Catálogo Directo & Streams Detectados
              </h3>
              <span className="text-xs text-slate-400 font-mono tabular-nums">
                {filteredStreams.length} canales
              </span>
            </div>

            {/* Interactive Category Filter */}
            <div className="flex items-center gap-1 p-1 bg-[#0B0F17] rounded-lg border border-slate-800 overflow-x-auto">
              {(['Todos', 'Película', 'Serie', 'Directo TV'] as const).map((cat) => (
                <button
                  key={cat}
                  type="button"
                  onClick={() => setCategoryFilter(cat)}
                  className={`px-2.5 py-1.5 rounded text-xs font-semibold transition-colors whitespace-nowrap shrink-0 ${
                    categoryFilter === cat
                      ? 'bg-[#38BDF8] text-slate-950'
                      : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  {cat}
                </button>
              ))}
            </div>

            {/* Stream List */}
            <div className="space-y-2 max-h-[360px] overflow-y-auto pr-1">
              {filteredStreams.map((item) => {
                const isSelected = item.url === activeStream.url;
                return (
                  <button
                    key={item.id}
                    type="button"
                    onClick={() => onSelectStream(item)}
                    className={`w-full text-left p-3 rounded-lg border transition-colors flex items-start justify-between gap-3 ${
                      isSelected
                        ? 'bg-[#0B0F17] border-[#38BDF8] text-slate-100'
                        : 'bg-[#0B0F17]/60 border-slate-800/90 text-slate-300 hover:border-slate-700'
                    }`}
                  >
                    <div className="min-w-0">
                      <div className="text-xs font-bold text-slate-100 truncate">
                        {item.title}
                      </div>
                      {item.synopsis && (
                        <p className="text-xs text-slate-400 line-clamp-2 mt-0.5">
                          {item.synopsis}
                        </p>
                      )}
                      <div className="text-[11px] text-slate-500 mt-1.5">
                        <span>{item.category}</span>
                        <span aria-hidden="true"> · </span>
                        <span>{item.quality}</span>
                      </div>
                    </div>
                    <div className="shrink-0 mt-0.5">
                      {item.category === 'Directo TV' ? (
                        <Tv className="w-4 h-4 text-[#38BDF8]" />
                      ) : (
                        <Film className="w-4 h-4 text-slate-400" />
                      )}
                    </div>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="pt-3 border-t border-slate-800 text-xs text-slate-400 flex items-center justify-between">
            <span>Aceleración Hardware H.264 / HLS</span>
            <span className="font-mono text-emerald-400 flex items-center gap-1">
              <Zap className="w-3.5 h-3.5" /> 0 Frames Perdidos
            </span>
          </div>
        </div>
      </div>
    </div>
  );
};
