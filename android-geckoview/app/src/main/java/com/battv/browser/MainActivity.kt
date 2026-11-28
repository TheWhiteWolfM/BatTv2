package com.battv.browser

import android.app.AlertDialog
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Choreographer
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView
import org.mozilla.geckoview.MediaSession
import org.mozilla.geckoview.PanZoomController
import org.mozilla.geckoview.ScreenLength
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebExtension
import java.util.Collections
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {

  companion object {
    private const val PREFS_NAME = "battv_browser_prefs"
    private const val KEY_BOOKMARKS = "bookmarks_tv_list_v5"
    private const val KEY_BOOKMARKS_SEEDED = "bookmarks_tv_seeded_v5"
    private const val KEY_BOOKMARKS_ADDED_V20 = "bookmarks_added_extra_v20"
    private const val KEY_HISTORY = "history_json_v2"
    private const val KEY_DESKTOP_MODE = "desktop_mode_enabled"
    private const val KEY_MODE_SELECTED = "initial_mode_selected_v2"

    private const val URL_BAR_INDEX = 2

    private const val BASE_CURSOR_FRAME_SPEED_PX = 6.8f
    private const val MAX_CURSOR_FRAME_SPEED_PX = 19.5f
    private const val BASE_SCROLL_STEP_PX = 9.0
    private const val SHORT_TAP_GLIDE_DISTANCE_PX = 36.0
    private const val EDGE_SCROLL_ZONE_PX = 14f
    private const val CURSOR_AUTO_HIDE_MS = 5000L
    private const val HOVER_DISPATCH_THROTTLE_MS = 50L
    private const val DPAD_HOLD_WATCHDOG_MS = 750L
  }

  private lateinit var prefs: SharedPreferences
  private val mainHandler = Handler(Looper.getMainLooper())

  private lateinit var rootLayout: FrameLayout
  private lateinit var topBarContainer: LinearLayout
  private lateinit var topBarLayout: LinearLayout
  private var isTopBarOverlayVisible: Boolean = true
  private var topBarHeightPx: Float = 0f

  private val scrollPointerProps = arrayOf(
    MotionEvent.PointerProperties().apply {
      id = 0
      toolType = MotionEvent.TOOL_TYPE_MOUSE
    }
  )
  private val scrollPointerCoords = arrayOf(MotionEvent.PointerCoords())

  // 1. Menu, 2. Recargar, 3. Campo URL/Busqueda, 4. Favoritos, 5. Historial, 6. Boton X
  private lateinit var btnMenu: Button
  private lateinit var btnReload: Button
  private lateinit var urlEditText: EditText
  private lateinit var btnBookmarks: Button
  private lateinit var btnHistory: Button
  private lateinit var btnCloseApp: Button
  private lateinit var toolbarItems: List<View>

  private lateinit var loadingProgressBar: ProgressBar
  private lateinit var webContainer: FrameLayout
  private lateinit var geckoView: GeckoView
  private lateinit var homeOverlayLayout: FrameLayout
  private lateinit var homeContentContainer: LinearLayout
  private lateinit var videoSeekOverlay: VideoSeekOverlayView
  private lateinit var cursorOverlay: CursorOverlayView

  private lateinit var geckoManager: BatTvGeckoViewManager
  private var geckoSession: GeckoSession? = null
  private var activeMediaSession: MediaSession? = null
  private val activeWebPorts = Collections.synchronizedSet(mutableSetOf<WebExtension.Port>())

  private var isHomeScreenVisible: Boolean = true
  private var isHomeToolbarFocused: Boolean = false
  private var canGoBackState: Boolean = false
  private var canGoForwardState: Boolean = false
  private var isFullScreenVideo: Boolean = false
  private var isTheaterModeActive: Boolean = false
  private var isExitingCompletely: Boolean = false

  private var currentUrl: String = ""
  private var currentTitle: String = ""
  private var latestExtractedHeading: String = ""
  private var lastSavedHistoryUrl: String = ""
  private var lastSavedHistoryTitle: String = ""

  private var isDesktopMode: Boolean = false
  private var isCursorModeActive: Boolean = false
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
  private var pendingSyntheticTapCount: Int = 0
  private var lastCursorInteractionUptimeMs: Long = 0L
  private var lastHoverDispatchUptimeMs: Long = 0L
  private var wokeCursorKeyCode: Int = 0

  private var hasActiveVodVideo: Boolean = false
  private var isLiveStreamVideo: Boolean = false
  private var vodIsPlaying: Boolean = false
  private var vodCurrentTimeSec: Double = 0.0
  private var vodDurationSec: Double = 0.0
  private var isUserSeekingVod: Boolean = false
  private var consecutiveSeekPresses: Int = 0
  private var lastSeekDirection: Int = 0
  private var lastSeekPressUptime: Long = 0L
  private var cumulativeSeekDeltaSec: Double = 0.0

  private var fullscreenPromptDialog: AlertDialog? = null
  private var fullscreenPromptDismissedForPage: Boolean = false
  private var lastFullscreenExitUptimeMs: Long = 0L
  private var lastDismissedPlayerKey: String = ""
  private var lastPromptedPlayerKey: String = ""
  private var lastTopNavChangeUptimeMs: Long = 0L

  private var activeDpadKeyCode: Int = 0
  private var isDpadPhysicallyHeld: Boolean = false
  private var dpadHoldStartUptimeMs: Long = 0L
  private var lastDpadEventUptimeMs: Long = 0L
  private var lastFrameTimeNanos: Long = 0L
  private var isSmoothDpadLoopRunning: Boolean = false
  private var remainingShortTapScrollPx: Double = 0.0
  private var remainingShortTapCursorX: Float = 0f
  private var remainingShortTapCursorY: Float = 0f

  private val smoothDpadFrameCallback = object : Choreographer.FrameCallback {
    override fun doFrame(frameTimeNanos: Long) {
      if (!isSmoothDpadLoopRunning || !isCursorModeActive || isHomeScreenVisible) {
        isSmoothDpadLoopRunning = false
        lastFrameTimeNanos = 0L
        remainingShortTapScrollPx = 0.0
        remainingShortTapCursorX = 0f
        remainingShortTapCursorY = 0f
        return
      }

      val dtScale = if (lastFrameTimeNanos == 0L) {
        1.0
      } else {
        ((frameTimeNanos - lastFrameTimeNanos) / 16_666_667.0).coerceIn(0.5, 2.2)
      }
      lastFrameTimeNanos = frameTimeNanos

      val now = SystemClock.uptimeMillis()

      if (isDpadPhysicallyHeld && activeDpadKeyCode != 0 && (now - lastDpadEventUptimeMs <= DPAD_HOLD_WATCHDOG_MS)) {
        val heldMs = (now - dpadHoldStartUptimeMs).coerceAtLeast(0L)
        val accelFactor = (heldMs.toFloat() / 520f).coerceIn(0f, 1f)
        val cursorSpeed = (BASE_CURSOR_FRAME_SPEED_PX +
          (MAX_CURSOR_FRAME_SPEED_PX - BASE_CURSOR_FRAME_SPEED_PX) * (accelFactor * accelFactor)) * dtScale.toFloat()

        val dx = when (activeDpadKeyCode) {
          KeyEvent.KEYCODE_DPAD_LEFT -> -cursorSpeed
          KeyEvent.KEYCODE_DPAD_RIGHT -> cursorSpeed
          else -> 0f
        }
        val dy = when (activeDpadKeyCode) {
          KeyEvent.KEYCODE_DPAD_UP -> -cursorSpeed
          KeyEvent.KEYCODE_DPAD_DOWN -> cursorSpeed
          else -> 0f
        }

        cursorOverlay.moveCursorBy(dx, dy)
        if (now - lastHoverDispatchUptimeMs >= HOVER_DISPATCH_THROTTLE_MS) {
          lastHoverDispatchUptimeMs = now
          dispatchHoverMoveAtCursor()
        }

        if (!isFullScreenVideo) {
          val constantScrollStep = BASE_SCROLL_STEP_PX * dtScale
          if (activeDpadKeyCode == KeyEvent.KEYCODE_DPAD_UP && cursorOverlay.cursorY <= EDGE_SCROLL_ZONE_PX) {
            remainingShortTapScrollPx = 0.0
            scrollWebPageNative(-constantScrollStep)
          } else if (activeDpadKeyCode == KeyEvent.KEYCODE_DPAD_DOWN &&
            cursorOverlay.cursorY >= (webContainer.height - EDGE_SCROLL_ZONE_PX)
          ) {
            remainingShortTapScrollPx = 0.0
            scrollWebPageNative(constantScrollStep)
          }
        }

        Choreographer.getInstance().postFrameCallback(this)
        return
      }

      isDpadPhysicallyHeld = false
      activeDpadKeyCode = 0

      var keepAnimatingShortTap = false

      if (abs(remainingShortTapCursorX) >= 0.4f || abs(remainingShortTapCursorY) >= 0.4f) {
        val stepX = remainingShortTapCursorX * 0.32f
        val stepY = remainingShortTapCursorY * 0.32f
        remainingShortTapCursorX -= stepX
        remainingShortTapCursorY -= stepY
        cursorOverlay.moveCursorBy(stepX, stepY)
        keepAnimatingShortTap = true
      } else {
        remainingShortTapCursorX = 0f
        remainingShortTapCursorY = 0f
      }

      if (!isFullScreenVideo && abs(remainingShortTapScrollPx) >= 0.8) {
        val stepScroll = remainingShortTapScrollPx * 0.28 * dtScale.coerceAtMost(1.3)
        remainingShortTapScrollPx -= stepScroll
        scrollWebPageNative(stepScroll)
        keepAnimatingShortTap = true
      } else {
        remainingShortTapScrollPx = 0.0
      }

      if (keepAnimatingShortTap) {
        Choreographer.getInstance().postFrameCallback(this)
      } else {
        isSmoothDpadLoopRunning = false
        lastFrameTimeNanos = 0L
        lastHoverDispatchUptimeMs = now
        dispatchHoverMoveAtCursor()
      }
    }
  }

  private val autoHideTopBarRunnable = Runnable {
    if (isCursorModeActive && !isHomeScreenVisible && !isEditingUrlText && !isEditingWebInput) {
      setTopBarOverlayVisible(false, animate = true)
    }
  }

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
    if (activeWebPorts.isNotEmpty()) {
      sendVideoCommandToWeb("SEEK_TO", vodCurrentTimeSec)
    } else {
      try {
        activeMediaSession?.seekTo(vodCurrentTimeSec, false)
      } catch (_: Exception) {}
    }
    isUserSeekingVod = false
  }

  private val fullScreenExitWatchdogRunnable = Runnable {
    if (isFullScreenVideo || isTheaterModeActive) {
      applyFullScreenState(false)
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    window.addFlags(
      WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
    )

    prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    isDesktopMode = prefs.getBoolean(KEY_DESKTOP_MODE, false)

    ensureDefaultBookmarksOnFirstInstall()
    setupUserInterface()

    geckoManager = BatTvGeckoViewManager(this, geckoView)
    val session = geckoManager.initialize(isDesktopMode, createBridgeMessageDelegate())
    geckoSession = session
    attachSessionDelegates(session)

    val externalIntentUrl = intent?.dataString?.takeIf {
      it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
    }

    webContainer.post {
      cursorOverlay.centerCursor()
      if (externalIntentUrl != null) {
        loadUrlOrSearch(externalIntentUrl)
      } else {
        showHomeScreen()
      }

      if (!prefs.getBoolean(KEY_MODE_SELECTED, false)) {
        showFirstLaunchModeDialog()
      }
    }
  }

  private fun ensureDefaultBookmarksOnFirstInstall() {
    if (!prefs.getBoolean(KEY_BOOKMARKS_SEEDED, false)) {
      val defaults = JSONArray().apply {
        put(JSONObject().put("title", "Doramasflix.io").put("url", "https://doramasflix.io/"))
        put(JSONObject().put("title", "Doramasflix.co").put("url", "https://doramasflix.co/"))
        put(JSONObject().put("title", "DramaChino").put("url", "https://dramachino.com/"))
        put(JSONObject().put("title", "TuDorama").put("url", "https://tudorama.com/"))
        put(JSONObject().put("title", "AsiaLiveAction").put("url", "https://asialiveaction.com/"))
        put(JSONObject().put("title", "KissKH").put("url", "https://kisskh.do/"))
        put(JSONObject().put("title", "JKAnime").put("url", "https://jkanime.net/"))
        put(JSONObject().put("title", "Chaturbate").put("url", "https://chaturbate.com/"))
        put(JSONObject().put("title", "LiveCamRips").put("url", "https://livecamrips.to/"))
        put(JSONObject().put("title", "OnlyJerk").put("url", "https://onlyjerk.net/"))
      }
      prefs.edit()
        .putString(KEY_BOOKMARKS, defaults.toString())
        .putBoolean(KEY_BOOKMARKS_SEEDED, true)
        .putBoolean(KEY_BOOKMARKS_ADDED_V20, true)
        .apply()
      return
    }

    if (!prefs.getBoolean(KEY_BOOKMARKS_ADDED_V20, false)) {
      val currentArr = getBookmarksArray()
      val newSites = listOf(
        "Chaturbate" to "https://chaturbate.com/",
        "LiveCamRips" to "https://livecamrips.to/",
        "OnlyJerk" to "https://onlyjerk.net/"
      )
      for ((title, url) in newSites) {
        var exists = false
        for (i in 0 until currentArr.length()) {
          val item = currentArr.optJSONObject(i) ?: continue
          if (item.optString("url").equals(url, ignoreCase = true)) {
            exists = true
            break
          }
        }
        if (!exists) {
          currentArr.put(JSONObject().put("title", title).put("url", url))
        }
      }
      prefs.edit()
        .putString(KEY_BOOKMARKS, currentArr.toString())
        .putBoolean(KEY_BOOKMARKS_ADDED_V20, true)
        .apply()
    }
  }

  @Suppress("DEPRECATION")
  override fun onTrimMemory(level: Int) {
    super.onTrimMemory(level)
    if (!::geckoManager.isInitialized) return
    try {
      if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL || level >= ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
        geckoManager.runtime.storageController.clearData(
          StorageController.ClearFlags.IMAGE_CACHE or StorageController.ClearFlags.NETWORK_CACHE
        )
      } else if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
        geckoManager.runtime.storageController.clearData(StorageController.ClearFlags.IMAGE_CACHE)
      }
    } catch (_: Exception) {}
  }

  override fun onLowMemory() {
    super.onLowMemory()
    if (::geckoManager.isInitialized) {
      try {
        geckoManager.runtime.storageController.clearData(
          StorageController.ClearFlags.IMAGE_CACHE or StorageController.ClearFlags.NETWORK_CACHE
        )
      } catch (_: Exception) {}
    }
  }

  override fun onResume() {
    super.onResume()
    if (!isHomeScreenVisible) {
      geckoSession?.setActive(true)
      geckoSession?.setFocused(true)
    }
    if (isFullScreenVideo || isTheaterModeActive) {
      applyImmersiveSystemUi(true)
    }
  }

  override fun onPause() {
    stopSmoothDpadMovement(immediate = true)
    super.onPause()
    mainHandler.removeCallbacks(autoHideTopBarRunnable)
    mainHandler.removeCallbacks(hideCursorRunnable)
    mainHandler.removeCallbacks(hideVideoSeekRunnable)
    geckoSession?.setActive(false)
  }

  override fun onStop() {
    super.onStop()
    if (!isChangingConfigurations && !isExitingCompletely) {
      exitBrowserCleanly()
    }
  }

  override fun onDestroy() {
    stopSmoothDpadMovement(immediate = true)
    dismissFullscreenPromptDialog()
    mainHandler.removeCallbacksAndMessages(null)
    if (::geckoManager.isInitialized && !isExitingCompletely) {
      geckoManager.closeSessionCleanly()
    }
    super.onDestroy()
  }

  private fun setupUserInterface() {
    val density = resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()

    rootLayout = FrameLayout(this).apply {
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      setBackgroundColor(Color.parseColor("#080B18"))
    }

    topBarLayout = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      setPadding(dp(8), dp(5), dp(8), dp(5))
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
        dp(52)
      )
    }

    btnMenu = createTvButton("\u2630 Men\u00FA", dp(80), isAccent = true).apply {
      setOnClickListener { showMainMenuDialog() }
    }

    btnReload = createTvButton("\u27F3", dp(44)).apply {
      layoutParams = LinearLayout.LayoutParams(dp(44), dp(38)).apply {
        marginStart = dp(6)
      }
      setOnClickListener {
        if (isHomeScreenVisible) {
          renderHomeScreenContent()
          Toast.makeText(this@MainActivity, "Favoritos actualizados", Toast.LENGTH_SHORT).show()
        } else {
          if (isFullScreenVideo || isTheaterModeActive) {
            exitFullScreenSafely()
          }
          fullscreenPromptDismissedForPage = false
          lastDismissedPlayerKey = ""
          lastPromptedPlayerKey = ""
          geckoSession?.stop()
          geckoSession?.reload()
          activateCursorMode(showPointerNow = false)
        }
      }
    }

    urlEditText = object : EditText(this) {
      override fun onKeyPreIme(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && event != null) {
          if (event.action == KeyEvent.ACTION_UP) {
            if (isEditingWebInput) {
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
      hint = "Escribir direcci\u00F3n web o realizar b\u00FAsqueda (Pulsa OK)..."
      setHintTextColor(Color.parseColor("#A5B4FC"))
      setTextColor(Color.parseColor("#F8FAFC"))
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
      isSingleLine = true
      isFocusable = false
      isFocusableInTouchMode = false
      isCursorVisible = false
      inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
      imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
      setPadding(dp(12), dp(4), dp(12), dp(4))
      background = createInputBackground(selectedOnBar = false, editingText = false)
      layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f).apply {
        marginStart = dp(8)
        marginEnd = dp(8)
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

        if (isEditingWebInput && actionId == EditorInfo.IME_ACTION_NEXT) {
          navigateAdjacentWebFormField(1)
          return@setOnEditorActionListener true
        }

        if (isEditingWebInput && isCurrentWebInputMultiField && actionId == EditorInfo.IME_ACTION_DONE) {
          closeNativeWebInputBridge(submit = false)
          return@setOnEditorActionListener true
        }

        val isEnterKey = keyEvent != null &&
          (keyEvent.keyCode == KeyEvent.KEYCODE_ENTER ||
            keyEvent.keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            keyEvent.keyCode == KeyEvent.KEYCODE_DPAD_CENTER) &&
          keyEvent.action == KeyEvent.ACTION_DOWN

        if (actionId == EditorInfo.IME_ACTION_GO ||
          actionId == EditorInfo.IME_ACTION_SEARCH ||
          actionId == EditorInfo.IME_ACTION_SEND ||
          actionId == EditorInfo.IME_ACTION_DONE ||
          isEnterKey
        ) {
          submitUrlFromInput()
          true
        } else {
          false
        }
      }
    }

    btnBookmarks = createTvButton("\u2B50", dp(44)).apply {
      setOnClickListener { showBookmarksDialog() }
    }

    btnHistory = createTvButton("\uD83D\uDD52", dp(44)).apply {
      layoutParams = LinearLayout.LayoutParams(dp(44), dp(38)).apply {
        marginStart = dp(6)
      }
      setOnClickListener { showHistoryDialog() }
    }

    btnCloseApp = createTvButton("\u2716", dp(44), isDanger = true).apply {
      layoutParams = LinearLayout.LayoutParams(dp(44), dp(38)).apply {
        marginStart = dp(6)
      }
      setOnClickListener { exitBrowserCleanly() }
    }

    toolbarItems = listOf(
      btnMenu,
      btnReload,
      urlEditText,
      btnBookmarks,
      btnHistory,
      btnCloseApp
    )

    for (item in toolbarItems) {
      topBarLayout.addView(item)
    }

    loadingProgressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
      max = 100
      progress = 0
      visibility = View.GONE
      elevation = 120f
      layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        dp(3),
        Gravity.TOP
      )
    }

    webContainer = FrameLayout(this).apply {
      clipChildren = false
      layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
    }

    geckoView = GeckoView(this).apply {
      layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      isFocusable = true
      isFocusableInTouchMode = true
      setDynamicToolbarMaxHeight(0)

      setOnTouchListener { _, event ->
        val isSynthetic = pendingSyntheticTapCount > 0
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
          lastUserWebClickTime = SystemClock.uptimeMillis()
          if (!isSynthetic && !isFullScreenVideo && !isTheaterModeActive && !isHomeScreenVisible) {
            if (!isTopBarOverlayVisible && event.y <= (38f * density)) {
              setTopBarOverlayVisible(true, animate = true)
              scheduleAutoHideTopBar(4200L)
            } else if (isTopBarOverlayVisible && event.y > topBarHeightPx) {
              setTopBarOverlayVisible(false, animate = true)
            }
          }
        }
        if (!isSynthetic) {
          cursorOverlay.hideCursorUntilMoved()
        }
        false
      }
    }

    homeOverlayLayout = FrameLayout(this).apply {
      layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      setBackgroundColor(Color.parseColor("#080B18"))
      visibility = View.VISIBLE
    }

    val homeScroll = ScrollView(this).apply {
      layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      isFillViewport = true
    }

    homeContentContainer = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
      setPadding(dp(36), dp(72), dp(36), dp(28))
      layoutParams = ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      )
    }

    homeScroll.addView(homeContentContainer)
    homeOverlayLayout.addView(homeScroll)

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

    cursorOverlay = CursorOverlayView(this).apply {
      layoutParams = FrameLayout.LayoutParams(dp(56), dp(56))
      isFocusable = false
      isFocusableInTouchMode = false
      isClickable = false
      elevation = 100f
      setLayerType(View.LAYER_TYPE_HARDWARE, null)
    }

    webContainer.addView(geckoView)
    webContainer.addView(homeOverlayLayout)
    webContainer.addView(videoSeekOverlay)
    webContainer.addView(cursorOverlay)

    topBarHeightPx = dp(56).toFloat()
    topBarContainer = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      elevation = 110f
      setLayerType(View.LAYER_TYPE_HARDWARE, null)
      layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        Gravity.TOP
      )
      addView(topBarLayout)
    }

    rootLayout.addView(webContainer)
    rootLayout.addView(topBarContainer)
    rootLayout.addView(loadingProgressBar)

    setContentView(rootLayout)
  }

  private fun setTopBarOverlayVisible(visible: Boolean, animate: Boolean = true) {
    if (!::topBarContainer.isInitialized) return
    if (isTopBarOverlayVisible == visible && animate &&
      ((visible && topBarContainer.visibility == View.VISIBLE) ||
        (!visible && topBarContainer.visibility == View.GONE))
    ) {
      return
    }
    isTopBarOverlayVisible = visible
    topBarContainer.descendantFocusability = if (visible) {
      ViewGroup.FOCUS_AFTER_DESCENDANTS
    } else {
      ViewGroup.FOCUS_BLOCK_DESCENDANTS
    }
    val slideDist = max(topBarHeightPx, topBarContainer.height.toFloat())
    val targetY = if (visible) 0f else -slideDist
    val targetAlpha = if (visible) 1f else 0f
    topBarContainer.animate().cancel()
    if (visible) {
      topBarContainer.visibility = View.VISIBLE
    }
    if (animate) {
      topBarContainer.animate()
        .translationY(targetY)
        .alpha(targetAlpha)
        .setDuration(140L)
        .withEndAction {
          if (!isTopBarOverlayVisible) {
            topBarContainer.visibility = View.GONE
          }
        }
        .start()
    } else {
      topBarContainer.translationY = targetY
      topBarContainer.alpha = targetAlpha
      topBarContainer.visibility = if (visible) View.VISIBLE else View.GONE
    }
  }

  private fun scheduleAutoHideTopBar(delayMs: Long = 2200L) {
    mainHandler.removeCallbacks(autoHideTopBarRunnable)
    if (!isHomeScreenVisible && isCursorModeActive && !isEditingUrlText && !isEditingWebInput) {
      mainHandler.postDelayed(autoHideTopBarRunnable, delayMs)
    }
  }

  private fun promptAutoFullscreenIfEligible(playerKey: String = "") {
    if (isHomeScreenVisible || isFullScreenVideo || isTheaterModeActive) return
    if (fullscreenPromptDialog?.isShowing == true) return
    val now = SystemClock.uptimeMillis()
    if (now - lastTopNavChangeUptimeMs < 900L) return

    val isNewPlayer = playerKey.isNotBlank() &&
      playerKey != lastDismissedPlayerKey &&
      playerKey != lastPromptedPlayerKey
    if (isNewPlayer) {
      fullscreenPromptDismissedForPage = false
    } else {
      if (fullscreenPromptDismissedForPage) return
      if (playerKey.isNotBlank() && playerKey == lastDismissedPlayerKey) return
      if (now - lastFullscreenExitUptimeMs < 3500L) return
    }

    lastPromptedPlayerKey = playerKey
    stopSmoothDpadMovement(immediate = true)

    val dialog = AlertDialog.Builder(this)
      .setTitle("\uD83C\uDFAC Reproducir en pantalla completa")
      .setMessage("\u00BFQuieres abrir el v\u00EDdeo en pantalla completa ahora?")
      .setCancelable(true)
      .setPositiveButton("Aceptar") { d, _ ->
        d.dismiss()
        fullscreenPromptDialog = null
        enterVideoFullscreenAutomatically()
      }
      .setNegativeButton("Cancelar") { d, _ ->
        fullscreenPromptDismissedForPage = true
        lastDismissedPlayerKey = playerKey
        d.dismiss()
        fullscreenPromptDialog = null
      }
      .setOnCancelListener {
        fullscreenPromptDismissedForPage = true
        lastDismissedPlayerKey = playerKey
        fullscreenPromptDialog = null
      }
      .create()

    fullscreenPromptDialog = dialog
    dialog.show()
    dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
  }

  private fun dismissFullscreenPromptDialog() {
    try {
      if (fullscreenPromptDialog?.isShowing == true) {
        fullscreenPromptDialog?.dismiss()
      }
    } catch (_: Exception) {}
    fullscreenPromptDialog = null
  }

  private fun enterVideoFullscreenAutomatically() {
    val isCbSite = currentUrl.contains("chaturbate.com", ignoreCase = true)
    applyFullScreenState(true, theaterOnly = isCbSite)
    sendVideoCommandToWeb("ENTER_FULLSCREEN", 0.0)
  }

  private fun showHomeScreen() {
    stopSmoothDpadMovement(immediate = true)
    dismissFullscreenPromptDialog()
    if (isFullScreenVideo || isTheaterModeActive) {
      exitFullScreenSafely()
    }
    if (isEditingWebInput) {
      closeNativeWebInputBridge(submit = false)
    }
    isHomeScreenVisible = true
    isHomeToolbarFocused = false
    isCursorModeActive = false
    mainHandler.removeCallbacks(autoHideTopBarRunnable)
    setTopBarOverlayVisible(true, animate = false)
    sendVideoCommandToWeb("STOP_ALL_MEDIA", 0.0)
    geckoSession?.stop()
    try {
      geckoSession?.loadUri("about:blank")
    } catch (_: Exception) {}
    geckoSession?.setActive(false)
    cursorOverlay.hideCursorUntilMoved()
    videoSeekOverlay.hideOverlayImmediate()
    loadingProgressBar.visibility = View.GONE
    homeOverlayLayout.visibility = View.VISIBLE

    suppressWebInputWatcher = true
    urlEditText.setText("")
    urlEditText.hint = "Escribir direcci\u00F3n web o realizar b\u00FAsqueda (Pulsa OK)..."
    suppressWebInputWatcher = false

    renderHomeScreenContent()
    updateToolbarSelectionVisuals()
  }

  private fun renderHomeScreenContent() {
    val density = resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()

    homeContentContainer.removeAllViews()

    val bookmarksArr = getBookmarksArray()

    val headerRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER_VERTICAL
      layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
      ).apply {
        bottomMargin = dp(20)
      }
    }

    val titleCol = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    }

    val appTitleView = TextView(this).apply {
      text = "Bat TV \u2022 Favoritos"
      setTextColor(Color.WHITE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
      setTypeface(typeface, Typeface.BOLD)
    }

    val modeSubtitleView = TextView(this).apply {
      val modeText = if (isDesktopMode) "Modo PC (Escritorio)" else "Modo TV (Control remoto)"
      text = "$modeText  \u2022  Google TV 1080p Optimizado • Anti-Ads & Directos 60FPS"
      setTextColor(Color.parseColor("#A5B4FC"))
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
    }

    titleCol.addView(appTitleView)
    titleCol.addView(modeSubtitleView)

    val btnAddFavHeader = createHomeActionButton("\u2795 Agregar favorito", isPrimary = true).apply {
      setOnClickListener { showAddBookmarkDialog() }
    }

    headerRow.addView(titleCol)
    headerRow.addView(btnAddFavHeader)
    homeContentContainer.addView(headerRow)

    if (bookmarksArr.length() == 0) {
      val emptyCard = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(32), dp(40), dp(32), dp(40))
        background = GradientDrawable().apply {
          shape = GradientDrawable.RECTANGLE
          cornerRadius = dp(16).toFloat()
          setColor(Color.parseColor("#111633"))
          setStroke(2, Color.parseColor("#312E81"))
        }
        layoutParams = LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
          topMargin = dp(16)
        }
      }

      val emptyTitle = TextView(this).apply {
        text = "Todav\u00EDa no tienes p\u00E1ginas en Favoritos"
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
      }

      val emptyDesc = TextView(this).apply {
        text = "Agrega tus sitios favoritos aqu\u00ED para abrirlos directamente al iniciar Bat TV o escribe una direcci\u00F3n en la barra superior."
        setTextColor(Color.parseColor("#94A3B8"))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
          ViewGroup.LayoutParams.WRAP_CONTENT,
          ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
          topMargin = dp(8)
          bottomMargin = dp(22)
        }
      }

      val btnAddFirstFavorite = createHomeActionButton("\u2795 Agregar primer favorito", isPrimary = true).apply {
        setOnClickListener { showAddBookmarkDialog() }
      }

      emptyCard.addView(emptyTitle)
      emptyCard.addView(emptyDesc)
      emptyCard.addView(btnAddFirstFavorite)
      homeContentContainer.addView(emptyCard)

      btnAddFirstFavorite.post {
        if (isHomeScreenVisible && !isHomeToolbarFocused) {
          btnAddFirstFavorite.requestFocus()
        }
      }
    } else {
      var currentRow: LinearLayout? = null
      var firstFavoriteButton: View? = null

      for (i in 0 until bookmarksArr.length()) {
        val obj = bookmarksArr.optJSONObject(i) ?: continue
        val favTitle = obj.optString("title", "Favorito")
        val favUrl = obj.optString("url", "")

        if (i % 2 == 0) {
          currentRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
              bottomMargin = dp(12)
            }
          }
          homeContentContainer.addView(currentRow)
        }

        val cardLayout = LinearLayout(this).apply {
          orientation = LinearLayout.HORIZONTAL
          gravity = Gravity.CENTER_VERTICAL
          setPadding(dp(14), dp(12), dp(12), dp(12))
          background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(Color.parseColor("#131836"))
            setStroke(2, Color.parseColor("#3730A3"))
          }
          layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            if (i % 2 == 0) marginEnd = dp(8) else marginStart = dp(8)
          }
        }

        val openBtn = Button(this).apply {
          isAllCaps = false
          isFocusable = true
          isFocusableInTouchMode = false
          gravity = Gravity.START or Gravity.CENTER_VERTICAL
          text = "\u2B50  $favTitle\n      $favUrl"
          setTextColor(Color.WHITE)
          setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
          maxLines = 2
          ellipsize = TextUtils.TruncateAt.END
          setPadding(dp(10), dp(6), dp(10), dp(6))
          background = createSelectableCardBackground(false)
          layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
          setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
              isHomeToolbarFocused = false
              updateToolbarSelectionVisuals()
            }
            v.background = createSelectableCardBackground(hasFocus)
          }
          setOnClickListener { loadUrlOrSearch(favUrl) }
        }

        if (firstFavoriteButton == null) {
          firstFavoriteButton = openBtn
        }

        val deleteBtn = Button(this).apply {
          text = "\uD83D\uDDD1\uFE0F"
          isAllCaps = false
          isFocusable = true
          isFocusableInTouchMode = false
          setTextColor(Color.WHITE)
          setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
          background = createButtonBackground(false, isAccent = false, isDanger = true)
          layoutParams = LinearLayout.LayoutParams(dp(44), dp(42)).apply {
            marginStart = dp(8)
          }
          setOnFocusChangeListener { v, hasFocus ->
            if (hasFocus) {
              isHomeToolbarFocused = false
              updateToolbarSelectionVisuals()
            }
            v.background = createButtonBackground(hasFocus, isAccent = false, isDanger = true)
          }
          setOnClickListener { deleteBookmarkAt(i) }
        }

        cardLayout.addView(openBtn)
        cardLayout.addView(deleteBtn)
        currentRow?.addView(cardLayout)
      }

      if (bookmarksArr.length() % 2 != 0 && currentRow != null) {
        val spacer = View(this).apply {
          layoutParams = LinearLayout.LayoutParams(0, dp(10), 1f).apply {
            marginStart = dp(8)
          }
        }
        currentRow.addView(spacer)
      }

      firstFavoriteButton?.post {
        if (isHomeScreenVisible && !isHomeToolbarFocused) {
          firstFavoriteButton.requestFocus()
        }
      }
    }
  }

  private fun createHomeActionButton(label: String, isPrimary: Boolean): Button {
    val density = resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()

    return Button(this).apply {
      text = label
      isAllCaps = false
      isFocusable = true
      isFocusableInTouchMode = false
      setTextColor(Color.WHITE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
      setPadding(dp(18), dp(10), dp(18), dp(10))
      background = createButtonBackground(false, isAccent = isPrimary, isDanger = false)
      setOnFocusChangeListener { v, hasFocus ->
        if (hasFocus) {
          isHomeToolbarFocused = false
          updateToolbarSelectionVisuals()
        }
        v.background = createButtonBackground(hasFocus, isAccent = isPrimary, isDanger = false)
      }
    }
  }

  private fun createSelectableCardBackground(focused: Boolean): GradientDrawable {
    return GradientDrawable().apply {
      shape = GradientDrawable.RECTANGLE
      cornerRadius = 10f
      if (focused) {
        setColor(Color.parseColor("#312E81"))
        setStroke(3, Color.parseColor("#F472B6"))
      } else {
        setColor(Color.TRANSPARENT)
      }
    }
  }

  private fun showFirstLaunchModeDialog() {
    val options = arrayOf(
      "\uD83D\uDCFA  Modo TV \u2014 Interfaz y navegaci\u00F3n optimizadas para Android TV y control remoto",
      "\uD83D\uDDA5\uFE0F  Modo PC \u2014 Comportamiento y User-Agent de escritorio para versiones completas"
    )

    AlertDialog.Builder(this)
      .setTitle("Selecciona el modo de navegaci\u00F3n de Bat TV")
      .setCancelable(false)
      .setItems(options) { d, which ->
        val chooseDesktop = (which == 1)
        setBrowserDisplayMode(chooseDesktop)
        prefs.edit().putBoolean(KEY_MODE_SELECTED, true).apply()
        d.dismiss()
      }
      .show()
  }

  private fun setBrowserDisplayMode(desktopMode: Boolean) {
    isDesktopMode = desktopMode
    prefs.edit().putBoolean(KEY_DESKTOP_MODE, isDesktopMode).apply()
    geckoManager.applyDisplayMode(
      isDesktopMode = isDesktopMode,
      reloadIfActive = !isHomeScreenVisible,
      currentUrl = currentUrl
    )
    if (isHomeScreenVisible) {
      renderHomeScreenContent()
    }
    val modeName = if (isDesktopMode) "\uD83D\uDDA5\uFE0F Modo PC activado" else "\uD83D\uDCFA Modo TV activado"
    Toast.makeText(this, modeName, Toast.LENGTH_SHORT).show()
  }

  private fun showMainMenuDialog() {
    stopSmoothDpadMovement(immediate = true)
    val items = mutableListOf<String>()
    val actions = mutableListOf<() -> Unit>()

    items.add("\uD83C\uDFE0  Inicio (Pantalla de Favoritos)")
    actions.add { showHomeScreen() }

    if (!isHomeScreenVisible && !isFullScreenVideo && !isTheaterModeActive) {
      items.add("\uD83C\uDFAC  Abrir v\u00EDdeo actual en pantalla completa")
      actions.add { enterVideoFullscreenAutomatically() }
    }

    if (!isHomeScreenVisible && canGoBackState) {
      items.add("\u25C4  P\u00E1gina anterior (Atr\u00E1s)")
      actions.add {
        sendVideoCommandToWeb("STOP_ALL_MEDIA", 0.0)
        geckoSession?.goBack()
        activateCursorMode(showPointerNow = false)
      }
    }
    if (!isHomeScreenVisible && canGoForwardState) {
      items.add("\u25BA  P\u00E1gina siguiente (Adelante)")
      actions.add {
        sendVideoCommandToWeb("STOP_ALL_MEDIA", 0.0)
        geckoSession?.goForward()
        activateCursorMode(showPointerNow = false)
      }
    }

    val currentModeLabel = if (isDesktopMode) "Modo PC \uD83D\uDDA5\uFE0F" else "Modo TV \uD83D\uDCFA"
    val switchTargetLabel = if (isDesktopMode) "Cambiar a Modo TV \uD83D\uDCFA" else "Cambiar a Modo PC \uD83D\uDDA5\uFE0F"
    items.add("\uD83D\uDD04  $switchTargetLabel (Actual: $currentModeLabel)")
    actions.add { setBrowserDisplayMode(!isDesktopMode) }

    if (!isHomeScreenVisible) {
      items.add("\u2328\uFE0F  Escribir en campo de texto de la p\u00E1gina actual")
      actions.add { requestActiveWebInputFromPage() }

      items.add("\u25C4 Ant  Ir al campo anterior del formulario")
      actions.add { navigateAdjacentWebFormField(-1) }

      items.add("Sig \u25BA  Ir al campo siguiente del formulario")
      actions.add { navigateAdjacentWebFormField(1) }

      items.add("\u2B50  Agregar p\u00E1gina actual a Favoritos")
      actions.add {
        addBookmark(
          title = currentTitle.ifBlank { currentUrl },
          url = currentUrl
        )
      }
    }

    items.add("\uD83D\uDEE1\uFE0F  Estado del Bloqueador Nativo (Anti-Ads, Anti-Popups y Acortadores)")
    actions.add { showExtensionsStatusDialog() }

    items.add("\uD83E\uDDF9  Limpiar cach\u00E9 y archivos temporales")
    actions.add {
      geckoManager.clearSafeCaches { freedBytes ->
        val freedMb = String.format("%.1f", freedBytes / (1024.0 * 1024.0))
        runOnUiThread {
          Toast.makeText(
            this,
            "\uD83E\uDDF9 Cach\u00E9 temporal liberada ($freedMb MB). Favoritos, historial y ajustes conservados.",
            Toast.LENGTH_LONG
          ).show()
        }
      }
    }

    items.add("\u2716  Salir de Bat TV")
    actions.add { exitBrowserCleanly() }

    AlertDialog.Builder(this)
      .setTitle("\u2630 Men\u00FA de Bat TV")
      .setItems(items.toTypedArray()) { _, which -> actions[which].invoke() }
      .setNegativeButton("Cerrar") { d, _ -> d.dismiss() }
      .show()
  }

  private fun showExtensionsStatusDialog() {
    val shieldLoaded = if (geckoManager.isExtensionLoaded(BatTvGeckoViewManager.BATTV_SHIELD_ID)) "ACTIVO" else "ACTIVO (Nativo)"

    val extItems = arrayOf(
      "1. Pop-ups Blocker (strict) + Cerrojo Anti-Popunders \u2014 [$shieldLoaded]",
      "2. Cortafuegos 0ms Anti-Ads y Saltador Pre-Roll VAST \u2014 [$shieldLoaded]",
      "3. Motor Directos 1080p Hardware + Acortadores \u2014 [$shieldLoaded]"
    )

    AlertDialog.Builder(this)
      .setTitle("\uD83D\uDEE1\uFE0F Protecci\u00F3n Nativa Ultraligera Activa")
      .setItems(extItems) { _, _ ->
        Toast.makeText(
          this,
          "El escudo nativo v44 est\u00E1 activo y optimizado para Google TV.",
          Toast.LENGTH_SHORT
        ).show()
      }
      .setNegativeButton("Volver") { d, _ -> d.dismiss() }
      .show()
  }

  private fun getBookmarksArray(): JSONArray {
    val raw = prefs.getString(KEY_BOOKMARKS, "[]") ?: "[]"
    return try {
      JSONArray(raw)
    } catch (_: Exception) {
      JSONArray()
    }
  }

  private fun saveBookmarksArray(arr: JSONArray) {
    prefs.edit().putString(KEY_BOOKMARKS, arr.toString()).apply()
    if (isHomeScreenVisible) {
      renderHomeScreenContent()
    }
  }

  private fun addBookmark(title: String, url: String) {
    val resolvedUrl = geckoManager.resolveInputToUrl(url)
    val cleanTitle = title.trim().ifBlank { resolvedUrl }
    val arr = getBookmarksArray()

    for (i in 0 until arr.length()) {
      val existing = arr.optJSONObject(i) ?: continue
      if (existing.optString("url").equals(resolvedUrl, ignoreCase = true)) {
        existing.put("title", cleanTitle)
        saveBookmarksArray(arr)
        Toast.makeText(this, "Favorito actualizado: $cleanTitle", Toast.LENGTH_SHORT).show()
        return
      }
    }

    arr.put(JSONObject().put("title", cleanTitle).put("url", resolvedUrl))
    saveBookmarksArray(arr)
    Toast.makeText(this, "\u2B50 Agregado a Favoritos: $cleanTitle", Toast.LENGTH_SHORT).show()
  }

  private fun deleteBookmarkAt(index: Int) {
    val arr = getBookmarksArray()
    val newArr = JSONArray()
    for (i in 0 until arr.length()) {
      if (i != index) {
        newArr.put(arr.optJSONObject(i))
      }
    }
    saveBookmarksArray(newArr)
    Toast.makeText(this, "Favorito eliminado", Toast.LENGTH_SHORT).show()
  }

  private fun showAddBookmarkDialog(prefillTitle: String = "", prefillUrl: String = "") {
    val density = resources.displayMetrics.density
    fun dp(value: Int): Int = (value * density).toInt()

    val container = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(dp(20), dp(14), dp(20), dp(8))
    }

    val titleInput = EditText(this).apply {
      hint = "Nombre del favorito (ej. Doramasflix, JKAnime...)"
      setText(prefillTitle)
      isSingleLine = true
    }

    val urlInput = EditText(this).apply {
      hint = "Direcci\u00F3n web (ej. https://doramasflix.co)"
      setText(prefillUrl)
      isSingleLine = true
      inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
    }

    container.addView(titleInput)
    container.addView(urlInput)

    AlertDialog.Builder(this)
      .setTitle("\u2795 Agregar favorito")
      .setView(container)
      .setPositiveButton("Guardar") { d, _ ->
        val rawUrl = urlInput.text?.toString()?.trim() ?: ""
        val rawTitle = titleInput.text?.toString()?.trim() ?: ""
        if (rawUrl.isNotEmpty()) {
          addBookmark(rawTitle.ifBlank { rawUrl }, rawUrl)
        } else {
          Toast.makeText(this, "Escribe una direcci\u00F3n web v\u00E1lida", Toast.LENGTH_SHORT).show()
        }
        d.dismiss()
      }
      .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
      .show()
  }

  private fun showBookmarksDialog() {
    stopSmoothDpadMovement(immediate = true)
    val arr = getBookmarksArray()
    val items = mutableListOf<String>()
    val actions = mutableListOf<() -> Unit>()

    items.add("\u2795 Agregar favorito")
    actions.add {
      val defaultTitle = if (!isHomeScreenVisible && currentUrl.startsWith("http")) currentTitle.ifBlank { currentUrl } else ""
      val defaultUrl = if (!isHomeScreenVisible && currentUrl.startsWith("http")) currentUrl else ""
      showAddBookmarkDialog(prefillTitle = defaultTitle, prefillUrl = defaultUrl)
    }

    if (arr.length() > 0) {
      items.add("\uD83D\uDDD1\uFE0F Eliminar favorito")
      actions.add { showDeleteBookmarkDialog(arr) }
    }

    for (i in 0 until arr.length()) {
      val obj = arr.optJSONObject(i) ?: continue
      val t = obj.optString("title", "Sitio")
      val u = obj.optString("url", "")
      items.add("\u2B50 $t  \u2014  $u")
      actions.add { loadUrlOrSearch(u) }
    }

    AlertDialog.Builder(this)
      .setTitle("\u2B50 Favoritos")
      .setItems(items.toTypedArray()) { _, which -> actions[which].invoke() }
      .setNegativeButton("Cerrar") { d, _ -> d.dismiss() }
      .show()
  }

  private fun showDeleteBookmarkDialog(arr: JSONArray) {
    val delItems = mutableListOf<String>()
    for (i in 0 until arr.length()) {
      val obj = arr.optJSONObject(i) ?: continue
      delItems.add("\u274C Eliminar: ${obj.optString("title")} (${obj.optString("url")})")
    }
    AlertDialog.Builder(this)
      .setTitle("\uD83D\uDDD1\uFE0F Selecciona el favorito a eliminar")
      .setItems(delItems.toTypedArray()) { _, which -> deleteBookmarkAt(which) }
      .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
      .show()
  }

  private fun formatContentTitleForHistory(rawTitle: String, url: String, extractedHeading: String): String {
    val baseCandidate = when {
      extractedHeading.isNotBlank() && extractedHeading.length >= 3 -> extractedHeading
      rawTitle.isNotBlank() -> rawTitle
      else -> url
    }

    var cleaned = baseCandidate
      .replace(Regex("(?i)\\s*[-|\\u2022\\u2013]\\s*(ver online|sub espa\\u00F1ol|espa\\u00F1ol|latino|gratis|hd|doramasflix|jkanime|dramachino|hdfull).*$"), "")
      .replace(Regex("(?i)^ver\\s+"), "")
      .trim()

    val combinedSource = "$cleaned $rawTitle ${Uri.decode(url)}"

    val seasonEpRegex1 = Regex("(?i)(?:temporada|temp\\.?|season|s)\\s*(\\d{1,2})\\s*[-\\u2022:/]?\\s*(?:episodio|ep\\.?|capitulo|cap\\u00EDtulo|cap\\.?)\\s*(\\d{1,4})")
    val seasonEpRegex2 = Regex("(?i)\\b(\\d{1,2})x(\\d{1,4})\\b")
    val seasonEpRegex3 = Regex("(?i)\\bS(\\d{1,2})E(\\d{1,4})\\b")
    val epOnlyRegex = Regex("(?i)(?:episodio|ep\\.?|capitulo|cap\\u00EDtulo|cap\\.?)\\s*(\\d{1,4})")

    val m1 = seasonEpRegex1.find(combinedSource)
    val m2 = seasonEpRegex2.find(combinedSource)
    val m3 = seasonEpRegex3.find(combinedSource)
    val mEp = epOnlyRegex.find(combinedSource)

    var seasonNum: String? = null
    var episodeNum: String? = null

    when {
      m1 != null -> {
        seasonNum = m1.groupValues[1]
        episodeNum = m1.groupValues[2]
      }
      m2 != null -> {
        seasonNum = m2.groupValues[1]
        episodeNum = m2.groupValues[2]
      }
      m3 != null -> {
        seasonNum = m3.groupValues[1]
        episodeNum = m3.groupValues[2]
      }
      mEp != null -> {
        episodeNum = mEp.groupValues[1]
      }
    }

    if (episodeNum != null) {
      val seriesName = cleaned
        .replace(seasonEpRegex1, "")
        .replace(seasonEpRegex2, "")
        .replace(seasonEpRegex3, "")
        .replace(epOnlyRegex, "")
        .replace(Regex("(?i)\\b(sub\\s*espa\\u00F1ol|espa\\u00F1ol|latino|online|hd)\\b"), "")
        .trim(' ', '-', '\u2022', ':', '|', '\u2013')
        .ifBlank { extractShortHost(url) }

      return if (!seasonNum.isNullOrBlank()) {
        "${seriesName.take(42)} \u2022 Temp. $seasonNum - Ep. $episodeNum"
      } else {
        "${seriesName.take(45)} \u2022 Episodio $episodeNum"
      }
    }

    if (cleaned.isBlank() || cleaned.startsWith("http", ignoreCase = true)) {
      cleaned = extractShortHost(url)
    }

    return if (cleaned.length > 56) "${cleaned.take(54)}\u2026" else cleaned
  }

  private fun extractShortHost(url: String): String {
    return try {
      Uri.parse(url).host?.removePrefix("www.") ?: url.take(40)
    } catch (_: Exception) {
      url.take(40)
    }
  }

  private fun saveHistoryEntry(rawTitle: String, url: String, extractedHeading: String = "") {
    if (!url.startsWith("http", ignoreCase = true)) return
    val formattedTitle = formatContentTitleForHistory(rawTitle, url, extractedHeading)
    if (url == lastSavedHistoryUrl && formattedTitle == lastSavedHistoryTitle) return
    lastSavedHistoryUrl = url
    lastSavedHistoryTitle = formattedTitle

    val raw = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
    val oldArr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
    val newArr = JSONArray()

    newArr.put(
      JSONObject()
        .put("title", formattedTitle)
        .put("url", url)
    )

    for (i in 0 until min(oldArr.length(), 30)) {
      val item = oldArr.optJSONObject(i) ?: continue
      if (!item.optString("url").equals(url, ignoreCase = true)) {
        newArr.put(item)
      }
    }
    prefs.edit().putString(KEY_HISTORY, newArr.toString()).apply()
  }

  private fun showHistoryDialog() {
    stopSmoothDpadMovement(immediate = true)
    val raw = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
    val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }

    val items = mutableListOf<String>()
    val actions = mutableListOf<() -> Unit>()

    if (arr.length() == 0) {
      items.add("A\u00FAn no hay p\u00E1ginas registradas en el historial")
      actions.add {}
    } else {
      items.add("\uD83D\uDDD1\uFE0F Borrar todo el historial")
      actions.add {
        lastSavedHistoryUrl = ""
        lastSavedHistoryTitle = ""
        prefs.edit().remove(KEY_HISTORY).apply()
        Toast.makeText(this, "Historial borrado", Toast.LENGTH_SHORT).show()
      }

      items.add("\u274C Eliminar una entrada espec\u00EDfica del historial...")
      actions.add { showDeleteHistoryItemDialog(arr) }

      for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        val u = obj.optString("url", "")
        val t = obj.optString("title", "").ifBlank { formatContentTitleForHistory("", u, "") }
        val host = extractShortHost(u)
        items.add("\uD83D\uDD52 $t  ($host)")
        actions.add { loadUrlOrSearch(u) }
      }
    }

    AlertDialog.Builder(this)
      .setTitle("\uD83D\uDD52 Historial de Navegaci\u00F3n")
      .setItems(items.toTypedArray()) { _, which -> actions[which].invoke() }
      .setNegativeButton("Cerrar") { d, _ -> d.dismiss() }
      .show()
  }

  private fun showDeleteHistoryItemDialog(arr: JSONArray) {
    val delItems = mutableListOf<String>()
    for (i in 0 until arr.length()) {
      val obj = arr.optJSONObject(i) ?: continue
      val t = obj.optString("title", obj.optString("url"))
      delItems.add("\u274C Borrar: $t")
    }
    AlertDialog.Builder(this)
      .setTitle("\uD83D\uDDD1\uFE0F Selecciona la entrada a borrar")
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

  private fun isSameSiteOrSearchEngineTarget(currentPageUrl: String, targetUrl: String): Boolean {
    if (currentPageUrl.isBlank() || !currentPageUrl.startsWith("http", ignoreCase = true)) return true
    val curHost = runCatching { Uri.parse(currentPageUrl).host?.lowercase()?.removePrefix("www.") ?: "" }.getOrDefault("")
    val tarHost = runCatching { Uri.parse(targetUrl).host?.lowercase()?.removePrefix("www.") ?: "" }.getOrDefault("")
    if (curHost.isEmpty() || tarHost.isEmpty()) return true
    if (curHost == tarHost || tarHost.endsWith(".$curHost") || curHost.endsWith(".$tarHost")) return true
    if (curHost.contains("google.") || curHost.contains("bing.com") || curHost.contains("duckduckgo.com")) return true
    val curParts = curHost.split(".")
    val tarParts = tarHost.split(".")
    if (curParts.size >= 2 && tarParts.size >= 2) {
      val curBrand = curParts[curParts.size - 2]
      val tarBrand = tarParts[tarParts.size - 2]
      if (curBrand.length >= 4 && curBrand == tarBrand) return true
    }
    return false
  }

  private fun attachSessionDelegates(session: GeckoSession) {
    session.navigationDelegate = object : GeckoSession.NavigationDelegate {
      override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) {
        canGoBackState = canGoBack
      }

      override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) {
        canGoForwardState = canGoForward
      }

      override fun onNewSession(session: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
        return GeckoResult.fromValue(null)
      }

      override fun onLoadRequest(
        session: GeckoSession,
        request: GeckoSession.NavigationDelegate.LoadRequest
      ): GeckoResult<AllowOrDeny>? {
        val uri = request.uri
        val parsed = runCatching { Uri.parse(uri) }.getOrNull()
        val scheme = parsed?.scheme?.lowercase() ?: ""

        if (scheme == "moz-extension" || scheme == "about" || scheme == "resource" || scheme == "blob" || scheme == "data") {
          return GeckoResult.allow()
        }

        if (scheme != "http" && scheme != "https") {
          return GeckoResult.deny()
        }

        if (geckoManager.isKnownAdOrPopupUri(uri)) {
          return GeckoResult.deny()
        }

        if (request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) {
          if (request.hasUserGesture && isSameSiteOrSearchEngineTarget(currentUrl, uri)) {
            session.loadUri(uri)
          }
          return GeckoResult.deny()
        }

        return GeckoResult.allow()
      }

      override fun onSubframeLoadRequest(
        session: GeckoSession,
        request: GeckoSession.NavigationDelegate.LoadRequest
      ): GeckoResult<AllowOrDeny>? {
        val uri = request.uri
        if (geckoManager.isKnownAdOrPopupUri(uri)) {
          return GeckoResult.deny()
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
          if (!isHomeScreenVisible && geckoManager.isKnownAdOrPopupUri(url)) {
            return
          }
          val changedPage = url.substringBefore('#') != currentUrl.substringBefore('#')
          if (changedPage) {
            dismissFullscreenPromptDialog()
            fullscreenPromptDismissedForPage = false
            lastDismissedPlayerKey = ""
            lastPromptedPlayerKey = ""
            lastTopNavChangeUptimeMs = SystemClock.uptimeMillis()
            hasActiveVodVideo = false
            isLiveStreamVideo = false
            vodIsPlaying = false
            vodDurationSec = 0.0
            videoSeekOverlay.hideOverlayImmediate()
            if (isFullScreenVideo || isTheaterModeActive) {
              sendVideoCommandToWeb("EXIT_FULLSCREEN", 0.0)
              try { geckoSession?.exitFullScreen() } catch (_: Exception) {}
              applyFullScreenState(false)
            }
          }
          currentUrl = url
          if (!isHomeScreenVisible && !isEditingUrlText && !isEditingWebInput) {
            suppressWebInputWatcher = true
            urlEditText.setText(url)
            suppressWebInputWatcher = false
          }
        }
      }
    }

    session.progressDelegate = object : GeckoSession.ProgressDelegate {
      override fun onPageStart(session: GeckoSession, url: String) {
        if (!isHomeScreenVisible && geckoManager.isKnownAdOrPopupUri(url)) {
          session.stop()
          return
        }
        if (url != currentUrl) {
          if (isFullScreenVideo || isTheaterModeActive) {
            sendVideoCommandToWeb("EXIT_FULLSCREEN", 0.0)
            try { geckoSession?.exitFullScreen() } catch (_: Exception) {}
            applyFullScreenState(false)
          }
          dismissFullscreenPromptDialog()
          fullscreenPromptDismissedForPage = false
          lastDismissedPlayerKey = ""
          lastPromptedPlayerKey = ""
          lastTopNavChangeUptimeMs = SystemClock.uptimeMillis()
          latestExtractedHeading = ""
          hasActiveVodVideo = false
          isLiveStreamVideo = false
          vodIsPlaying = false
          vodDurationSec = 0.0
          videoSeekOverlay.hideOverlayImmediate()
          if (isEditingWebInput) {
            closeNativeWebInputBridge(submit = false)
          }
        }
        currentUrl = url
        if (!isHomeScreenVisible && !isEditingUrlText && !isEditingWebInput) {
          suppressWebInputWatcher = true
          urlEditText.setText(url)
          suppressWebInputWatcher = false
        }
        if (!isFullScreenVideo && !isTheaterModeActive && !isHomeScreenVisible) {
          loadingProgressBar.visibility = View.VISIBLE
          loadingProgressBar.progress = 20
        }
      }

      override fun onProgressChange(session: GeckoSession, progress: Int) {
        loadingProgressBar.progress = progress
        if (progress >= 100 || isFullScreenVideo || isTheaterModeActive || isHomeScreenVisible) {
          loadingProgressBar.visibility = View.GONE
        }
      }

      override fun onPageStop(session: GeckoSession, success: Boolean) {
        loadingProgressBar.visibility = View.GONE
        if (success && !isHomeScreenVisible && currentUrl.startsWith("http")) {
          saveHistoryEntry(currentTitle, currentUrl, latestExtractedHeading)
        }
      }
    }

    session.contentDelegate = object : GeckoSession.ContentDelegate {
      override fun onTitleChange(session: GeckoSession, title: String?) {
        if (!title.isNullOrBlank()) {
          currentTitle = title
          if (!isHomeScreenVisible && currentUrl.startsWith("http")) {
            saveHistoryEntry(currentTitle, currentUrl, latestExtractedHeading)
          }
        }
      }

      override fun onCloseRequest(session: GeckoSession) {
        if (isFullScreenVideo || isTheaterModeActive) {
          applyFullScreenState(false)
        }
      }

      override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
        runOnUiThread {
          mainHandler.removeCallbacks(fullScreenExitWatchdogRunnable)
          applyFullScreenState(fullScreen)
          if (!fullScreen) {
            sendVideoCommandToWeb("EXIT_FULLSCREEN", 0.0)
          }
        }
      }

      override fun onCrash(session: GeckoSession) {
        runOnUiThread {
          applyFullScreenState(false)
          try {
            session.open(geckoManager.runtime)
            if (currentUrl.startsWith("http")) {
              session.loadUri(currentUrl)
            }
          } catch (_: Exception) {}
        }
      }

      override fun onKill(session: GeckoSession) {
        runOnUiThread {
          applyFullScreenState(false)
          try {
            session.open(geckoManager.runtime)
            if (currentUrl.startsWith("http")) {
              session.loadUri(currentUrl)
            }
          } catch (_: Exception) {}
        }
      }
    }

    session.mediaSessionDelegate = object : MediaSession.Delegate {
      override fun onActivated(session: GeckoSession, mediaSession: MediaSession) {
        activeMediaSession = mediaSession
      }

      override fun onDeactivated(session: GeckoSession, mediaSession: MediaSession) {
        if (activeMediaSession === mediaSession) {
          activeMediaSession = null
        }
      }

      override fun onPlay(session: GeckoSession, mediaSession: MediaSession) {
        activeMediaSession = mediaSession
        runOnUiThread {
          vodIsPlaying = true
          loadingProgressBar.visibility = View.GONE
          val recentlyUsedCursor = SystemClock.uptimeMillis() - lastCursorInteractionUptimeMs < CURSOR_AUTO_HIDE_MS
          if (!isSmoothDpadLoopRunning && !recentlyUsedCursor) {
            mainHandler.removeCallbacks(hideCursorRunnable)
            mainHandler.postDelayed(hideCursorRunnable, 1200L)
          }
        }
      }

      override fun onPause(session: GeckoSession, mediaSession: MediaSession) {
        runOnUiThread { vodIsPlaying = false }
      }

      override fun onStop(session: GeckoSession, mediaSession: MediaSession) {
        runOnUiThread { vodIsPlaying = false }
      }

      override fun onPositionState(
        session: GeckoSession,
        mediaSession: MediaSession,
        state: MediaSession.PositionState
      ) {
        runOnUiThread {
          if (state.duration >= 20.0 && state.duration < 36000.0) {
            hasActiveVodVideo = true
            isLiveStreamVideo = false
            vodDurationSec = state.duration
          }
          if (!isUserSeekingVod && state.position >= 0.0) {
            vodCurrentTimeSec = state.position
            if (isFullScreenVideo && videoSeekOverlay.isOverlayVisible()) {
              videoSeekOverlay.updateProgressOnly(vodCurrentTimeSec, vodDurationSec, vodIsPlaying)
            }
          }
        }
      }

      override fun onFullscreen(
        session: GeckoSession,
        mediaSession: MediaSession,
        enabled: Boolean,
        meta: MediaSession.ElementMetadata?
      ) {
        runOnUiThread {
          mainHandler.removeCallbacks(fullScreenExitWatchdogRunnable)
          applyFullScreenState(enabled)
          if (!enabled) {
            sendVideoCommandToWeb("EXIT_FULLSCREEN", 0.0)
          }
        }
      }
    }

    session.textInput.setDelegate(object : GeckoSession.TextInputDelegate {
      override fun showSoftInput(session: GeckoSession) {
        runOnUiThread {
          if (SystemClock.uptimeMillis() - lastUserWebClickTime < 4000L && !isEditingWebInput) {
            requestActiveWebInputFromPage()
          }
        }
      }

      override fun restartInput(session: GeckoSession, reason: Int) {}
      override fun hideSoftInput(session: GeckoSession) {}
    })
  }

  private fun applyFullScreenState(fullScreen: Boolean, theaterOnly: Boolean = false) {
    stopSmoothDpadMovement(immediate = true)
    mainHandler.removeCallbacks(autoHideTopBarRunnable)

    if (fullScreen) {
      dismissFullscreenPromptDialog()
      if (isEditingWebInput || isEditingUrlText) {
        closeNativeWebInputBridge(submit = false)
      }
      homeOverlayLayout.visibility = View.GONE
      loadingProgressBar.visibility = View.GONE
      setTopBarOverlayVisible(false, animate = false)
      applyImmersiveSystemUi(true)

      isCursorModeActive = true
      if (theaterOnly) {
        isFullScreenVideo = false
        isTheaterModeActive = true
        cursorOverlay.showCursorAndScheduleFade()
        scheduleCursorAutoHide()
      } else {
        isFullScreenVideo = true
        isTheaterModeActive = false
        cursorOverlay.hideCursorUntilMoved()
      }
      geckoView.requestFocus()
    } else {
      isFullScreenVideo = false
      isTheaterModeActive = false
      lastFullscreenExitUptimeMs = SystemClock.uptimeMillis()
      mainHandler.removeCallbacks(hideVideoSeekRunnable)
      isUserSeekingVod = false
      consecutiveSeekPresses = 0
      cumulativeSeekDeltaSec = 0.0
      videoSeekOverlay.hideOverlayImmediate()

      applyImmersiveSystemUi(false)
      if (isHomeScreenVisible) {
        homeOverlayLayout.visibility = View.VISIBLE
        setTopBarOverlayVisible(true, animate = false)
      } else {
        setTopBarOverlayVisible(false, animate = false)
        activateCursorMode(showPointerNow = true)
      }
    }
  }

  private fun exitFullScreenSafely() {
    fullscreenPromptDismissedForPage = true
    lastDismissedPlayerKey = lastPromptedPlayerKey
    lastFullscreenExitUptimeMs = SystemClock.uptimeMillis()
    try {
      geckoSession?.exitFullScreen()
    } catch (_: Exception) {}
    sendVideoCommandToWeb("EXIT_FULLSCREEN", 0.0)
    mainHandler.removeCallbacks(fullScreenExitWatchdogRunnable)
    mainHandler.postDelayed(fullScreenExitWatchdogRunnable, 250L)
    applyFullScreenState(false)
  }

  @Suppress("DEPRECATION")
  private fun applyImmersiveSystemUi(fullScreen: Boolean) {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val controller = window.insetsController
        if (controller != null) {
          if (fullScreen) {
            controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            controller.systemBarsBehavior =
              WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
          } else {
            controller.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
          }
        }
      } else {
        if (fullScreen) {
          window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
              or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
              or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
              or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
              or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
              or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
        } else {
          window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
      }
    } catch (_: Exception) {}
  }

  private fun createBridgeMessageDelegate(): WebExtension.MessageDelegate {
    return object : WebExtension.MessageDelegate {
      override fun onConnect(port: WebExtension.Port) {
        activeWebPorts.add(port)
        port.setDelegate(object : WebExtension.PortDelegate {
          override fun onPortMessage(message: Any, port: WebExtension.Port) {
            val json = message as? JSONObject ?: return
            when (json.optString("type")) {
              "WEB_INPUT_FOCUS" -> {
                val inpId = json.optString("inputId", "")
                val initVal = json.optString("value", "")
                val hint = json.optString("placeholder", "")
                val isMulti = json.optBoolean("isMultiField", false)
                runOnUiThread {
                  if (isEditingWebInput || SystemClock.uptimeMillis() - lastUserWebClickTime < 4000L) {
                    openNativeWebInputBridge(inpId, initVal, hint, isMulti)
                  }
                }
              }
              "VIDEO_PLAY_STARTED_PROMPT_FS" -> {
                val dur = json.optDouble("duration", 0.0)
                val isLive = json.optBoolean("isLive", false)
                val pKey = json.optString("playerKey", "")
                runOnUiThread {
                  isLiveStreamVideo = isLive
                  hasActiveVodVideo = !isLive && dur >= 20.0
                  if (dur > 0.0) vodDurationSec = dur
                  loadingProgressBar.visibility = View.GONE
                  if (isCursorModeActive && !isHomeScreenVisible) {
                    setTopBarOverlayVisible(false, animate = true)
                  }
                  if (isLive || dur >= 20.0) {
                    promptAutoFullscreenIfEligible(pKey)
                  }
                }
              }
              "REQUEST_UNIFIED_FULLSCREEN" -> {
                val enable = json.optBoolean("enable", true)
                val fromUserClick = json.optBoolean("fromUserClick", false)
                runOnUiThread {
                  if (enable) {
                    applyFullScreenState(true)
                    if (!fromUserClick) {
                      sendVideoCommandToWeb("ENTER_FULLSCREEN", 0.0)
                    } else {
                      sendVideoCommandToWeb("EXPAND_PLAYER_CSS_ONLY", 0.0)
                    }
                  } else {
                    exitFullScreenSafely()
                  }
                }
              }
              "EXPAND_PLAYER_IFRAME_FALLBACK" -> {
                runOnUiThread {
                  applyFullScreenState(true)
                  sendVideoCommandToWeb("EXPAND_PLAYER_IFRAME_FALLBACK", 0.0)
                }
              }
              "WEB_FULLSCREEN_STATE" -> {
                val enabled = json.optBoolean("enabled", false)
                val theaterOnly = json.optBoolean("theaterOnly", false)
                runOnUiThread {
                  if (enabled != isFullScreenVideo || (enabled && theaterOnly != isTheaterModeActive) || (!enabled && isTheaterModeActive)) {
                    applyFullScreenState(enabled, theaterOnly)
                  }
                }
              }
              "VIDEO_STATE" -> {
                val hasVod = json.optBoolean("hasVod", false)
                val isLive = json.optBoolean("isLive", false)
                val playing = json.optBoolean("playing", false)
                val curTime = json.optDouble("currentTime", 0.0)
                val dur = json.optDouble("duration", 0.0)
                runOnUiThread {
                  val startedPlayingNow = !vodIsPlaying && playing
                  isLiveStreamVideo = isLive
                  hasActiveVodVideo = !isLive && hasVod && dur >= 20.0
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
                  if (startedPlayingNow && (hasActiveVodVideo || isLiveStreamVideo)) {
                    loadingProgressBar.visibility = View.GONE
                    if (isCursorModeActive && !isHomeScreenVisible && !isEditingUrlText && !isEditingWebInput) {
                      setTopBarOverlayVisible(false, animate = true)
                    }
                    val recentlyUsedCursor = SystemClock.uptimeMillis() - lastCursorInteractionUptimeMs < CURSOR_AUTO_HIDE_MS
                    if (!isSmoothDpadLoopRunning && !recentlyUsedCursor) {
                      mainHandler.removeCallbacks(hideCursorRunnable)
                      mainHandler.postDelayed(hideCursorRunnable, 900L)
                    }
                  }
                }
              }
              "PAGE_MEDIA_METADATA" -> {
                val heading = json.optString("rawHeading", "")
                val pageTitle = json.optString("pageTitle", "")
                val pageUrl = json.optString("url", currentUrl)
                runOnUiThread {
                  if (heading.isNotBlank()) {
                    latestExtractedHeading = heading
                  }
                  if (pageTitle.isNotBlank()) {
                    currentTitle = pageTitle
                  }
                  if (!isHomeScreenVisible && pageUrl.startsWith("http")) {
                    saveHistoryEntry(currentTitle, pageUrl, latestExtractedHeading)
                  }
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
    mainHandler.postDelayed(activateNativeWebInputRunnable, 50L)
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
    if (isEditingWebInput) {
      val curText = urlEditText.text?.toString() ?: ""
      syncWebInputTextToPage(curText, submit = false)
    }
    val msg = JSONObject().apply {
      put("type", "WEB_FORM_NAVIGATE")
      put("direction", direction)
      put("inputId", activeWebInputId)
    }
    broadcastToWebPorts(msg)
  }

  private fun requestActiveWebInputFromPage() {
    val msg = JSONObject().apply {
      put("type", "REQUEST_ACTIVE_WEB_INPUT")
    }
    broadcastToWebPorts(msg)
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
    broadcastToWebPorts(msg)
  }

  private fun syncWebInputTextToPage(text: String, submit: Boolean) {
    val msg = JSONObject().apply {
      put("type", "WEB_INPUT_UPDATE")
      put("inputId", activeWebInputId)
      put("value", text)
      put("submit", submit)
    }
    broadcastToWebPorts(msg)
  }

  private fun sendVideoCommandToWeb(action: String, targetTimeSec: Double = 0.0) {
    val msg = JSONObject().apply {
      put("type", "VIDEO_COMMAND")
      put("action", action)
      put("time", targetTimeSec)
    }
    broadcastToWebPorts(msg)
  }

  private fun broadcastToWebPorts(msg: JSONObject) {
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

  private fun startEditingUrlText(
    forWebInput: Boolean = false,
    initialValue: String? = null,
    placeholderHint: String? = null
  ) {
    stopSmoothDpadMovement(immediate = true)
    mainHandler.removeCallbacks(autoHideTopBarRunnable)
    setTopBarOverlayVisible(true, animate = true)
    isCursorModeActive = false
    isHomeToolbarFocused = true
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
      urlEditText.imeOptions = (
        if (isCurrentWebInputMultiField) EditorInfo.IME_ACTION_NEXT
        else EditorInfo.IME_ACTION_SEARCH
        ) or EditorInfo.IME_FLAG_NO_EXTRACT_UI
      urlEditText.hint = if (!placeholderHint.isNullOrBlank()) {
        "Campo web: $placeholderHint"
      } else {
        "Escribiendo en formulario de la p\u00E1gina (Atr\u00E1s conserva el texto)..."
      }
      if (initialValue != null) {
        suppressWebInputWatcher = true
        urlEditText.setText(initialValue)
        urlEditText.setSelection(initialValue.length)
        suppressWebInputWatcher = false
      }
    } else {
      urlEditText.imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
      urlEditText.hint = "Escribir direcci\u00F3n web o realizar b\u00FAsqueda (Pulsa OK)..."
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

  private fun closeNativeWebInputBridge(submit: Boolean) {
    mainHandler.removeCallbacks(activateNativeWebInputRunnable)
    if (submit) {
      val finalQuery = urlEditText.text?.toString() ?: ""
      syncWebInputTextToPage(finalQuery, submit = true)
    } else {
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
    urlEditText.imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
    urlEditText.hint = "Escribir direcci\u00F3n web o realizar b\u00FAsqueda (Pulsa OK)..."
    suppressWebInputWatcher = true
    urlEditText.setText(if (isHomeScreenVisible) "" else currentUrl)
    suppressWebInputWatcher = false
    hideSoftKeyboardFromView(urlEditText)
    urlEditText.clearFocus()
    urlEditText.isCursorVisible = false
    urlEditText.isFocusable = false
    urlEditText.isFocusableInTouchMode = false
    if (!isHomeScreenVisible) {
      activateCursorMode(showPointerNow = true)
    } else {
      updateToolbarSelectionVisuals()
    }
  }

  private fun stopEditingUrlTextAndStayOnBar() {
    if (isEditingWebInput) {
      closeNativeWebInputBridge(submit = false)
      return
    }
    isEditingUrlText = false
    hideSoftKeyboardFromView(urlEditText)
    urlEditText.clearFocus()
    urlEditText.isCursorVisible = false
    urlEditText.isFocusable = false
    urlEditText.isFocusableInTouchMode = false
    updateToolbarSelectionVisuals()
  }

  private fun submitUrlFromInput() {
    val rawInput = urlEditText.text?.toString()?.trim() ?: ""
    if (isEditingWebInput) {
      closeNativeWebInputBridge(submit = true)
      return
    }
    isEditingUrlText = false
    hideSoftKeyboardFromView(urlEditText)
    urlEditText.clearFocus()
    urlEditText.isCursorVisible = false
    urlEditText.isFocusable = false
    urlEditText.isFocusableInTouchMode = false

    if (rawInput.isNotEmpty()) {
      loadUrlOrSearch(rawInput)
    } else if (!isHomeScreenVisible) {
      activateCursorMode(showPointerNow = false)
    }
  }

  private fun loadUrlOrSearch(input: String) {
    val finalUri = geckoManager.resolveInputToUrl(input)
    stopSmoothDpadMovement(immediate = true)
    dismissFullscreenPromptDialog()
    if (isFullScreenVideo || isTheaterModeActive) {
      exitFullScreenSafely()
    }
    fullscreenPromptDismissedForPage = false
    lastDismissedPlayerKey = ""
    lastPromptedPlayerKey = ""
    lastTopNavChangeUptimeMs = SystemClock.uptimeMillis()
    hasActiveVodVideo = false
    isLiveStreamVideo = false
    vodIsPlaying = false
    vodDurationSec = 0.0
    isHomeScreenVisible = false
    isHomeToolbarFocused = false
    homeOverlayLayout.visibility = View.GONE

    currentUrl = finalUri
    suppressWebInputWatcher = true
    urlEditText.setText(finalUri)
    suppressWebInputWatcher = false

    geckoSession?.setActive(true)
    setTopBarOverlayVisible(true, animate = false)
    sendVideoCommandToWeb("STOP_ALL_MEDIA", 0.0)
    geckoSession?.stop()
    geckoSession?.loadUri(finalUri)
    activateCursorMode(showPointerNow = false)
  }

  private fun updateToolbarSelectionVisuals() {
    val barActive = (!isCursorModeActive && !isHomeScreenVisible) || (isHomeScreenVisible && isHomeToolbarFocused)
    for (i in toolbarItems.indices) {
      val view = toolbarItems[i]
      val selectedOnBar = barActive && (i == toolbarIndex)
      when (view) {
        is EditText -> {
          view.background = createInputBackground(
            selectedOnBar = selectedOnBar,
            editingText = selectedOnBar && isEditingUrlText
          )
        }
        is Button -> {
          val isAccent = (view === btnMenu)
          val isDanger = (view === btnCloseApp)
          view.background = createButtonBackground(
            focused = selectedOnBar,
            isAccent = isAccent,
            isDanger = isDanger
          )
        }
      }
    }
  }

  private fun scheduleCursorAutoHide() {
    mainHandler.removeCallbacks(hideCursorRunnable)
    mainHandler.postDelayed(hideCursorRunnable, CURSOR_AUTO_HIDE_MS)
  }

  private fun activateCursorMode(showPointerNow: Boolean = true) {
    if (isHomeScreenVisible) return
    mainHandler.removeCallbacks(activateNativeWebInputRunnable)
    isCursorModeActive = true
    isHomeToolbarFocused = false
    isEditingUrlText = false
    isEditingWebInput = false
    urlEditText.hint = "Escribir direcci\u00F3n web o realizar b\u00FAsqueda (Pulsa OK)..."
    hideSoftKeyboardFromView(urlEditText)
    urlEditText.clearFocus()
    urlEditText.isCursorVisible = false
    urlEditText.isFocusable = false
    urlEditText.isFocusableInTouchMode = false
    updateToolbarSelectionVisuals()

    if (showPointerNow) {
      lastCursorInteractionUptimeMs = SystemClock.uptimeMillis()
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
    if (vodIsPlaying || isFullScreenVideo || isTheaterModeActive) {
      setTopBarOverlayVisible(false, animate = true)
    } else {
      scheduleAutoHideTopBar(2400L)
    }
  }

  private fun activateToolbarMode() {
    stopSmoothDpadMovement(immediate = true)
    if (isFullScreenVideo || isTheaterModeActive) {
      exitFullScreenSafely()
    }
    if (isEditingWebInput) {
      closeNativeWebInputBridge(submit = false)
    }
    isCursorModeActive = false
    isHomeToolbarFocused = true
    isEditingUrlText = false
    isEditingWebInput = false
    toolbarIndex = URL_BAR_INDEX
    mainHandler.removeCallbacks(autoHideTopBarRunnable)
    setTopBarOverlayVisible(true, animate = true)
    homeOverlayLayout.clearFocus()
    urlEditText.clearFocus()
    urlEditText.isCursorVisible = false
    urlEditText.isFocusable = false
    urlEditText.isFocusableInTouchMode = false
    cursorOverlay.hideCursorUntilMoved()
    updateToolbarSelectionVisuals()
  }

  private fun startSmoothDpadMovement(keyCode: Int, isInitialTap: Boolean) {
    val now = SystemClock.uptimeMillis()
    lastDpadEventUptimeMs = now
    lastCursorInteractionUptimeMs = now
    isDpadPhysicallyHeld = true
    cursorOverlay.showCursorAndScheduleFade()
    scheduleCursorAutoHide()

    if (isInitialTap || activeDpadKeyCode != keyCode) {
      activeDpadKeyCode = keyCode
      dpadHoldStartUptimeMs = now
      remainingShortTapScrollPx = 0.0
      remainingShortTapCursorX = 0f
      remainingShortTapCursorY = 0f
    }

    if (!isSmoothDpadLoopRunning) {
      isSmoothDpadLoopRunning = true
      lastFrameTimeNanos = 0L
      Choreographer.getInstance().postFrameCallback(smoothDpadFrameCallback)
    }
  }

  private fun handleDpadKeyRelease(releasedKeyCode: Int) {
    val now = SystemClock.uptimeMillis()
    val heldDurationMs = (now - dpadHoldStartUptimeMs).coerceAtLeast(0L)
    lastCursorInteractionUptimeMs = now
    isDpadPhysicallyHeld = false
    activeDpadKeyCode = 0

    if (heldDurationMs < 195L) {
      when (releasedKeyCode) {
        KeyEvent.KEYCODE_DPAD_UP -> {
          remainingShortTapCursorY = -12f
          if (!isFullScreenVideo && cursorOverlay.cursorY <= EDGE_SCROLL_ZONE_PX) {
            remainingShortTapScrollPx = -SHORT_TAP_GLIDE_DISTANCE_PX
          }
        }
        KeyEvent.KEYCODE_DPAD_DOWN -> {
          remainingShortTapCursorY = 12f
          if (!isFullScreenVideo && cursorOverlay.cursorY >= (webContainer.height - EDGE_SCROLL_ZONE_PX)) {
            remainingShortTapScrollPx = SHORT_TAP_GLIDE_DISTANCE_PX
          }
        }
        KeyEvent.KEYCODE_DPAD_LEFT -> remainingShortTapCursorX = -12f
        KeyEvent.KEYCODE_DPAD_RIGHT -> remainingShortTapCursorX = 12f
      }
      if (!isSmoothDpadLoopRunning) {
        isSmoothDpadLoopRunning = true
        lastFrameTimeNanos = 0L
        Choreographer.getInstance().postFrameCallback(smoothDpadFrameCallback)
      }
    } else {
      stopSmoothDpadMovement(immediate = true)
      lastHoverDispatchUptimeMs = now
      dispatchHoverMoveAtCursor()
    }
  }

  private fun stopSmoothDpadMovement(immediate: Boolean = true) {
    isDpadPhysicallyHeld = false
    activeDpadKeyCode = 0
    if (immediate) {
      remainingShortTapScrollPx = 0.0
      remainingShortTapCursorX = 0f
      remainingShortTapCursorY = 0f
      lastFrameTimeNanos = 0L
      if (isSmoothDpadLoopRunning) {
        isSmoothDpadLoopRunning = false
        try {
          Choreographer.getInstance().removeFrameCallback(smoothDpadFrameCallback)
        } catch (_: Exception) {}
      }
    }
  }

  private fun formatOffsetDelta(deltaSec: Double): String {
    val sign = if (deltaSec >= 0.0) "+" else "\u2212"
    val absSec = abs(deltaSec.toInt())
    val mins = absSec / 60
    val secs = absSec % 60
    return String.format("%s%02d:%02d", sign, mins, secs)
  }

  private fun handleDpadVideoSeek(keyCode: Int, repeatCount: Int) {
    if (!isFullScreenVideo || isLiveStreamVideo || !hasActiveVodVideo || vodDurationSec <= 0.0) return
    val now = SystemClock.uptimeMillis()
    val dir = if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1 else -1

    if (dir == lastSeekDirection && (now - lastSeekPressUptime) < 800L) {
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
      effectiveCount >= 10 -> 30.0
      effectiveCount >= 6 -> 20.0
      effectiveCount >= 3 -> 10.0
      else -> 5.0
    }

    val delta = dir * stepSec
    isUserSeekingVod = true
    vodCurrentTimeSec = (vodCurrentTimeSec + delta).coerceIn(0.0, vodDurationSec)
    cumulativeSeekDeltaSec += delta

    videoSeekOverlay.showSeekState(
      currentSec = vodCurrentTimeSec,
      totalSec = vodDurationSec,
      deltaLabel = formatOffsetDelta(cumulativeSeekDeltaSec),
      isForward = dir > 0,
      isPlaying = vodIsPlaying
    )

    mainHandler.removeCallbacks(commitVideoSeekRunnable)
    mainHandler.postDelayed(commitVideoSeekRunnable, 280L)

    mainHandler.removeCallbacks(hideVideoSeekRunnable)
    mainHandler.postDelayed(hideVideoSeekRunnable, 2200L)
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    val keyCode = event.keyCode
    val action = event.action

    if (keyCode == KeyEvent.KEYCODE_MENU) {
      if (action == KeyEvent.ACTION_UP && !isHomeScreenVisible && !isFullScreenVideo) {
        activateToolbarMode()
      }
      return true
    }

    if (keyCode == KeyEvent.KEYCODE_BACK) {
      stopSmoothDpadMovement(immediate = true)
      if (action == KeyEvent.ACTION_UP) {
        when {
          isEditingWebInput -> closeNativeWebInputBridge(submit = false)
          isEditingUrlText -> stopEditingUrlTextAndStayOnBar()
          isFullScreenVideo || isTheaterModeActive -> exitFullScreenSafely()
          !isHomeScreenVisible && !isCursorModeActive -> activateCursorMode(showPointerNow = true)
          !isHomeScreenVisible && canGoBackState -> {
            sendVideoCommandToWeb("STOP_ALL_MEDIA", 0.0)
            geckoSession?.goBack()
          }
          !isHomeScreenVisible && !canGoBackState -> showHomeScreen()
          isHomeScreenVisible && isHomeToolbarFocused -> {
            isHomeToolbarFocused = false
            updateToolbarSelectionVisuals()
            renderHomeScreenContent()
          }
          else -> showExitConfirmationDialog()
        }
      }
      return true
    }

    if (!isHomeScreenVisible && (hasActiveVodVideo || isLiveStreamVideo)) {
      when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
          if (action == KeyEvent.ACTION_DOWN) {
            sendVideoCommandToWeb("TOGGLE_PLAY")
          }
          return true
        }
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
          if (action == KeyEvent.ACTION_DOWN && isFullScreenVideo && !isLiveStreamVideo) {
            handleDpadVideoSeek(KeyEvent.KEYCODE_DPAD_RIGHT, event.repeatCount)
          }
          return true
        }
        KeyEvent.KEYCODE_MEDIA_REWIND -> {
          if (action == KeyEvent.ACTION_DOWN && isFullScreenVideo && !isLiveStreamVideo) {
            handleDpadVideoSeek(KeyEvent.KEYCODE_DPAD_LEFT, event.repeatCount)
          }
          return true
        }
      }
    }

    if (isEditingUrlText) {
      if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && action == KeyEvent.ACTION_DOWN) {
        stopEditingUrlTextAndStayOnBar()
        return true
      }
      return super.dispatchKeyEvent(event)
    }

    if (isHomeScreenVisible) {
      if (isHomeToolbarFocused) {
        when (keyCode) {
          KeyEvent.KEYCODE_DPAD_LEFT -> {
            if (action == KeyEvent.ACTION_DOWN) {
              toolbarIndex = max(0, toolbarIndex - 1)
              updateToolbarSelectionVisuals()
            }
            return true
          }
          KeyEvent.KEYCODE_DPAD_RIGHT -> {
            if (action == KeyEvent.ACTION_DOWN) {
              toolbarIndex = min(toolbarItems.size - 1, toolbarIndex + 1)
              updateToolbarSelectionVisuals()
            }
            return true
          }
          KeyEvent.KEYCODE_DPAD_DOWN -> {
            if (action == KeyEvent.ACTION_DOWN) {
              isHomeToolbarFocused = false
              updateToolbarSelectionVisuals()
              renderHomeScreenContent()
            }
            return true
          }
          KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
            if (action == KeyEvent.ACTION_UP) {
              toolbarItems[toolbarIndex].performClick()
            }
            return true
          }
        }
        return true
      } else {
        if (action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
          val focused = currentFocus
          val focusUp = focused?.focusSearch(View.FOCUS_UP)
          if (focusUp == null || !isViewInsideHomeContent(focusUp)) {
            activateToolbarMode()
            return true
          }
        }
        return super.dispatchKeyEvent(event)
      }
    }

    if (!isCursorModeActive) {
      when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT -> {
          if (action == KeyEvent.ACTION_DOWN) {
            toolbarIndex = max(0, toolbarIndex - 1)
            updateToolbarSelectionVisuals()
          }
          return true
        }
        KeyEvent.KEYCODE_DPAD_RIGHT -> {
          if (action == KeyEvent.ACTION_DOWN) {
            toolbarIndex = min(toolbarItems.size - 1, toolbarIndex + 1)
            updateToolbarSelectionVisuals()
          }
          return true
        }
        KeyEvent.KEYCODE_DPAD_DOWN -> {
          if (action == KeyEvent.ACTION_DOWN) {
            activateCursorMode(showPointerNow = true)
          }
          return true
        }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
          if (action == KeyEvent.ACTION_UP) {
            toolbarItems[toolbarIndex].performClick()
          }
          return true
        }
      }
      return true
    }

    if (wokeCursorKeyCode != 0 && keyCode == wokeCursorKeyCode && action == KeyEvent.ACTION_UP) {
      wokeCursorKeyCode = 0
      scheduleCursorAutoHide()
      return true
    }

    if (isFullScreenVideo && (hasActiveVodVideo || isLiveStreamVideo) && !cursorOverlay.isCursorCurrentlyVisible()) {
      when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
          if (!isLiveStreamVideo && hasActiveVodVideo) {
            if (action == KeyEvent.ACTION_DOWN) {
              handleDpadVideoSeek(keyCode, event.repeatCount)
            }
            return true
          }
          if (action == KeyEvent.ACTION_DOWN) {
            wokeCursorKeyCode = keyCode
            lastCursorInteractionUptimeMs = SystemClock.uptimeMillis()
            cursorOverlay.showCursorAndScheduleFade()
            dispatchHoverMoveAtCursor()
            scheduleCursorAutoHide()
          }
          return true
        }
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
          if (action == KeyEvent.ACTION_UP) {
            sendVideoCommandToWeb("TOGGLE_PLAY")
            if (!isLiveStreamVideo && hasActiveVodVideo) {
              videoSeekOverlay.showSeekState(
                currentSec = vodCurrentTimeSec,
                totalSec = vodDurationSec,
                deltaLabel = if (vodIsPlaying) "\u23F8 Pausa" else "\u25B6 Reproduciendo",
                isForward = true,
                isPlaying = !vodIsPlaying
              )
              mainHandler.removeCallbacks(hideVideoSeekRunnable)
              mainHandler.postDelayed(hideVideoSeekRunnable, 1800L)
            }
          }
          return true
        }
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
          if (action == KeyEvent.ACTION_DOWN) {
            wokeCursorKeyCode = keyCode
            lastCursorInteractionUptimeMs = SystemClock.uptimeMillis()
            cursorOverlay.showCursorAndScheduleFade()
            dispatchHoverMoveAtCursor()
            scheduleCursorAutoHide()
          }
          return true
        }
      }
    }

    when (keyCode) {
      KeyEvent.KEYCODE_DPAD_UP,
      KeyEvent.KEYCODE_DPAD_DOWN,
      KeyEvent.KEYCODE_DPAD_LEFT,
      KeyEvent.KEYCODE_DPAD_RIGHT -> {
        if (action == KeyEvent.ACTION_DOWN) {
          wokeCursorKeyCode = 0
          if (keyCode == KeyEvent.KEYCODE_DPAD_UP &&
            event.repeatCount == 0 &&
            !isFullScreenVideo &&
            !isTheaterModeActive &&
            cursorOverlay.cursorY <= 6f
          ) {
            activateToolbarMode()
            return true
          }
          startSmoothDpadMovement(keyCode, isInitialTap = (event.repeatCount == 0))
        } else if (action == KeyEvent.ACTION_UP) {
          handleDpadKeyRelease(keyCode)
          scheduleCursorAutoHide()
        }
        return true
      }

      KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
        stopSmoothDpadMovement(immediate = true)
        if (action == KeyEvent.ACTION_UP) {
          simulateTapAtCursor()
        }
        return true
      }
    }

    return super.dispatchKeyEvent(event)
  }

  private fun isViewInsideHomeContent(view: View): Boolean {
    var current: Any? = view
    while (current is View) {
      if (current === homeContentContainer) return true
      current = current.parent
    }
    return false
  }

  private fun dispatchHoverMoveAtCursor() {
    val x = cursorOverlay.cursorX
    val y = cursorOverlay.cursorY
    val now = SystemClock.uptimeMillis()
    try {
      val hoverEvent = MotionEvent.obtain(now, now, MotionEvent.ACTION_HOVER_MOVE, x, y, 0).apply {
        source = InputDevice.SOURCE_MOUSE
      }
      geckoView.dispatchGenericMotionEvent(hoverEvent)
      hoverEvent.recycle()
    } catch (_: Exception) {}
  }

  private fun simulateTapAtCursor() {
    val x = cursorOverlay.cursorX
    val y = cursorOverlay.cursorY
    val downTime = SystemClock.uptimeMillis()
    lastUserWebClickTime = downTime
    lastCursorInteractionUptimeMs = downTime
    pendingSyntheticTapCount++

    geckoView.requestFocus()
    geckoSession?.setActive(true)
    geckoSession?.setFocused(true)

    dispatchHoverMoveAtCursor()

    try {
      val downEvent = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0).apply {
        source = InputDevice.SOURCE_TOUCHSCREEN
      }
      geckoView.dispatchTouchEvent(downEvent)
      downEvent.recycle()

      mainHandler.postDelayed({
        val upTime = SystemClock.uptimeMillis()
        try {
          val upEvent = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, x, y, 0).apply {
            source = InputDevice.SOURCE_TOUCHSCREEN
          }
          geckoView.dispatchTouchEvent(upEvent)
          upEvent.recycle()
        } catch (_: Exception) {
        } finally {
          pendingSyntheticTapCount = max(0, pendingSyntheticTapCount - 1)
        }
      }, 24L)
    } catch (_: Exception) {
      pendingSyntheticTapCount = max(0, pendingSyntheticTapCount - 1)
    }

    cursorOverlay.showCursorAndScheduleFade()
    cursorOverlay.animateClickPulse()
    scheduleCursorAutoHide()
  }

  private fun scrollWebPageNative(deltaYPixels: Double) {
    if (abs(deltaYPixels) < 0.1) return
    try {
      val now = SystemClock.uptimeMillis()
      val vScroll = (-deltaYPixels / 64.0).toFloat().coerceIn(-0.45f, 0.45f)
      scrollPointerCoords[0].apply {
        clear()
        x = cursorOverlay.cursorX.coerceIn(12f, max(12f, webContainer.width - 12f))
        y = cursorOverlay.cursorY.coerceIn(12f, max(12f, webContainer.height - 12f))
        setAxisValue(MotionEvent.AXIS_VSCROLL, vScroll)
      }
      val scrollEvent = MotionEvent.obtain(
        now, now, MotionEvent.ACTION_SCROLL,
        1, scrollPointerProps, scrollPointerCoords,
        0, 0, 1f, 1f, 0, 0,
        InputDevice.SOURCE_MOUSE, 0
      )
      geckoSession?.panZoomController?.onMotionEvent(scrollEvent)
      scrollEvent.recycle()
    } catch (_: Exception) {
      try {
        geckoSession?.panZoomController?.scrollBy(
          ScreenLength.zero(),
          ScreenLength.fromPixels(deltaYPixels),
          PanZoomController.SCROLL_BEHAVIOR_AUTO
        )
      } catch (_: Exception) {}
    }
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
      setTextColor(Color.WHITE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
      setPadding(dp(6), dp(2), dp(6), dp(2))
      background = createButtonBackground(focused = false, isAccent = isAccent, isDanger = isDanger)
      layoutParams = LinearLayout.LayoutParams(widthPx, dp(38))
    }
  }

  private fun createButtonBackground(
    focused: Boolean,
    isAccent: Boolean,
    isDanger: Boolean
  ): GradientDrawable {
    return GradientDrawable().apply {
      shape = GradientDrawable.RECTANGLE
      cornerRadius = 10f
      when {
        focused -> {
          setColor(
            when {
              isDanger -> Color.parseColor("#DC2626")
              isAccent -> Color.parseColor("#7C3AED")
              else -> Color.parseColor("#4F46E5")
            }
          )
          setStroke(3, Color.parseColor("#F9A8D4"))
        }
        isDanger -> {
          setColor(Color.parseColor("#451A2B"))
          setStroke(1, Color.parseColor("#F43F5E"))
        }
        isAccent -> {
          setColor(Color.parseColor("#3B1D6E"))
          setStroke(1, Color.parseColor("#A78BFA"))
        }
        else -> {
          setColor(Color.parseColor("#1E2448"))
          setStroke(1, Color.parseColor("#4338CA"))
        }
      }
    }
  }

  private fun createInputBackground(selectedOnBar: Boolean, editingText: Boolean): GradientDrawable {
    return GradientDrawable().apply {
      shape = GradientDrawable.RECTANGLE
      cornerRadius = 10f
      when {
        editingText -> {
          setColor(Color.parseColor("#1E1B4B"))
          setStroke(3, Color.parseColor("#38BDF8"))
        }
        selectedOnBar -> {
          setColor(Color.parseColor("#232052"))
          setStroke(3, Color.parseColor("#F472B6"))
        }
        else -> {
          setColor(Color.parseColor("#121735"))
          setStroke(1, Color.parseColor("#4338CA"))
        }
      }
    }
  }

  private fun hideSoftKeyboardFromView(view: View) {
    try {
      val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
      imm?.hideSoftInputFromWindow(view.windowToken, 0)
    } catch (_: Exception) {}
  }

  private fun showExitConfirmationDialog() {
    stopSmoothDpadMovement(immediate = true)
    AlertDialog.Builder(this)
      .setTitle("Salir de Bat TV")
      .setMessage("\u00BFDeseas cerrar el navegador?")
      .setPositiveButton("Salir") { _, _ -> exitBrowserCleanly() }
      .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
      .show()
  }

  private fun exitBrowserCleanly() {
    if (isExitingCompletely) return
    isExitingCompletely = true
    stopSmoothDpadMovement(immediate = true)
    dismissFullscreenPromptDialog()
    mainHandler.removeCallbacksAndMessages(null)
    sendVideoCommandToWeb("STOP_ALL_MEDIA", 0.0)
    try {
      geckoSession?.stop()
      geckoSession?.loadUri("about:blank")
    } catch (_: Exception) {}
    if (::geckoManager.isInitialized) {
      geckoManager.closeSessionCleanly()
    }
    try {
      finishAndRemoveTask()
    } catch (_: Exception) {
      finish()
    }
    mainHandler.postDelayed({
      try {
        android.os.Process.killProcess(android.os.Process.myPid())
      } catch (_: Exception) {}
      exitProcess(0)
    }, 180L)
  }

  private inner class VideoSeekOverlayView(context: Context) : View(context) {
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#D90B1024")
      style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#818CF8")
      style = Paint.Style.STROKE
      strokeWidth = 3f
    }
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#334155")
      style = Paint.Style.FILL
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#A855F7")
      style = Paint.Style.FILL
    }
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.WHITE
      textSize = 34f
      typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#E2E8F0")
      textSize = 26f
      typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }

    private val cardRect = RectF()
    private val trackRect = RectF()
    private val fillRect = RectF()

    private var curSec: Double = 0.0
    private var totSec: Double = 0.0
    private var deltaText: String = ""
    private var forwardDir: Boolean = true
    private var playingState: Boolean = true

    fun isOverlayVisible(): Boolean = visibility == VISIBLE

    fun showSeekState(
      currentSec: Double,
      totalSec: Double,
      deltaLabel: String,
      isForward: Boolean,
      isPlaying: Boolean
    ) {
      curSec = currentSec
      totSec = max(1.0, totalSec)
      deltaText = deltaLabel
      forwardDir = isForward
      playingState = isPlaying
      alpha = 1f
      visibility = VISIBLE
      postInvalidateOnAnimation()
    }

    fun updateProgressOnly(currentSec: Double, totalSec: Double, isPlaying: Boolean) {
      curSec = currentSec
      totSec = max(1.0, totalSec)
      playingState = isPlaying
      postInvalidateOnAnimation()
    }

    fun fadeOutAndHide() {
      visibility = GONE
    }

    fun hideOverlayImmediate() {
      visibility = GONE
    }

    private fun fmtTime(sec: Double): String {
      val total = max(0, sec.toInt())
      val hrs = total / 3600
      val mins = (total % 3600) / 60
      val secs = total % 60
      return if (hrs > 0) {
        String.format("%d:%02d:%02d", hrs, mins, secs)
      } else {
        String.format("%02d:%02d", mins, secs)
      }
    }

    override fun onDraw(canvas: Canvas) {
      super.onDraw(canvas)
      val w = width.toFloat()
      val h = height.toFloat()
      if (w <= 0f || h <= 0f) return

      val cardW = min(w * 0.68f, 920f)
      val cardH = 148f
      val left = (w - cardW) / 2f
      val top = h - cardH - 56f
      cardRect.set(left, top, left + cardW, top + cardH)

      canvas.drawRoundRect(cardRect, 22f, 22f, cardPaint)
      canvas.drawRoundRect(cardRect, 22f, 22f, borderPaint)

      val arrow = if (forwardDir) "\u23E9" else "\u23EA"
      val headline = "$arrow  $deltaText"
      canvas.drawText(headline, left + 34f, top + 52f, titlePaint)

      val timeStr = "${fmtTime(curSec)} / ${fmtTime(totSec)}"
      val timeWidth = timePaint.measureText(timeStr)
      canvas.drawText(timeStr, left + cardW - timeWidth - 34f, top + 52f, timePaint)

      val barLeft = left + 34f
      val barRight = left + cardW - 34f
      val barTop = top + 90f
      val barBottom = barTop + 18f
      trackRect.set(barLeft, barTop, barRight, barBottom)
      canvas.drawRoundRect(trackRect, 9f, 9f, trackPaint)

      val ratio = (curSec / totSec).toFloat().coerceIn(0f, 1f)
      fillRect.set(barLeft, barTop, barLeft + (barRight - barLeft) * ratio, barBottom)
      canvas.drawRoundRect(fillRect, 9f, 9f, progressPaint)
    }
  }

  private inner class CursorOverlayView(context: Context) : View(context) {
    private val hotspotOffsetPx = 16f

    var cursorX: Float = 400f
      private set
    var cursorY: Float = 260f
      private set

    private var cursorVisible: Boolean = false
    private var pulseRadius: Float = 0f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#F8FAFC")
      style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#7C3AED")
      style = Paint.Style.STROKE
      strokeWidth = 3.5f
    }
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.parseColor("#8038BDF8")
      style = Paint.Style.STROKE
      strokeWidth = 3.5f
    }
    private val pointerPath = Path().apply {
      val ox = 16f
      val oy = 16f
      moveTo(ox, oy)
      lineTo(ox, oy + 26f)
      lineTo(ox + 7.5f, oy + 20f)
      lineTo(ox + 13f, oy + 31f)
      lineTo(ox + 17.5f, oy + 28.5f)
      lineTo(ox + 12f, oy + 18f)
      lineTo(ox + 20f, oy + 18f)
      close()
    }

    fun isCursorCurrentlyVisible(): Boolean = cursorVisible && visibility == VISIBLE

    fun centerCursor() {
      val parentW = webContainer.width
      val parentH = webContainer.height
      if (parentW > 0 && parentH > 0) {
        cursorX = parentW / 2f
        cursorY = parentH / 2f
        translationX = cursorX - hotspotOffsetPx
        translationY = cursorY - hotspotOffsetPx
      }
    }

    fun moveCursorBy(dx: Float, dy: Float): Boolean {
      val maxW = max(1f, webContainer.width.toFloat())
      val maxH = max(1f, webContainer.height.toFloat())
      val nextY = cursorY + dy
      val hitTop = nextY <= 2f
      val clampedX = (cursorX + dx).coerceIn(4f, maxW - 4f)
      val clampedY = nextY.coerceIn(2f, maxH - 4f)
      if (abs(clampedX - cursorX) >= 0.05f || abs(clampedY - cursorY) >= 0.05f) {
        cursorX = clampedX
        cursorY = clampedY
        translationX = cursorX - hotspotOffsetPx
        translationY = cursorY - hotspotOffsetPx
      }
      if (!cursorVisible || visibility != VISIBLE) {
        cursorVisible = true
        visibility = VISIBLE
      }
      return hitTop
    }

    fun showCursorAndScheduleFade() {
      val targetX = cursorX - hotspotOffsetPx
      val targetY = cursorY - hotspotOffsetPx
      if (abs(translationX - targetX) >= 0.05f) translationX = targetX
      if (abs(translationY - targetY) >= 0.05f) translationY = targetY
      if (!cursorVisible || visibility != VISIBLE) {
        cursorVisible = true
        visibility = VISIBLE
      }
    }

    fun hideCursorUntilMoved() {
      if (!cursorVisible && visibility == GONE) return
      cursorVisible = false
      pulseRadius = 0f
      visibility = GONE
    }

    fun animateClickPulse() {
      pulseRadius = 14f
      invalidate()
      postDelayed({
        pulseRadius = 0f
        invalidate()
      }, 130L)
    }

    override fun onDraw(canvas: Canvas) {
      super.onDraw(canvas)
      if (pulseRadius > 0f) {
        canvas.drawCircle(hotspotOffsetPx, hotspotOffsetPx, pulseRadius, pulsePaint)
      }
      canvas.drawPath(pointerPath, fillPaint)
      canvas.drawPath(pointerPath, strokePaint)
    }
  }
}
