package com.sjs.printerapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

// ⚠️ غيّري هاد الرابط لرابط موقعك المباشر (نفس الرابط يلي عم تفتحيه من المتصفح العادي)
private const val SITE_URL = "https://saberjosnack.github.io/App-sjs/"

class MainActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 🔒 نطلب صلاحيات البلوتوث وقت التشغيل (لازمة بأندرويد 12+)
        requestBluetoothPermissionsIfNeeded()

        val webView = findViewById<WebView>(R.id.webView)
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE // نفس فكرة Cache-Control اللي ضفتوها بالموقع — دايمًا آخر نسخة

        // 🖨️ هون بالضبط بينربط الجسر — لازم يكون اسمه "SJSPrinter" بالحرف عشان يطابق الكود بالموقع
        webView.addJavascriptInterface(SJSPrinterBridge(this, webView), "SJSPrinter")

        webView.webViewClient = WebViewClient() // يخلي كل التنقل جوا نفس الـWebView (مش يفتح متصفح خارجي)
        webView.loadUrl(SITE_URL)
    }

    private fun requestBluetoothPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val needed = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
            val missing = needed.filter {
                ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
            }
        } else {
            val needed = arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
            val missing = needed.filter {
                ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
            }
        }
    }
}
