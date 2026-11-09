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
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController

/**
 * BatTvGeckoViewManager
 *
 * Gestor central de Mozilla GeckoView para Android TV.
 * Gestiona las extensiones, navegación, bloqueo de ventanas emergentes
 * y configuración de la sesión del navegador.
 */
class BatTvGeckoViewManager(
    private val context: Context,
    private val geckoView: GeckoView,
    private val onUrlChanged: (String) -> Unit,
    private val onProgressChanged: (Int) -> Unit
) {

    companion object {
        private const val TAG = "BatTvGeckoView"

        const val FIREFOX_ANDROID_OFFICIAL_UA =
            "Mozilla/5.0 (Android 14; Mobile; rv:132.0) Gecko/132.0 Firefox/132.0"

        @Volatile
        private var sharedRuntime: GeckoRuntime? = null

        private val AD_POPUP_DOMAINS = listOf(
            "popads.net",
            "propellerads.com",
            "exoclick.com",
            "adsterra.com",
            "clickadu.com",
            "hilltopads.net",
            "galaksion.com",
            "juicyads.com",
            "trafficjunky.net",
            "onclickads.net",
            "doubleclick.net",
            "googlesyndication.com",
            "blurtsbrerd.com",
            "aarems.org",
            "21wiz.com",
            "capsidsflyleaf.com",
            "skypipevatican.com",
            "1xbet.com",
            "bet365.com",
            "mostbet.com",
            "caliente.mx"
        )
    }

    lateinit var runtime: GeckoRuntime
        private set

    lateinit var session: GeckoSession
        private set

    private var currentActiveHost: String? = null

    fun initialize() {
        runtime = getOrCreateRuntime(context.applicationContext)

        setupWebExtensions(runtime.webExtensionController)

        val sessionSettings = GeckoSessionSettings.Builder()
            .usePrivateMode(false)
            .useTrackingProtection(true)
            .userAgentMode(GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
            .userAgentOverride(FIREFOX_ANDROID_OFFICIAL_UA)
            .suspendMediaWhenInactive(false)
            .build()

        session = GeckoSession(sessionSettings)

        setupNavigationDelegate(session)
        setupContentDelegate(session)
        setupProgressDelegate(session)
        setupMediaSessionDelegate(session)

        session.open(runtime)
        geckoView.setSession(session)
    }

    private fun getOrCreateRuntime(appContext: Context): GeckoRuntime {
        return sharedRuntime ?: synchronized(this) {
            sharedRuntime ?: run {
                val runtimeSettings = GeckoRuntimeSettings.Builder()
                    .javaScriptEnabled(true)
                    .consoleOutput(true)
                    .aboutConfigEnabled(true)
                    .automaticFontSizeAdjustment(true)
                    .contentBlocking(
                        org.mozilla.geckoview.ContentBlocking.Settings.Builder()
                            .antiTracking(
                                org.mozilla.geckoview.ContentBlocking.AntiTracking.DEFAULT or
                                    org.mozilla.geckoview.ContentBlocking.AntiTracking.STP
                            )
                            .safeBrowsing(
                                org.mozilla.geckoview.ContentBlocking.SafeBrowsing.DEFAULT
                            )
                            .cookieBehavior(
                                org.mozilla.geckoview.ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY
                            )
                            .build()
                    )
                    .build()

                GeckoRuntime.create(appContext, runtimeSettings).also {
                    sharedRuntime = it
                }
            }
        }
    }

    /**
     * Instalación y habilitación de las extensiones integradas.
     * La firma de onOptionalPrompt corresponde a GeckoView 130.
     */
    private fun setupWebExtensions(controller: WebExtensionController) {
        controller.promptDelegate =
            object : WebExtensionController.PromptDelegate {

                override fun onOptionalPrompt(
                    extension: WebExtension,
                    permissions: Array<out String>,
                    origins: Array<out String>
                ): GeckoResult<AllowOrDeny>? {
                    Log.i(
                        TAG,
                        "Auto-aprobando permisos para extensión: ${extension.id}"
                    )
                    return GeckoResult.allow()
                }
            }

        val builtInExtensions = listOf(
            Pair(
                "resource://android/assets/extensions/ublock_origin/",
                "uBlock0@raymondhill.net"
            ),
            Pair(
                "resource://android/assets/extensions/violentmonkey/",
                "{aecec67f-0d10-4fa7-b7c7-609a2db280cf}"
            ),
            Pair(
                "resource://android/assets/extensions/battv_shield/",
                "shield@battv.browser"
            )
        )

        for ((assetUri, extId) in builtInExtensions) {
            controller.ensureBuiltIn(assetUri, extId).accept(
                { extension ->
                    if (extension != null) {
                        Log.i(
                            TAG,
                            "Extensión instalada correctamente: ${extension.id} " +
                                "(v${extension.metaData.version})"
                        )

                        controller.setAllowedInPrivateBrowsing(extension, true)

                        controller.enable(
                            extension,
                            WebExtensionController.EnableSource.USER
                        ).accept(
                            { enabledExt ->
                                Log.i(
                                    TAG,
                                    "Extensión activa: ${enabledExt?.id}"
                                )
                            },
                            { err ->
                                Log.e(
                                    TAG,
                                    "Error al habilitar extensión ${extension.id}",
                                    err
                                )
                            }
                        )
                    }
                },
                { throwable ->
                    Log.e(
                        TAG,
                        "Fallo al instalar extensión desde $assetUri ($extId)",
                        throwable
                    )
                }
            )
        }
    }

    private fun setupNavigationDelegate(geckoSession: GeckoSession) {
        geckoSession.navigationDelegate =
            object : GeckoSession.NavigationDelegate {

                override fun onNewSession(
                    session: GeckoSession,
                    uri: String
                ): GeckoResult<GeckoSession>? {
                    Log.w(TAG, "Pop-up bloqueado: $uri")
                    return GeckoResult.fromValue(null)
                }

                override fun onLoadRequest(
                    session: GeckoSession,
                    request: GeckoSession.NavigationDelegate.LoadRequest
                ): GeckoResult<AllowOrDeny>? {

                    val uriString = request.uri
                    val parsedUri = runCatching {
                        Uri.parse(uriString)
                    }.getOrNull()

                    val targetHost = parsedUri?.host?.lowercase() ?: ""

                    if (
                        request.target ==
                        GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW
                    ) {
                        Log.w(
                            TAG,
                            "Bloqueado intento de abrir pestaña emergente: $uriString"
                        )
                        return GeckoResult.deny()
                    }

                    if (isAdOrPopupDomain(targetHost)) {
                        Log.w(
                            TAG,
                            "Bloqueado dominio publicitario: $uriString"
                        )
                        return GeckoResult.deny()
                    }

                    val scheme = parsedUri?.scheme?.lowercase() ?: ""

                    if (
                        scheme != "http" &&
                        scheme != "https" &&
                        scheme != "about" &&
                        scheme != "resource" &&
                        scheme != "data" &&
                        scheme != "blob"
                    ) {
                        Log.w(TAG, "Bloqueado esquema externo: $uriString")
                        return GeckoResult.deny()
                    }

                    if (
                        request.isRedirect &&
                        !request.hasUserGesture &&
                        currentActiveHost != null
                    ) {
                        val active = currentActiveHost!!

                        if (!isSameOrTrustedStreamingDomain(active, targetHost)) {
                            Log.w(
                                TAG,
                                "Bloqueada redirección automática desde " +
                                    "$active hacia $targetHost"
                            )
                            return GeckoResult.deny()
                        }
                    }

                    return GeckoResult.allow()
                }

                override fun onLocationChange(
                    session: GeckoSession,
                    url: String?,
                    perms: MutableList<GeckoSession.PermissionDelegate.ContentPermission>,
                    hasUserGesture: Boolean
                ) {
                    if (!url.isNullOrBlank() && !url.startsWith("resource://")) {
                        currentActiveHost = runCatching {
                            Uri.parse(url).host?.lowercase()
                        }.getOrNull()

                        onUrlChanged(url)
                    }
                }
            }
    }

    private fun setupContentDelegate(geckoSession: GeckoSession) {
        geckoSession.contentDelegate =
            object : GeckoSession.ContentDelegate {

                override fun onCloseRequest(session: GeckoSession) {
                    Log.w(
                        TAG,
                        "Ignorado intento de cerrar la pestaña mediante window.close"
                    )
                }

                override fun onFullScreen(
                    session: GeckoSession,
                    fullScreen: Boolean
                ) {
                    Log.i(
                        TAG,
                        "Estado de pantalla completa: $fullScreen"
                    )
                }
            }
    }

    private fun setupProgressDelegate(geckoSession: GeckoSession) {
        geckoSession.progressDelegate =
            object : GeckoSession.ProgressDelegate {

                override fun onProgressChange(
                    session: GeckoSession,
                    progress: Int
                ) {
                    onProgressChanged(progress)
                }
            }
    }

    private fun setupMediaSessionDelegate(geckoSession: GeckoSession) {
        geckoSession.mediaSessionDelegate =
            object : org.mozilla.geckoview.MediaSession.Delegate {

                override fun onActivated(
                    session: GeckoSession,
                    mediaSession: org.mozilla.geckoview.MediaSession
                ) {
                    Log.i(
                        TAG,
                        "Reproductor multimedia activo en GeckoView"
                    )
                }
            }
    }

    fun loadInputFromAddressBar(rawInput: String) {
        val targetUrl = resolveInputToUrl(rawInput)

        currentActiveHost = runCatching {
            Uri.parse(targetUrl).host?.lowercase()
        }.getOrNull()

        session.loadUri(targetUrl)
    }

    fun resolveInputToUrl(rawInput: String): String {
        val trimmed = rawInput.trim()

        if (trimmed.isEmpty()) {
            return "https://www.google.com/"
        }

        if (
            trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
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

    private fun isAdOrPopupDomain(host: String): Boolean {
        if (host.isEmpty()) return false

        return AD_POPUP_DOMAINS.any { adDomain ->
            host == adDomain || host.endsWith(".$adDomain")
        }
    }

    private fun isSameOrTrustedStreamingDomain(
        originHost: String,
        targetHost: String
    ): Boolean {
        if (originHost == targetHost) return true

        val trustedHosts = listOf(
            "google.com",
            "doramasflix.io",
            "doramasflix.co",
            "dramachino.com",
            "asialiveaction.com",
            "tudorama.com",
            "kisskh.do",
            "kisskh.co",
            "jkanime.net",
            "twitch.tv",
            "voe.sx",
            "filemoon.sx",
            "byse.sx",
            "bysebuho.com",
            "bysefujedu.com",
            "n1mwq.org",
            "primeload.co",
            "streamwish.to",
            "do7go.com",
            "streamtape.com",
            "ok.ru",
            "vidhidevip.com",
            "callistanise.com",
            "mega.nz",
            "pkaa.top"
        )

        return trustedHosts.any { trusted ->
            targetHost == trusted || targetHost.endsWith(".$trusted")
        }
    }
}
