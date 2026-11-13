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

        const val POPUP_BLOCKER_ID = "{54e2eb33-18eb-46ad-a4e4-1329c29f6e17}"
        const val POPUP_BLOCKER_URI = "resource://android/assets/extensions/popup_blocker/"

        const val UBLOCK_ORIGIN_ID = "uBlock0@raymondhill.net"
        const val UBLOCK_ORIGIN_URI = "resource://android/assets/extensions/ublock_origin/"

        const val VIOLENTMONKEY_ID = "{aecec67f-0d10-4fa7-b7c7-609a2db280cf}"
        const val VIOLENTMONKEY_URI = "resource://android/assets/extensions/violentmonkey/"

        const val NATIVE_BRIDGE_NAME = "battv_bridge"

        const val TV_MODE_USER_AGENT =
            "Mozilla/5.0 (Android 14; Mobile; rv:130.0) Gecko/130.0 Firefox/130.0"

        const val PC_MODE_USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0"

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
        setupWebExtensions(runtime.webExtensionController)

        session.open(runtime)
        geckoView.setSession(session)
        session.setActive(true)
        session.setFocused(true)
        try {
            session.setPriorityHint(GeckoSession.PRIORITY_HIGH)
        } catch (_: Exception) {}

        installedExtensions[VIOLENTMONKEY_ID]?.let { vmExt ->
            attachBridgeToExtension(vmExt)
        }

        return session
    }

    private fun getOrCreateRuntime(appContext: Context): GeckoRuntime {
        return sharedRuntime ?: synchronized(Companion) {
            sharedRuntime ?: run {
                val cfgFile = writeOptimizedGeckoConfig(appContext)
                val builder = GeckoRuntimeSettings.Builder()
                    .javaScriptEnabled(true)
                    .consoleOutput(false)
                    .aboutConfigEnabled(true)
                    .doubleTapZoomingEnabled(false)
                    .automaticFontSizeAdjustment(false)

                if (cfgFile != null) {
                    builder.configFilePath(cfgFile.absolutePath)
                }

                GeckoRuntime.create(appContext, builder.build()).also {
                    sharedRuntime = it
                }
            }
        }
    }

    private fun writeOptimizedGeckoConfig(appContext: Context): File? {
        return try {
            val cfgFile = File(appContext.filesDir, "battv_gecko_config.yaml")
            val yamlContent = """
                prefs:
                  browser.cache.disk.enable: true
                  browser.cache.disk.capacity: 40960
                  browser.cache.disk.smart_size.enabled: false
                  browser.cache.offline.enable: false
                  browser.cache.memory.enable: true
                  browser.cache.memory.capacity: 16384
                  browser.sessionhistory.max_total_viewers: 0
                  media.suspend-bkgnd-video.enabled: false
                  media.cache_size: 24576
                  media.memory_cache_max_size: 12288
                  media.memory_caches_combined_limit_kb: 36864
                  media.cache_readahead_limit: 30
                  media.cache_resume_threshold: 10
                  media.preload.default: 2
                  media.autoplay.default: 0
                  media.autoplay.blocking_policy: 0
                  media.av1.enabled: false
                  media.ffvpx.enabled: false
                  media.rdd-ffvpx.enabled: false
                  media.android-media-codec.enabled: true
                  media.android-media-codec.preferred: true
                  media.hardware-video-decoding.enabled: true
                  media.hardware-video-decoding.force-enabled: true
                  media.navigator.mediacapabilities.enabled: true
                  media.decoder.skip-to-next-key-frame.enabled: false
                  media.ruin-av-sync.enabled: false
                  media.eme.enabled: true
                  media.mediasource.enabled: true
                  media.mediasource.mp4.enabled: true
                  media.mediasource.webm.enabled: true
                  dom.ipc.processCount: 1
                  dom.min_background_timeout_value: 4
                  full-screen-api.enabled: true
                  full-screen-api.allow-trusted-requests-only: false
                  full-screen-api.warning.delay: -1
                  full-screen-api.warning.timeout: 0
                  nglayout.initialpaint.delay: 0
                  layout.frame_rate: 60
                  layout.css.backdrop-filter.enabled: false
                  gfx.webrender.all: true
                  gfx.webrender.compositor: true
                  gfx.canvas.accelerated: true
                  layers.acceleration.force-enabled: true
                  network.buffer.cache.size: 32768
                  network.buffer.cache.count: 32
                  network.http.http3.enable: false
                  network.http.keep-alive.timeout: 180
                  network.http.max-connections: 64
                  network.http.max-persistent-connections-per-server: 8
                  network.http.pacing.requests.enabled: false
                  network.cookie.cookieBehavior: 4
                  network.cookie.sameSite.laxByDefault: false
                  network.cookie.sameSite.noneRequiresSecure: false
                  network.http.sendRefererHeader: 2
                  network.dns.disablePrefetch: false
                  network.dns.disablePrefetchFromHTTPS: false
                  network.predictor.enabled: true
                  security.mixed_content.block_active_content: false
                  security.mixed_content.block_display_content: false
                  dom.security.https_only_mode: false
                  dom.disable_open_during_load: true
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
                return GeckoResult.fromValue(
                    GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
                )
            }
        }
    }

    private fun setupWebExtensions(controller: WebExtensionController) {
        controller.promptDelegate = object : WebExtensionController.PromptDelegate {
            override fun onOptionalPrompt(
                extension: WebExtension,
                permissions: Array<out String>,
                origins: Array<out String>
            ): GeckoResult<AllowOrDeny>? {
                return GeckoResult.allow()
            }
        }

        val requiredExtensions = listOf(
            Pair(POPUP_BLOCKER_URI, POPUP_BLOCKER_ID),
            Pair(UBLOCK_ORIGIN_URI, UBLOCK_ORIGIN_ID),
            Pair(VIOLENTMONKEY_URI, VIOLENTMONKEY_ID)
        )

        val allowedIds = requiredExtensions.map { it.second }.toSet()

        controller.list().accept({ existingList ->
            existingList?.forEach { ext ->
                if (!allowedIds.contains(ext.id)) {
                    controller.uninstall(ext)
                }
            }
        }, {})

        for ((assetUri, extId) in requiredExtensions) {
            controller.ensureBuiltIn(assetUri, extId).accept(
                { extension ->
                    if (extension != null) {
                        installedExtensions[extension.id] = extension

                        if (extension.id == VIOLENTMONKEY_ID) {
                            attachBridgeToExtension(extension)
                        }

                        controller.setAllowedInPrivateBrowsing(extension, true)
                        controller.enable(
                            extension,
                            WebExtensionController.EnableSource.USER
                        ).accept(
                            { enabledExt ->
                                if (enabledExt != null) {
                                    installedExtensions[enabledExt.id] = enabledExt
                                    if (enabledExt.id == VIOLENTMONKEY_ID) {
                                        attachBridgeToExtension(enabledExt)
                                    }
                                    Log.i(TAG, "Extension activa: ${enabledExt.id}")
                                }
                            },
                            { err ->
                                Log.e(TAG, "Error al habilitar extension ${extension.id}", err)
                            }
                        )
                    }
                },
                { err ->
                    Log.e(TAG, "Error al cargar extension $extId desde $assetUri", err)
                }
            )
        }
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
        return installedExtensions.containsKey(extensionId)
    }

    fun getExtensionDashboardUrl(extensionId: String, fallbackPath: String): String? {
        val ext = installedExtensions[extensionId] ?: return null
        val meta = try {
            ext.metaData
        } catch (_: Exception) {
            null
        }
        val optionsUrl = meta?.optionsPageUrl
        val baseUrl = meta?.baseUrl

        return when {
            !optionsUrl.isNullOrBlank() -> optionsUrl
            !baseUrl.isNullOrBlank() -> baseUrl.trimEnd('/') + "/" + fallbackPath.trimStart('/')
            else -> null
        }
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
                } else if (child.name != "storage" && child.name != "extensions") {
                    bytes += cleanDisposableGeckoDirs(child)
                }
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
    }
}
