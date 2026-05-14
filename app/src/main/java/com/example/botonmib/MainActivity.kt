package com.example.botonmib

import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.example.botonmib.ui.theme.BotonMIBTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BotonMIBTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Headers que queremos rastrear para ver cuándo se pierden
                    val authHeaders = mapOf(
                        "Authorization" to "Bearer TU_TOKEN_AQUI",
                        "X-Custom-Auth" to "ValidacionSegura",
                        "X-Device-Id" to "Android-12345",
                        "Platform" to "Android"
                    )

                    WebViewScreen(
                        url = "https://enigma-mdp-qa.apps.ambientesbc.com/web/transfer-gateway/checkout/_XEKkyxJxNk",//"https://httpbin.org/headers",
                        headers = authHeaders,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun WebViewScreen(
    url: String,
    headers: Map<String, String>,
    modifier: Modifier = Modifier
) {
    // Variable de estado para controlar cuándo iniciar el WebView
    var iniciarWebView by remember { mutableStateOf(false) }
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (!iniciarWebView) {
            androidx.compose.material3.Button(onClick = { iniciarWebView = true }) {
                Text("Iniciar Flujo en WebView")
            }
        }
        else {
        AndroidView(
            modifier = modifier.fillMaxSize(),
            factory = { context ->
                WebView.setWebContentsDebuggingEnabled(true)
                WebView(context).apply {
                    // Configuración de Cookies
                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        javaScriptCanOpenWindowsAutomatically = true
                    }

                    webViewClient = object : WebViewClient() {

                        // 1. Monitoreo de Redirecciones (Comportamiento natural: NO re-inyectamos)
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val nextUrl = request?.url.toString()
                            Log.w("WebViewSpy", "🔍 Navegando a: $nextUrl")
                            Log.i(
                                "WebViewSpy",
                                "ℹ️ Verificando si el WebView mantiene los headers por sí solo..."
                            )
                            // Retornamos false para que el sistema maneje la URL de forma nativa
                            return false
                        }

                        // 2. Auditoría de Headers en cada petición saliente
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val requestUrl = request?.url.toString()
                            val requestHeaders = request?.requestHeaders

                            if (request?.isForMainFrame == true) {
                                Log.d(
                                    "WebViewSpy",
                                    "--------------------------------------------------"
                                )
                                Log.d("WebViewSpy", "🌍 PETICIÓN DETECTADA: $requestUrl")

                                // 1. Auditar Headers
                                headers.keys.forEach { key ->
                                    val value = requestHeaders?.get(key)
                                    if (value != null) {
                                        Log.v("WebViewSpy", "✅ Header PRESENTE: $key")
                                    } else {
                                        Log.e("WebViewSpy", "❌ Header PERDIDO: $key")
                                    }
                                }

                                // 2. Auditar Cookies
                                val cookieManager = CookieManager.getInstance()
                                val currentCookies = cookieManager.getCookie(requestUrl)
                                if (!currentCookies.isNullOrEmpty()) {
                                    Log.v(
                                        "WebViewSpy",
                                        "🍪 Cookies PRESENTES para esta URL: $currentCookies"
                                    )
                                } else {
                                    Log.e("WebViewSpy", "⚠️ No hay cookies para esta URL")
                                }

                                Log.d(
                                    "WebViewSpy",
                                    "--------------------------------------------------"
                                )
                            }

                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            Log.i("WebViewSpy", "🏁 Carga FINALIZADA en: $url")

                            // Consultamos las cookies exactas que el navegador tiene para esta URL final
                            val finalCookies = CookieManager.getInstance().getCookie(url)
                            if (!finalCookies.isNullOrEmpty()) {
                                Log.v("WebViewSpy", "🍪 Cookies en destino final: $finalCookies")
                            } else {
                                Log.w("WebViewSpy", "⚠️ Cero cookies en el destino final")
                            }
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            Log.i("WebViewSpy", "🚩 Iniciando carga en el WebView: $url")
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            if (request?.isForMainFrame == true) {
                                Log.e(
                                    "WebViewSpy",
                                    "🚫 Error: ${error?.description} en ${request.url}"
                                )
                            }
                        }
                    }

                    // Carga inicial (Aquí es donde enviamos los headers por primera vez)
                    Log.d("WebViewSpy", "🚀 Iniciando carga original con headers...")
                    loadUrl(url, headers)
                }
            },
            update = { /* No requerido */ }
        )
    }
}
}
