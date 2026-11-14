package com.battv.browser

import android.app.AlertDialog
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
import org.mozilla.geckoview.WebExtension
import java.util.Collections
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {

  companion object {
    private const val PREFS_NAME = "battv_browser_prefs"
    private const val KEY_BOOKMARKS = "bookmarks_user_list_v2"
    private const val KEY_HISTORY = "history_json_v2"
    private const val KEY_DESKTOP_MODE = "desktop_mode_enabled"
    private const val KEY_MODE_SELECTED = "initial_mode_selected_v2"

    private const val URL_BAR_INDEX = 2

    // Velocidades calibradas para bucle VSYNC a 60 FPS
    private const val BASE_CURSOR_FRAME_SPEED_PX = 6.5f
    private const val MAX_CURSOR_FRAME_SPEED_PX = 20.0f
    private const val BASE_SCROLL_FRAME_SPEED_PX = 14.0
    private const val MAX_SCROLL_FRAME_SPEED_PX = 38.0
    private const val EDGE_SCROLL_ZONE_PX = 105f
    private const val CURSOR_AUTO_HIDE_MS = 3000L
  }

  private lateinit var prefs: SharedPreferences
  private val mainHandler = Handler(Looper.getMainLooper())

  private lateinit var rootLayout: LinearLayout
  private lateinit var topBarLayout: LinearLayout

  // Exactamente 6 elementos en la barra superior y en este orden:
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

  private var currentUrl: String = ""
  private var currentTitle: String = ""
  private var latestExtractedHeading: String = ""
  private var allowTopDomainChangeUntilUptimeMs: Long = 0L

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
  private var isSyntheticDpadTap: Boolean = false

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

  // Estado del motor fluido a 60 FPS para cursor y scroll
  private var activeDpadKeyCode: Int = 0
  private var dpadHoldStartUptimeMs: Long = 0L
  private var lastDpadEventUptimeMs: Long = 0L
  private var isSmoothDpadLoopRunning: Boolean = false
  private var lastHoverDispatchUptimeMs: Long = 0L

  private val smoothDpadFrameCallback = object : Choreographer.FrameCallback {
    override fun doFrame(frameTimeNanos: Long) {
      if (!isSmoothDpadLoopRunning || !isCursorModeActive || isHomeScreenVisible) {
        isSmoothDpadLoopRunning = false
        return
      }
      val now = SystemClock.uptimeMillis()
      // Si el control remoto soltó la tecla sin enviar ACTION_UP, detener de forma segura
      if (now - lastDpadEventUptimeMs > 240L) {
        stopSmoothDpadMovement()
        return
      }

      val heldMs = (now - dpadHoldStartUptimeMs).coerceAtLeast(0L)
      val accelFactor = (heldMs.toFloat() / 650f).coerceIn(0f, 1f)
      val cursorSpeed = BASE_CURSOR_FRAME_SPEED_PX +
        (MAX_CURSOR_FRAME_SPEED_PX - BASE_CURSOR_FRAME_SPEED_PX) * (accelFactor * accelFactor)

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

      if (now - lastHoverDispatchUptimeMs >= 90L) {
        lastHoverDispatchUptimeMs = now
        dispatchHoverMoveAtCursor()
      }

      // Desplazamiento vertical nativo a 60 FPS sin bloqueos al llegar a los bordes superior o inferior
      if (!isFullScreenVideo) {
        val scrollAccel = (heldMs.toDouble() / 700.0).coerceIn(0.0, 1.0)
        val scrollStep = BASE_SCROLL_FRAME_SPEED_PX +
          (MAX_SCROLL_FRAME_SPEED_PX - BASE_SCROLL_FRAME_SPEED_PX) * (scrollAccel * scrollAccel)

        if (activeDpadKeyCode == KeyEvent.KEYCODE_DPAD_UP && cursorOverlay.cursorY <= EDGE_SCROLL_ZONE_PX) {
          scrollWebPageNative(-scrollStep)
        } else if (activeDpadKeyCode == KeyEvent.KEYCODE_DPAD_DOWN &&
          cursorOverlay.cursorY >= (webContainer.height - EDGE_SCROLL_ZONE_PX)
        ) {
          scrollWebPageNative(scrollStep)
        }
      }

      Choreographer.getInstance().postFrameCallback(this)
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
    try {
      activeMediaSession?.seekTo(vodCurrentTimeSec, false)
    } catch (_: Exception) {}
    sendVideoCommandToWeb("SEEK_TO", vodCurrentTimeSec)
    isUserSeekingVod = false
  }

  private val fullScreenExitWatchdogRunnable = Runnable {
    if (isFullScreenVideo) {
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

  override fun onResume() {
    super.onResume()
    geckoSession?.setActive(true)
    geckoSession?.setFocused(true)
    if (isFullScreenVideo) {
      applyImmersiveSystemUi(true)
    }
  }

  override fun onPause() {
    stopSmoothDpadMovement()
    super.onPause()
    mainHandler.removeCallbacks(hideCursorRunnable)
    mainHandler.removeCallbacks(hideVideoSeekRunnable)
    geckoSession?.setActive(false)
    if (::geckoManager.isInitialized) {
      geckoManager.pruneDisposableStorageNow()
    }
  }

  override fun onDestroy() {
    stopSmoothDpadMovement()
    dismissFullscreenPromptDialog()
    mainHandler.removeCallbacksAndMessages(null)
    if (::geckoManager.isInitialized) {
      geckoManager.closeSessionCleanly()
    }
    super.onDestroy()
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

    // 1. Menu
    btnMenu = createTvButton("\u2630 Men\u00FA", dp(80), isAccent = true).apply {
      setOnClickListener { showMainMenuDialog() }
    }

    // 2. Recargar pagina
    btnReload = createTvButton("\u27F3", dp(44)).apply {
      layoutParams = LinearLayout.LayoutParams(dp(44), dp(38)).apply {
        marginStart = dp(6)
      }
      setOnClickListener {
        if (isHomeScreenVisible) {
          renderHomeScreenContent()
          Toast.makeText(this@MainActivity, "Favoritos actualizados", Toast.LENGTH_SHORT).show()
        } else {
          if (isFullScreenVideo) {
            exitFullScreenSafely()
          }
          fullscreenPromptDismissedForPage = false
          allowTopDomainChangeUntilUptimeMs = SystemClock.uptimeMillis() + 5000L
          geckoSession?.stop()
          geckoSession?.reload()
          activateCursorMode(showPointerNow = false)
        }
      }
    }

    // 3. Campo para escribir direcciones web o realizar busquedas
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

    // 4. Favoritos
    btnBookmarks = createTvButton("\u2B50", dp(44)).apply {
      setOnClickListener { showBookmarksDialog() }
    }

    // 5. Historial
    btnHistory = createTvButton("\uD83D\uDD52", dp(44)).apply {
      layoutParams = LinearLayout.LayoutParams(dp(44), dp(38)).apply {
        marginStart = dp(6)
      }
      setOnClickListener { showHistoryDialog() }
    }

    // 6. Boton X para cerrar o salir del navegador
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
      setPadding(dp(36), dp(24), dp(36), dp(28))
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
      layoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
      )
      isFocusable = false
      isFocusableInTouchMode = false
      isClickable = false
      elevation = 100f
    }

    webContainer.addView(geckoView)
    webContainer.addView(homeOverlayLayout)
    webContainer.addView(videoSeekOverlay)
    webContainer.addView(cursorOverlay)

    rootLayout.addView(topBarLayout)
    rootLayout.addView(loadingProgressBar)
    rootLayout.addView(webContainer)

    setContentView(rootLayout)
  }

  private fun promptAutoFullscreenIfEligible() {
    if (isHomeScreenVisible || isFullScreenVideo || fullscreenPromptDismissedForPage) return
    if (fullscreenPromptDialog?.isShowing == true) return
    if (SystemClock.uptimeMillis() - lastFullscreenExitUptimeMs < 4000L) return

    stopSmoothDpadMovement()

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
        d.dismiss()
        fullscreenPromptDialog = null
      }
      .setOnCancelListener {
        fullscreenPromptDismissedForPage = true
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
    applyFullScreenState(true)
    sendVideoCommandToWeb("ENTER_FULLSCREEN", 0.0)
    mainHandler.postDelayed({
      if (isFullScreenVideo) {
        sendVideoCommandToWeb("ENTER_FULLSCREEN", 0.0)
      }
    }, 180L)
  }

  private fun showHomeScreen() {
    stopSmoothDpadMovement()
    dismissFullscreenPromptDialog()
    if (isFullScreenVideo) {
      exitFullScreenSafely()
    }
    if (isEditingWebInput) {
      closeNativeWebInputBridge(submit = false)
    }
    geckoSession?.stop()
    isHomeScreenVisible = true
    isHomeToolbarFocused = false
    isCursorModeActive = false
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
      text = "Battv.2 \u2022 Favoritos"
      setTextColor(Color.WHITE)
      setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
      setTypeface(typeface, Typeface.BOLD)
    }

    val modeSubtitleView = TextView(this).apply {
      val modeText = if (isDesktopMode) "Modo PC (Escritorio)" else "Modo TV (Control remoto)"
      text = "$modeText  \u2022  Pop-ups Blocker, uBlock Origin y Violentmonkey activos"
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
        text = "Agrega tus sitios favoritos aqu\u00ED para abrirlos directamente al iniciar Battv.2 o escribe una direcci\u00F3n en la barra superior."
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
      .setTitle("Selecciona el modo de navegaci\u00F3n de Battv.2")
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
    allowTopDomainChangeUntilUptimeMs = SystemClock.uptimeMillis() + 5000L
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
    stopSmoothDpadMovement()
    val items = mutableListOf<String>()
    val actions = mutableListOf<() -> Unit>()

    items.add("\uD83C\uDFE0  Inicio (Pantalla de Favoritos)")
    actions.add { showHomeScreen() }

    if (!isHomeScreenVisible && !isFullScreenVideo) {
      items.add("\uD83C\uDFAC  Abrir v\u00EDdeo actual en pantalla completa")
      actions.add { enterVideoFullscreenAutomatically() }
    }

    if (!isHomeScreenVisible && canGoBackState) {
      items.add("\u25C4  P\u00E1gina anterior (Atr\u00E1s)")
      actions.add {
        allowTopDomainChangeUntilUptimeMs = SystemClock.uptimeMillis() + 5000L
        geckoSession?.goBack()
        activateCursorMode(showPointerNow = false)
      }
    }
    if (!isHomeScreenVisible && canGoForwardState) {
      items.add("\u25BA  P\u00E1gina siguiente (Adelante)")
      actions.add {
        allowTopDomainChangeUntilUptimeMs = SystemClock.uptimeMillis() + 5000L
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

    items.add("\uD83E\uDDE9  Extensiones activas (Pop-ups Blocker, uBlock Origin, Violentmonkey)")
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

    items.add("\u2716  Salir de Battv.2")
    actions.add { exitBrowserCleanly() }

    AlertDialog.Builder(this)
      .setTitle("\u2630 Men\u00FA de Battv.2")
      .setItems(items.toTypedArray()) { _, which -> actions[which].invoke() }
      .setNegativeButton("Cerrar") { d, _ -> d.dismiss() }
      .show()
  }

  private fun showExtensionsStatusDialog() {
    val popupLoaded = if (geckoManager.isExtensionLoaded(BatTvGeckoViewManager.POPUP_BLOCKER_ID)) "ACTIVO" else "CARGANDO"
    val ublockLoaded = if (geckoManager.isExtensionLoaded(BatTvGeckoViewManager.UBLOCK_ORIGIN_ID)) "ACTIVO" else "CARGANDO"
    val vmLoaded = if (geckoManager.isExtensionLoaded(BatTvGeckoViewManager.VIOLENTMONKEY_ID)) "ACTIVO" else "CARGANDO"

    val extItems = arrayOf(
      "1. Pop-ups Blocker (strict) \u2014 [$popupLoaded] (Abrir ajustes)",
      "2. uBlock Origin \u2014 [$ublockLoaded] (Abrir panel de filtros)",
      "3. Violentmonkey (\u00ABSaltarse Todos los Enlaces Acortados\u00BB) \u2014 [$vmLoaded] (Abrir panel)"
    )

    AlertDialog.Builder(this)
      .setTitle("\uD83E\uDDE9 Extensiones integradas (3 de 3)")
      .setItems(extItems) { _, which ->
        when (which) {
          0 -> openExtensionOptions(BatTvGeckoViewManager.POPUP_BLOCKER_ID, "data/options/index.html")
          1 -> openExtensionOptions(BatTvGeckoViewManager.UBLOCK_ORIGIN_ID, "dashboard.html")
          2 -> openExtensionOptions(BatTvGeckoViewManager.VIOLENTMONKEY_ID, "options/index.html")
        }
      }
      .setNegativeButton("Volver") { d, _ -> d.dismiss() }
      .show()
  }

  private fun openExtensionOptions(extId: String, fallbackPath: String) {
    val url = geckoManager.getExtensionDashboardUrl(extId, fallbackPath)
    if (!url.isNullOrBlank()) {
      loadUrlOrSearch(url)
    } else {
      Toast.makeText(
        this,
        "La extensi\u00F3n est\u00E1 activa y operando en segundo plano.",
        Toast.LENGTH_SHORT
      ).show()
    }
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
      .setTitle("\u2795 Agregar a Favoritos")
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
    stopSmoothDpadMovement()
    val arr = getBookmarksArray()
    val items = mutableListOf<String>()
    val actions = mutableListOf<() -> Unit>()

    if (!isHomeScreenVisible && currentUrl.startsWith("http")) {
      items.add("\u2795 Guardar p\u00E1gina actual en Favoritos (${currentTitle.ifBlank { currentUrl }.take(38)})")
      actions.add { addBookmark(currentTitle.ifBlank { currentUrl }, currentUrl) }
    }

    items.add("\u2795 Agregar nuevo favorito manualmente...")
    actions.add { showAddBookmarkDialog() }

    if (!isHomeScreenVisible) {
      items.add("\uD83C\uDFE0 Ir a la pantalla de inicio de Favoritos")
      actions.add { showHomeScreen() }
    }

    if (arr.length() > 0) {
      items.add("\uD83D\uDDD1\uFE0F Eliminar un favorito...")
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
      .setTitle("\u2B50 Favoritos de Battv.2")
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

    val raw = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
    val oldArr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
    val newArr = JSONArray()

    newArr.put(
      JSONObject()
        .put("title", formattedTitle)
        .put("url", url)
    )

    for (i in 0 until min(oldArr.length(), 45)) {
      val item = oldArr.optJSONObject(i) ?: continue
      if (!item.optString("url").equals(url, ignoreCase = true)) {
        newArr.put(item)
      }
    }
    prefs.edit().putString(KEY_HISTORY, newArr.toString()).apply()
  }

  private fun showHistoryDialog() {
    stopSmoothDpadMovement()
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

        val now = SystemClock.uptimeMillis()
        val isExplicitUserBarNavigation = (now <= allowTopDomainChangeUntilUptimeMs) || request.isDirectNavigation

        if (request.target == GeckoSession.NavigationDelegate.TARGET_WINDOW_NEW) {
          if (request.hasUserGesture && geckoManager.isSameOrTrustedNavigationTarget(currentUrl, uri)) {
            allowTopDomainChangeUntilUptimeMs = now + 4000L
            session.loadUri(uri)
          }
          return GeckoResult.deny()
        }

        // Cerrojo anti-scam ("Felicitaciones reclama tu bono" / "Limpia tu unknown"):
        // Si ya estamos en una página web, bloquear cualquier redirección de la pestaña principal
        // hacia otro dominio distinto que no haya sido escrita o elegida por el usuario en la barra/favoritos.
        if (!isHomeScreenVisible && !isExplicitUserBarNavigation && currentUrl.startsWith("http", ignoreCase = true)) {
          if (!geckoManager.isSameOrTrustedNavigationTarget(currentUrl, uri)) {
            return GeckoResult.deny()
          }
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
        if (url != currentUrl && isFullScreenVideo) {
          applyFullScreenState(false)
        }
        if (url != currentUrl) {
          dismissFullscreenPromptDialog()
          fullscreenPromptDismissedForPage = false
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
        if (!isFullScreenVideo && !isHomeScreenVisible) {
          loadingProgressBar.visibility = View.VISIBLE
          loadingProgressBar.progress = 20
        }
      }

      override fun onProgressChange(session: GeckoSession, progress: Int) {
        loadingProgressBar.progress = progress
        if (progress >= 100 || isFullScreenVideo || isHomeScreenVisible) {
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
        if (isFullScreenVideo) {
          applyFullScreenState(false)
        }
      }

      override fun onFullScreen(session: GeckoSession, fullScreen: Boolean) {
        runOnUiThread {
          mainHandler.removeCallbacks(fullScreenExitWatchdogRunnable)
          applyFullScreenState(fullScreen)
          if (fullScreen) {
            sendVideoCommandToWeb("ENTER_FULLSCREEN", 0.0)
          } else {
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
          if (vodDurationSec >= 90.0 || isLiveStreamVideo) {
            promptAutoFullscreenIfEligible()
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
          if (state.duration >= 90.0 && state.duration < 36000.0) {
            hasActiveVodVideo = true
            isLiveStreamVideo = false
            vodDurationSec = state.duration
            if (vodIsPlaying && state.position >= 0.4) {
              promptAutoFullscreenIfEligible()
            }
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
          if (enabled) {
            sendVideoCommandToWeb("ENTER_FULLSCREEN", 0.0)
          } else {
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

  private fun applyFullScreenState(fullScreen: Boolean) {
    isFullScreenVideo = fullScreen
    stopSmoothDpadMovement()

    if (fullScreen) {
      dismissFullscreenPromptDialog()
      if (isEditingWebInput || isEditingUrlText) {
        closeNativeWebInputBridge(submit = false)
      }
      homeOverlayLayout.visibility = View.GONE
      loadingProgressBar.visibility = View.GONE
      topBarLayout.visibility = View.GONE
      applyImmersiveSystemUi(true)

      isCursorModeActive = true
      cursorOverlay.hideCursorUntilMoved()
      rootLayout.requestLayout()
      webContainer.requestLayout()
      geckoView.requestLayout()
      geckoView.requestFocus()
    } else {
      lastFullscreenExitUptimeMs = SystemClock.uptimeMillis()
      mainHandler.removeCallbacks(hideVideoSeekRunnable)
      isUserSeekingVod = false
      consecutiveSeekPresses = 0
      cumulativeSeekDeltaSec = 0.0
      videoSeekOverlay.hideOverlayImmediate()

      applyImmersiveSystemUi(false)
      topBarLayout.visibility = View.VISIBLE
      if (isHomeScreenVisible) {
        homeOverlayLayout.visibility = View.VISIBLE
      }
      rootLayout.requestLayout()
      webContainer.requestLayout()
      geckoView.requestLayout()
      if (!isHomeScreenVisible) {
        activateCursorMode(showPointerNow = true)
      }
    }
  }

  private fun exitFullScreenSafely() {
    fullscreenPromptDismissedForPage = true
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
                runOnUiThread {
                  isLiveStreamVideo = isLive
                  hasActiveVodVideo = !isLive && dur >= 90.0
                  if (dur > 0.0) vodDurationSec = dur
                  if (isLive || dur >= 90.0) {
                    promptAutoFullscreenIfEligible()
                  }
                }
              }
              "REQUEST_UNIFIED_FULLSCREEN" -> {
                val enable = json.optBoolean("enable", true)
                runOnUiThread {
                  if (enable) {
                    enterVideoFullscreenAutomatically()
                  } else {
                    exitFullScreenSafely()
                  }
                }
              }
              "WEB_FULLSCREEN_STATE" -> {
                val enabled = json.optBoolean("enabled", false)
                runOnUiThread {
                  if (enabled != isFullScreenVideo) {
                    applyFullScreenState(enabled)
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
                  hasActiveVodVideo = !isLive && hasVod && dur >= 90.0
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
                  if (isFullScreenVideo && startedPlayingNow && (hasActiveVodVideo || isLiveStreamVideo)) {
                    mainHandler.removeCallbacks(hideCursorRunnable)
                    mainHandler.postDelayed(hideCursorRunnable, 900L)
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
    stopSmoothDpadMovement()
    topBarLayout.visibility = View.VISIBLE
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
      allowTopDomainChangeUntilUptimeMs = SystemClock.uptimeMillis() + 5000L
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
    stopSmoothDpadMovement()
    dismissFullscreenPromptDialog()
    if (isFullScreenVideo) {
      exitFullScreenSafely()
    }
    fullscreenPromptDismissedForPage = false
    allowTopDomainChangeUntilUptimeMs = SystemClock.uptimeMillis() + 6000L
    isHomeScreenVisible = false
    isHomeToolbarFocused = false
    homeOverlayLayout.visibility = View.GONE

    currentUrl = finalUri
    suppressWebInputWatcher = true
    urlEditText.setText(finalUri)
    suppressWebInputWatcher = false

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
    stopSmoothDpadMovement()
    if (isFullScreenVideo) {
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
    homeOverlayLayout.clearFocus()
    urlEditText.clearFocus()
    urlEditText.isCursorVisible = false
    urlEditText.isFocusable = false
    urlEditText.isFocusableInTouchMode = false
    cursorOverlay.hideCursorUntilMoved()
    updateToolbarSelectionVisuals()
  }

  private fun startSmoothDpadMovement(keyCode: Int) {
    val now = SystemClock.uptimeMillis()
    lastDpadEventUptimeMs = now
    cursorOverlay.showCursorAndScheduleFade()
    scheduleCursorAutoHide()

    if (activeDpadKeyCode != keyCode || !isSmoothDpadLoopRunning) {
      activeDpadKeyCode = keyCode
      dpadHoldStartUptimeMs = now
      if (!isSmoothDpadLoopRunning) {
        isSmoothDpadLoopRunning = true
        Choreographer.getInstance().postFrameCallback(smoothDpadFrameCallback)
      }
    }
  }

  private fun stopSmoothDpadMovement() {
    activeDpadKeyCode = 0
    if (isSmoothDpadLoopRunning) {
      isSmoothDpadLoopRunning = false
      try {
        Choreographer.getInstance().removeFrameCallback(smoothDpadFrameCallback)
      } catch (_: Exception) {}
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
    mainHandler.postDelayed(commitVideoSeekRunnable, 220L)

    mainHandler.removeCallbacks(hideVideoSeekRunnable)
    mainHandler.postDelayed(hideVideoSeekRunnable, 2200L)
  }

  override fun dispatchKeyEvent(event: KeyEvent): Boolean {
    val keyCode = event.keyCode
    val action = event.action

    // 1. Manejo del boton Atras (KEYCODE_BACK)
    if (keyCode == KeyEvent.KEYCODE_BACK) {
      stopSmoothDpadMovement()
      if (action == KeyEvent.ACTION_UP) {
        when {
          isEditingWebInput -> closeNativeWebInputBridge(submit = false)
          isEditingUrlText -> stopEditingUrlTextAndStayOnBar()
          isFullScreenVideo -> exitFullScreenSafely()
          !isHomeScreenVisible && !isCursorModeActive -> activateCursorMode(showPointerNow = true)
          !isHomeScreenVisible && canGoBackState -> {
            allowTopDomainChangeUntilUptimeMs = SystemClock.uptimeMillis() + 5000L
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

    // 2. Controles multimedia dedicados del control remoto
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

    // 3. Si estamos editando texto en el campo superior
    if (isEditingUrlText) {
      if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && action == KeyEvent.ACTION_DOWN) {
        stopEditingUrlTextAndStayOnBar()
        return true
      }
      return super.dispatchKeyEvent(event)
    }

    // 4. Navegacion en la pantalla de Inicio (Favoritos)
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

    // 5. Navegacion en la barra superior mientras se navega en una web
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

    // 6. Controles directos en pantalla completa cuando el puntero esta oculto
    if (isFullScreenVideo && (hasActiveVodVideo || isLiveStreamVideo) && !cursorOverlay.isCursorCurrentlyVisible()) {
      when (keyCode) {
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
          if (!isLiveStreamVideo && hasActiveVodVideo) {
            if (action == KeyEvent.ACTION_DOWN) {
              handleDpadVideoSeek(keyCode, event.repeatCount)
            }
            return true
          }
          // En un directo en vivo, Izquierda/Derecha despierta el puntero por si el usuario quiere cambiar calidad
          if (action == KeyEvent.ACTION_DOWN) {
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
            cursorOverlay.showCursorAndScheduleFade()
            dispatchHoverMoveAtCursor()
            scheduleCursorAutoHide()
          }
          return true
        }
      }
    }

    // 7. Movimiento fluido a 60 FPS del puntero y del scroll con las flechas del D-pad
    when (keyCode) {
      KeyEvent.KEYCODE_DPAD_UP,
      KeyEvent.KEYCODE_DPAD_DOWN,
      KeyEvent.KEYCODE_DPAD_LEFT,
      KeyEvent.KEYCODE_DPAD_RIGHT -> {
        if (action == KeyEvent.ACTION_DOWN) {
          // Si el usuario da un toque nuevo hacia ARRIBA cuando el puntero ya está arriba del todo,
          // subimos a la barra superior de 6 elementos; si mantiene pulsado, sigue haciendo scroll fluido.
          if (keyCode == KeyEvent.KEYCODE_DPAD_UP &&
            event.repeatCount == 0 &&
            !isFullScreenVideo &&
            cursorOverlay.cursorY <= 5f
          ) {
            activateToolbarMode()
            return true
          }
          startSmoothDpadMovement(keyCode)
        } else if (action == KeyEvent.ACTION_UP || action == KeyEvent.ACTION_CANCEL) {
          stopSmoothDpadMovement()
          dispatchHoverMoveAtCursor()
          scheduleCursorAutoHide()
        }
        return true
      }

      KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
        stopSmoothDpadMovement()
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
    val now = SystemClock.uptimeMillis()
    lastUserWebClickTime = now
    isSyntheticDpadTap = true

    geckoView.requestFocus()
    geckoSession?.setActive(true)
    geckoSession?.setFocused(true)

    try {
      val downEvent = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0).apply {
        source = InputDevice.SOURCE_TOUCHSCREEN
      }
      val upEvent = MotionEvent.obtain(now, now + 35L, MotionEvent.ACTION_UP, x, y, 0).apply {
        source = InputDevice.SOURCE_TOUCHSCREEN
      }
      geckoView.dispatchTouchEvent(downEvent)
      geckoView.dispatchTouchEvent(upEvent)
      downEvent.recycle()
      upEvent.recycle()
    } catch (_: Exception) {
    } finally {
      isSyntheticDpadTap = false
    }

    cursorOverlay.animateClickPulse()
    scheduleCursorAutoHide()
  }

  /**
   * Desplazamiento directo por el compositor nativo de GeckoView (SCROLL_BEHAVIOR_AUTO)
   * llamado en cada fotograma VSYNC (60 FPS) para lograr fluidez sin saltos ni bloqueos.
   */
  private fun scrollWebPageNative(deltaYPixels: Double) {
    try {
      geckoSession?.panZoomController?.scrollBy(
        ScreenLength.zero(),
        ScreenLength.fromPixels(deltaYPixels),
        PanZoomController.SCROLL_BEHAVIOR_AUTO
      )
    } catch (_: Exception) {}
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
    stopSmoothDpadMovement()
    AlertDialog.Builder(this)
      .setTitle("Salir de Battv.2")
      .setMessage("\u00BFDeseas cerrar el navegador?")
      .setPositiveButton("Salir") { _, _ -> exitBrowserCleanly() }
      .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
      .show()
  }

  private fun exitBrowserCleanly() {
    stopSmoothDpadMovement()
    dismissFullscreenPromptDialog()
    if (::geckoManager.isInitialized) {
      geckoManager.closeSessionCleanly()
    }
    finishAndRemoveTask()
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
      strokeWidth = 4f
    }
    private val pointerPath = Path()

    fun isCursorCurrentlyVisible(): Boolean = cursorVisible

    fun centerCursor() {
      if (width > 0 && height > 0) {
        cursorX = width / 2f
        cursorY = height / 2f
        postInvalidateOnAnimation()
      }
    }

    fun moveCursorBy(dx: Float, dy: Float): Boolean {
      val maxW = max(1f, width.toFloat())
      val maxH = max(1f, height.toFloat())
      val nextY = cursorY + dy
      val hitTop = nextY <= 2f
      cursorX = (cursorX + dx).coerceIn(4f, maxW - 4f)
      cursorY = nextY.coerceIn(2f, maxH - 4f)
      cursorVisible = true
      postInvalidateOnAnimation()
      return hitTop
    }

    fun showCursorAndScheduleFade() {
      cursorVisible = true
      postInvalidateOnAnimation()
    }

    fun hideCursorUntilMoved() {
      cursorVisible = false
      pulseRadius = 0f
      postInvalidateOnAnimation()
    }

    fun animateClickPulse() {
      pulseRadius = 22f
      postInvalidateOnAnimation()
      postDelayed({
        pulseRadius = 0f
        postInvalidateOnAnimation()
      }, 140L)
    }

    override fun onDraw(canvas: Canvas) {
      super.onDraw(canvas)
      if (!cursorVisible) return

      if (pulseRadius > 0f) {
        canvas.drawCircle(cursorX, cursorY, pulseRadius, pulsePaint)
      }

      pointerPath.reset()
      pointerPath.moveTo(cursorX, cursorY)
      pointerPath.lineTo(cursorX, cursorY + 26f)
      pointerPath.lineTo(cursorX + 7.5f, cursorY + 20f)
      pointerPath.lineTo(cursorX + 13f, cursorY + 31f)
      pointerPath.lineTo(cursorX + 17.5f, cursorY + 28.5f)
      pointerPath.lineTo(cursorX + 12f, cursorY + 18f)
      pointerPath.lineTo(cursorX + 20f, cursorY + 18f)
      pointerPath.close()

      canvas.drawPath(pointerPath, fillPaint)
      canvas.drawPath(pointerPath, strokePaint)
    }
  }
}
