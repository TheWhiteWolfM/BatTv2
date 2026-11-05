import React, { useState } from 'react';
import {
  ShieldCheck,
  ShieldAlert,
  Ban,
  Code2,
  Check,
  Plus,
  Trash2,
  Zap,
  ExternalLink,
  X,
  RotateCcw,
} from 'lucide-react';
import {
  FilterListConfig,
  UserscriptItem,
  BlockedEvent,
  resolveShortlinkUrl,
} from '../engine/extensionEngine';

interface ExtensionsModalProps {
  isOpen: boolean;
  initialTab: 'ublock' | 'popup-strict' | 'violentmonkey';
  onClose: () => void;
  ublockEnabled: boolean;
  onToggleUblock: (val: boolean) => void;
  filterLists: FilterListConfig[];
  onToggleFilterList: (id: string) => void;
  customRulesText: string;
  onSaveCustomRules: (text: string) => void;
  popupStrictEnabled: boolean;
  onTogglePopupStrict: (val: boolean) => void;
  whitelistedHosts: string[];
  onAddWhitelistHost: (host: string) => void;
  onRemoveWhitelistHost: (host: string) => void;
  onTriggerSimulatedPopupAttack: () => void;
  violentmonkeyEnabled: boolean;
  onToggleViolentmonkey: (val: boolean) => void;
  userscripts: UserscriptItem[];
  onToggleUserscript: (id: string) => void;
  onUpdateUserscriptCode: (id: string, code: string) => void;
  onAddUserscript: (script: UserscriptItem) => void;
  blockedEvents: BlockedEvent[];
  onClearBlockedEvents: () => void;
  onNavigateToUrl: (url: string) => void;
  onRecordBypassEvent: (shortUrl: string, finalUrl: string, method: string) => void;
}

