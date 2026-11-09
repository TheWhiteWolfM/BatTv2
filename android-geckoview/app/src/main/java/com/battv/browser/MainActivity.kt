package com.battv.browser

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.PanZoomController
import org.mozilla.geckoview.ScreenLength
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import java.io.File
import java.lang.reflect.Proxy
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.Collections
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "BatTVBrowser"
        private const val DEFAULT_HOME_URL = "https://www.google.com"
        private const val HDFULL_URL = "https://hdfull.one/"
        private const val DORAMASFLIX_URL = "https://doramasflix.co/"
        private const val JKANIME_URL = "https://jkanime.net/"
        private const val MOZILLA_ADDONS_URL = "https://addons.mozilla.org/es/firefox/extensions/"
        private const val GREASYFORK_URL = "https://greasyfork.org/es/scripts"

        private const val UBLOCK_ASSET_URI = "resource://android/assets/extensions/ublock/"
        private const val UBLOCK_EXTENSION_ID = "uBlock0@raymondhill.net"

        private const val VIOLENTMONKEY_ASSET_URI = "resource://android/assets/extensions/violentmonkey/"
        private const val VIOLENTMONKEY_EXTENSION_ID = "{aecec67f-0d10-4fa7-b7c7-609a2db280cf}"

        private const val BATTV_SCRIPTS_ASSET_URI = "resource://android/assets/extensions/battv_scripts/"
        private const val BATTV_SCRIPTS_EXTENSION_ID = "battv-scripts@battv.local"
        private const val BATTV_NATIVE_BRIDGE_APP = "battv_bridge"

        private const val PREFS_NAME = "battv_browser_prefs"
        private const val KEY_BOOKMARKS = "bookmarks_json_v18"
        private const val KEY_HISTORY = "history_json"
        private const val KEY_USERSCRIPTS = "userscripts_json"
        private const val KEY_DESKTOP_MODE = "desktop_mode_enabled"
        private const val KEY_REDIRECT_LOCK = "redirect_lock_enabled"

        private const val BASE_CURSOR_STEP_PX = 14f
        private const val MAX_CURSOR_STEP_PX = 28f
        private const val CURSOR_ACCEL = 1.1f
        private const val EDGE_SCROLL_MARGIN_PX = 125f
        private const val CORNER_HIDE_MARGIN_PX = 36f
        private const val SCROLL_STEP_PX = 145.0

        private const val CURSOR_AUTO_HIDE_MS = 3000L
        private const val URL_BAR_INDEX = 4

        private const val DRAMACHINO_URL = "https://dramachino.com/"

        private val AD_BLOCK_DOMAINS = listOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "popads.net", "popcash.net", "propellerads.com", "exoclick.com",
            "juicyads.com", "adsterra.com", "onclickads.net", "hilltopads.net",
            "taboola.com", "outbrain.com", "zedo.com", "adnxs.com",
            "criteo.com", "pubmatic.com", "revcontent.com", "mgid.com",
            "trafficjunky.net", "bidgear.com", "admaven.com", "galaksion.com",
            "clickadu.com", "adcash.com", "bidvertiser.com", "richads.com",
            "monetag.com", "acscdn.com", "tsyndicate.com", "betteradsystem.com",
            "realsrv.com", "magsrv.com", "clksite.com", "clkmon.com",
            "adspyglass.com", "rollerads.com", "evadav.com", "pushground.com",
            "trafficstars.com", "trafficfactory.biz", "yllix.com", "plugrush.com",
            "adskeeper.com", "adskeeper.co.uk", "a-ads.com", "adsco.re",
            "Rubiconproject.com", "openx.net", "casalemedia.com", "smartadserver.com",
            "teads.tv", "vidoomy.com", "optad360.io", "setupad.com", "twinrdsyn.com"
        )

        private val AD_URL_PATTERNS = listOf(
            "/apu.php", "/afu.php", "/popunder", "/tabunder", "/clickunder",
            "utm_source=pop", "ad_type=pop", "/adclick", "zoneid=", "directlink"
        )

        private val GECKO_DISPOSABLE_DIRS = setOf(
            "cache2", "OfflineCache", "thumbnails", "safebrowsing",
            "crashes", "minidumps", "saved-telemetry-pings", "datareporting"
        )

        private var sGeckoRuntime: GeckoRuntime? = null
        private val installedExtensionsMap = mutableMapOf<String, WebExtension>()
    }

    private lateinit var prefs: SharedPreferences
    private val mainHandler = Handler(Looper.getMainLooper())
    private var wifiHighPerfLock: WifiManager.WifiLock? = null

    private lateinit var rootLayout: LinearLayout
    private lateinit var topBarLayout: LinearLayout
    private lateinit var brandIcon: ImageView
    private lateinit var btnHome: Button
    private lateinit var btnBack: Button
    private lateinit var btnReload: Button
    private lateinit var urlEditText: EditText
    private lateinit var btnPrevField: Button
    private lateinit var btnNextField: Button
    private lateinit var btnGo: Button
    private lateinit var btnDesktopMode: Button
    private lateinit var btnBookmarks: Button
    private lateinit var btnHistory: Button
    private lateinit var btnCleanCache: Button
    private lateinit var btnExtensions: Button
    private lateinit var btnCloseApp: Button
    private lateinit var toolbarItems: List<View>

    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var webContainer: FrameLayout
    private lateinit var geckoView: GeckoView
    private lateinit var videoSeekOverlay: VideoSeekOverlayView
    private lateinit var cursorOverlay: CursorOverlayView

    private var hasActiveVodVideo: Boolean = false
    private var vodIsPlaying: Boolean = false
    private var vodCurrentTimeSec: Double = 0.0
    private var vodDurationSec: Double = 0.0
    private var isUserSeekingVod: Boolean = false
    private var consecutiveSeekPresses: Int = 0
    private var lastSeekDirection: Int = 0
    private var lastSeekPressUptime: Long = 0L
    private var cumulativeSeekDeltaSec: Double = 0.0
    private var webScrollY: Double = 0.0
    private var verticalScrollDirection: Int = 0
    private var verticalScrollStartUptimeMs: Long = 0L
    private var lastVerticalScrollEventUptimeMs: Long = 0L

    private var geckoSession: GeckoSession? = null
    private val activeWebPorts = Collections.synchronizedSet(mutableSetOf<WebExtension.Port>())
    private var canGoBackState: Boolean = false
    private var isFullScreenVideo: Boolean = false
    private var currentUrl: String = DEFAULT_HOME_URL
    private var currentTitle: String = "Bat TV"

    private var allowedUserTargetUrl: String = DEFAULT_HOME_URL
    private var isDesktopMode: Boolean = true
    private var isRedirectLockEnabled: Boolean = true

    private var isCursorModeActive: Boolean = true
    private var toolbarIndex: Int = URL_BAR_INDEX
    private var isEditingUrlText: Boolean = false
    private var isEditingWebInput: Boolean = false
    private var activeWebInputId: String = ""
    private var isCurrentWebInputMultiField: Boolean = false
    private var suppressWebInputWatcher: Boolean = false
    private var pendingWebInputId: String = ""
    private var pendingWebInputInitialValue: String? = null
    private var pendingWebInputHint: String? = null
    private var pendingWebInputMultiField: Boolean = false
    private var lastUserWebClickTime: Long = 0L
    private var allowHistoryBackUntilUptimeMs: Long = 0L
    private var isSyntheticDpadTap: Boolean = false

    private var backPressAttemptsOnSamePage: Int = 0
    private var lastBackPressTime: Long = 0L
    private var isExitDialogShowing: Boolean = false

    private val hideCursorRunnable = Runnable {
        cursorOverlay.hideCursorUntilMoved()
    }

    private val activateNativeWebInputRunnable = Runnable {
        activateNativeWebInputNow()
    }

    private val hideVideoSeekRunnable = Runnable {
        isUserSeekingVod = false
        consecutiveSeekPresses = 0
        cumulativeSeekDeltaSec = 0.0
        videoSeekOverlay.fadeOutAndHide()
    }

    private val commitVideoSeekRunnable = Runnable {
        sendVideoCommandToWeb("SEEK_TO", vodCurrentTimeSec)
        isUserSeekingVod = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )
        acquireHighPerfWifiLock()
        prewarmDnsCacheInBackground()

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isDesktopMode = prefs.getBoolean(KEY_DESKTOP_MODE, true)
        isRedirectLockEnabled = prefs.getBoolean(KEY_REDIRECT_LOCK, true)

        ensureDefaultBookmarks()
        ensureDefaultPreinstalledScripts()

        setupUserInterface()

        val initialUrl = intent?.dataString?.takeIf { it.isNotBlank() } ?: DEFAULT_HOME_URL
        webContainer.post {
            cursorOverlay.centerCursor()
            setupGeckoEngine(initialUrl)
            activateCursorMode(showPointerNow = true)
        }
    }

    private fun prewarmDnsCacheInBackground() {
        Thread {
            val hosts = listOf(
                "www.google.com",
                "dramachino.com",
                "doramasflix.co",
                "jkanime.net",
                "hdfull.one",
                "www.youtube.com",
                "www.twitch.tv",
                "kick.com"
            )
            for (h in hosts) {
                try {
                    InetAddress.getAllByName(h)
                } catch (_: Exception) {}
            }
        }.start()
    }

    @Suppress("DEPRECATION")
    private fun acquireHighPerfWifiLock() {
        try {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            if (wm != null && wifiHighPerfLock == null) {
                wifiHighPerfLock = wm.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "BatTV:LiveStreamWifiLock"
                ).apply {
                    setReferenceCounted(false)
                    acquire()
                }
            } else if (wifiHighPerfLock?.isHeld == false) {
                wifiHighPerfLock?.acquire()
            }
        } catch (_: Exception) {}
    }

    private fun releaseHighPerfWifiLock() {
        try {
            if (wifiHighPerfLock?.isHeld == true) {
                wifiHighPerfLock?.release()
            }
        } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        acquireHighPerfWifiLock()
        geckoSession?.setActive(true)
        geckoSession?.setFocused(true)
        try {
            geckoSession?.setPriorityHint(GeckoSession.PRIORITY_HIGH)
        } catch (_: Exception) {}
        if (isCursorModeActive) {
            geckoView.requestFocus()
        }
    }

    private fun setupUserInterface() {
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#080B18"))
        }

        topBarLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(5), dp(4), dp(5), dp(4))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(
                    Color.parseColor("#0D1333"),
                    Color.parseColor("#1D1147"),
                    Color.parseColor("#2E104B")
                )
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(50)
            )
        }

        brandIcon = ImageView(this).apply {
            setImageResource(R.drawable.ic_bat_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            isFocusable = true
            isFocusableInTouchMode = false
            setPadding(dp(2), dp(2), dp(2), dp(2))
            background = createIconBackground(false)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                marginEnd = dp(4)
            }
            setOnClickListener {
                loadUrlOrSearch(DEFAULT_HOME_URL)
                Toast.makeText(this@MainActivity, "🌸 Inicio Bat TV", Toast.LENGTH_SHORT).show()
            }
        }

        btnHome = createTvButton("🏠", dp(34)).apply {
            setOnClickListener {
                loadUrlOrSearch(DEFAULT_HOME_URL)
            }
        }

        btnBack = createTvButton("◄", dp(34)).apply {
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(36)).apply {
                marginStart = dp(2)
            }
            setOnClickListener {
                handleSmartBackNavigation()
            }
        }

        btnReload = createTvButton("⟳", dp(34)).apply {
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(36)).apply {
                marginStart = dp(2)
            }
            setOnClickListener {
                geckoSession?.stop()
                geckoSession?.reload()
                activateCursorMode(showPointerNow = false)
            }
        }

        // Usamos el MISMO urlEditText de la barra superior tanto para escribir URLs como para
        // escribir en los buscadores internos de Doramasflix, JKAnime, hdfull.one, etc.
        urlEditText = object : EditText(this) {
            override fun onKeyPreIme(keyCode: Int, event: KeyEvent?): Boolean {
                if (keyCode == KeyEvent.KEYCODE_BACK && event != null) {
                    if (event.action == KeyEvent.ACTION_UP) {
                        if (isEditingWebInput) {
                            // Al pulsar Atrás con el teclado abierto: cerrar teclado SIN enviar Enter,
                            // SIN recargar la página y conservando intacto todo lo escrito en el campo
                            closeNativeWebInputBridge(submit = false)
                        } else if (isEditingUrlText) {
                            stopEditingUrlTextAndStayOnBar()
                        }
                    }
                    return true
                }
                return super.onKeyPreIme(keyCode, event)
            }
        }.apply {
            hint = "Escribir web o buscar doramas, anime, películas (Pulsa OK para escribir)..."
            setHintTextColor(Color.parseColor("#A5B4FC"))
            setTextColor(Color.parseColor("#F8FAFC"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
            isSingleLine = true
            isFocusable = false
            isFocusableInTouchMode = false
            isCursorVisible = false
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setPadding(dp(8), dp(3), dp(8), dp(3))
            background = createInputBackground(selectedOnBar = false, editingText = false)
            layoutParams = LinearLayout.LayoutParams(0, dp(36), 1f).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            }

            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (!suppressWebInputWatcher && isEditingWebInput) {
                        syncWebInputTextToPage(s?.toString() ?: "", submit = false)
                    }
                }
            })

            setOnClickListener {
                startEditingUrlText(forWebInput = isEditingWebInput)
            }

            setOnEditorActionListener { _, actionId, keyEvent ->
                if (keyEvent != null && keyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (keyEvent.action == KeyEvent.ACTION_UP && isEditingWebInput) {
                        closeNativeWebInputBridge(submit = false)
                    }
                    return@setOnEditorActionListener true
                }

                // Si el usuario está en un formulario web y pulsa Siguiente (NEXT), pasamos al siguiente campo sin enviar
                if (isEditingWebInput && actionId == EditorInfo.IME_ACTION_NEXT) {
                    navigateAdjacentWebFormField(1)
                    return@setOnEditorActionListener true
                }

                // Si es un formulario de varios campos y pulsa Listo (DONE), conservamos el texto y cerramos el teclado sin enviar
                if (isEditingWebInput && isCurrentWebInputMultiField && actionId == EditorInfo.IME_ACTION_DONE) {
                    closeNativeWebInputBridge(submit = false)
                    return@setOnEditorActionListener true
                }

                val isEnterKey = keyEvent != null &&
                    (keyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
                        keyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) &&
                    keyEvent.action == KeyEvent.ACTION_DOWN

                if (actionId == EditorInfo.IME_ACTION_GO ||
                    actionId == EditorInfo.IME_ACTION_SEARCH ||
                    actionId == EditorInfo.IME_ACTION_SEND ||
                    (!isEditingWebInput && actionId == EditorInfo.IME_ACTION_DONE) ||
                    (!isCurrentWebInputMultiField && actionId == EditorInfo.IME_ACTION_DONE) ||
                    isEnterKey
                ) {
                    submitUrlFromInput()
                    true
                } else {
                    false
                }
            }
        }

        btnPrevField = createTvButton("◄Ant", dp(42), isAccent = true).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(36)).apply {
                marginEnd = dp(2)
            }
            setOnClickListener {
                navigateAdjacentWebFormField(-1)
            }
        }

        btnNextField = createTvButton("Sig►", dp(42), isAccent = true).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(36)).apply {
                marginEnd = dp(3)
            }
            setOnClickListener {
                navigateAdjacentWebFormField(1)
            }
        }

        btnGo = createTvButton("Ir", dp(36)).apply {
            setOnClickListener {
                submitUrlFromInput()
            }
        }

        btnDesktopMode = createTvButton(if (isDesktopMode) "🖥️" else "📱", dp(34), isAccent = isDesktopMode).apply {
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(36)).apply {
                marginStart = dp(2)
            }
            setOnClickListener {
                toggleDesktopMode()
            }
        }

        btnBookmarks = createTvButton("⭐", dp(34)).apply {
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(36)).apply {
                marginStart = dp(2)
            }
            setOnClickListener {
                showBookmarksDialog()
            }
        }

        btnHistory = createTvButton("🕒", dp(34)).apply {
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(36)).apply {
                marginStart = dp(2)
            }
            setOnClickListener {
                showHistoryDialog()
            }
        }

        btnCleanCache = createTvButton("🧹", dp(34)).apply {
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(36)).apply {
                marginStart = dp(2)
            }
            setOnClickListener {
                clearSafeAndroidCachesOnly(silent = false)
            }
        }

        btnExtensions = createTvButton("🧩", dp(36), isAccent = true).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                marginStart = dp(2)
            }
            setOnClickListener {
                showExtensionsDialog()
            }
        }

        btnCloseApp = createTvButton("✖", dp(36), isDanger = true).apply {
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                marginStart = dp(3)
            }
            setOnClickListener {
                exitAndKillBrowserImmediately()
            }
        }

        toolbarItems = listOf(
            brandIcon,
            btnHome,
            btnBack,
            btnReload,
            urlEditText,
            btnPrevField,
            btnNextField,
            btnGo,
            btnDesktopMode,
            btnBookmarks,
            btnHistory,
            btnCleanCache,
            btnExtensions,
            btnCloseApp
        )

        for (item in toolbarItems) {
            topBarLayout.addView(item)
        }

        loadingProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3)
            )
        }

        webContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        geckoView = GeckoView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFocusable = true
            isFocusableInTouchMode = true

            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    lastUserWebClickTime = SystemClock.uptimeMillis()
                }
                if (!isSyntheticDpadTap) {
                    cursorOverlay.hideCursorUntilMoved()
                }
                false
            }
        }

        cursorOverlay = CursorOverlayView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = false
            elevation = 100f
        }

        videoSeekOverlay = VideoSeekOverlayView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = false
            visibility = View.GONE
            elevation = 90f
        }

        webContainer.addView(geckoView)
        webContainer.addView(videoSeekOverlay)
        webContainer.addView(cursorOverlay)

        rootLayout.addView(topBarLayout)
        rootLayout.addView(loadingProgressBar)
        rootLayout.addView(webContainer)

        setContentView(rootLayout)
    }

    private fun openNativeWebInputBridge(
        inputId: String = "",
        initialValue: String? = null,
        placeholderHint: String? = null,
        isMultiField: Boolean = false
    ) {
        if (isEditingUrlText && !isEditingWebInput) return
        if (inputId.isNotBlank()) {
            pendingWebInputId = inputId
        }
        if (initialValue != null) {
            pendingWebInputInitialValue = initialValue
        }
        if (!placeholderHint.isNullOrBlank()) {
            pendingWebInputHint = placeholderHint
        }
        pendingWebInputMultiField = isMultiField
        mainHandler.removeCallbacks(activateNativeWebInputRunnable)
        mainHandler.postDelayed(activateNativeWebInputRunnable, 55L)
    }

    private fun activateNativeWebInputNow() {
        if (isEditingUrlText && !isEditingWebInput) return
        if (pendingWebInputId.isNotBlank()) {
            activeWebInputId = pendingWebInputId
        }
        isCurrentWebInputMultiField = pendingWebInputMultiField
        startEditingUrlText(
            forWebInput = true,
            initialValue = pendingWebInputInitialValue,
            placeholderHint = pendingWebInputHint
        )
    }

    private fun navigateAdjacentWebFormField(direction: Int) {
        // Primero confirmamos el valor actual en el campo activo sin enviar el formulario
        val curText = urlEditText.text?.toString() ?: ""
        syncWebInputTextToPage(curText, submit = false)

        val msg = JSONObject().apply {
            put("type", "WEB_FORM_NAVIGATE")
            put("direction", direction)
            put("inputId", activeWebInputId)
        }
        synchronized(activeWebPorts) {
            val iterator = activeWebPorts.iterator()
            while (iterator.hasNext()) {
                val p = iterator.next()
                try {
                    p.postMessage(msg)
                } catch (_: Exception) {
                    iterator.remove()
                }
            }
        }
    }

    private fun requestActiveWebInputFromPage() {
        val msg = JSONObject().apply {
            put("type", "REQUEST_ACTIVE_WEB_INPUT")
        }
        synchronized(activeWebPorts) {
            val iterator = activeWebPorts.iterator()
            while (iterator.hasNext()) {
                val p = iterator.next()
                try {
                    p.postMessage(msg)
                } catch (_: Exception) {
                    iterator.remove()
                }
            }
        }
    }

    private fun commitWebInputWithoutSubmit() {
        if (activeWebInputId.isNotBlank()) {
            val curText = urlEditText.text?.toString() ?: ""
            syncWebInputTextToPage(curText, submit = false)
        }
        val msg = JSONObject().apply {
            put("type", "WEB_INPUT_COMMIT_ONLY")
            put("inputId", activeWebInputId)
        }
        synchronized(activeWebPorts) {
            val iterator = activeWebPorts.iterator()
            while (iterator.hasNext()) {
                val p = iterator.next()
                try {
                    p.postMessage(msg)
                } catch (_: Exception) {
                    iterator.remove()
                }
            }
        }
    }

    private fun syncWebInputTextToPage(text: String, submit: Boolean) {
        val msg = JSONObject().apply {
            put("type", "WEB_INPUT_UPDATE")
            put("inputId", activeWebInputId)
            put("value", text)
            put("submit", submit)
        }

        synchronized(activeWebPorts) {
            val iterator = activeWebPorts.iterator()
            while (iterator.hasNext()) {
                val p = iterator.next()
                try {
                    p.postMessage(msg)
                } catch (_: Exception) {
                    iterator.remove()
                }
            }
        }
    }

    private fun sendVideoCommandToWeb(action: String, targetTimeSec: Double = 0.0) {
        val msg = JSONObject().apply {
            put("type", "VIDEO_COMMAND")
            put("action", action)
            put("time", targetTimeSec)
        }

        synchronized(activeWebPorts) {
            val iterator = activeWebPorts.iterator()
            while (iterator.hasNext()) {
                val p = iterator.next()
                try {
                    p.postMessage(msg)
                } catch (_: Exception) {
                    iterator.remove()
                }
            }
        }
    }

    private fun formatOffsetDelta(deltaSec: Double): String {
        val sign = if (deltaSec >= 0.0) "+" else "−"
        val absSec = kotlin.math.abs(deltaSec.toInt())
        val mins = absSec / 60
        val secs = absSec % 60
        return String.format("%s%02d:%02d", sign, mins, secs)
    }

    private fun handleDpadVideoSeek(keyCode: Int, repeatCount: Int) {
        if (!isFullScreenVideo || !hasActiveVodVideo || vodDurationSec <= 0.0) return
        val now = SystemClock.uptimeMillis()
        val dir = if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1

        if (dir == lastSeekDirection && (now - lastSeekPressUptime) < 750L) {
            consecutiveSeekPresses++
        } else {
            consecutiveSeekPresses = 1
            if (!videoSeekOverlay.isOverlayVisible() || dir != lastSeekDirection) {
                cumulativeSeekDeltaSec = 0.0
            }
        }
        lastSeekDirection = dir
        lastSeekPressUptime = now

        val effectiveCount = max(consecutiveSeekPresses, repeatCount + 1)
        val stepSec = when {
            effectiveCount >= 10 -> 25.0
            effectiveCount >= 6 -> 15.0
            effectiveCount >= 3 -> 10.0
            else -> 5.0
        }

        val delta = stepSec * dir
        isUserSeekingVod = true
        vodCurrentTimeSec = (vodCurrentTimeSec + delta).coerceIn(0.0, vodDurationSec)
        cumulativeSeekDeltaSec += delta

        cursorOverlay.hideCursorUntilMoved()
        mainHandler.removeCallbacks(hideCursorRunnable)

        val badge = formatOffsetDelta(cumulativeSeekDeltaSec)
        videoSeekOverlay.showSeekState(vodCurrentTimeSec, vodDurationSec, badge, vodIsPlaying)

        mainHandler.removeCallbacks(commitVideoSeekRunnable)
        mainHandler.postDelayed(commitVideoSeekRunnable, 150L)

        mainHandler.removeCallbacks(hideVideoSeekRunnable)
        mainHandler.postDelayed(hideVideoSeekRunnable, 2500L)
    }

    private fun closeNativeWebInputBridge(submit: Boolean) {
        mainHandler.removeCallbacks(activateNativeWebInputRunnable)
        if (submit) {
            val finalQuery = urlEditText.text?.toString() ?: ""
            syncWebInputTextToPage(finalQuery, submit = true)
        } else {
            // Al cerrar con Atrás o bajar al cursor: conservamos el texto escrito en el campo sin enviar Enter
            commitWebInputWithoutSubmit()
        }
        pendingWebInputId = ""
        pendingWebInputInitialValue = null
        pendingWebInputHint = null
        pendingWebInputMultiField = false
        activeWebInputId = ""
        isCurrentWebInputMultiField = false
        isEditingWebInput = false
        isEditingUrlText = false
        btnPrevField.visibility = View.GONE
        btnNextField.visibility = View.GONE
        btnGo.text = "Ir"
        urlEditText.imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        urlEditText.hint = "Escribir web o buscar doramas, anime, películas (Pulsa OK para escribir)..."
        suppressWebInputWatcher = true
        urlEditText.setText(currentUrl)
        suppressWebInputWatcher = false
        hideSoftKeyboardFromView(urlEditText)
        urlEditText.clearFocus()
        urlEditText.isCursorVisible = false
        urlEditText.isFocusable = false
        urlEditText.isFocusableInTouchMode = false
        activateCursorMode(showPointerNow = true)
    }

    private fun startEditingUrlText(
        forWebInput: Boolean = false,
        initialValue: String? = null,
        placeholderHint: String? = null
    ) {
        topBarLayout.visibility = View.VISIBLE
        isCursorModeActive = false
        toolbarIndex = URL_BAR_INDEX
        isEditingUrlText = true
        isEditingWebInput = forWebInput

        cursorOverlay.hideCursorUntilMoved()
        hideSoftKeyboardFromView(geckoView)
        geckoView.clearFocus()
        geckoView.isFocusable = false
        geckoView.isFocusableInTouchMode = false
        geckoSession?.setFocused(true)

        if (forWebInput) {
            btnPrevField.visibility = View.VISIBLE
            btnNextField.visibility = View.VISIBLE
            btnGo.text = "🔍"
            urlEditText.imeOptions = (if (isCurrentWebInputMultiField) EditorInfo.IME_ACTION_NEXT else EditorInfo.IME_ACTION_SEARCH) or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            urlEditText.hint = if (!placeholderHint.isNullOrBlank()) {
                placeholderHint
            } else {
                "🌸 Escribir en el campo (◄Ant / Sig► para cambiar de campo, Atrás conserva texto)..."
            }
            if (initialValue != null) {
                suppressWebInputWatcher = true
                urlEditText.setText(initialValue)
                urlEditText.setSelection(initialValue.length)
                suppressWebInputWatcher = false
            }
        } else {
            btnPrevField.visibility = View.GONE
            btnNextField.visibility = View.GONE
            btnGo.text = "Ir"
            urlEditText.imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            urlEditText.hint = "Escribir web o buscar doramas, anime, películas (Pulsa OK para escribir)..."
        }

        urlEditText.isFocusable = true
        urlEditText.isFocusableInTouchMode = true
        urlEditText.isCursorVisible = true
        updateToolbarSelectionVisuals()
        urlEditText.requestFocus()
        urlEditText.post {
            if (!isEditingUrlText) return@post
            urlEditText.requestFocus()
            if (isEditingWebInput) {
                urlEditText.setSelection(urlEditText.text?.length ?: 0)
            } else {
                urlEditText.selectAll()
            }
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(urlEditText, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun stopEditingUrlTextAndStayOnBar() {
        if (isEditingWebInput) {
            closeNativeWebInputBridge(submit = false)
            return
        }
        isEditingUrlText = false
        btnGo.text = "Ir"
        hideSoftKeyboardFromView(urlEditText)
        urlEditText.clearFocus()
        urlEditText.isCursorVisible = false
        urlEditText.isFocusable = false
        urlEditText.isFocusableInTouchMode = false
        updateToolbarSelectionVisuals()
    }

    private fun updateToolbarSelectionVisuals() {
        for (i in toolbarItems.indices) {
            val view = toolbarItems[i]
            val isSelected = !isCursorModeActive && (i == toolbarIndex)
            when (view) {
                is ImageView -> {
                    view.background = createIconBackground(isSelected)
                }
                is EditText -> {
                    view.background = createInputBackground(
                        selectedOnBar = isSelected,
                        editingText = isSelected && isEditingUrlText
                    )
                }
                is Button -> {
                    val isAccent = (view === btnDesktopMode && isDesktopMode) || (view === btnExtensions)
                    val isDanger = (view === btnCloseApp)
                    view.background = createButtonBackground(isSelected, isAccent, isDanger)
                }
            }
        }
    }

    private fun handleSmartBackNavigation() {
        val session = geckoSession
        val now = SystemClock.uptimeMillis()
        val pressedQuickly = (now - lastBackPressTime) < 1300L
        lastBackPressTime = now

        if (canGoBackState && session != null && backPressAttemptsOnSamePage < 2 && !pressedQuickly) {
            backPressAttemptsOnSamePage++
            allowHistoryBackUntilUptimeMs = now + 2500L
            session.stop()
            session.goBack()
            activateCursorMode(showPointerNow = false)
        } else if (canGoBackState && session != null && backPressAttemptsOnSamePage == 0) {
            backPressAttemptsOnSamePage++
            allowHistoryBackUntilUptimeMs = now + 2500L
            session.stop()
            session.goBack()
            activateCursorMode(showPointerNow = false)
        } else {
            showExitBrowserConfirmationDialog()
        }
    }

    private fun showExitBrowserConfirmationDialog() {
        if (isExitDialogShowing) return
        isExitDialogShowing = true

        val dialog = AlertDialog.Builder(this)
            .setTitle("🚪 ¿Cerrar Bat TV?")
            .setMessage("¿Deseas cerrar la aplicación por completo? Se cerrará la sesión activa y se limpiará todo el caché temporal automáticamente.")
            .setPositiveButton("✖ Cerrar aplicación") { _, _ ->
                isExitDialogShowing = false
                exitAndKillBrowserImmediately()
            }
            .setNeutralButton("🏠 Ir al Inicio (Google)") { d, _ ->
                isExitDialogShowing = false
                d.dismiss()
                backPressAttemptsOnSamePage = 0
                loadUrlOrSearch(DEFAULT_HOME_URL)
            }
            .setNegativeButton("Cancelar") { d, _ ->
                isExitDialogShowing = false
                backPressAttemptsOnSamePage = 0
                d.dismiss()
                activateCursorMode(showPointerNow = true)
            }
            .setOnCancelListener {
                isExitDialogShowing = false
                backPressAttemptsOnSamePage = 0
            }
            .create()

        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
    }

    private fun exitAndKillBrowserImmediately() {
        Toast.makeText(this, "Cerrando sesión y limpiando caché...", Toast.LENGTH_SHORT).show()
        releaseHighPerfWifiLock()
        try {
            mainHandler.removeCallbacksAndMessages(null)
            geckoSession?.stop()
            geckoSession?.setActive(false)
            geckoSession?.close()
            geckoSession = null
            sGeckoRuntime?.shutdown()
            sGeckoRuntime = null
        } catch (_: Exception) {}

        try {
            deleteDirectoryContents(cacheDir)
            externalCacheDir?.let { deleteDirectoryContents(it) }
            val mozillaDir = File(filesDir, "mozilla")
            if (mozillaDir.exists()) {
                cleanGeckoProfileCaches(mozillaDir)
            }
        } catch (_: Exception) {}

        try {
            finishAndRemoveTask()
        } catch (_: Exception) {
            finish()
        }

        Process.killProcess(Process.myPid())
        exitProcess(0)
    }

    private fun toggleDesktopMode() {
        isDesktopMode = !isDesktopMode
        prefs.edit().putBoolean(KEY_DESKTOP_MODE, isDesktopMode).apply()
        btnDesktopMode.text = if (isDesktopMode) "🖥️" else "📱"

        val session = geckoSession
        if (session != null) {
            allowedUserTargetUrl = currentUrl
            session.stop()
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
            session.reload()
        }

        val modeLabel = if (isDesktopMode) {
            "🖥️ Modo PC activado"
        } else {
            "📱 Modo Móvil activado"
        }
        Toast.makeText(this, modeLabel, Toast.LENGTH_SHORT).show()
        activateCursorMode(showPointerNow = false)
    }

    private fun createTurboGeckoConfigFile(): File? {
        return try {
            val cfgFile = File(filesDir, "battv_gecko_config.yaml")
            val yamlContent = """
                prefs:
                  browser.cache.disk.enable: true
                  browser.cache.disk.capacity: 51200
                  browser.cache.disk.smart_size.enabled: false
                  browser.cache.offline.enable: false
                  browser.cache.memory.enable: true
                  browser.cache.memory.capacity: 24576
                  browser.sessionhistory.max_total_viewers: 0
                  media.suspend-bkgnd-video.enabled: false
                  media.cache_size: 32768
                  media.memory_cache_max_size: 16384
                  media.memory_caches_combined_limit_kb: 49152
                  media.cache_readahead_limit: 35
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
                  nglayout.initialpaint.delay: 0
                  layout.frame_rate: 60
                  layout.css.backdrop-filter.enabled: false
                  gfx.webrender.all: true
                  gfx.webrender.compositor: true
                  gfx.webrender.compositor.force-enabled: false
                  gfx.canvas.accelerated: true
                  layers.acceleration.force-enabled: true
                  network.buffer.cache.size: 65536
                  network.buffer.cache.count: 48
                  network.http.http3.enable: false
                  network.http.keep-alive.timeout: 300
                  network.http.max-connections: 96
                  network.http.max-persistent-connections-per-server: 10
                  network.http.pacing.requests.enabled: false
                  network.cookie.cookieBehavior: 4
                  network.cookie.sameSite.laxByDefault: false
                  network.cookie.sameSite.noneRequiresSecure: false
                  network.http.sendRefererHeader: 2
                  network.http.referer.XOriginPolicy: 0
                  network.http.referer.XOriginTrimmingPolicy: 0
                  network.dns.disablePrefetch: false
                  network.dns.disablePrefetchFromHTTPS: false
                  network.predictor.enabled: true
                  network.predictor.enable-prefetch: true
                  security.mixed_content.block_active_content: false
                  security.mixed_content.block_display_content: false
                  dom.security.https_only_mode: false
                  dom.disable_open_during_load: true
                  dom.webdriver.enabled: false
                  privacy.resistFingerprinting: false
            """.trimIndent()
            cfgFile.writeText(yamlContent)
            cfgFile
        } catch (e: Exception) {
            null
        }
    }

    private fun clearSafeAndroidCachesOnly(silent: Boolean) {
        Thread {
            var freedBytes = 0L
            try {
                freedBytes += deleteDirectoryContents(cacheDir)
                externalCacheDir?.let { freedBytes += deleteDirectoryContents(it) }
            } catch (_: Exception) {}

            if (!silent) {
                val freedMb = String.format("%.1f", freedBytes / (1024.0 * 1024.0))
                runOnUiThread {
                    Toast.makeText(
                        this@MainActivity,
                        "🧹 Caché temporal liberado ($freedMb MB). Pulsa ✖ si deseas cerrar la sesión por completo.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }.start()
    }

    private fun cleanGeckoProfileCaches(dir: File): Long {
        var bytes = 0L
        val children = dir.listFiles() ?: return 0L
        for (child in children) {
            if (child.isDirectory) {
                if (GECKO_DISPOSABLE_DIRS.contains(child.name)) {
                    bytes += getFolderSize(child)
                    child.deleteRecursively()
                } else {
                    bytes += cleanGeckoProfileCaches(child)
                }
            } else if (child.isFile && child.name == "prefs.js") {
                try {
                    val sanitized = child.readLines().filterNot { line ->
                        line.contains("dom.ipc.processCount") ||
                            line.contains("gfx.webrender.compositor.force-enabled") ||
                            line.contains("media.rdd-ffvpx.enabled") ||
                            line.contains("media.ffvpx.enabled") ||
                            line.contains("media.suspend-bkgnd-video") ||
                            line.contains("network.http.http3.enable") ||
                            line.contains("media.preload.default") ||
                            line.contains("media.cache_readahead_limit") ||
                            line.contains("media.memory_caches_combined_limit_kb")
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

    private fun isAdOrPopupUrl(url: String): Boolean {
        val lower = url.lowercase()
        for (domain in AD_BLOCK_DOMAINS) {
            if (lower.contains(domain)) return true
        }
        for (pattern in AD_URL_PATTERNS) {
            if (lower.contains(pattern)) return true
        }
        return false
    }

    private fun extractRegistrableDomain(url: String): String {
        return try {
            val rawHost = Uri.parse(url).host?.lowercase() ?: return ""
            val host = if (rawHost.startsWith("www")) rawHost.substringAfter(".", rawHost) else rawHost
            val parts = host.split(".")
            if (parts.size >= 2) {
                "${parts[parts.size - 2]}.${parts[parts.size - 1]}"
            } else {
                host
            }
        } catch (_: Exception) {
            ""
        }
    }

    private fun extractCoreBrand(url: String): String {
        return try {
            val rawHost = Uri.parse(url).host?.lowercase() ?: return ""
            val host = if (rawHost.startsWith("www")) rawHost.substringAfter(".", rawHost) else rawHost
            val parts = host.split(".")
            if (parts.size >= 2) parts[parts.size - 2] else host
        } catch (_: Exception) {
            ""
        }
    }

    private fun isSameOrMirrorSite(urlA: String, urlB: String): Boolean {
        val regA = extractRegistrableDomain(urlA)
        val regB = extractRegistrableDomain(urlB)
        if (regA.isBlank() || regB.isBlank()) return false
        if (regA == regB) return true

        val brandA = extractCoreBrand(urlA)
        val brandB = extractCoreBrand(urlB)
        if (brandA.length >= 5 && brandB.length >= 5) {
            if (brandA == brandB) return true
            if (brandA.length >= 6 && brandB.length >= 6 &&
                (brandA.startsWith(brandB) || brandB.startsWith(brandA))
            ) {
                return true
            }
        }
        return false
    }

    private fun isSearchOrStoreUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("google.") ||
            lower.contains("bing.com") ||
            lower.contains("duckduckgo.com") ||
            lower.contains("yahoo.com") ||
            lower.contains("mozilla.org") ||
            lower.contains("greasyfork.org")
    }

    private fun setupGeckoEngine(initialUrl: String) {
        if (sGeckoRuntime == null) {
            try {
                val mozillaDir = File(filesDir, "mozilla")
                if (mozillaDir.exists()) {
                    cleanGeckoProfileCaches(mozillaDir)
                }
            } catch (_: Exception) {}

            val builder = GeckoRuntimeSettings.Builder()
                .javaScriptEnabled(true)
                .consoleOutput(false)
                .aboutConfigEnabled(true)
                .doubleTapZoomingEnabled(false)

            val cfgFile = createTurboGeckoConfigFile()
            if (cfgFile != null) {
                try {
                    val m = builder.javaClass.methods.firstOrNull {
                        it.name == "configFilePath" && it.parameterTypes.size == 1
                    }
                    m?.invoke(builder, cfgFile.absolutePath)
                } catch (_: Exception) {}
            }

            sGeckoRuntime = GeckoRuntime.create(applicationContext, builder.build())
            setupExtensionController(sGeckoRuntime!!)
        }

        val sessionSettings = GeckoSessionSettings.Builder()
            .useTrackingProtection(false)
            .userAgentMode(
                if (isDesktopMode) GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                else GeckoSessionSettings.USER_AGENT_MODE_MOBILE
            )
            .viewportMode(
                if (isDesktopMode) GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                else GeckoSessionSettings.VIEWPORT_MODE_MOBILE
            )
            .build()

        val session = GeckoSession(sessionSettings)
        geckoSession = session

        attachAutoPermissionDelegate(session)

        installedExtensionsMap[BATTV_SCRIPTS_EXTENSION_ID]?.let { ext ->
            attachBattvBridgeToExtension(ext)
        }

        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
                canGoBackState = canGoBack
                btnBack.alpha = 1.0f
            }

            override fun onLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                val uri = request.uri

                if (uri.startsWith("moz-extension://", ignoreCase = true) || uri.startsWith("about:", ignoreCase = true)) {
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                }

                if (isAdOrPopupUrl(uri)) {
                    Log.i(TAG, "Anuncio/Popup bloqueado: $uri")
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                if (uri.endsWith(".user.js", ignoreCase = true) || uri.contains(".user.js?", ignoreCase = true)) {
                    installUserscriptFromUrl(uri)
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                if (uri.endsWith(".xpi", ignoreCase = true) ||
                    uri.contains("/firefox/downloads/file/", ignoreCase = true) ||
                    uri.contains("/firefox/downloads/latest/", ignoreCase = true)
                ) {
                    installExtensionFromUrl(uri)
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                if (request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) {
                    if (isSearchOrStoreUrl(currentUrl)) {
                        allowedUserTargetUrl = uri
                        session.stop()
                        session.loadUri(uri)
                    } else if (isSameOrMirrorSite(currentUrl, uri) && uri != currentUrl && !isAdOrPopupUrl(uri)) {
                        // Si un capítulo o episodio del mismo sitio (ej. Dramachino.com) usa target="_blank",
                        // lo abrimos limpiamente en la pestaña actual sin abrir popups externos:
                        allowedUserTargetUrl = uri
                        session.stop()
                        session.loadUri(uri)
                    } else {
                        Log.i(TAG, "Ventana emergente / Tab-Under bloqueado: $uri")
                    }
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                if (SystemClock.uptimeMillis() <= allowHistoryBackUntilUptimeMs) {
                    allowHistoryBackUntilUptimeMs = 0L
                    allowedUserTargetUrl = uri
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                }

                if (isSearchOrStoreUrl(currentUrl) || isSearchOrStoreUrl(uri)) {
                    allowedUserTargetUrl = uri
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                }

                if (isSameOrMirrorSite(currentUrl, uri) || isSameOrMirrorSite(allowedUserTargetUrl, uri)) {
                    return GeckoResult.fromValue(AllowOrDeny.ALLOW)
                }

                if (isRedirectLockEnabled) {
                    Log.i(TAG, "Redirección publicitaria bloqueada en $currentUrl -> $uri")
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }

                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }

            override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                return GeckoResult.fromValue(null)
            }

            override fun onSubframeLoadRequest(
                session: GeckoSession,
                request: GeckoSession.NavigationDelegate.LoadRequest
            ): GeckoResult<AllowOrDeny>? {
                val uri = request.uri
                if (isAdOrPopupUrl(uri)) {
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }
        }

        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                if (url != currentUrl) {
                    backPressAttemptsOnSamePage = 0
                    webScrollY = 0.0
                    hasActiveVodVideo = false
                    vodIsPlaying = false
                    vodDurationSec = 0.0
                    videoSeekOverlay.hideOverlayImmediate()
                    if (isEditingWebInput) {
                        closeNativeWebInputBridge(submit = false)
                    }
                }
                currentUrl = url
                if (allowedUserTargetUrl.isBlank()) {
                    allowedUserTargetUrl = url
                }
                if (!isEditingUrlText && !isEditingWebInput) {
                    suppressWebInputWatcher = true
                    urlEditText.setText(url)
                    suppressWebInputWatcher = false
                }
                loadingProgressBar.visibility = View.VISIBLE
                loadingProgressBar.progress = 25
            }

            override fun onProgressChange(session: GeckoSession, progress: Int) {
                loadingProgressBar.progress = progress
                if (progress >= 100) {
                    loadingProgressBar.visibility = View.GONE
                }
            }

            override fun onPageStop(session: GeckoSession, success: Boolean) {
                loadingProgressBar.visibility = View.GONE
                if (success && currentUrl.startsWith("http")) {
                    saveHistoryEntry(currentTitle, currentUrl)
                }
            }
        }

        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onTitleChange(session: GeckoSession, title: String?) {
                if (!title.isNullOrBlank()) {
                    currentTitle = title
                }
            }

            override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
                isFullScreenVideo = fullScreen
                topBarLayout.visibility = if (fullScreen) View.GONE else View.VISIBLE
                if (!fullScreen) {
                    mainHandler.removeCallbacks(hideVideoSeekRunnable)
                    isUserSeekingVod = false
                    consecutiveSeekPresses = 0
                    cumulativeSeekDeltaSec = 0.0
                    videoSeekOverlay.hideOverlayImmediate()
                }
                if (fullScreen && isEditingWebInput) {
                    closeNativeWebInputBridge(submit = false)
                }
                if (fullScreen) {
                    cursorOverlay.hideCursorUntilMoved()
                    Toast.makeText(
                        this@MainActivity,
                        "Pantalla completa • ◄ ► Adelantar/Retroceder (±00:05) • ▲ ▼ Ratón",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        attachNativeTvTextInputDelegate(session)
        session.open(sGeckoRuntime!!)
        geckoView.setSession(session)
        attachNativeTvTextInputDelegate(session)
        session.setActive(true)
        session.setFocused(true)
        try {
            session.setPriorityHint(GeckoSession.PRIORITY_HIGH)
        } catch (_: Exception) {}

        loadUrlOrSearch(initialUrl)
    }

    private fun attachNativeTvTextInputDelegate(session: GeckoSession) {
        session.textInput.setDelegate(object : GeckoSession.TextInputDelegate {
            override fun showSoftInput(session: GeckoSession) {
                runOnUiThread {
                    if (SystemClock.uptimeMillis() - lastUserWebClickTime < 4000L && !isEditingWebInput) {
                        requestActiveWebInputFromPage()
                    }
                }
            }

            override fun restartInput(session: GeckoSession, reason: Int) {
                // WEB_INPUT_FOCUS gestiona el cambio de campo con su valor exacto sin borrar lo escrito
            }

            override fun hideSoftInput(session: GeckoSession) {
                // Mantenemos abierto urlEditText mientras el usuario escribe con el teclado de TV
            }
        })
    }

    private fun attachBattvBridgeToExtension(ext: WebExtension) {
        val msgDelegate = object : WebExtension.MessageDelegate {
            override fun onConnect(port: WebExtension.Port) {
                activeWebPorts.add(port)
                port.setDelegate(object : WebExtension.PortDelegate {
                    override fun onPortMessage(message: Any, port: WebExtension.Port) {
                        val json = message as? JSONObject ?: return
                        val msgType = json.optString("type")
                        if (msgType == "WEB_INPUT_FOCUS") {
                            val inpId = json.optString("inputId", "")
                            val initVal = json.optString("value", "")
                            val hint = json.optString("placeholder", "")
                            val isMulti = json.optBoolean("isMultiField", false)
                            runOnUiThread {
                                if (isEditingWebInput || SystemClock.uptimeMillis() - lastUserWebClickTime < 4000L) {
                                    openNativeWebInputBridge(inpId, initVal, hint, isMulti)
                                }
                            }
                        } else if (msgType == "VIDEO_STATE") {
                            val hasVod = json.optBoolean("hasVod", false)
                            val playing = json.optBoolean("playing", false)
                            val curTime = json.optDouble("currentTime", 0.0)
                            val dur = json.optDouble("duration", 0.0)
                            runOnUiThread {
                                val startedPlayingNow = !vodIsPlaying && playing
                                hasActiveVodVideo = hasVod && dur >= 40.0
                                vodIsPlaying = playing
                                if (dur > 0.0) {
                                    vodDurationSec = dur
                                }
                                if (!isUserSeekingVod && curTime >= 0.0) {
                                    vodCurrentTimeSec = curTime
                                    if (isFullScreenVideo && videoSeekOverlay.isOverlayVisible()) {
                                        videoSeekOverlay.updateProgressOnly(vodCurrentTimeSec, vodDurationSec, vodIsPlaying)
                                    }
                                }
                                if (isFullScreenVideo && startedPlayingNow && hasActiveVodVideo) {
                                    mainHandler.removeCallbacks(hideCursorRunnable)
                                    mainHandler.postDelayed(hideCursorRunnable, 900L)
                                }
                            }
                        } else if (msgType == "PAGE_SCROLL_STATE") {
                            val st = json.optDouble("scrollTop", 0.0)
                            runOnUiThread {
                                webScrollY = max(0.0, st)
                            }
                        } else if (msgType == "HARD_LOAD_NEW_EPISODE") {
                            val nextEpisodeUrl = json.optString("url", "")
                            if (nextEpisodeUrl.startsWith("http")) {
                                runOnUiThread {
                                    hasActiveVodVideo = false
                                    vodIsPlaying = false
                                    vodDurationSec = 0.0
                                    webScrollY = 0.0
                                    videoSeekOverlay.hideOverlayImmediate()
                                    allowedUserTargetUrl = nextEpisodeUrl
                                    currentUrl = nextEpisodeUrl
                                    if (!isEditingUrlText && !isEditingWebInput) {
                                        suppressWebInputWatcher = true
                                        urlEditText.setText(nextEpisodeUrl)
                                        suppressWebInputWatcher = false
                                    }
                                    geckoSession?.stop()
                                    geckoSession?.loadUri(nextEpisodeUrl)
                                }
                            }
                        }
                    }

                    override fun onDisconnect(port: WebExtension.Port) {
                        activeWebPorts.remove(port)
                    }
                })
            }
        }

        try {
            ext.setMessageDelegate(msgDelegate, BATTV_NATIVE_BRIDGE_APP)
        } catch (_: Exception) {}

        try {
            geckoSession?.webExtensionController?.setMessageDelegate(
                ext,
                msgDelegate,
                BATTV_NATIVE_BRIDGE_APP
            )
        } catch (_: Exception) {}
    }

    private fun attachAutoPermissionDelegate(session: GeckoSession) {
        try {
            val permInterface = GeckoSession.PermissionDelegate::class.java
            val proxy = Proxy.newProxyInstance(
                permInterface.classLoader,
                arrayOf(permInterface)
            ) { _, method, _ ->
                if (method.name == "onContentPermissionRequest") {
                    GeckoResult.fromValue(1)
                } else {
                    null
                }
            } as GeckoSession.PermissionDelegate
            session.permissionDelegate = proxy
        } catch (e: Exception) {
            Log.w(TAG, "Aviso en PermissionDelegate", e)
        }
    }

    private fun setupExtensionController(runtime: GeckoRuntime) {
        val extController: WebExtensionController = runtime.webExtensionController

        try {
            val promptInterface = Class.forName("org.mozilla.geckoview.WebExtensionController\$PromptDelegate")
            val proxy = Proxy.newProxyInstance(
                promptInterface.classLoader,
                arrayOf(promptInterface)
            ) { _, method, _ ->
                when (method.name) {
                    "onInstallPrompt" -> GeckoResult.fromValue(AllowOrDeny.ALLOW)
                    "onInstallPromptRequest" -> {
                        try {
                            val respClass = Class.forName("org.mozilla.geckoview.WebExtension\$PermissionPromptResponse")
                            val ctor = respClass.constructors.firstOrNull { it.parameterTypes.size == 3 }
                            val resp = ctor?.newInstance(true, true, true)
                            GeckoResult.fromValue(resp)
                        } catch (_: Exception) {
                            null
                        }
                    }
                    else -> null
                }
            }
            val setter = extController.javaClass.methods.firstOrNull { it.name == "setPromptDelegate" }
            setter?.invoke(extController, proxy)
        } catch (e: Exception) {
            Log.w(TAG, "Aviso al configurar PromptDelegate", e)
        }

        extController.ensureBuiltIn(UBLOCK_ASSET_URI, UBLOCK_EXTENSION_ID).accept(
            { ext ->
                if (ext != null) {
                    installedExtensionsMap[ext.id] = ext
                    registerExtensionTabHandler(ext)
                }
            },
            { err -> Log.e(TAG, "Error en uBlock Origin", err) }
        )

        extController.ensureBuiltIn(VIOLENTMONKEY_ASSET_URI, VIOLENTMONKEY_EXTENSION_ID).accept(
            { ext ->
                if (ext != null) {
                    installedExtensionsMap[ext.id] = ext
                    registerExtensionTabHandler(ext)
                }
            },
            { err -> Log.e(TAG, "Error en Violentmonkey", err) }
        )

        extController.ensureBuiltIn(BATTV_SCRIPTS_ASSET_URI, BATTV_SCRIPTS_EXTENSION_ID).accept(
            { ext ->
                if (ext != null) {
                    installedExtensionsMap[ext.id] = ext
                    attachBattvBridgeToExtension(ext)
                }
            },
            { err -> Log.e(TAG, "Error en BatTV Scripts", err) }
        )

        refreshInstalledExtensions()
    }

    private fun registerExtensionTabHandler(ext: WebExtension) {
        try {
            val tabInterface = WebExtension.TabDelegate::class.java
            val proxy = Proxy.newProxyInstance(
                tabInterface.classLoader,
                arrayOf(tabInterface)
            ) { _, method, args ->
                if (method.name == "onNewTab" && args != null && args.size >= 2) {
                    val details = args[1]
                    val uriField = details?.javaClass?.fields?.firstOrNull { it.name == "uri" }
                    val targetUri = uriField?.get(details) as? String
                    if (!targetUri.isNullOrBlank()) {
                        runOnUiThread {
                            loadUrlOrSearch(targetUri)
                        }
                    }
                    GeckoResult.fromValue<GeckoSession?>(null)
                } else {
                    null
                }
            } as WebExtension.TabDelegate
            ext.tabDelegate = proxy
        } catch (e: Exception) {
            Log.w(TAG, "Aviso en TabDelegate", e)
        }
    }

    private fun ensureDefaultPreinstalledScripts() {
        if (!prefs.contains(KEY_USERSCRIPTS)) {
            val arr = JSONArray().apply {
                put(JSONObject().put("name", "Bypass All Shortlinks").put("enabled", true).put("builtin", true))
                put(JSONObject().put("name", "Pop-up & Tab-Under Blocker (HDFull / Doramasflix)").put("enabled", true).put("builtin", true))
                put(JSONObject().put("name", "Cinema 60FPS & 1080p60 Live Stream Stabilizer").put("enabled", true).put("builtin", true))
            }
            prefs.edit().putString(KEY_USERSCRIPTS, arr.toString()).apply()
        }
    }

    private fun installUserscriptFromUrl(scriptUrl: String) {
        Toast.makeText(this, "Descargando e instalando script en Bat TV...", Toast.LENGTH_SHORT).show()
        Thread {
            var scriptName = scriptUrl.substringAfterLast("/").removeSuffix(".user.js").replace("-", " ")
            try {
                val conn = URL(scriptUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val nameLine = text.lineSequence().firstOrNull { it.contains("@name") }
                if (nameLine != null) {
                    val parsedName = nameLine.substringAfter("@name").trim()
                    if (parsedName.isNotBlank()) {
                        scriptName = parsedName
                    }
                }
            } catch (_: Exception) {}

            runOnUiThread {
                val raw = prefs.getString(KEY_USERSCRIPTS, "[]") ?: "[]"
                val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
                var exists = false
                for (i in 0 until arr.length()) {
                    if (arr.optJSONObject(i)?.optString("name").equals(scriptName, ignoreCase = true)) {
                        exists = true
                        break
                    }
                }
                if (!exists) {
                    arr.put(JSONObject().put("name", scriptName).put("enabled", true).put("url", scriptUrl))
                    prefs.edit().putString(KEY_USERSCRIPTS, arr.toString()).apply()
                }

                AlertDialog.Builder(this)
                    .setTitle("✅ Script Instalado en Bat TV")
                    .setMessage("El script '$scriptName' se ha instalado y activado correctamente en el motor de scripts de Bat TV.")
                    .setPositiveButton("Aceptar") { d, _ -> d.dismiss() }
                    .show()
            }
        }.start()
    }

    @Suppress("UNCHECKED_CAST")
    private fun refreshInstalledExtensions(onComplete: (() -> Unit)? = null) {
        val controller = sGeckoRuntime?.webExtensionController ?: run {
            onComplete?.invoke()
            return
        }
        try {
            val listMethod = controller.javaClass.methods.firstOrNull { it.name == "list" && it.parameterTypes.isEmpty() }
            val result = listMethod?.invoke(controller) as? GeckoResult<List<WebExtension>>
            if (result != null) {
                result.accept(
                    { list ->
                        if (list != null) {
                            for (ext in list) {
                                installedExtensionsMap[ext.id] = ext
                                registerExtensionTabHandler(ext)
                            }
                        }
                        onComplete?.invoke()
                    },
                    { _ -> onComplete?.invoke() }
                )
            } else {
                onComplete?.invoke()
            }
        } catch (_: Exception) {
            onComplete?.invoke()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun installExtensionFromUrl(xpiUrl: String) {
        val controller = sGeckoRuntime?.webExtensionController ?: return
        Toast.makeText(this, "Instalando extensión de Mozilla...", Toast.LENGTH_LONG).show()

        try {
            val installMethod1 = controller.javaClass.methods.firstOrNull {
                it.name == "install" && it.parameterTypes.size == 1 && it.parameterTypes[0] == String::class.java
            }
            val installMethod2 = controller.javaClass.methods.firstOrNull {
                it.name == "install" && it.parameterTypes.size == 2 && it.parameterTypes[0] == String::class.java
            }
            val result = when {
                installMethod1 != null -> installMethod1.invoke(controller, xpiUrl) as? GeckoResult<WebExtension>
                installMethod2 != null -> installMethod2.invoke(controller, xpiUrl, "manager") as? GeckoResult<WebExtension>
                else -> null
            }

            result?.accept(
                { ext ->
                    if (ext != null) {
                        installedExtensionsMap[ext.id] = ext
                        registerExtensionTabHandler(ext)
                        val extName = getExtensionMetaString(ext, "name") ?: ext.id
                        Toast.makeText(
                            this,
                            "✅ ¡Extensión instalada en Bat TV: $extName!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                { err ->
                    Toast.makeText(
                        this,
                        "Error al instalar extensión: ${err?.message ?: ""}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Error al iniciar instalación: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun getExtensionMetaString(ext: WebExtension?, fieldName: String): String? {
        if (ext == null) return null
        return try {
            val metaField = ext.javaClass.fields.firstOrNull { it.name == "metaData" }
            val metaObj = metaField?.get(ext) ?: return null
            val targetField = metaObj.javaClass.fields.firstOrNull { it.name == fieldName }
            targetField?.get(metaObj) as? String
        } catch (_: Exception) {
            null
        }
    }

    private fun showExtensionsDialog() {
        refreshInstalledExtensions {
            runOnUiThread {
                val rawScripts = prefs.getString(KEY_USERSCRIPTS, "[]") ?: "[]"
                val scriptsArr = try { JSONArray(rawScripts) } catch (_: Exception) { JSONArray() }

                val items = mutableListOf<String>()
                val actions = mutableListOf<() -> Unit>()

                items.add("🔍 Abrir Teclado Buscador para la Página Actual")
                actions.add {
                    openNativeWebInputBridge()
                }

                items.add("✖ Cerrar Bat TV por Completo y Borrar Caché")
                actions.add {
                    exitAndKillBrowserImmediately()
                }

                val lockStatus = if (isRedirectLockEnabled) {
                    "🔒 Escudo Anti-Cambio de Página y Popups: ACTIVO"
                } else {
                    "🔓 Escudo Anti-Cambio de Página: PAUSADO (Toca para activar)"
                }
                items.add(lockStatus)
                actions.add {
                    isRedirectLockEnabled = !isRedirectLockEnabled
                    prefs.edit().putBoolean(KEY_REDIRECT_LOCK, isRedirectLockEnabled).apply()
                    Toast.makeText(
                        this,
                        if (isRedirectLockEnabled) "🔒 Escudo Anti-Cambio de Página Activado" else "🔓 Escudo Anti-Cambio de Página Pausado",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                val modeStatus = if (isDesktopMode) "🖥️ Modo PC Activo (Toca para cambiar a Móvil 📱)" else "📱 Modo Móvil Activo (Toca para cambiar a PC 🖥️)"
                items.add(modeStatus)
                actions.add {
                    toggleDesktopMode()
                }

                items.add("🏮 Abrir DramaChino (https://dramachino.com/)")
                actions.add {
                    loadUrlOrSearch(DRAMACHINO_URL)
                }

                items.add("🎬 Abrir Doramasflix (https://doramasflix.co/)")
                actions.add {
                    loadUrlOrSearch(DORAMASFLIX_URL)
                }

                items.add("🎌 Abrir JKAnime (https://jkanime.net/)")
                actions.add {
                    loadUrlOrSearch(JKANIME_URL)
                }

                items.add("🍿 Abrir HDFull (https://hdfull.one/)")
                actions.add {
                    loadUrlOrSearch(HDFULL_URL)
                }

                items.add("🛡️ uBlock Origin + Escudo Anti-Ads Nativo (ACTIVO)")
                actions.add {
                    openExtensionDashboard(UBLOCK_EXTENSION_ID, "dashboard.html")
                }

                items.add("🐵 Ver / Gestionar Scripts Activos (${scriptsArr.length()} instalados)")
                actions.add {
                    showInstalledScriptsDialog()
                }

                items.add("📜 Buscar más Scripts en GreasyFork (Instalación en 1 clic)")
                actions.add {
                    loadUrlOrSearch(GREASYFORK_URL)
                }

                items.add("⚙️ Abrir Panel Avanzado de Violentmonkey")
                actions.add {
                    openExtensionDashboard(VIOLENTMONKEY_EXTENSION_ID, "options/index.html")
                }

                items.add("🦊 Buscar más Extensiones en Mozilla Add-ons")
                actions.add {
                    loadUrlOrSearch(MOZILLA_ADDONS_URL)
                }

                AlertDialog.Builder(this)
                    .setTitle("🌸 Bat TV 19.7 Cinema 60FPS • Doramas, Anime, Películas & Directos")
                    .setItems(items.toTypedArray()) { _, which ->
                        actions[which].invoke()
                    }
                    .setNegativeButton("Cerrar") { dialog, _ ->
                        dialog.dismiss()
                    }
                    .show()
            }
        }
    }

    private fun showInstalledScriptsDialog() {
        val raw = prefs.getString(KEY_USERSCRIPTS, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }

        val items = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name", "Script")
            val enabled = obj.optBoolean("enabled", true)
            val isBuiltin = obj.optBoolean("builtin", false)
            val status = if (enabled) "✅ ACTIVO" else "⏸️ PAUSADO"
            val tag = if (isBuiltin) "[Preinstalado]" else "[GreasyFork]"
            items.add("$status • $name $tag")
            actions.add {
                obj.put("enabled", !enabled)
                prefs.edit().putString(KEY_USERSCRIPTS, arr.toString()).apply()
                Toast.makeText(
                    this,
                    if (!enabled) "Script activado: $name" else "Script pausado: $name",
                    Toast.LENGTH_SHORT
                ).show()
                showInstalledScriptsDialog()
            }
        }

        items.add("➕ Buscar e instalar nuevos scripts en GreasyFork")
        actions.add {
            loadUrlOrSearch(GREASYFORK_URL)
        }

        AlertDialog.Builder(this)
            .setTitle("🐵 Scripts de Violentmonkey / Bat TV (Toca para activar/pausar)")
            .setItems(items.toTypedArray()) { _, which ->
                actions[which].invoke()
            }
            .setNegativeButton("Volver") { d, _ -> d.dismiss() }
            .show()
    }

    private fun openExtensionDashboard(extensionId: String, fallbackPath: String) {
        val ext = installedExtensionsMap[extensionId]
        val optionsUrl = getExtensionMetaString(ext, "optionsPageUrl")
        val baseUrl = getExtensionMetaString(ext, "baseUrl")

        val targetUrl = when {
            !optionsUrl.isNullOrBlank() -> optionsUrl
            !baseUrl.isNullOrBlank() -> baseUrl.trimEnd('/') + "/" + fallbackPath.trimStart('/')
            else -> null
        }

        if (!targetUrl.isNullOrBlank()) {
            loadUrlOrSearch(targetUrl)
        } else {
            Toast.makeText(this, "uBlock Origin y el Escudo Anti-Ads están activos bloqueando publicidad", Toast.LENGTH_LONG).show()
        }
    }

    private fun ensureDefaultBookmarks() {
        if (!prefs.contains(KEY_BOOKMARKS)) {
            val defaults = JSONArray().apply {
                put(JSONObject().put("title", "🏮 DramaChino (Doramas Chinos y Asiáticos HD)").put("url", DRAMACHINO_URL))
                put(JSONObject().put("title", "🎬 Doramasflix (Doramas Asiáticos HD)").put("url", DORAMASFLIX_URL))
                put(JSONObject().put("title", "🎌 JKAnime (Anime HD)").put("url", JKANIME_URL))
                put(JSONObject().put("title", "🍿 HDFull (Películas y Series)").put("url", HDFULL_URL))
                put(JSONObject().put("title", "Inicio (Google)").put("url", "https://www.google.com"))
                put(JSONObject().put("title", "YouTube").put("url", "https://www.youtube.com"))
                put(JSONObject().put("title", "Twitch (Directos)").put("url", "https://www.twitch.tv"))
                put(JSONObject().put("title", "Kick (Directos)").put("url", "https://kick.com"))
                put(JSONObject().put("title", "GreasyFork (Scripts)").put("url", GREASYFORK_URL))
                put(JSONObject().put("title", "Mozilla Add-ons").put("url", MOZILLA_ADDONS_URL))
            }
            prefs.edit().putString(KEY_BOOKMARKS, defaults.toString()).apply()
        }
    }

    private fun showBookmarksDialog() {
        val raw = prefs.getString(KEY_BOOKMARKS, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }

        val items = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        items.add("➕ Guardar página actual en Favoritos (${currentUrl.take(30)})")
        actions.add {
            val newObj = JSONObject().put("title", currentTitle.ifBlank { currentUrl }).put("url", currentUrl)
            arr.put(newObj)
            prefs.edit().putString(KEY_BOOKMARKS, arr.toString()).apply()
            Toast.makeText(this, "Guardado en Favoritos", Toast.LENGTH_SHORT).show()
        }

        if (arr.length() > 0) {
            items.add("🗑️ Eliminar un marcador de Favoritos...")
            actions.add {
                showDeleteBookmarkDialog(arr)
            }
        }

        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val t = obj.optString("title", "Sitio")
            val u = obj.optString("url", DEFAULT_HOME_URL)
            items.add("⭐ $t  —  $u")
            actions.add {
                loadUrlOrSearch(u)
            }
        }

        AlertDialog.Builder(this)
            .setTitle("⭐ Favoritos de Bat TV")
            .setItems(items.toTypedArray()) { _, which ->
                actions[which].invoke()
            }
            .setNegativeButton("Cerrar") { d, _ -> d.dismiss() }
            .show()
    }

    private fun showDeleteBookmarkDialog(arr: JSONArray) {
        val delItems = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            delItems.add("❌ Eliminar: ${obj.optString("title")} (${obj.optString("url")})")
        }
        AlertDialog.Builder(this)
            .setTitle("🗑️ Selecciona el Favorito que deseas eliminar")
            .setItems(delItems.toTypedArray()) { _, which ->
                val newArr = JSONArray()
                for (i in 0 until arr.length()) {
                    if (i != which) newArr.put(arr.getJSONObject(i))
                }
                prefs.edit().putString(KEY_BOOKMARKS, newArr.toString()).apply()
                Toast.makeText(this, "Favorito eliminado", Toast.LENGTH_SHORT).show()
                showBookmarksDialog()
            }
            .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
            .show()
    }

    private fun saveHistoryEntry(title: String, url: String) {
        val raw = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val oldArr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        val newArr = JSONArray()

        newArr.put(JSONObject().put("title", title.ifBlank { url }).put("url", url))
        for (i in 0 until min(oldArr.length(), 35)) {
            val item = oldArr.optJSONObject(i) ?: continue
            if (item.optString("url") != url) {
                newArr.put(item)
            }
        }
        prefs.edit().putString(KEY_HISTORY, newArr.toString()).apply()
    }

    private fun showHistoryDialog() {
        val raw = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }

        val items = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        if (arr.length() == 0) {
            items.add("Aún no hay páginas en el historial")
            actions.add {}
        } else {
            items.add("🗑️ Borrar TODO el historial")
            actions.add {
                prefs.edit().remove(KEY_HISTORY).apply()
                Toast.makeText(this, "Todo el historial ha sido borrado", Toast.LENGTH_SHORT).show()
            }

            items.add("❌ Eliminar una página específica del historial...")
            actions.add {
                showDeleteHistoryItemDialog(arr)
            }

            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val u = obj.optString("url", DEFAULT_HOME_URL)
                items.add("🕒 $u")
                actions.add {
                    loadUrlOrSearch(u)
                }
            }
        }

        AlertDialog.Builder(this)
            .setTitle("🕒 Historial de Navegación")
            .setItems(items.toTypedArray()) { _, which ->
                actions[which].invoke()
            }
            .setNegativeButton("Cerrar") { d, _ -> d.dismiss() }
            .show()
    }

    private fun showDeleteHistoryItemDialog(arr: JSONArray) {
        val delItems = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            delItems.add("❌ Borrar: ${obj.optString("url")}")
        }
        AlertDialog.Builder(this)
            .setTitle("🗑️ Selecciona la entrada a borrar")
            .setItems(delItems.toTypedArray()) { _, which ->
                val newArr = JSONArray()
                for (i in 0 until arr.length()) {
                    if (i != which) newArr.put(arr.getJSONObject(i))
                }
                prefs.edit().putString(KEY_HISTORY, newArr.toString()).apply()
                Toast.makeText(this, "Entrada eliminada del historial", Toast.LENGTH_SHORT).show()
                showHistoryDialog()
            }
            .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
            .show()
    }

    private fun submitUrlFromInput() {
        val rawInput = urlEditText.text.toString().trim()
        if (isEditingWebInput) {
            closeNativeWebInputBridge(submit = true)
            return
        }
        isEditingUrlText = false
        btnGo.text = "Ir"
        hideSoftKeyboardFromView(urlEditText)
        urlEditText.clearFocus()
        urlEditText.isCursorVisible = false
        urlEditText.isFocusable = false
        urlEditText.isFocusableInTouchMode = false
        if (rawInput.isNotEmpty()) {
            loadUrlOrSearch(rawInput)
        }
        activateCursorMode(showPointerNow = false)
    }

    private fun loadUrlOrSearch(input: String) {
        val trimmed = input.trim()
        val finalUri = when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("about:", ignoreCase = true) ||
                trimmed.startsWith("moz-extension://", ignoreCase = true) -> trimmed
            !trimmed.contains(" ") && trimmed.contains(".") -> "https://$trimmed"
            else -> "https://www.google.com/search?q=${Uri.encode(trimmed)}&hl=es"
        }
        backPressAttemptsOnSamePage = 0
        allowedUserTargetUrl = finalUri
        currentUrl = finalUri
        suppressWebInputWatcher = true
        urlEditText.setText(finalUri)
        suppressWebInputWatcher = false
        geckoSession?.stop()
        geckoSession?.loadUri(finalUri)
        activateCursorMode(showPointerNow = false)
    }

    private fun scheduleCursorAutoHide() {
        mainHandler.removeCallbacks(hideCursorRunnable)
        mainHandler.postDelayed(hideCursorRunnable, CURSOR_AUTO_HIDE_MS)
    }

    private fun activateCursorMode(showPointerNow: Boolean = true) {
        mainHandler.removeCallbacks(activateNativeWebInputRunnable)
        isCursorModeActive = true
        isEditingUrlText = false
        isEditingWebInput = false
        btnPrevField.visibility = View.GONE
        btnNextField.visibility = View.GONE
        btnGo.text = "Ir"
        urlEditText.hint = "Escribir web o buscar doramas, anime, películas (Pulsa OK para escribir)..."
        hideSoftKeyboardFromView(urlEditText)
        urlEditText.clearFocus()
        urlEditText.isCursorVisible = false
        urlEditText.isFocusable = false
        urlEditText.isFocusableInTouchMode = false
        updateToolbarSelectionVisuals()
        if (showPointerNow) {
            cursorOverlay.showCursorAndScheduleFade()
            scheduleCursorAutoHide()
        } else {
            cursorOverlay.hideCursorUntilMoved()
        }
        geckoView.isFocusable = true
        geckoView.isFocusableInTouchMode = true
        geckoView.requestFocus()
        geckoSession?.setActive(true)
        geckoSession?.setFocused(true)
    }

    private fun activateToolbarMode() {
        if (isFullScreenVideo) {
            topBarLayout.visibility = View.VISIBLE
        }
        if (isEditingWebInput) {
            closeNativeWebInputBridge(submit = false)
        }
        isCursorModeActive = false
        isEditingUrlText = false
        isEditingWebInput = false
        toolbarIndex = URL_BAR_INDEX
        urlEditText.clearFocus()
        urlEditText.isCursorVisible = false
        urlEditText.isFocusable = false
        urlEditText.isFocusableInTouchMode = false
        cursorOverlay.hideCursorUntilMoved()
        updateToolbarSelectionVisuals()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action

        if (keyCode == KeyEvent.KEYCODE_MENU && action == KeyEvent.ACTION_DOWN) {
            if (isFullScreenVideo) {
                geckoSession?.exitFullScreen()
                isFullScreenVideo = false
                topBarLayout.visibility = View.VISIBLE
            }
            showExitBrowserConfirmationDialog()
            return true
        }

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (action == KeyEvent.ACTION_DOWN && event.repeatCount > 0) {
                showExitBrowserConfirmationDialog()
                return true
            }
            if (action == KeyEvent.ACTION_UP) {
                if (isEditingWebInput) {
                    closeNativeWebInputBridge(submit = false)
                    return true
                } else if (isFullScreenVideo) {
                    geckoSession?.exitFullScreen()
                    isFullScreenVideo = false
                    topBarLayout.visibility = View.VISIBLE
                    return true
                } else if (!isCursorModeActive) {
                    if (isEditingUrlText) {
                        stopEditingUrlTextAndStayOnBar()
                    } else {
                        activateCursorMode(showPointerNow = true)
                    }
                    return true
                } else {
                    handleSmartBackNavigation()
                    return true
                }
            }
            return true
        }

        if (!isCursorModeActive) {
            if (isEditingUrlText) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && action == KeyEvent.ACTION_DOWN) {
                    if (isEditingWebInput) {
                        // Al pulsar Abajo salimos del campo conservando el texto escrito SIN enviar el formulario
                        isEditingUrlText = false
                        hideSoftKeyboardFromView(urlEditText)
                        urlEditText.clearFocus()
                        urlEditText.isCursorVisible = false
                        urlEditText.isFocusable = false
                        urlEditText.isFocusableInTouchMode = false
                        updateToolbarSelectionVisuals()
                    } else {
                        stopEditingUrlTextAndStayOnBar()
                    }
                    return true
                }
                return super.dispatchKeyEvent(event)
            }

            // Permitir moverse por los botones de la barra (incluyendo ◄Ant, Sig►, 🔍) saltando botones ocultos
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (action == KeyEvent.ACTION_DOWN) {
                        var nextIdx = toolbarIndex - 1
                        while (nextIdx >= 0 && toolbarItems[nextIdx].visibility != View.VISIBLE) {
                            nextIdx--
                        }
                        if (nextIdx >= 0) {
                            toolbarIndex = nextIdx
                            updateToolbarSelectionVisuals()
                        }
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (action == KeyEvent.ACTION_DOWN) {
                        var nextIdx = toolbarIndex + 1
                        while (nextIdx < toolbarItems.size && toolbarItems[nextIdx].visibility != View.VISIBLE) {
                            nextIdx++
                        }
                        if (nextIdx < toolbarItems.size) {
                            toolbarIndex = nextIdx
                            updateToolbarSelectionVisuals()
                        }
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (action == KeyEvent.ACTION_DOWN) {
                        if (isEditingWebInput) {
                            closeNativeWebInputBridge(submit = false)
                        } else {
                            activateCursorMode(showPointerNow = true)
                        }
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_UP -> {
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    if (action == KeyEvent.ACTION_UP) {
                        val selectedView = toolbarItems.getOrNull(toolbarIndex)
                        if (selectedView === urlEditText) {
                            startEditingUrlText(forWebInput = isEditingWebInput)
                        } else {
                            selectedView?.performClick()
                        }
                    }
                    return true
                }
            }
            return super.dispatchKeyEvent(event)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val shouldControlSeekBar = isFullScreenVideo &&
                    hasActiveVodVideo &&
                    vodDurationSec > 0.0

                if (shouldControlSeekBar) {
                    if (action == KeyEvent.ACTION_DOWN) {
                        handleDpadVideoSeek(keyCode, event.repeatCount)
                    }
                    return true
                }

                if (action == KeyEvent.ACTION_DOWN) {
                    handleDpadCursorMovement(keyCode, event.repeatCount)
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (action == KeyEvent.ACTION_DOWN) {
                    if (videoSeekOverlay.isOverlayVisible()) {
                        mainHandler.removeCallbacks(hideVideoSeekRunnable)
                        videoSeekOverlay.hideOverlayImmediate()
                    }
                    handleDpadCursorMovement(keyCode, event.repeatCount)
                }
                return true
            }

            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (isFullScreenVideo && videoSeekOverlay.isOverlayVisible() && hasActiveVodVideo) {
                    if (action == KeyEvent.ACTION_UP) {
                        vodIsPlaying = !vodIsPlaying
                        val badge = if (vodIsPlaying) "▶ Play" else "⏸ Pausa"
                        videoSeekOverlay.showSeekState(vodCurrentTimeSec, vodDurationSec, badge, vodIsPlaying)
                        sendVideoCommandToWeb("TOGGLE_PLAY")
                        mainHandler.removeCallbacks(hideVideoSeekRunnable)
                        mainHandler.postDelayed(hideVideoSeekRunnable, 2800L)
                    }
                    return true
                }

                if (action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                    cursorOverlay.setClickingState(true)
                    scheduleCursorAutoHide()
                } else if (action == KeyEvent.ACTION_UP) {
                    cursorOverlay.setClickingState(false)
                    simulateTouchClickAtCursor()
                }
                return true
            }
        }

        return super.dispatchKeyEvent(event)
    }

    private fun computeProgressiveVerticalScrollStepPx(dir: Int, repeatCount: Int): Double {
        val now = SystemClock.uptimeMillis()
        val isContinuingSameDirection = (dir == verticalScrollDirection) &&
            (repeatCount > 0 || (now - lastVerticalScrollEventUptimeMs) <= 380L)

        if (!isContinuingSameDirection) {
            verticalScrollDirection = dir
            verticalScrollStartUptimeMs = now
        }
        lastVerticalScrollEventUptimeMs = now

        val heldMs = (now - verticalScrollStartUptimeMs).coerceAtLeast(0L)
        return when {
            heldMs <= 1000L -> {
                val t = heldMs.toDouble() / 1000.0
                8.0 + (4.0 * t)
            }
            heldMs <= 3500L -> {
                val t = (heldMs - 1000L).toDouble() / 2500.0
                12.0 + (12.0 * t)
            }
            heldMs <= 6000L -> {
                val t = (heldMs - 3500L).toDouble() / 2500.0
                24.0 + (21.0 * t * t)
            }
            else -> {
                val fastT = ((heldMs - 6000L).toDouble() / 2000.0).coerceIn(0.0, 1.0)
                45.0 + (47.0 * fastT)
            }
        }
    }

    private fun handleDpadCursorMovement(keyCode: Int, repeatCount: Int) {
        val width = webContainer.width.toFloat().coerceAtLeast(1f)
        val height = webContainer.height.toFloat().coerceAtLeast(1f)

        val accelRepeats = max(0, repeatCount - 2)
        val step = min(
            MAX_CURSOR_STEP_PX,
            BASE_CURSOR_STEP_PX + (accelRepeats * CURSOR_ACCEL)
        )

        var newX = cursorOverlay.cursorX
        var newY = cursorOverlay.cursorY

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                verticalScrollDirection = 0
                newX = max(6f, newX - step)
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                verticalScrollDirection = 0
                newX = min(width - 6f, newX + step)
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                if (!isFullScreenVideo) {
                    if (newY <= EDGE_SCROLL_MARGIN_PX && webScrollY > 4.0) {
                        val scrollStep = computeProgressiveVerticalScrollStepPx(-1, repeatCount)
                        newY = max(20f, newY - step)
                        webScrollY = max(0.0, webScrollY - scrollStep)
                        performFastVerticalScroll(-scrollStep, newX / width, newY / height)
                    } else if (newY <= 8f && webScrollY <= 4.0 && repeatCount == 0) {
                        verticalScrollDirection = 0
                        activateToolbarMode()
                        return
                    } else {
                        newY = max(4f, newY - step)
                        if (newY <= EDGE_SCROLL_MARGIN_PX) {
                            val scrollStep = computeProgressiveVerticalScrollStepPx(-1, repeatCount)
                            performFastVerticalScroll(-scrollStep, newX / width, newY / height)
                        } else {
                            verticalScrollDirection = 0
                        }
                    }
                } else {
                    newY = max(0f, newY - step)
                }
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                val maxBottomY = if (isFullScreenVideo) height - 1f else height - 18f
                newY = min(maxBottomY, newY + step)
                if (!isFullScreenVideo && newY >= (height - EDGE_SCROLL_MARGIN_PX)) {
                    val scrollStep = computeProgressiveVerticalScrollStepPx(1, repeatCount)
                    webScrollY += scrollStep
                    performFastVerticalScroll(scrollStep, newX / width, newY / height)
                } else {
                    verticalScrollDirection = 0
                }
            }
        }

        val isInCorner = isFullScreenVideo &&
            (newX <= CORNER_HIDE_MARGIN_PX || newX >= width - CORNER_HIDE_MARGIN_PX) &&
            (newY <= CORNER_HIDE_MARGIN_PX || newY >= height - CORNER_HIDE_MARGIN_PX)

        if (isInCorner) {
            cursorOverlay.updatePositionAndHide(newX, newY)
            mainHandler.removeCallbacks(hideCursorRunnable)
        } else {
            cursorOverlay.updatePosition(newX, newY)
            scheduleCursorAutoHide()
        }
    }

    private fun performFastVerticalScroll(deltaYPixels: Double, normX: Float, normY: Float) {
        try {
            geckoSession?.panZoomController?.scrollBy(
                ScreenLength.zero(),
                ScreenLength.fromPixels(deltaYPixels),
                PanZoomController.SCROLL_BEHAVIOR_AUTO
            )
        } catch (e: Exception) {
            Log.w(TAG, "Aviso en scrollBy", e)
        }
    }

    private fun simulateTouchClickAtCursor() {
        val targetX = cursorOverlay.cursorX
        val targetY = cursorOverlay.cursorY
        val downTime = SystemClock.uptimeMillis()
        lastUserWebClickTime = downTime

        geckoView.requestFocus()
        geckoSession?.setActive(true)
        geckoSession?.setFocused(true)

        val properties = arrayOf(MotionEvent.PointerProperties().apply {
            id = 0
            toolType = MotionEvent.TOOL_TYPE_FINGER
        })

        val coords = arrayOf(MotionEvent.PointerCoords().apply {
            x = targetX
            y = targetY
            pressure = 1.0f
            size = 1.0f
        })

        isSyntheticDpadTap = true
        val downEvent = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            1,
            properties,
            coords,
            0,
            0,
            1.0f,
            1.0f,
            0,
            0,
            InputDevice.SOURCE_TOUCHSCREEN,
            0
        )
        geckoView.dispatchTouchEvent(downEvent)
        downEvent.recycle()

        mainHandler.postDelayed({
            val upTime = SystemClock.uptimeMillis()
            val upEvent = MotionEvent.obtain(
                downTime,
                upTime,
                MotionEvent.ACTION_UP,
                1,
                properties,
                coords,
                0,
                0,
                1.0f,
                1.0f,
                0,
                0,
                InputDevice.SOURCE_TOUCHSCREEN,
                0
            )
            geckoView.dispatchTouchEvent(upEvent)
            upEvent.recycle()
            isSyntheticDpadTap = false
            if (!isEditingWebInput) {
                scheduleCursorAutoHide()
            }
        }, 25L)
    }

    private fun hideSoftKeyboardFromView(view: View) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun createTvButton(
        textLabel: String,
        widthPx: Int,
        isAccent: Boolean = false,
        isDanger: Boolean = false
    ): Button {
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        return Button(this).apply {
            text = textLabel
            isAllCaps = false
            isFocusable = false
            isFocusableInTouchMode = false
            setTextColor(
                when {
                    isDanger -> Color.WHITE
                    isAccent -> Color.parseColor("#FCE7F3")
                    else -> Color.WHITE
                }
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f)
            setPadding(dp(2), 0, dp(2), 0)
            background = createButtonBackground(false, isAccent, isDanger)
            layoutParams = LinearLayout.LayoutParams(widthPx, dp(36))
        }
    }

    private fun createIconBackground(selected: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (selected) Color.parseColor("#4C1D95") else Color.TRANSPARENT)
            if (selected) {
                setStroke(3, Color.parseColor("#F472B6"))
            }
        }
    }

    private fun createButtonBackground(focused: Boolean, isAccent: Boolean, isDanger: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 11f
            when {
                focused -> {
                    setColor(if (isDanger) Color.parseColor("#DC2626") else Color.parseColor("#6D28D9"))
                    setStroke(3, Color.parseColor("#F472B6"))
                }
                isDanger -> {
                    setColor(Color.parseColor("#991B1B"))
                    setStroke(2, Color.parseColor("#F87171"))
                }
                isAccent -> {
                    setColor(Color.parseColor("#4A154B"))
                    setStroke(2, Color.parseColor("#EC4899"))
                }
                else -> {
                    setColor(Color.parseColor("#17153B"))
                    setStroke(2, Color.parseColor("#6366F1"))
                }
            }
        }
    }

    private fun createInputBackground(selectedOnBar: Boolean, editingText: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12f
            setColor(if (editingText) Color.parseColor("#191339") else Color.parseColor("#0B0E22"))
            when {
                editingText -> setStroke(3, Color.parseColor("#10B981"))
                selectedOnBar -> setStroke(3, Color.parseColor("#EC4899"))
                else -> setStroke(2, Color.parseColor("#8B5CF6"))
            }
        }
    }

    override fun onStop() {
        releaseHighPerfWifiLock()
        clearSafeAndroidCachesOnly(silent = true)
        super.onStop()
    }

    override fun onDestroy() {
        releaseHighPerfWifiLock()
        mainHandler.removeCallbacks(hideCursorRunnable)
        mainHandler.removeCallbacks(activateNativeWebInputRunnable)
        mainHandler.removeCallbacks(hideVideoSeekRunnable)
        mainHandler.removeCallbacks(commitVideoSeekRunnable)
        geckoSession?.close()
        super.onDestroy()
    }

    inner class VideoSeekOverlayView(context: Context) : View(context) {
        private var currentSec: Double = 0.0
        private var totalSec: Double = 0.0
        private var statusBadge: String = "⏩ +10s"
        private var isPlayingState: Boolean = true

        private val cardRect = RectF()
        private val trackRect = RectF()
        private val fillRect = RectF()

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#E80B0F29")
        }

        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = Color.parseColor("#EC4899")
        }

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#334155")
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#EC4899")
        }

        private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#F8FAFC")
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 26f
            isFakeBoldText = true
        }

        private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CBD5E1")
            textSize = 20f
        }

        fun isOverlayVisible(): Boolean = visibility == View.VISIBLE && alpha > 0.05f

        fun showSeekState(cur: Double, tot: Double, badge: String, playing: Boolean) {
            if (!isFullScreenVideo) {
                hideOverlayImmediate()
                return
            }
            currentSec = cur
            totalSec = tot
            statusBadge = badge
            isPlayingState = playing
            animate().cancel()
            if (visibility != View.VISIBLE) {
                alpha = 0f
                visibility = View.VISIBLE
                animate().alpha(1f).setDuration(140L).start()
            } else {
                alpha = 1f
            }
            postInvalidateOnAnimation()
        }

        fun updateProgressOnly(cur: Double, tot: Double, playing: Boolean) {
            if (!isFullScreenVideo) {
                hideOverlayImmediate()
                return
            }
            currentSec = cur
            totalSec = tot
            isPlayingState = playing
            if (visibility == View.VISIBLE) {
                postInvalidateOnAnimation()
            }
        }

        fun fadeOutAndHide() {
            if (visibility != View.VISIBLE) return
            animate().cancel()
            animate().alpha(0f).setDuration(220L).withEndAction {
                visibility = View.GONE
            }.start()
        }

        fun hideOverlayImmediate() {
            animate().cancel()
            alpha = 0f
            visibility = View.GONE
        }

        private fun formatSec(sec: Double): String {
            val total = sec.toInt().coerceAtLeast(0)
            val h = total / 3600
            val m = (total % 3600) / 60
            val s = total % 60
            return if (h > 0) {
                String.format("%d:%02d:%02d", h, m, s)
            } else {
                String.format("%02d:%02d", m, s)
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (visibility != View.VISIBLE || totalSec <= 0.0) return

            val w = width.toFloat()
            val h = height.toFloat()
            val marginH = w * 0.08f
            val cardH = 116f
            val cardTop = h - cardH - 34f
            val cardBottom = h - 34f

            cardRect.set(marginH, cardTop, w - marginH, cardBottom)
            canvas.drawRoundRect(cardRect, 22f, 22f, bgPaint)
            canvas.drawRoundRect(cardRect, 22f, 22f, borderPaint)

            val leftPad = marginH + 28f
            val rightPad = w - marginH - 28f

            val playIcon = if (isPlayingState) "▶" else "⏸"
            val timeLabel = "$playIcon  ${formatSec(currentSec)} / ${formatSec(totalSec)}   •   $statusBadge"
            canvas.drawText(timeLabel, leftPad, cardTop + 42f, textPaint)

            val hintLabel = "◄ ► Adelantar/Atrasar   •   OK: Pausa/Play   •   ▲ ▼ Ratón"
            val hintWidth = subTextPaint.measureText(hintLabel)
            if (rightPad - hintWidth > leftPad + 320f) {
                canvas.drawText(hintLabel, rightPad - hintWidth, cardTop + 40f, subTextPaint)
            }

            val barTop = cardTop + 72f
            val barBottom = cardTop + 86f
            trackRect.set(leftPad, barTop, rightPad, barBottom)
            canvas.drawRoundRect(trackRect, 7f, 7f, trackPaint)

            val ratio = (currentSec / totalSec).toFloat().coerceIn(0f, 1f)
            val fillRight = leftPad + (rightPad - leftPad) * ratio
            fillRect.set(leftPad, barTop, fillRight, barBottom)
            canvas.drawRoundRect(fillRect, 7f, 7f, fillPaint)

            val centerY = (barTop + barBottom) * 0.5f
            canvas.drawCircle(fillRight, centerY, 12f, fillPaint)
            canvas.drawCircle(fillRight, centerY, 7f, thumbPaint)
        }
    }

    inner class CursorOverlayView(context: Context) : View(context) {

        var cursorX: Float = 480f
            private set
        var cursorY: Float = 270f
            private set

        private var isClicking: Boolean = false
        private val arrowPath = Path()

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }

        private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            strokeJoin = Paint.Join.ROUND
            color = Color.parseColor("#1E1B4B")
        }

        private val clickSpotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#EC4899")
        }

        fun centerCursor() {
            if (width > 0 && height > 0) {
                cursorX = width / 2f
                cursorY = height / 2f
                showCursorAndScheduleFade()
            }
        }

        fun updatePosition(x: Float, y: Float) {
            cursorX = x
            cursorY = y
            if (visibility != View.VISIBLE) {
                visibility = View.VISIBLE
            }
            alpha = 1.0f
            postInvalidateOnAnimation()
        }

        fun updatePositionAndHide(x: Float, y: Float) {
            cursorX = x
            cursorY = y
            alpha = 0.0f
            visibility = View.GONE
        }

        fun showCursorAndScheduleFade() {
            if (visibility != View.VISIBLE) {
                visibility = View.VISIBLE
            }
            alpha = 1.0f
            postInvalidateOnAnimation()
        }

        fun hideCursorUntilMoved() {
            alpha = 0.0f
            visibility = View.GONE
        }

        fun isCursorVisibleNow(): Boolean {
            return visibility == View.VISIBLE && alpha > 0.1f
        }

        fun setClickingState(clicking: Boolean) {
            isClicking = clicking
            if (visibility != View.VISIBLE) {
                visibility = View.VISIBLE
            }
            alpha = 1.0f
            postInvalidateOnAnimation()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (alpha <= 0.01f) return

            val scale = if (isClicking) 0.88f else 1.0f
            val x = cursorX
            val y = cursorY

            arrowPath.reset()
            arrowPath.moveTo(x, y)
            arrowPath.lineTo(x, y + 36f * scale)
            arrowPath.lineTo(x + 9.5f * scale, y + 27.5f * scale)
            arrowPath.lineTo(x + 16.5f * scale, y + 41f * scale)
            arrowPath.lineTo(x + 22.5f * scale, y + 38f * scale)
            arrowPath.lineTo(x + 15.5f * scale, y + 24.5f * scale)
            arrowPath.lineTo(x + 27f * scale, y + 24.5f * scale)
            arrowPath.close()

            fillPaint.color = if (isClicking) Color.parseColor("#EC4899") else Color.WHITE
            strokePaint.color = if (isClicking) Color.WHITE else Color.parseColor("#1E1B4B")

            canvas.drawPath(arrowPath, fillPaint)
            canvas.drawPath(arrowPath, strokePaint)

            if (isClicking) {
                canvas.drawCircle(x, y, 6f, clickSpotPaint)
            }
        }
    }
}
