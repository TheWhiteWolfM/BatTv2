package com.battv.browser

import android.content.Context
import android.net.Uri
import android.util.Log
import android.util.Patterns
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class BatTvGeckoViewManager(
    private val context: Context,
    private val geckoView: GeckoView
) {

    companion object {
        private const val TAG = "BatTvGeckoManager"

        const val BATTV_SHIELD_ID = "shield_v44@battv.browser"
        const val BATTV_SHIELD_URI = "resource://android/assets/extensions/battv_shield_v44/"

        const val NATIVE_BRIDGE_NAME = "battv_bridge"

        const val PC_MODE_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0"

        private val AD_AND_POPUP_DOMAINS = hashSetOf(
            "vidoomy.com", "viwom.com", "wortise.com", "aniview.com", "primis.tech",
            "adinplay.com", "brid.tv", "doubleclick.net", "googlesyndication.com",
            "googleadservices.com", "google-analytics.com", "googletagmanager.com",
            "popads.net", "popcash.net", "propellerads.com", "exoclick.com", "exosrv.com",
            "exdynsrv.com", "orbsrv.com", "magsrv.com", "realsrv.com", "juicyads.com",
            "adsterra.com", "monetag.com", "acscdn.com", "onclckmn.com", "onclicka.com",
            "onclickads.net", "hilltopads.net", "yellowishgather.com", "galaksion.com",
            "clickadu.com", "adcash.com", "richads.com", "admaven.com", "bidgear.com",
            "trafficjunky.net", "trafficstars.com", "tsyndicate.com", "rollerads.com",
            "evadav.com", "optad360.io", "teads.tv", "taboola.com", "outbrain.com",
            "mgid.com", "revcontent.com", "criteo.com", "pubmatic.com",
            "rubiconproject.com", "openx.net", "smartadserver.com", "1xbet.com",
            "bet365.com", "mostbet.com", "caliente.mx", "codere.mx", "betano.com",
            "bwin.com", "pin-up.bet", "pin-up.casino", "20bet.com", "22bet.com",
            "melbet.com", "1win.pro", "1win.com", "apuestasroyal.com",
            "pushground.com", "yllix.com", "plugrush.com", "adspyglass.com",
            "clksite.com", "clkmon.com", "betteradsystem.com", "blurtsbrerd.com",
            "capsidsflyleaf.com", "skypipevatican.com", "21wiz.com", "aarems.org",
            "twinrdsyn.com", "adsco.re", "a-ads.com", "adskeeper.com",
            "adskeeper.co.uk", "wpadmngr.com", "onmarshtompor.com", "alwingulla.com",
            "profitablecpmrate.com", "highperformanceformat.com", "effectivegatecpm.com",
            "topcreativeformat.com", "wayfarerorthodox.com", "surfe.pro",
            "go2buddyspin.com", "cactusheadroomscaling.com", "ero-advertising.com",
            "adtng.com", "xlivrdr.com", "bngpt.com", "bongacams.com",
            "livejasmin.com", "awempire.com", "jerkmate.com", "cam4.com",
            "stripchat.com", "chatmate.tv", "chatmate.com", "adxpansion.com",
            "spotscenered.info", "traforama.com", "mondiad.com",
            "t6r-rotator-marketingg.com", "rotator-marketingg.com",
            "358g46.fun", "dfuybl-bclxb.love", "fbpbpmjjg-f.club"
        )

        private val SCAM_AND_AD_URL_PATTERNS = listOf(
            "/popunder", "/tabunder", "/clickunder", "utm_source=pop", "ad_type=pop",
            "/apu.php", "/afu.php", "/cig.", "/0/p.", "zoneid=", "directlink",
            "reclama-tu", "felicitaciones", "spin-wheel", "lucky-winner",
            "phone-cleaner", "virus-scan", "battery-alert", "/splash.php",
            "rotator-marketing"
        )

        private val GECKO_DISPOSABLE_CACHE_DIRS = setOf(
            "OfflineCache",
            "thumbnails",
            "safebrowsing",
            "crashes",
            "minidumps",
            "saved-telemetry-pings",
            "datareporting"
        )

        @Volatile
        private var sharedRuntime: GeckoRuntime? = null

        private val installedExtensions = ConcurrentHashMap<String, WebExtension>()
    }

    lateinit var runtime: GeckoRuntime
        private set

    lateinit var session: GeckoSession
        private set

    private var bridgeMessageDelegate: WebExtension.MessageDelegate? = null

    fun initialize(
        isDesktopMode: Boolean,
        messageDelegate: WebExtension.MessageDelegate
    ): GeckoSession {
        bridgeMessageDelegate = messageDelegate
        runtime = getOrCreateRuntime(context.applicationContext)

        val sessionSettings = GeckoSessionSettings.Builder()
            .usePrivateMode(false)
            .useTrackingProtection(true)
            .suspendMediaWhenInactive(true)
            .userAgentMode(
                if (isDesktopMode) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            )
            .viewportMode(
                if (isDesktopMode) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
            )
            .apply {
                if (isDesktopMode) {
                    userAgentOverride(PC_MODE_USER_AGENT)
                }
            }
            .build()

        session = GeckoSession(sessionSettings)

        setupPermissionDelegate(session)
        setupNativeShieldExtension(runtime.webExtensionController)

        session.open(runtime)
        geckoView.setDynamicToolbarMaxHeight(0)
        geckoView.setSession(session)
        session.setActive(true)
        session.setFocused(true)
        try {
            session.setPriorityHint(GeckoSession.PRIORITY_HIGH)
        } catch (_: Exception) {}

        installedExtensions[BATTV_SHIELD_ID]?.let { shieldExt ->
            attachBridgeToExtension(shieldExt)
        }

        return session
    }

    private fun getOrCreateRuntime(appContext: Context): GeckoRuntime {
        return sharedRuntime ?: synchronized(Companion) {
            sharedRuntime ?: run {
                try {
                    appContext.externalCacheDir?.let { deleteDirectoryContents(it) }
                    val mozillaDir = File(appContext.filesDir, "mozilla")
                    if (mozillaDir.exists()) {
                        cleanDisposableGeckoDirs(mozillaDir)
                    }
                } catch (_: Exception) {}

                val cfgFile = writeHighPerformanceTvGeckoConfig(appContext)
                val builder = GeckoRuntimeSettings.Builder()
                    .javaScriptEnabled(true)
                    .consoleOutput(false)
                    .remoteDebuggingEnabled(false)
                    .aboutConfigEnabled(false)
                    .doubleTapZoomingEnabled(false)
                    .automaticFontSizeAdjustment(false)

                runCatching {
                    builder.javaClass
                        .getMethod("useMaxScreenDepth", Boolean::class.javaPrimitiveType)
                        .invoke(builder, false)
                }

                if (cfgFile != null) {
                    builder.configFilePath(cfgFile.absolutePath)
                }

                GeckoRuntime.create(appContext, builder.build()).also {
                    sharedRuntime = it
                }
            }
        }
    }

    /**
     * Perfil optimizado para Chromecast con Google TV 1080p (1.5 GB RAM, Cortex-A35, Mali-G31 MP2):
     * - Decodificacion por hardware forzada (MediaCodec) y salto al siguiente keyframe para directos fluidos.
     * - Limites estrictos de memoria para evitar cierres por LMK de Android TV.
     * - HTTP/3 desactivado y tope de conexiones moderado para no saturar CPU/Wi-Fi.
     * - WebRender hardware + desactivacion de backdrop-filter/smoothScroll para eliminar lag de UI.
     */
    private fun writeHighPerformanceTvGeckoConfig(appContext: Context): File? {
        return try {
            val cfgFile = File(appContext.filesDir, "battv_gecko_config.yaml")
            val yamlContent = """
                prefs:
                  media.hardware-video-decoding.enabled: true
                  media.hardware-video-decoding.force-enabled: true
                  media.android-media-codec.enabled: true
                  media.android-media-codec.preferred: true
                  media.decoder.recycle.enabled: false
                  media.decoder.skip-to-next-key-frame.enabled: true
                  media.ruin-av-sync.enabled: false
                  media.av1.enabled: false
                  media.suspend-bkgnd-video.enabled: true
                  media.autoplay.default: 0
                  media.autoplay.blocking_policy: 0
                  media.hls.enabled: true
                  media.eme.enabled: true
                  media.mediasource.enabled: true
                  media.mediasource.mp4.enabled: true
                  media.mediasource.webm.enabled: true
                  media.cache_size: 32768
                  media.memory_cache_max_size: 16384
                  media.memory_caches_combined_limit_kb: 32768
                  media.cache_readahead_limit: 24
                  media.cache_resume_threshold: 10
                  gfx.webrender.all: true
                  gfx.webrender.software: false
                  gfx.canvas.accelerated: true
                  layout.css.backdrop-filter.enabled: false
                  layout.css.scroll-driven-animations.enabled: false
                  general.smoothScroll: false
                  general.smoothScroll.msdPhysics.enabled: false
                  nglayout.initialpaint.delay: 0
                  browser.sessionhistory.max_total_viewers: 0
                  browser.cache.memory.enable: true
                  browser.cache.memory.capacity: 24576
                  browser.cache.disk.enable: true
                  browser.cache.disk.capacity: 32768
                  browser.cache.disk.smart_size.enabled: false
                  browser.cache.offline.enable: false
                  image.decode-immediately.enabled: false
                  image.mem.surfacecache.max_size_kb: 16384
                  image.mem.discardable: true
                  image.mem.min_discard_timeout_ms: 10000
                  dom.min_background_timeout_value: 1000
                  browser.safebrowsing.malware.enabled: false
                  browser.safebrowsing.phishing.enabled: false
                  browser.safebrowsing.downloads.enabled: false
                  datareporting.policy.dataSubmissionEnabled: false
                  datareporting.healthreport.uploadEnabled: false
                  toolkit.telemetry.enabled: false
                  apz.overscroll.enabled: false
                  apz.allow_zooming: false
                  dom.disable_open_during_load: true
                  dom.popup_maximum: 0
                  full-screen-api.enabled: true
                  full-screen-api.allow-trusted-requests-only: false
                  full-screen-api.warning.delay: -1
                  full-screen-api.warning.timeout: 0
                  network.http.http3.enable: false
                  network.http.max-connections: 48
                  network.http.max-persistent-connections-per-server: 6
                  network.http.sendRefererHeader: 2
                  network.dns.disablePrefetch: false
                  network.cookie.cookieBehavior: 0
                  network.cookie.sameSite.laxByDefault: false
                  network.cookie.sameSite.noneRequiresSecure: false
                  security.mixed_content.block_active_content: false
                  security.mixed_content.block_display_content: false
                  dom.security.https_only_mode: false
                  dom.webdriver.enabled: false
                  xpinstall.signatures.required: false
                  extensions.langpacks.signatures.required: false
            """.trimIndent()
            cfgFile.writeText(yamlContent)
            cfgFile
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo escribir battv_gecko_config.yaml", e)
            null
        }
    }

    private fun setupPermissionDelegate(geckoSession: GeckoSession) {
        geckoSession.permissionDelegate = object : GeckoSession.PermissionDelegate {
            override fun onContentPermissionRequest(
                session: GeckoSession,
                perm: GeckoSession.PermissionDelegate.ContentPermission
            ): GeckoResult<Int>? {
                return when (perm.permission) {
                    GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION,
                    GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION,
                    GeckoSession.PermissionDelegate.PERMISSION_PERSISTENT_STORAGE ->
                        GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY)
                    else ->
                        GeckoResult.fromValue(GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW)
                }
            }
        }
    }

    private fun setupNativeShieldExtension(controller: WebExtensionController) {
        controller.promptDelegate = object : WebExtensionController.PromptDelegate {
            override fun onOptionalPrompt(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>
            ): GeckoResult<AllowOrDeny>? {
                return GeckoResult.allow()
            }
        }

        controller.list().accept({ existingList ->
            existingList?.forEach { ext ->
                if (ext.id != BATTV_SHIELD_ID) {
                    controller.uninstall(ext)
                }
            }
        }, {})

        controller.ensureBuiltIn(BATTV_SHIELD_URI, BATTV_SHIELD_ID).accept(
            { extension ->
                if (extension != null) {
                    installedExtensions[extension.id] = extension
                    attachBridgeToExtension(extension)
                    Log.i(TAG, "Escudo nativo activo: ${extension.id}")
                }
            },
            { err ->
                Log.e(TAG, "Error al cargar escudo nativo", err)
            }
        )
    }

    private fun attachBridgeToExtension(extension: WebExtension) {
        val delegate = bridgeMessageDelegate ?: return
        try {
            extension.setMessageDelegate(delegate, NATIVE_BRIDGE_NAME)
        } catch (_: Exception) {}
        try {
            if (::session.isInitialized) {
                session.webExtensionController.setMessageDelegate(
                    extension,
                    delegate,
                    NATIVE_BRIDGE_NAME
                )
            }
        } catch (_: Exception) {}
    }

    private fun isHostBlocked(host: String): Boolean {
        if (host.isEmpty()) return false
        if (AD_AND_POPUP_DOMAINS.contains(host)) return true
        var idx = host.indexOf('.')
        while (idx != -1) {
            val sub = host.substring(idx + 1)
            if (AD_AND_POPUP_DOMAINS.contains(sub)) return true
            idx = host.indexOf('.', idx + 1)
        }
        return false
    }

    fun isKnownAdOrPopupUri(uriString: String): Boolean {
        val parsed = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        val host = parsed.host?.lowercase()?.removePrefix("www.") ?: ""
        if (isHostBlocked(host)) return true

        val lower = uriString.lowercase()
        for (pattern in SCAM_AND_AD_URL_PATTERNS) {
            if (lower.contains(pattern)) {
                return true
            }
        }
        val path = parsed.path ?: ""
        if (path.length > 20 && (path.startsWith("/ciG.") || path.startsWith("/cIg.") || path.contains("/0/P."))) {
            return true
        }
        return false
    }

    fun applyDisplayMode(isDesktopMode: Boolean, reloadIfActive: Boolean, currentUrl: String) {
        if (!::session.isInitialized) return
        session.settings.userAgentMode = if (isDesktopMode) {
            GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
        } else {
            GeckoSessionSettings.USER_AGENT_MODE_MOBILE
        }
        session.settings.viewportMode = if (isDesktopMode) {
            GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
        } else {
            GeckoSessionSettings.VIEWPORT_MODE_MOBILE
        }
        session.settings.userAgentOverride = if (isDesktopMode) {
            PC_MODE_USER_AGENT
        } else {
            null
        }

        if (reloadIfActive && currentUrl.isNotBlank() && currentUrl != "about:blank") {
            session.stop()
            session.reload()
        }
    }

    fun isExtensionLoaded(@Suppress("UNUSED_PARAMETER") extensionId: String): Boolean {
        return installedExtensions.containsKey(BATTV_SHIELD_ID)
    }

    fun resolveInputToUrl(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return "https://www.google.com/"
        }

        val isHttp = trimmed.startsWith("http://", ignoreCase = true)
        val isHttps = trimmed.startsWith("https://", ignoreCase = true)
        val isAbout = trimmed.startsWith("about:", ignoreCase = true)
        val isMozExt = trimmed.startsWith("moz-extension://", ignoreCase = true)

        if (isHttp || isHttps || isAbout || isMozExt) {
            return trimmed
        }

        val looksLikeDomain = !trimmed.contains(" ") &&
            trimmed.contains(".") &&
            Patterns.WEB_URL.matcher("https://$trimmed").matches()

        return if (looksLikeDomain) {
            "https://$trimmed"
        } else {
            "https://www.google.com/search?q=${Uri.encode(trimmed)}&hl=es"
        }
    }

    fun pruneDisposableStorageNow() {
        Thread {
            try {
                context.externalCacheDir?.let { deleteDirectoryContents(it) }
                val mozillaDir = File(context.filesDir, "mozilla")
                if (mozillaDir.exists()) {
                    cleanDisposableGeckoDirs(mozillaDir)
                }
            } catch (_: Exception) {}
        }.start()
    }

    fun clearSafeCaches(onComplete: (Long) -> Unit) {
        if (::runtime.isInitialized) {
            try {
                val flags = StorageController.ClearFlags.IMAGE_CACHE or
                    StorageController.ClearFlags.NETWORK_CACHE
                runtime.storageController.clearData(flags)
            } catch (_: Exception) {}
        }

        Thread {
            var freedBytes = 0L
            try {
                freedBytes += deleteDirectoryContents(context.cacheDir)
                context.externalCacheDir?.let { freedBytes += deleteDirectoryContents(it) }
                val mozillaDir = File(context.filesDir, "mozilla")
                if (mozillaDir.exists()) {
                    freedBytes += cleanDisposableGeckoDirs(mozillaDir)
                }
            } catch (_: Exception) {}
            onComplete(freedBytes)
        }.start()
    }

    private fun cleanDisposableGeckoDirs(dir: File): Long {
        var bytes = 0L
        val children = dir.listFiles() ?: return 0L
        for (child in children) {
            if (child.isDirectory) {
                if (GECKO_DISPOSABLE_CACHE_DIRS.contains(child.name)) {
                    bytes += getFolderSize(child)
                    child.deleteRecursively()
                } else if (child.name == "storage") {
                    val tempDir = File(child, "temporary")
                    if (tempDir.exists()) {
                        bytes += getFolderSize(tempDir)
                        tempDir.deleteRecursively()
                    }
                } else if (child.name != "extensions") {
                    bytes += cleanDisposableGeckoDirs(child)
                }
            } else if (child.isFile && child.name == "prefs.js") {
                try {
                    val sanitized = child.readLines().filterNot { line ->
                        line.contains("dom.ipc.processCount") ||
                            line.contains("layers.acceleration") ||
                            line.contains("network.http.http3") ||
                            line.contains("network.http.max-connections") ||
                            line.contains("browser.cache.disk.enable") ||
                            line.contains("media.cache_resume_threshold") ||
                            line.contains("skip-to-next-key-frame") ||
                            line.contains("places.history.enabled") ||
                            line.contains("browser.chrome.site_icons") ||
                            line.contains("browser.chrome.favicons") ||
                            line.contains("network.cookie.cookieBehavior") ||
                            line.contains("media.hls.enabled")
                    }
                    child.writeText(sanitized.joinToString("\n") + "\n")
                } catch (_: Exception) {}
            }
        }
        return bytes
    }

    private fun deleteDirectoryContents(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var bytes = 0L
        val files = dir.listFiles() ?: return 0L
        for (f in files) {
            bytes += getFolderSize(f)
            f.deleteRecursively()
        }
        return bytes
    }

    private fun getFolderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (!file.isDirectory) return file.length()
        var total = 0L
        val children = file.listFiles() ?: return 0L
        for (c in children) {
            total += getFolderSize(c)
        }
        return total
    }

    fun closeSessionCleanly() {
        try {
            if (::session.isInitialized) {
                session.stop()
                session.setActive(false)
                session.close()
            }
        } catch (_: Exception) {}
        try {
            val mozillaDir = File(context.filesDir, "mozilla")
            if (mozillaDir.exists()) {
                cleanDisposableGeckoDirs(mozillaDir)
            }
        } catch (_: Exception) {}
        try {
            if (::runtime.isInitialized) {
                runtime.shutdown()
                sharedRuntime = null
            }
        } catch (_: Exception) {}
    }
}
