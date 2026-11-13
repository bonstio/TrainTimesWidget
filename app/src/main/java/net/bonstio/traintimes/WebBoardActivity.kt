package net.bonstio.traintimes

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Activity hosting the integrated single-page Worldline Tiger Customer Information Screen (CIS) web app.
 */
class WebBoardActivity : AppCompatActivity() {

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar

    private var currentFrom: String = "STP"
    private var currentTo: String = "HPD"
    private var isFilterActive: Boolean = true
    private var canScrollUp: Boolean = false

    companion object {
        private const val TAG = "WebBoardActivity"
        const val EXTRA_FROM_STATION = "extra_from_station"
        const val EXTRA_TO_STATION = "extra_to_station"
        const val EXTRA_FILTER = "extra_filter"
    }

    private class BridgeInterface(
        private val onScroll: (Boolean) -> Unit,
        private val onRouteSwapped: (String, String) -> Unit,
        private val onFilterToggled: (Boolean) -> Unit,
        private val onRefreshRequested: () -> Unit
    ) {
        @JavascriptInterface
        fun onScrollChanged(hasScrolledDown: Boolean) {
            onScroll(hasScrolledDown)
        }

        @JavascriptInterface
        fun onRouteSwapped(newFrom: String, newTo: String) {
            onRouteSwapped(newFrom, newTo)
        }

        @JavascriptInterface
        fun onFilterToggled(filterActive: Boolean) {
            onFilterToggled(filterActive)
        }

        @JavascriptInterface
        fun onRefreshRequested() {
            onRefreshRequested()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_web_board)

        window.statusBarColor = Color.parseColor("#0033A0") // Tiger Royal Blue
        window.navigationBarColor = Color.BLACK

        swipeRefresh = findViewById(R.id.swipe_refresh)
        webView = findViewById(R.id.web_view)
        progressBar = findViewById(R.id.progress_bar)

        setupSwipeRefresh()
        resolveRoute(intent)
        setupWebView()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        loadBoard()

        // Background refresh widgets on entering webview to keep departures consistent
        refreshWidgetsInBackground(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resolveRoute(intent)
        updateRouteInWebView()
        fetchAndInjectLiveDepartures()
        refreshWidgetsInBackground(this)
    }

    private fun setupSwipeRefresh() {
        swipeRefresh.setColorSchemeColors(
            Color.parseColor("#0033A0"), // Tiger Royal Blue
            Color.parseColor("#f8fa2b")  // Tiger Yellow
        )
        swipeRefresh.setProgressBackgroundColorSchemeColor(Color.parseColor("#1A1C1E"))

        swipeRefresh.setOnRefreshListener {
            Log.d(TAG, "SwipeRefreshLayout: OnRefresh triggered")
            refreshWidgetsInBackground(this)
            refreshBoard()
        }

        // Only allow pull-to-refresh when at the top of the web page
        swipeRefresh.setOnChildScrollUpCallback { _, _ ->
            val cannotRefresh = canScrollUp || webView.scrollY > 0 || webView.canScrollVertically(-1)
            Log.v(TAG, "canChildScrollUp: $cannotRefresh (bridge=$canScrollUp, scrollY=${webView.scrollY}, canScrollVert=${webView.canScrollVertically(-1)})")
            cannotRefresh
        }
    }

    private fun resolveRoute(intent: Intent?) {
        var from = intent?.getStringExtra(EXTRA_FROM_STATION)?.trim()?.uppercase()
        var to = intent?.getStringExtra(EXTRA_TO_STATION)?.trim()?.uppercase()

        // Fallback: check query parameters if passed in data Uri
        val dataUri = intent?.data
        if (dataUri != null) {
            if (from.isNullOrEmpty()) from = dataUri.getQueryParameter("from")?.trim()?.uppercase()
            if (to.isNullOrEmpty()) to = dataUri.getQueryParameter("to")?.trim()?.uppercase()
        }

        currentFrom = if (!from.isNullOrEmpty()) from else "STP"
        currentTo = if (!to.isNullOrEmpty()) to else "HPD"
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.setBackgroundColor(Color.BLACK)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            builtInZoomControls = false
            displayZoomControls = false
        }

        webView.addJavascriptInterface(BridgeInterface(
            onScroll = { hasScrolled ->
                canScrollUp = hasScrolled
            },
            onRouteSwapped = { newFrom, newTo ->
                runOnUiThread {
                    Log.d(TAG, "Bridge: onRouteSwapped to $newFrom -> $newTo")
                    currentFrom = newFrom.uppercase().trim()
                    currentTo = newTo.uppercase().trim()
                    fetchAndInjectLiveDepartures()
                }
            },
            onFilterToggled = { filterActive ->
                runOnUiThread {
                    Log.d(TAG, "Bridge: onFilterToggled to $filterActive")
                    isFilterActive = filterActive
                    fetchAndInjectLiveDepartures()
                }
            },
            onRefreshRequested = {
                runOnUiThread {
                    Log.d(TAG, "Bridge: onRefreshRequested")
                    fetchAndInjectLiveDepartures()
                }
            }
        ), "AndroidBridge")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress < 100) {
                    progressBar.visibility = View.VISIBLE
                    progressBar.progress = newProgress
                } else {
                    progressBar.visibility = View.GONE
                }
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                updateRouteInWebView()
                fetchAndInjectLiveDepartures()
            }
        }
    }

    private fun loadBoard() {
        val url = "file:///android_asset/tiger.html?from=$currentFrom&to=$currentTo&filter=true"
        webView.loadUrl(url)
    }

    private fun refreshBoard() {
        fetchAndInjectLiveDepartures()
        val js = """
            if (window.TigerBoard && typeof window.TigerBoard.refresh === 'function') {
                window.TigerBoard.refresh();
            }
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun updateRouteInWebView() {
        val js = """
            if (window.TigerBoard && typeof window.TigerBoard.setRoute === 'function') {
                window.TigerBoard.setRoute({ from: '$currentFrom', to: '$currentTo', filterActive: $isFilterActive });
            }
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun fetchAndInjectLiveDepartures() {
        val apiKey = ApiKeyManager.getApiKey(this)
        if (apiKey.isNullOrEmpty()) {
            Log.w(TAG, "No API key available for live departures")
            swipeRefresh.isRefreshing = false
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching live departures for $currentFrom -> $currentTo (filterActive=$isFilterActive)...")
                val client = RailDataClient(apiKey)
                val jsonString = client.getWebDeparturesJson(
                    fromStation = currentFrom,
                    toStation = currentTo,
                    filterByTo = isFilterActive,
                    numRows = 25
                )
                withContext(Dispatchers.Main) {
                    swipeRefresh.isRefreshing = false
                    val js = "if (window.TigerBoard && typeof window.TigerBoard.setDepartures === 'function') { window.TigerBoard.setDepartures($jsonString); }"
                    webView.evaluateJavascript(js, null)
                    Log.d(TAG, "Injected live departures successfully into WebView")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch and inject live departures", e)
                withContext(Dispatchers.Main) {
                    swipeRefresh.isRefreshing = false
                }
            }
        }
    }

    /**
     * Triggers a background refresh of the widgets to keep departures as consistent as possible.
     */
    private fun refreshWidgetsInBackground(context: Context) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, TrainTimesWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                if (ApiKeyManager.hasApiKey(context)) {
                    val data = Data.Builder()
                        .putIntArray(WidgetUpdateWorker.KEY_WIDGET_IDS, ids)
                        .build()

                    val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                        .setInputData(data)
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build()

                    WorkManager.getInstance(context).enqueueUniqueWork(
                        "widget_webview_refresh_${System.currentTimeMillis()}",
                        ExistingWorkPolicy.REPLACE,
                        request
                    )
                }

                val updateIntent = Intent(context, TrainTimesWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(updateIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to refresh widgets in background", e)
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
