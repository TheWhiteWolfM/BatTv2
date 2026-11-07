package com.battv.browser

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.GeckoView

class MainActivity : AppCompatActivity() {

    private lateinit var geckoView: GeckoView
    private lateinit var urlAddressInput: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var geckoManager: BatTvGeckoViewManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        // 1. Contenedor principal de pantalla completa
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D12"))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 2. Barra superior de navegación (apta para control remoto D-Pad)
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#181824"))
            setPadding(dp(10), dp(6), dp(10), dp(6))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Campo de texto de URL / Búsqueda en Google
        urlAddressInput = EditText(this).apply {
            hint = "Buscar en Google o escribir URL (ej. doramasflix.io)..."
            setHintTextColor(Color.parseColor("#888899"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setBackgroundColor(Color.parseColor("#252538"))
            setPadding(dp(12), dp(8), dp(12), dp(8))
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
        }

        // Botón "Ir" para confirmar con el botón central (OK) del control remoto
        val goButton = Button(this).apply {
            text = "IR"
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#F5C518"))
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                 dp(64),
                 dp(38)
            )
            setOnClickListener {
                navegarDesdeBarra()
            }
        }

        topBar.addView(urlAddressInput)
        topBar.addView(goButton)

        // 3. Barra de progreso de carga
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3)
            )
        }

        // 4. Motor GeckoView ocupando el resto del televisor
        geckoView = GeckoView(this).apply {
            isFocusable = true
            isFocusableInTouchMode = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        rootLayout.addView(topBar)
        rootLayout.addView(progressBar)
        rootLayout.addView(geckoView)

        // IMPORTANTE: Dibujar el diseño en la pantalla
        setContentView(rootLayout)

        // 5. Inicializar el gestor de GeckoView + Extensiones
        geckoManager = BatTvGeckoViewManager(
            context = this,
            geckoView = geckoView,
            onUrlChanged = { newUrl ->
                runOnUiThread {
                    if (!urlAddressInput.hasFocus()) {
                        urlAddressInput.setText(newUrl)
                    }
                }
            },
            onProgressChanged = { progress ->
                runOnUiThread {
                    progressBar.progress = progress
                    progressBar.visibility = if (progress in 1..99) View.VISIBLE else View.GONE
                }
            }
        )

        geckoManager.initialize()

        // Evento al presionar Enter / Ir en el teclado en pantalla del TV
        urlAddressInput.setOnEditorActionListener { _, actionId, keyEvent ->
            val isEnterKey = keyEvent != null &&
                keyEvent.keyCode == KeyEvent.KEYCODE_ENTER &&
                keyEvent.action == KeyEvent.ACTION_DOWN

            if (actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                isEnterKey
            ) {
                navegarDesdeBarra()
                true
            } else {
                false
            }
        }

        // Cargar página inicial
        val initialUrl = intent?.dataString ?: "https://doramasflix.io/"
        geckoManager.loadInputFromAddressBar(initialUrl)
        geckoView.requestFocus()
    }

    private fun navegarDesdeBarra() {
        val queryOrUrl = urlAddressInput.text.toString()
        geckoManager.loadInputFromAddressBar(queryOrUrl)
        // Ocultar el teclado virtual del TV tras buscar
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(urlAddressInput.windowToken, 0)
        geckoView.requestFocus()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        geckoManager.session.goBack()
    }
}
