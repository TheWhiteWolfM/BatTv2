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

        const val BATTV_SHIELD_ID = "shield@battv.browser"
        const val BATTV_SHIELD_URI = "resource://android/assets/extensions/battv_shield/"

        const val POPUP_BLOCKER_ID = BATTV_SHIELD_ID
        const val UBLOCK_ORIGIN_ID = BATTV_SHIELD_ID
        const val VIOLENTMONKEY_ID = BATTV_SHIELD_ID

        const val NATIVE_BRIDGE_NAME = "battv_bridge"

        const val TV_MODE_USER_AGENT =
            "Mozilla/5.0 (Android 14; Mobile; rv:130.0) Gecko/130.0 Firefox/130.0"

        const val PC_MODE_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0"

        private val AD_AND_POPUP_DOMAINS = listOf(
            "vidoomy.com", "viwom.com", "wortise.com", "aniview.com", "primis.tech",
            "adinplay.com", "brid.tv", "doubleclick.net", "googlesyndication.com",
            "googleadservices.com", "popads.net", "popcash.net", "propellerads.com",
            "exoclick.com", "magsrv.com", "realsrv.com", "juicyads.com", "adsterra.com",
            "monetag.com", "acscdn.com", "onclickads.net", "hilltopads.net",
            "galaksion.com", "clickadu.com", "adcash.com", "richads.com", "admaven.com",
            "bidgear.com", "trafficjunky.net", "trafficstars.com", "tsyndicate.com",
            "rollerads.com", "evadav.com", "optad360.io", "teads.tv", "taboola.com",
            "outbrain.com", "1xbet.com", "bet365.com", "mostbet.com", "caliente.mx",
            "pushground.com", "yllix.com", "plugrush.com", "adspyglass.com",
            "clksite.com", "clkmon.com", "betteradsystem.com", "blurtsbrerd.com",
            "capsidsflyleaf.com", "skypipevatican.com", "21wiz.com", "aarems.org",
            "twinrdsyn.com", "adsco.re", "a-ads.com", "adskeeper.com", "adskeeper.co.uk",
            "wpadmngr.com", "onmarshtompor.com", "alwingulla.com", "profitablecpmrate.com",
            "highperformanceformat.com", "surfe.pro"
        )

        private val SCAM_AND_AD_URL_PATTERNS = listOf(
            "/popunder", "/tabunder", "/clickunder", "utm_source=pop", "ad_type=pop",
            "/apu.php", "/afu.php", "zoneid=", "directlink", "reclama-tu", "felicitaciones",
            "spin-wheel", "lucky-winner", "phone-cleaner", "virus-scan", "battery-alert"
        )

        private val GECKO_DISPOSABLE_CACHE_DIRS = setOf(
            "cache2",
            "OfflineCache",
            "thumbnails",
            "shader-cache",
            "startupCache",
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
            .useTrackingProtection(false)
            .suspendMediaWhenInactive(false)
            .userAgentMode(
                if (isDesktopMode) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            )
            .viewportMode(
                if (isDesktopMode) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
            )
            .userAgentOverride(if (isDesktopMode) PC_MODE_USER_AGENT else TV_MODE_USER_AGENT)
            .build()

        session = GeckoSession(sessionSettings)

        setupPermissionDelegate(session)
        setupNativeShieldExtension(runtime.webExtensionController)

        session.open(runtime)
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
                // 1. Eliminar la carpeta antigua filesDir/mozilla que causaba los 39.80 MB de "Datos"
                try {
                    val legacyMozillaDir = File(appContext.filesDir, "mozilla")
                    if (legacyMozillaDir.exists()) {
                        legacyMozillaDir.deleteRecursively()
                    }
                    val legacyCfg = File(appContext.filesDir, "battv_gecko_config.yaml")
                    if (legacyCfg.exists()) {
                        legacyCfg.delete()
                    }
                } catch (_: Exception) {}

                // 2. Ubicar el perfil de Gecko dentro de cacheDir para que "Datos" pese < 1 MB
                val cacheProfileDir = File(appContext.cacheDir, "gv_profile").apply {
                    if (!exists()) mkdirs()
                    cleanDisposableGeckoDirs(this)
                }

                val cfgFile = writeHighPerformanceTvGeckoConfig(appContext)
                val builder = GeckoRuntimeSettings.Builder()
                    .javaScriptEnabled(true)
                    .consoleOutput(false)
                    .aboutConfigEnabled(false)
                    .doubleTapZoomingEnabled(false)
                    .automaticFontSizeAdjustment(false)
                    .arguments(arrayOf("-profile", cacheProfileDir.absolutePath))

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
     * Configuración calibrada para:
     * - Cero inflación de "Datos" (places.history.enabled = false, site_icons = false, disk.enable = false).
     * - Directos de Chaturbate a 1080p60 sin cortes: media.hls.enabled = false fuerza el motor MSE (hls.js)
     *   acelerado por hardware MediaCodec con 48 MB de búfer dedicado en RAM.
     */
    private fun writeHighPerformanceTvGeckoConfig(appContext: Context): File? {
        return try {
            val cfgFile = File(appContext.cacheDir, "battv_gecko_config.yaml")
            val yamlContent = """
                prefs:
                  browser.cache.disk.enable: false
                  browser.cache.disk.capacity: 0
                  browser.cache.disk.smart_size.enabled: false
                  browser.cache.offline.enable: false
                  browser.cache.memory.enable: true
                  browser.cache.memory.capacity: 16384
                  browser.sessionhistory.max_total_viewers: 0
                  places.history.enabled: false
                  browser.chrome.site_icons: false
                  browser.chrome.favicons: false
                  dom.storage.default_quota: 2048
                  browser.safebrowsing.malware.enabled: false
                  browser.safebrowsing.phishing.enabled: false
                  browser.safebrowsing.downloads.enabled: false
                  datareporting.policy.dataSubmissionEnabled: false
                  datareporting.healthreport.uploadEnabled: false
                  toolkit.telemetry.enabled: false
                  gfx.webrender.all: true
                  gfx.canvas.accelerated: true
                  layout.frame_rate: 60
                  layout.css.backdrop-filter.enabled: false
                  image.mem.decode_bytes_at_a_time: 65536
                  image.mem.shared: true
                  apz.overscroll.enabled: false
                  apz.fling_min_velocity_threshold: "0.3"
                  media.suspend-bkgnd-video.enabled: false
                  media.cache_size: 49152
                  media.memory_cache_max_size: 24576
                  media.memory_caches_combined_limit_kb: 49152
                  media.cache_readahead_limit: 60
                  media.cache_resume_threshold: 4
                  media.preload.default: 2
                  media.autoplay.default: 0
                  media.autoplay.blocking_policy: 0
                  media.hls.enabled: false
                  media.av1.enabled: false
                  media.android-media-codec.enabled: true
                  media.android-media-codec.preferred: true
                  media.hardware-video-decoding.enabled: true
                  media.hardware-video-decoding.force-enabled: true
                  media.decoder.skip-to-next-key-frame.enabled: false
                  media.ruin-av-sync.enabled: false
                  media.eme.enabled: true
                  media.mediasource.enabled: true
                  media.mediasource.mp4.enabled: true
                  media.mediasource.webm.enabled: true
                  dom.disable_open_during_load: true
                  dom.popup_maximum: 0
                  full-screen-api.enabled: true
                  full-screen-api.allow-trusted-requests-only: false
                  full-screen-api.warning.delay: -1
                  full-screen-api.warning.timeout: 0
                  nglayout.initialpaint.delay: 0
                  network.http.http3.enable: false
                  network.http.max-connections: 64
                  network.http.max-persistent-connections-per-server: 12
                  network.http.pacing.requests.enabled: false
                  network.buffer.cache.size: 32768
                  network.buffer.cache.count: 32
                  network.dns.disablePrefetch: false
                  network.dns.disablePrefetchFromHTTPS: false
                  network.predictor.enabled: true
                  network.cookie.cookieBehavior: 4
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
                    NATIVE_BRIDGE_NAME)
            }
        } catch (_: Exception) {}
    }

    fun isKnownAdOrPopupUri(uriString: String): Boolean {
        val lower = uriString.lowercase()
        val host = runCatching { Uri.parse(uriString).host?.lowercase() ?: "" }.getOrDefault("")
        for (domain in AD_AND_POPUP_DOMAINS) {
            if (host == domain || host.endsWith(".$domain") || lower.contains(domain)) {
                return true
            }
        }
        for (pattern in SCAM_AND_AD_URL_PATTERNS) {
            if (lower.contains(pattern)) {
                return true
            }
        }
        return false
    }

    fun isSameOrTrustedNavigationTarget(currentPageUrl: String, targetUrl: String): Boolean {
        if (currentPageUrl.isBlank() || !currentPageUrl.startsWith("http", ignoreCase = true)) return true
        val curHost = runCatching { Uri.parse(currentPageUrl).host?.lowercase()?.removePrefix("www.") ?: "" }.getOrDefault("")
        val tarHost = runCatching { Uri.parse(targetUrl).host?.lowercase()?.removePrefix("www.") ?: "" }.getOrDefault("")
        if (curHost.isEmpty() || tarHost.isEmpty()) return true
        if (curHost == tarHost || tarHost.endsWith(".$curHost") || curHost.endsWith(".$tarHost")) return true

        if (curHost.contains("google.") || curHost.contains("bing.com") || curHost.contains("duckduckgo.com")) {
            return true
        }

        val curParts = curHost.split(".")
        val tarParts = tarHost.split(".")
        if (curParts.size >= 2 && tarParts.size >= 2) {
            val curBrand = curParts[curParts.size - 2]
            val tarBrand = tarParts[tarParts.size - 2]
            if (curBrand.length >= 4 && curBrand == tarBrand) {
                return true
            }
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
            TV_MODE_USER_AGENT
        }

        if (reloadIfActive && currentUrl.isNotBlank() && currentUrl != "about:blank") {
            session.stop()
            session.reload()
        }
    }

    fun isExtensionLoaded(extensionId: String): Boolean {
        return installedExtensions.containsKey(BATTV_SHIELD_ID)
    }

    fun getExtensionDashboardUrl(extensionId: String, fallbackPath: String): String? {
        return null
    }

    fun resolveInputToUrl(rawInput: String): String {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            return "https://www.google.com/"
        }

        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("about:", ignoreCase = true) ||
            trimmed.startsWith("moz-extension://", ignoreCase = true)
        ) {
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
                val legacyMozillaDir = File(context.filesDir, "mozilla")
                if (legacyMozillaDir.exists()) {
                    legacyMozillaDir.deleteRecursively()
                }
                val cacheProfileDir = File(context.cacheDir, "gv_profile")
                if (cacheProfileDir.exists()) {
                    cleanDisposableGeckoDirs(cacheProfileDir)
                }
                context.externalCacheDir?.let { deleteDirectoryContents(it) }
            } catch (_: Exception) {}
        }.start()
    }

    fun clearSafeCaches(onComplete: (Long) -> Unit) {
        if (::runtime.isInitialized) {
            try {
                runtime.storageController.clearData(
                    StorageController.ClearFlags.IMAGE_CACHE or
                        StorageController.ClearFlags.NETWORK_CACHE
                )
            } catch (_: Exception) {}
        }

        Thread {
            var freedBytes = 0L
            try {
                val legacyMozillaDir = File(context.filesDir, "mozilla")
                if (legacyMozillaDir.exists()) {
                    freedBytes += getFolderSize(legacyMozillaDir)
                    legacyMozillaDir.deleteRecursively()
                }
                val cacheProfileDir = File(context.cacheDir, "gv_profile")
                if (cacheProfileDir.exists()) {
                    freedBytes += cleanDisposableGeckoDirs(cacheProfileDir)
                }
                context.externalCacheDir?.let { freedBytes += deleteDirectoryContents(it) }
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
                } else if (child.name != "extensions") {
                    bytes += cleanDisposableGeckoDirs(child)
                }
            } else if (child.isFile && (
                    child.name.startsWith("places.sqlite") ||
                        child.name.startsWith("favicons.sqlite") ||
                        child.name.startsWith("webappsstore.sqlite") ||
                        child.name.endsWith(".tmp")
                    )
            ) {
                bytes += child.length()
                child.delete()
            } else if (child.isFile && child.name == "prefs.js") {
                try {
                    val sanitized = child.readLines().filterNot { line ->
                        line.contains("media.hls.enabled") ||
                            line.contains("browser.cache.disk.enable") ||
                            line.contains("media.cache_resume_threshold") ||
                            line.contains("skip-to-next-key-frame")
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
            pruneDisposableStorageNow()
        } catch (_: Exception) {}
    }
}