export const ExtensionsModal: React.FC<ExtensionsModalProps> = ({
  isOpen,
  initialTab,
  onClose,
  ublockEnabled,
  onToggleUblock,
  filterLists,
  onToggleFilterList,
  customRulesText,
  onSaveCustomRules,
  popupStrictEnabled,
  onTogglePopupStrict,
  whitelistedHosts,
  onAddWhitelistHost,
  onRemoveWhitelistHost,
  onTriggerSimulatedPopupAttack,
  violentmonkeyEnabled,
  onToggleViolentmonkey,
  userscripts,
  onToggleUserscript,
  onUpdateUserscriptCode,
  onAddUserscript,
  blockedEvents,
  onClearBlockedEvents,
  onNavigateToUrl,
  onRecordBypassEvent,
}) => {
  const [activeTab, setActiveTab] = useState<'ublock' | 'popup-strict' | 'violentmonkey'>(initialTab);
  const [rulesDraft, setRulesDraft] = useState(customRulesText);
  const [rulesSavedNotice, setRulesSavedNotice] = useState(false);
  const [newWhitelistDomain, setNewWhitelistDomain] = useState('');
  const [shortlinkTestInput, setShortlinkTestInput] = useState(
    'https://ouo.io/qs/9841?url=https%3A%2F%2Fcommondatastorage.googleapis.com%2Fgtv-videos-bucket%2Fsample%2FTearsOfSteel.mp4'
  );
  const [unshortenResult, setUnshortenResult] = useState<{
    finalUrl: string;
    method: string;
    bypassed: boolean;
  } | null>(null);
  const [editingScriptId, setEditingScriptId] = useState<string | null>(null);
  const [editingScriptCode, setEditingScriptCode] = useState('');
  const [newScriptName, setNewScriptName] = useState('');
  const [newScriptDesc, setNewScriptDesc] = useState('');
  const [showNewScriptForm, setShowNewScriptForm] = useState(false);

  React.useEffect(() => {
    setActiveTab(initialTab);
  }, [initialTab]);

  React.useEffect(() => {
    setRulesDraft(customRulesText);
  }, [customRulesText]);

  if (!isOpen) return null;

  const handleSaveRules = (e: React.FormEvent) => {
    e.preventDefault();
    onSaveCustomRules(rulesDraft);
    setRulesSavedNotice(true);
    setTimeout(() => setRulesSavedNotice(false), 2000);
  };

  const handleWhitelistSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const cleaned = newWhitelistDomain.trim().replace(/^https?:\/\//i, '').replace(/\/.*$/, '');
    if (!cleaned) return;
    onAddWhitelistHost(cleaned);
    setNewWhitelistDomain('');
  };

  const handleUnshortenNow = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = shortlinkTestInput.trim();
    if (!trimmed) return;

    const local = resolveShortlinkUrl(trimmed, true);
    if (local.bypassed) {
      setUnshortenResult({
        finalUrl: local.finalUrl,
        method: local.method,
        bypassed: true,
      });
      onRecordBypassEvent(trimmed, local.finalUrl, local.method);
      return;
    }

    try {
      const resp = await fetch('/api/unshorten', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url: trimmed, violentmonkeyEnabled: true }),
      });
      const data = await resp.json();
      setUnshortenResult({
        finalUrl: data.finalUrl || trimmed,
        method: data.method || 'Inspección directa',
        bypassed: Boolean(data.bypassed),
      });
      if (data.bypassed && data.finalUrl) {
        onRecordBypassEvent(trimmed, data.finalUrl, data.method);
      }
    } catch {
      setUnshortenResult({
        finalUrl: local.finalUrl,
        method: local.method,
        bypassed: local.bypassed,
      });
    }
  };

  const handleCreateScript = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newScriptName.trim()) return;
    const created: UserscriptItem = {
      id: `custom-${Date.now()}`,
      name: newScriptName.trim(),
      namespace: 'https://veloxtv.local/userscripts/custom',
      version: '1.0.0',
      description: newScriptDesc.trim() || 'Userscript personalizado para Google TV.',
      matches: ['*://*/*'],
      enabled: true,
      runAt: 'document-start',
      bypassCount: 0,
      code: `// ==UserScript==\n// @name         ${newScriptName.trim()}\n// @match        *://*/*\n// @run-at       document-start\n// ==/UserScript==\n\n(function() {\n  'use strict';\n  console.log('Userscript activo en Google TV');\n})();`,
    };
    onAddUserscript(created);
    setNewScriptName('');
    setNewScriptDesc('');
    setShowNewScriptForm(false);
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="extensions-modal-title"
    >
      <div className="w-full max-w-5xl bg-[#111827] border border-slate-800 rounded-xl overflow-hidden flex flex-col max-h-[90vh]">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-800 bg-[#0B0F17]">
          <div>
            <h2 id="extensions-modal-title" className="text-xl font-bold text-slate-100">
              Motor de Extensiones Blindadas para Google TV
            </h2>
            <p className="text-xs text-slate-400 mt-0.5">
              Ejecución nativa pre-renderizado · Cero consumo extra de RAM en Chromecast 1080p y Onn TV
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar panel de extensiones"
            className="px-3 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium flex items-center gap-1.5 transition-colors"
          >
            <X className="w-4 h-4" />
            <span>Cerrar (ESC)</span>
          </button>
        </div>

        {/* Extension Switcher Tabs */}
        <div className="flex items-center gap-2 px-6 py-3 bg-[#0B0F17]/60 border-b border-slate-800 overflow-x-auto">
          <button
            type="button"
            onClick={() => setActiveTab('ublock')}
            className={`px-4 py-2.5 rounded-lg text-xs font-semibold flex items-center gap-2 transition-colors whitespace-nowrap shrink-0 ${
              activeTab === 'ublock'
                ? 'bg-[#38BDF8] text-slate-950'
                : 'bg-slate-800/80 text-slate-300 hover:bg-slate-800'
            }`}
          >
            <ShieldCheck className="w-4 h-4" />
            <span>1. uBlock Origin ({ublockEnabled ? 'Activo' : 'Pausado'})</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('popup-strict')}
            className={`px-4 py-2.5 rounded-lg text-xs font-semibold flex items-center gap-2 transition-colors whitespace-nowrap shrink-0 ${
              activeTab === 'popup-strict'
                ? 'bg-[#38BDF8] text-slate-950'
                : 'bg-slate-800/80 text-slate-300 hover:bg-slate-800'
            }`}
          >
            <Ban className="w-4 h-4" />
            <span>2. Popup Blocker (strict) ({popupStrictEnabled ? 'Estricto' : 'Pausado'})</span>
          </button>

          <button
            type="button"
            onClick={() => setActiveTab('violentmonkey')}
            className={`px-4 py-2.5 rounded-lg text-xs font-semibold flex items-center gap-2 transition-colors whitespace-nowrap shrink-0 ${
              activeTab === 'violentmonkey'
                ? 'bg-[#38BDF8] text-slate-950'
                : 'bg-slate-800/80 text-slate-300 hover:bg-slate-800'
            }`}
          >
            <Code2 className="w-4 h-4" />
            <span>3. Violentmonkey · Saltar Acortadores ({violentmonkeyEnabled ? 'Activo' : 'Pausado'})</span>
          </button>
        </div>

        {/* Content Body */}
        <div className="p-6 overflow-y-auto space-y-6 flex-1">
          {activeTab === 'ublock' && (
            <div className="space-y-6">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-5 border-b border-slate-800">
                <div>
                  <h3 className="text-base font-bold text-slate-100">
                    uBlock Origin — Motor de Filtrado de Red y DOM
                  </h3>
                  <p className="text-xs text-slate-400 mt-1">
                    Elimina servidores de anuncios, mineros WebGL y rastreadores antes de que consuman memoria en el televisor.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => onToggleUblock(!ublockEnabled)}
                  className={`px-4 py-2.5 rounded-lg text-xs font-semibold transition-colors whitespace-nowrap shrink-0 ${
                    ublockEnabled
                      ? 'bg-emerald-600 hover:bg-emerald-500 text-white'
                      : 'bg-rose-600 hover:bg-rose-500 text-white'
                  }`}
                >
                  {ublockEnabled ? 'Escudo uBlock Origin: ACTIVO' : 'Escudo uBlock Origin: DESACTIVADO'}
                </button>
              </div>

              {/* Filter Lists */}
              <div>
                <h4 className="text-sm font-semibold text-slate-200 mb-3">
                  Listas de Filtros Activas (Actualizadas 2026)
                </h4>
                <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                  {filterLists.map((list) => (
                    <div
                      key={list.id}
                      className="p-4 rounded-lg bg-[#0B0F17] border border-slate-800 flex items-start justify-between gap-4"
                    >
                      <div>
                        <div className="text-sm font-semibold text-slate-100">{list.name}</div>
                        <p className="text-xs text-slate-400 mt-1">{list.description}</p>
                        <div className="text-xs text-slate-500 mt-2 font-mono tabular-nums">
                          {list.rulesCount.toLocaleString('es-ES')} reglas compiladas · Categoría: {list.category}
                        </div>
                      </div>
                      <button
                        type="button"
                        onClick={() => onToggleFilterList(list.id)}
                        aria-label={`Alternar lista ${list.name}`}
                        className={`px-3 py-1.5 rounded text-xs font-semibold whitespace-nowrap shrink-0 ${
                          list.enabled
                            ? 'bg-emerald-950/90 text-emerald-300 border border-emerald-700/60'
                            : 'bg-slate-800 text-slate-400'
                        }`}
                      >
                        {list.enabled ? 'Activa' : 'Inactiva'}
                      </button>
                    </div>
                  ))}
                </div>
              </div>

              {/* Custom Rules Editor */}
              <form onSubmit={handleSaveRules} className="space-y-2">
                <div className="flex items-center justify-between">
                  <label htmlFor="ublock-custom-rules" className="text-sm font-semibold text-slate-200">
                    Mis Filtros Personalizados (Sintaxis Adblock / uBlock)
                  </label>
                  {rulesSavedNotice && (
                    <span className="text-xs text-emerald-400 font-medium flex items-center gap-1">
                      <Check className="w-3.5 h-3.5" /> Reglas compiladas en memoria
                    </span>
                  )}
                </div>
                <textarea
                  id="ublock-custom-rules"
                  rows={4}
                  value={rulesDraft}
                  onChange={(e) => setRulesDraft(e.target.value)}
                  className="w-full rounded-lg bg-[#0B0F17] border border-slate-800 p-3 text-xs font-mono text-slate-200 focus:border-[#38BDF8]"
                  placeholder="||ejemplo-anuncios.com^&#10;||popunder-stream.net^"
                />
                <div className="flex justify-end">
                  <button
                    type="submit"
                    className="px-4 py-2 rounded-lg bg-[#38BDF8] hover:bg-sky-300 text-slate-950 text-xs font-semibold transition-colors"
                  >
                    Aplicar Filtros Personalizados
                  </button>
                </div>
              </form>
            </div>
          )}

          {activeTab === 'popup-strict' && (
            <div className="space-y-6">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-5 border-b border-slate-800">
                <div>
                  <h3 className="text-base font-bold text-slate-100">
                    Popup Blocker (strict) — Bloqueo Total de Ventanas y Clickjacking
                  </h3>
                  <p className="text-xs text-slate-400 mt-1">
                    Impide por completo que al pulsar el botón central del mando de Google TV en un reproductor se abran pestañas de casinos o apuestas.
                  </p>
                </div>
                <div className="flex items-center gap-2 shrink-0">
                  <button
                    type="button"
                    onClick={onTriggerSimulatedPopupAttack}
                    className="px-3.5 py-2.5 rounded-lg bg-amber-500/20 border border-amber-500/50 hover:bg-amber-500/30 text-amber-300 text-xs font-semibold transition-colors whitespace-nowrap"
                  >
                    Probar Ataque de Popups Ahora
                  </button>
                  <button
                    type="button"
                    onClick={() => onTogglePopupStrict(!popupStrictEnabled)}
                    className={`px-4 py-2.5 rounded-lg text-xs font-semibold transition-colors whitespace-nowrap ${
                      popupStrictEnabled
                        ? 'bg-emerald-600 hover:bg-emerald-500 text-white'
                        : 'bg-rose-600 hover:bg-rose-500 text-white'
                    }`}
                  >
                    {popupStrictEnabled ? 'Modo Estricto: ACTIVO' : 'Modo Estricto: PAUSADO'}
                  </button>
                </div>
              </div>

              {/* Strict Rules Breakdown */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <div className="p-4 rounded-lg bg-[#0B0F17] border border-slate-800">
                  <div className="text-sm font-semibold text-slate-100">
                    1. Neutralización de window.open()
                  </div>
                  <p className="text-xs text-slate-400 mt-1">
                    Sobrescribe la API nativa del navegador para devolver null ante cualquier script que intente abrir ventanas emergentes.
                  </p>
                </div>
                <div className="p-4 rounded-lg bg-[#0B0F17] border border-slate-800">
                  <div className="text-sm font-semibold text-slate-100">
                    2. Destructor de Capas Invisibles
                  </div>
                  <p className="text-xs text-slate-400 mt-1">
                    Elimina automáticamente elementos div transparentes con z-index alto colocados encima del botón Play de las películas.
                  </p>
                </div>
                <div className="p-4 rounded-lg bg-[#0B0F17] border border-slate-800">
                  <div className="text-sm font-semibold text-slate-100">
                    3. Protección Anti Tab-Under
                  </div>
                  <p className="text-xs text-slate-400 mt-1">
                    Evita que la página actual se redirija a publicidad mientras abre la película en otra pestaña.
                  </p>
                </div>
              </div>

              {/* Whitelist Management */}
              <div className="p-4 rounded-lg bg-[#0B0F17] border border-slate-800 space-y-3">
                <div className="text-sm font-semibold text-slate-200">
                  Dominios en Lista Blanca (Excepciones permitidas por el usuario)
                </div>
                <form onSubmit={handleWhitelistSubmit} className="flex gap-2">
                  <label htmlFor="whitelist-domain-input" className="sr-only">
                    Dominio a permitir
                  </label>
                  <input
                    id="whitelist-domain-input"
                    type="text"
                    value={newWhitelistDomain}
                    onChange={(e) => setNewWhitelistDomain(e.target.value)}
                    placeholder="ejemplo: archive.org"
                    className="flex-1 rounded-lg bg-[#111827] border border-slate-700 px-3 py-2 text-xs text-slate-100"
                  />
                  <button
                    type="submit"
                    className="px-4 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold flex items-center gap-1.5 whitespace-nowrap"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    <span>Añadir Excepción</span>
                  </button>
                </form>
                <div className="flex flex-wrap gap-2 pt-1">
                  {whitelistedHosts.length === 0 ? (
                    <span className="text-xs text-slate-500">
                      Ningún dominio en lista blanca (Bloqueo estricto 100% en todas las webs).
                    </span>
                  ) : (
                    whitelistedHosts.map((host) => (
                      <div
                        key={host}
                        className="inline-flex items-center gap-2 px-3 py-1 rounded bg-slate-800 text-xs text-slate-200 font-mono"
                      >
                        <span>{host}</span>
                        <button
                          type="button"
                          onClick={() => onRemoveWhitelistHost(host)}
                          aria-label={`Eliminar excepción ${host}`}
                          className="text-slate-400 hover:text-rose-400"
                        >
                          <Trash2 className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    ))
                  )}
                </div>
              </div>
            </div>
          )}

          {activeTab === 'violentmonkey' && (
            <div className="space-y-6">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-5 border-b border-slate-800">
                <div>
                  <h3 className="text-base font-bold text-slate-100">
                    Violentmonkey — Gestor de Scripts y Bypass Universal de Acortadores
                  </h3>
                  <p className="text-xs text-slate-400 mt-1">
                    Incluye el script preinstalado FastForward TV para saltarse todos los enlaces acortados (ouo.io, adf.ly, shrinkme, linkvertise, cuty.io, exe.io) al instante.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() => onToggleViolentmonkey(!violentmonkeyEnabled)}
                  className={`px-4 py-2.5 rounded-lg text-xs font-semibold transition-colors whitespace-nowrap shrink-0 ${
                    violentmonkeyEnabled
                      ? 'bg-emerald-600 hover:bg-emerald-500 text-white'
                      : 'bg-rose-600 hover:bg-rose-500 text-white'
                  }`}
                >
                  {violentmonkeyEnabled ? 'Violentmonkey: ACTIVO' : 'Violentmonkey: PAUSADO'}
                </button>
              </div>

              {/* Interactive Instant Shortlink Bypasser Tool */}
              <div className="p-4 rounded-lg bg-[#0B0F17] border border-slate-800 space-y-3">
                <div className="flex items-center justify-between">
                  <div className="text-sm font-semibold text-[#38BDF8] flex items-center gap-2">
                    <Zap className="w-4 h-4" />
                    <span>Probador y Desacortador Directo Instantáneo (0 Segundos de Espera)</span>
                  </div>
                  <div className="flex items-center gap-2 text-xs">
                    <button
                      type="button"
                      onClick={() =>
                        setShortlinkTestInput('https://ouo.io/pelicula-1080p')
                      }
                      className="text-slate-400 hover:text-slate-200 underline"
                    >
                      Ejemplo ouo.io
                    </button>
                    <span className="text-slate-600">·</span>
                    <button
                      type="button"
                      onClick={() =>
                        setShortlinkTestInput(
                          'https://linkvertise.com/48192?r=aHR0cHM6Ly9jb21tb25kYXRhc3RvcmFnZS5nb29nbGVhcGlzLmNvbS9ndHYtdmlkZW9zLWJ1Y2tldC9zYW1wbGUvU2ludGVsLm1wNA=='
                        )
                      }
                      className="text-slate-400 hover:text-slate-200 underline"
                    >
                      Ejemplo Linkvertise Base64
                    </button>
                  </div>
                </div>

                <form onSubmit={handleUnshortenNow} className="flex flex-col sm:flex-row gap-2">
                  <label htmlFor="shortlink-input" className="sr-only">
                    Enlace acortado a saltar
                  </label>
                  <input
                    id="shortlink-input"
                    type="text"
                    value={shortlinkTestInput}
                    onChange={(e) => setShortlinkTestInput(e.target.value)}
                    placeholder="Pega cualquier enlace acortado (ouo.io, adf.ly, shrinkme, cuty.io, linkvertise...)"
                    className="flex-1 rounded-lg bg-[#111827] border border-slate-700 px-3 py-2 text-xs font-mono text-slate-100"
                  />
                  <button
                    type="submit"
                    className="px-4 py-2 rounded-lg bg-[#38BDF8] hover:bg-sky-300 text-slate-950 text-xs font-semibold transition-colors whitespace-nowrap"
                  >
                    Saltar Acortador Ahora
                  </button>
                </form>

                {unshortenResult && (
                  <div className="p-3 rounded-lg bg-emerald-950/40 border border-emerald-700/50 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                    <div className="space-y-1 min-w-0">
                      <div className="text-xs font-semibold text-emerald-300">
                        Enlace Desbloqueado ({unshortenResult.method})
                      </div>
                      <div className="text-xs font-mono text-slate-200 truncate">
                        Destino Real: {unshortenResult.finalUrl}
                      </div>
                    </div>
                    <button
                      type="button"
                      onClick={() => {
                        onNavigateToUrl(unshortenResult.finalUrl);
                        onClose();
                      }}
                      className="px-3 py-1.5 rounded bg-emerald-500 hover:bg-emerald-400 text-slate-950 text-xs font-semibold flex items-center gap-1.5 whitespace-nowrap shrink-0"
                    >
                      <ExternalLink className="w-3.5 h-3.5" />
                      <span>Abrir Destino Directo</span>
                    </button>
                  </div>
                )}
              </div>

              {/* Installed Userscripts List */}
              <div className="space-y-3">
                <div className="flex items-center justify-between">
                  <h4 className="text-sm font-semibold text-slate-200">
                    Scripts Instalados en Violentmonkey ({userscripts.length})
                  </h4>
                  <button
                    type="button"
                    onClick={() => setShowNewScriptForm(!showNewScriptForm)}
                    className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-semibold flex items-center gap-1.5"
                  >
                    <Plus className="w-3.5 h-3.5" />
                    <span>Nuevo Userscript</span>
                  </button>
                </div>

                {showNewScriptForm && (
                  <form
                    onSubmit={handleCreateScript}
                    className="p-4 rounded-lg bg-[#0B0F17] border border-slate-700 space-y-3"
                  >
                    <div className="text-xs font-semibold text-slate-200">
                      Añadir Nuevo Script a Violentmonkey
                    </div>
                    <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                      <input
                        type="text"
                        value={newScriptName}
                        onChange={(e) => setNewScriptName(e.target.value)}
                        placeholder="Nombre del script (ej. Auto-Play PelisPlus)"
                        className="rounded bg-[#111827] border border-slate-700 px-3 py-2 text-xs text-slate-100"
                        required
                      />
                      <input
                        type="text"
                        value={newScriptDesc}
                        onChange={(e) => setNewScriptDesc(e.target.value)}
                        placeholder="Descripción breve"
                        className="rounded bg-[#111827] border border-slate-700 px-3 py-2 text-xs text-slate-100"
                      />
                    </div>
                    <div className="flex justify-end gap-2">
                      <button
                        type="button"
                        onClick={() => setShowNewScriptForm(false)}
                        className="px-3 py-1.5 rounded bg-slate-800 text-xs text-slate-300"
                      >
                        Cancelar
                      </button>
                      <button
                        type="submit"
                        className="px-3 py-1.5 rounded bg-[#38BDF8] text-slate-950 text-xs font-semibold"
                      >
                        Guardar e Instalar Script
                      </button>
                    </div>
                  </form>
                )}

                {userscripts.map((script) => (
                  <div
                    key={script.id}
                    className="p-4 rounded-lg bg-[#0B0F17] border border-slate-800 space-y-3"
                  >
                    <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-3">
                      <div>
                        <div className="text-sm font-bold text-slate-100">
                          {script.name} <span className="text-xs font-mono text-slate-400">v{script.version}</span>
                        </div>
                        <p className="text-xs text-slate-400 mt-1">{script.description}</p>
                        <div className="text-xs text-slate-500 mt-2 font-mono tabular-nums">
                          Ejecución: {script.runAt} · Saltos realizados: {script.bypassCount}
                        </div>
                      </div>
                      <div className="flex items-center gap-2 shrink-0">
                        <button
                          type="button"
                          onClick={() => {
                            if (editingScriptId === script.id) {
                              setEditingScriptId(null);
                            } else {
                              setEditingScriptId(script.id);
                              setEditingScriptCode(script.code);
                            }
                          }}
                          className="px-3 py-1.5 rounded bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium"
                        >
                          {editingScriptId === script.id ? 'Ocultar Código' : 'Ver / Editar Código'}
                        </button>
                        <button
                          type="button"
                          onClick={() => onToggleUserscript(script.id)}
                          className={`px-3 py-1.5 rounded text-xs font-semibold ${
                            script.enabled
                              ? 'bg-emerald-950/90 text-emerald-300 border border-emerald-700/60'
                              : 'bg-slate-800 text-slate-400'
                          }`}
                        >
                          {script.enabled ? 'Habilitado' : 'Deshabilitado'}
                        </button>
                      </div>
                    </div>

                    {editingScriptId === script.id && (
                      <div className="space-y-2 pt-2 border-t border-slate-800">
                        <textarea
                          rows={8}
                          value={editingScriptCode}
                          onChange={(e) => setEditingScriptCode(e.target.value)}
                          aria-label={`Código fuente de ${script.name}`}
                          className="w-full rounded bg-[#111827] border border-slate-700 p-3 text-xs font-mono text-slate-200"
                        />
                        <div className="flex justify-end">
                          <button
                            type="button"
                            onClick={() => {
                              onUpdateUserscriptCode(script.id, editingScriptCode);
                              setEditingScriptId(null);
                            }}
                            className="px-3 py-1.5 rounded bg-[#38BDF8] text-slate-950 text-xs font-semibold"
                          >
                            Guardar Cambios en Userscript
                          </button>
                        </div>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Real-time Extension Interception Log */}
          <div className="pt-4 border-t border-slate-800">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-semibold text-slate-300">
                Registro de Intercepción en Tiempo Real ({blockedEvents.length} eventos)
              </span>
              {blockedEvents.length > 0 && (
                <button
                  type="button"
                  onClick={onClearBlockedEvents}
                  className="text-xs text-slate-400 hover:text-slate-200 flex items-center gap-1"
                >
                  <RotateCcw className="w-3 h-3" />
                  <span>Limpiar registro</span>
                </button>
              )}
            </div>
            <div className="max-h-36 overflow-y-auto rounded-lg bg-[#0B0F17] border border-slate-800 divide-y divide-slate-800/80">
              {blockedEvents.length === 0 ? (
                <div className="p-3 text-xs text-slate-500 text-center">
                  El registro está limpio. Navega o prueba el laboratorio anti-acortadores para ver las intercepciones en vivo.
                </div>
              ) : (
                blockedEvents.slice(0, 12).map((ev) => (
                  <div
                    key={ev.id}
                    className="px-3 py-2 text-xs flex items-center justify-between gap-4 font-mono"
                  >
                    <div className="flex items-center gap-2 min-w-0">
                      <span className="text-slate-500 shrink-0">{ev.timestamp}</span>
                      <span
                        className={`font-semibold shrink-0 ${
                          ev.extension === 'ublock'
                            ? 'text-emerald-400'
                            : ev.extension === 'popup-strict'
                            ? 'text-amber-400'
                            : 'text-[#38BDF8]'
                        }`}
                      >
                        [{ev.extension}]
                      </span>
                      <span className="text-slate-300 truncate">{ev.targetOrRule}</span>
                    </div>
                    <span className="text-slate-500 shrink-0 tabular-nums">
                      -{ev.ramSavedKb} KB RAM
                    </span>
                  </div>
                ))
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
