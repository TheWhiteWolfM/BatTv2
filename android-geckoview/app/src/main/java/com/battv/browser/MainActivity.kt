package com.battv.browser

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import org.mozilla.geckoview.GeckoView

/**
 * MainActivity de Bat TV para Android TV / Google TV.
 * Conecta la barra de direcciones/búsqueda y controles D-Pad del control remoto
 * directamente con BatTvGeckoViewManager sin interfaces de búsqueda estáticas locales.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var geckoView: GeckoView
    private lateinit var urlAddressInput: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var geckoManager: BatTvGeckoViewManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inicialización de vistas principales (GeckoView + Barra de direcciones superior)
        geckoView = GeckoView(this)
        urlAddressInput = EditText(this)
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)

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

        // PUNTO 4: Al presionar "Ir" / Enter en la barra de direcciones o búsqueda,
        // navegar directamente al sitio o a https://www.google.com/search?q=... dentro de GeckoView
        urlAddressInput.setOnEditorActionListener { textView, actionId, keyEvent ->
            val isEnterKey = keyEvent != null &&
                keyEvent.keyCode == KeyEvent.KEYCODE_ENTER &&
                keyEvent.action == KeyEvent.ACTION_DOWN

            if (actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                isEnterKey
            ) {
                val queryOrUrl = textView.text.toString()
                geckoManager.loadInputFromAddressBar(queryOrUrl)
                geckoView.requestFocus()
                true
            } else {
                false
            }
        }

        // Cargar página inicial o intent entrante
        val initialUrl = intent?.dataString ?: "https://doramasflix.io/"
        geckoManager.loadInputFromAddressBar(initialUrl)
    }

    override fun onBackPressed() {
        // En Android TV, el botón Atrás del control remoto retrocede en el historial de GeckoView
        geckoManager.session.goBack()
    }
}
