package net.bonstio.traintimes

import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import java.util.Locale
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

    private var initialFrom: String = "STP"
    private var initialTo: String = "HPD"
    private var currentFrom: String = "STP"
    private var currentTo: String = "HPD"
    private var considerNearbyStations: Boolean = true
    private var isFilterActive: Boolean = true
    private var canScrollUp: Boolean = false

    companion object {
        private const val TAG = "WebBoardActivity"
        const val EXTRA_FROM_STATION = "extra_from_station"
        const val EXTRA_TO_STATION = "extra_to_station"
        const val EXTRA_FILTER = "extra_filter"
        const val EXTRA_CONSIDER_NEARBY = "extra_consider_nearby"
    }

    private class BridgeInterface(
        private val onScrollCallback: (Boolean) -> Unit,
        private val onRouteSwappedCallback: (String, String) -> Unit,
        private val onFilterToggledCallback: (Boolean) -> Unit,
        private val onRefreshRequestedCallback: () -> Unit,
        private val onEditRouteRequestedCallback: () -> Unit,
        private val onResetRouteRequestedCallback: () -> Unit
    ) {
        @JavascriptInterface
        fun onScrollChanged(hasScrolledDown: Boolean) {
            onScrollCallback.invoke(hasScrolledDown)
        }

        @JavascriptInterface
        fun onRouteSwapped(newFrom: String, newTo: String) {
            onRouteSwappedCallback.invoke(newFrom, newTo)
        }

        @JavascriptInterface
        fun onFilterToggled(filterActive: Boolean) {
            onFilterToggledCallback.invoke(filterActive)
        }

        @JavascriptInterface
        fun onRefreshRequested() {
            onRefreshRequestedCallback.invoke()
        }

        @JavascriptInterface
        fun onEditRouteRequested() {
            onEditRouteRequestedCallback.invoke()
        }

        @JavascriptInterface
        fun onResetRouteRequested() {
            onResetRouteRequestedCallback.invoke()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        supportActionBar?.hide()
        setContentView(R.layout.activity_web_board)

        val webBoardRoot = findViewById<View>(R.id.web_board_root)
        val statusBarScrim = findViewById<View>(R.id.status_bar_scrim)
        ViewCompat.setOnApplyWindowInsetsListener(webBoardRoot) { view, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            statusBarScrim.layoutParams = statusBarScrim.layoutParams.apply {
                height = insets.top
            }
            view.setPadding(insets.left, 0, insets.right, insets.bottom)
            WindowInsetsCompat.CONSUMED
        }

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
        var nearby = intent?.getBooleanExtra(EXTRA_CONSIDER_NEARBY, true) ?: true

        // Fallback: check query parameters if passed in data Uri
        val dataUri = intent?.data
        if (dataUri != null) {
            if (from.isNullOrEmpty()) from = dataUri.getQueryParameter("from")?.trim()?.uppercase()
            if (to.isNullOrEmpty()) to = dataUri.getQueryParameter("to")?.trim()?.uppercase()
            dataUri.getQueryParameter("nearby")?.let { nearbyParam ->
                nearby = nearbyParam != "false" && nearbyParam != "0"
            }
        }

        val resolvedFrom = if (!from.isNullOrEmpty()) from else "STP"
        val resolvedTo = if (!to.isNullOrEmpty()) to else ""

        initialFrom = resolvedFrom
        initialTo = resolvedTo
        currentFrom = resolvedFrom
        currentTo = resolvedTo
        isFilterActive = resolvedTo.isNotEmpty()
        considerNearbyStations = nearby
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
            onScrollCallback = { hasScrolled ->
                canScrollUp = hasScrolled
            },
            onRouteSwappedCallback = { newFrom, newTo ->
                runOnUiThread {
                    Log.d(TAG, "Bridge: onRouteSwapped to $newFrom -> $newTo")
                    currentFrom = newFrom.uppercase().trim()
                    currentTo = newTo.uppercase().trim()
                    fetchAndInjectLiveDepartures()
                }
            },
            onFilterToggledCallback = { filterActive ->
                runOnUiThread {
                    Log.d(TAG, "Bridge: onFilterToggled to $filterActive")
                    isFilterActive = filterActive
                    fetchAndInjectLiveDepartures()
                }
            },
            onRefreshRequestedCallback = {
                runOnUiThread {
                    Log.d(TAG, "Bridge: onRefreshRequested")
                    fetchAndInjectLiveDepartures()
                }
            },
            onEditRouteRequestedCallback = {
                runOnUiThread {
                    Log.d(TAG, "Bridge: onEditRouteRequested")
                    showEditRouteDialog()
                }
            },
            onResetRouteRequestedCallback = {
                runOnUiThread {
                    Log.d(TAG, "Bridge: onResetRouteRequested")
                    resetToInitialRoute()
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
        val url = "file:///android_asset/tiger.html?from=$currentFrom&to=$currentTo&filter=$isFilterActive"
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
                window.TigerBoard.setRoute({
                    from: '$currentFrom',
                    to: '$currentTo',
                    initialFrom: '$initialFrom',
                    initialTo: '$initialTo',
                    filterActive: $isFilterActive
                });
            }
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun resetToInitialRoute() {
        currentFrom = initialFrom
        currentTo = initialTo
        isFilterActive = initialTo.isNotEmpty()
        updateRouteInWebView()
        fetchAndInjectLiveDepartures()
    }

    private fun showEditRouteDialog() {
        val stations = StationRepository.getStations(this)
        val view = layoutInflater.inflate(R.layout.dialog_web_route_edit, null)

        val fromLayout = view.findViewById<TextInputLayout>(R.id.dialog_from_layout)
        val fromInput = view.findViewById<MaterialAutoCompleteTextView>(R.id.dialog_from_input)
        val toLayout = view.findViewById<TextInputLayout>(R.id.dialog_to_layout)
        val toInput = view.findViewById<MaterialAutoCompleteTextView>(R.id.dialog_to_input)

        fun configureAutoComplete(
            layout: TextInputLayout,
            input: MaterialAutoCompleteTextView,
            initialCode: String
        ) {
            layout.setEndIconOnClickListener {
                input.setText("")
            }
            input.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    layout.isEndIconVisible = !s.isNullOrEmpty()
                }
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            })
            input.setAdapter(StationAdapter(this, stations))
            val matched = stations.find { it.code.equals(initialCode, ignoreCase = true) }
            if (matched != null) {
                input.setText(matched.toString(), false)
            } else {
                input.setText(initialCode, false)
            }
            layout.isEndIconVisible = initialCode.isNotEmpty()
            input.setOnClickListener { input.selectAll() }
            input.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    input.post { input.selectAll() }
                }
            }
        }

        configureAutoComplete(fromLayout, fromInput, currentFrom)
        configureAutoComplete(toLayout, toInput, currentTo)

        fun extractStationCode(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return ""
            val match = stations.find {
                it.code.equals(trimmed, ignoreCase = true) ||
                    it.name.equals(trimmed, ignoreCase = true) ||
                    it.toString().equals(trimmed, ignoreCase = true)
            }
            if (match != null) return match.code
            val parenMatch = Regex("\\(([A-Za-z]{3})\\)\\s*$").find(trimmed)
            if (parenMatch != null) return parenMatch.groupValues[1].uppercase(Locale.getDefault())
            return trimmed.uppercase(Locale.getDefault())
        }

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.edit_route_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val newFrom = extractStationCode(fromInput.text.toString())
                val newTo = extractStationCode(toInput.text.toString())
                if (newFrom.isNotEmpty()) {
                    currentFrom = newFrom
                    currentTo = newTo
                    isFilterActive = newTo.isNotEmpty()
                    updateRouteInWebView()
                    fetchAndInjectLiveDepartures()
                }
            }
            .setNeutralButton(R.string.reset_route) { _, _ ->
                resetToInitialRoute()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        fromInput.post {
            fromInput.selectAll()
            fromInput.requestFocus()
        }
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
                Log.d(TAG, "Fetching live departures for $currentFrom -> $currentTo (filterActive=$isFilterActive, nearby=$considerNearbyStations)...")
                val client = RailDataClient(apiKey)
                val jsonString = client.getWebDeparturesJson(
                    fromStation = currentFrom,
                    toStation = currentTo,
                    filterByTo = isFilterActive,
                    numRows = 25,
                    considerNearbyStations = considerNearbyStations
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
