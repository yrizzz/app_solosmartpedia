package com.solosmartpedia.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.solosmartpedia.app.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var fileUploadCallback: ValueCallback<Array<Uri>>? = null
    private var cameraImageUri: Uri? = null

    // NFC
    private var nfcAdapter: NfcAdapter? = null
    private var pendingNfcIntent: PendingIntent? = null

    // Bluetooth Printer
    private lateinit var printerManager: BluetoothPrinterManager

    private val btEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK) openPrinterSheet()
    }

    private val btPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.all { it }) openPrinterSheet()
        else Toast.makeText(this, getString(R.string.bt_permission_denied), Toast.LENGTH_SHORT).show()
    }

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val results = when {
                data?.clipData != null -> {
                    val count = data.clipData!!.itemCount
                    Array(count) { data.clipData!!.getItemAt(it).uri }
                }
                data?.data != null -> arrayOf(data.data!!)
                cameraImageUri != null -> arrayOf(cameraImageUri!!)
                else -> null
            }
            fileUploadCallback?.onReceiveValue(results)
        } else {
            fileUploadCallback?.onReceiveValue(null)
        }
        fileUploadCallback = null
        cameraImageUri = null
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) openFileChooser()
        else {
            Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
            fileUploadCallback?.onReceiveValue(null)
            fileUploadCallback = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNfc()
        setupPrinter()
        setupWebView()
        setupSwipeRefresh()

        if (isNetworkAvailable()) loadWebsite() else showOfflinePage()
    }

    // ─── NFC Setup ───────────────────────────────────────────────────────────

    private fun setupNfc() {
        val nfcManager = getSystemService(Context.NFC_SERVICE) as NfcManager
        nfcAdapter = nfcManager.defaultAdapter

        if (nfcAdapter == null) {
            // Device has no NFC — hide button silently
            binding.btnNfc.visibility = View.GONE
            return
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_MUTABLE else 0

        pendingNfcIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            flags
        )

        binding.btnNfc.visibility = View.VISIBLE
        binding.btnNfc.setOnClickListener { showNfcScanOverlay() }
    }

    private fun showNfcScanOverlay() {
        if (nfcAdapter?.isEnabled == false) {
            Toast.makeText(this, getString(R.string.nfc_disabled), Toast.LENGTH_LONG).show()
            startActivity(Intent(android.provider.Settings.ACTION_NFC_SETTINGS))
            return
        }
        binding.nfcOverlay.visibility = View.VISIBLE
        binding.btnNfcClose.setOnClickListener { hideNfcOverlay() }
    }

    private fun hideNfcOverlay() {
        binding.nfcOverlay.visibility = View.GONE
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(
            this, pendingNfcIntent,
            arrayOf(
                IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED),
                IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
                IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED)
            ),
            null
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action in listOf(
                NfcAdapter.ACTION_TAG_DISCOVERED,
                NfcAdapter.ACTION_NDEF_DISCOVERED,
                NfcAdapter.ACTION_TECH_DISCOVERED
            )
        ) {
            val tag: Tag? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            else
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)

            tag?.let { handleNfcTag(it) }
        }
    }

    private fun handleNfcTag(tag: Tag) {
        hideNfcOverlay()
        val uid = NfcHelper.getCardUid(tag)
        val cardType = NfcHelper.getCardType(tag)

        // Send to WebView JavaScript
        val js = "javascript:if(typeof onNfcRead==='function'){onNfcRead('$uid','$cardType');}"
        binding.webView.post { binding.webView.loadUrl(js) }

        Toast.makeText(
            this,
            "${getString(R.string.nfc_read_success)}: $cardType\nUID: $uid",
            Toast.LENGTH_LONG
        ).show()
    }

    // ─── Bluetooth Printer ────────────────────────────────────────────────────

    private fun setupPrinter() {
        printerManager = BluetoothPrinterManager(this)
        binding.btnPrinter.setOnClickListener { checkBtAndOpenSheet() }
        updatePrinterFabState()
    }

    private fun updatePrinterFabState() {
        val tint = if (printerManager.isConnected) R.color.success else R.color.primary
        binding.btnPrinter.backgroundTintList =
            android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, tint))
    }

    @SuppressLint("MissingPermission")
    private fun checkBtAndOpenSheet() {
        if (!printerManager.isBluetoothAvailable()) {
            Toast.makeText(this, getString(R.string.bt_not_available), Toast.LENGTH_SHORT).show()
            return
        }
        // Request permissions on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val needed = listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ).filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
            if (needed.isNotEmpty()) { btPermLauncher.launch(needed.toTypedArray()); return }
        }
        if (!printerManager.isBluetoothEnabled()) {
            btEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        openPrinterSheet()
    }

    private fun openPrinterSheet() {
        PrinterBottomSheet(printerManager) { connected, name ->
            updatePrinterFabState()
            val js = if (connected)
                "javascript:if(typeof onPrinterConnected==='function'){onPrinterConnected('$name');}"
            else
                "javascript:if(typeof onPrinterDisconnected==='function'){onPrinterDisconnected();}"
            binding.webView.post { binding.webView.loadUrl(js) }
        }.show(supportFragmentManager, "printer")
    }

    // ─── WebView ─────────────────────────────────────────────────────────────

    private fun setupWebView() {
        binding.webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
                loadsImagesAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = "${userAgentString} SoloSmartPediaApp/1.0"
                builtInZoomControls = false
                displayZoomControls = false
            }
            webViewClient = SoloWebViewClient()
            webChromeClient = SoloWebChromeClient()
            addJavascriptInterface(WebAppInterface(this@MainActivity), "AndroidBridge")
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.apply {
            setColorSchemeColors(
                ContextCompat.getColor(this@MainActivity, R.color.primary),
                ContextCompat.getColor(this@MainActivity, R.color.primary_dark)
            )
            setOnRefreshListener {
                if (isNetworkAvailable()) { hideOfflinePage(); binding.webView.reload() }
                else { isRefreshing = false; showOfflinePage() }
            }
        }
    }

    private fun loadWebsite() { binding.webView.loadUrl(BASE_URL) }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun showOfflinePage() {
        binding.webView.visibility = View.GONE
        binding.swipeRefresh.isEnabled = false
        binding.offlineLayout.visibility = View.VISIBLE
        binding.btnRetry.setOnClickListener {
            if (isNetworkAvailable()) { hideOfflinePage(); loadWebsite() }
            else Toast.makeText(this, getString(R.string.still_offline), Toast.LENGTH_SHORT).show()
        }
    }

    private fun hideOfflinePage() {
        binding.offlineLayout.visibility = View.GONE
        binding.webView.visibility = View.VISIBLE
        binding.swipeRefresh.isEnabled = true
    }

    private fun saveLoginSession(url: String) {
        val prefs = getSharedPreferences(SplashActivity.SESSION_PREFS, Context.MODE_PRIVATE)
        val isLoggedIn = !url.contains("/auth") && !url.contains("/login") && url.startsWith(BASE_URL)
        prefs.edit().putBoolean(SplashActivity.KEY_IS_LOGGED_IN, isLoggedIn).apply()
    }

    // ─── File Upload / Camera ─────────────────────────────────────────────────

    private fun checkAndRequestPermissions() {
        val needed = buildList {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (needed.isEmpty()) openFileChooser() else permissionLauncher.launch(needed.toTypedArray())
    }

    private fun openFileChooser() {
        val photoFile = createImageFile()
        cameraImageUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
        }
        val galleryIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "application/pdf", "text/*"))
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        }
        filePickerLauncher.launch(
            Intent.createChooser(galleryIntent, getString(R.string.choose_file)).apply {
                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
            }
        )
    }

    private fun createImageFile(): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return File.createTempFile("IMG_${stamp}_", ".jpg", getExternalFilesDir(Environment.DIRECTORY_PICTURES))
    }

    override fun onBackPressed() {
        when {
            binding.nfcOverlay.visibility == View.VISIBLE -> hideNfcOverlay()
            binding.webView.canGoBack() -> binding.webView.goBack()
            else -> super.onBackPressed()
        }
    }

    // ─── WebViewClient / ChromeClient ─────────────────────────────────────────

    inner class SoloWebViewClient : WebViewClient() {
        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            binding.progressBar.visibility = View.VISIBLE
        }
        override fun onPageFinished(view: WebView?, url: String?) {
            binding.progressBar.visibility = View.GONE
            binding.swipeRefresh.isRefreshing = false
            url?.let { saveLoginSession(it) }
        }
        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
            if (request?.isForMainFrame == true) {
                binding.progressBar.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
                if (!isNetworkAvailable()) showOfflinePage()
            }
        }
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            val url = request?.url?.toString() ?: return false
            return when {
                url.startsWith("https://solosmartpedia.com") || url.startsWith("http://solosmartpedia.com") -> false
                url.startsWith("mailto:") || url.startsWith("tel:") -> {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))); true
                }
                else -> false
            }
        }
    }

    inner class SoloWebChromeClient : WebChromeClient() {
        override fun onProgressChanged(view: WebView?, newProgress: Int) {
            binding.progressBar.progress = newProgress
            binding.progressBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
        }
        override fun onShowFileChooser(
            webView: WebView?, filePathCallback: ValueCallback<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
        ): Boolean {
            fileUploadCallback?.onReceiveValue(null)
            fileUploadCallback = filePathCallback
            checkAndRequestPermissions()
            return true
        }
    }

    inner class WebAppInterface(private val ctx: Context) {
        @JavascriptInterface fun showToast(msg: String) =
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

        @JavascriptInterface fun closeApp() = (ctx as? Activity)?.finish()

        @JavascriptInterface fun isNfcAvailable(): Boolean = nfcAdapter != null
        @JavascriptInterface fun isNfcEnabled(): Boolean = nfcAdapter?.isEnabled == true
        @JavascriptInterface fun openNfcScan() = runOnUiThread { showNfcScanOverlay() }

        // ── Printer bridge ────────────────────────────────────────
        @JavascriptInterface fun isPrinterAvailable(): Boolean =
            printerManager.isBluetoothAvailable()

        @JavascriptInterface fun isPrinterConnected(): Boolean =
            printerManager.isConnected

        @JavascriptInterface fun getPrinterName(): String =
            printerManager.connectedDeviceName

        @JavascriptInterface fun openPrinterSettings() =
            runOnUiThread { checkBtAndOpenSheet() }

        /** Print receipt from JSON string */
        @JavascriptInterface fun printReceipt(json: String) {
            if (!printerManager.isConnected) {
                runOnUiThread {
                    Toast.makeText(ctx, ctx.getString(R.string.printer_not_connected), Toast.LENGTH_SHORT).show()
                    checkBtAndOpenSheet()
                }
                return
            }
            printerManager.printJson(json,
                onSuccess = {
                    runOnUiThread {
                        val js = "javascript:if(typeof onPrintSuccess==='function'){onPrintSuccess();}"
                        binding.webView.loadUrl(js)
                    }
                },
                onError = { err ->
                    runOnUiThread {
                        Toast.makeText(ctx, "Print gagal: $err", Toast.LENGTH_SHORT).show()
                        val js = "javascript:if(typeof onPrintError==='function'){onPrintError('${err.replace("'","\\'")}');}"
                        binding.webView.loadUrl(js)
                    }
                }
            )
        }

        /** Print plain text */
        @JavascriptInterface fun printText(text: String) {
            if (!printerManager.isConnected) {
                runOnUiThread { checkBtAndOpenSheet() }
                return
            }
            printerManager.print(
                EscPosHelper.buildPlain(text),
                onSuccess = {},
                onError   = { err -> runOnUiThread { Toast.makeText(ctx, err, Toast.LENGTH_SHORT).show() } }
            )
        }
    }

    companion object {
        const val BASE_URL = "https://solosmartpedia.com/auth"
    }
}
