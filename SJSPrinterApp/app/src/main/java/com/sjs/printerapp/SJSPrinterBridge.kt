package com.sjs.printerapp

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.Socket
import java.util.UUID

/**
 * الجسر الأصلي للطباعة — نفس الاسم والدوال يلي الكود بالموقع (index.html) عم يستدعيهم:
 *   window.SJSPrinter.listPairedDevices()
 *   window.SJSPrinter.printNetwork(ip, port, base64Data)
 *   window.SJSPrinter.printBluetooth(address, base64Data)
 * وبعد كل محاولة طباعة، لازم نرجّع الرد للموقع عن طريق:
 *   window.__onNativePrintResult(true/false, "رسالة")
 */
class SJSPrinterBridge(private val context: Context, private val webView: WebView) {

    // UUID القياسي لبروفايل SPP (Serial Port Profile) — نفسه لأغلب طابعات الفيش البلوتوث الكلاسيكية
    private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    @JavascriptInterface
    fun listPairedDevices(): String {
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return "[]"
            val bonded = adapter.bondedDevices ?: emptySet()
            val arr = JSONArray()
            for (d in bonded) {
                val obj = JSONObject()
                obj.put("name", d.name ?: "جهاز")
                obj.put("address", d.address)
                arr.put(obj)
            }
            arr.toString()
        } catch (e: SecurityException) {
            "[]" // ما في صلاحية بلوتوث ممنوحة بعد
        } catch (e: Exception) {
            "[]"
        }
    }

    @JavascriptInterface
    fun printBluetooth(address: String, base64Data: String) {
        Thread {
            val maxAttempts = 3
            var lastError: String? = null
            for (attempt in 1..maxAttempts) {
                var socket: BluetoothSocket? = null
                try {
                    val adapter = BluetoothAdapter.getDefaultAdapter()
                        ?: throw IOException("الجهاز ما بيدعم بلوتوث")
                    val device = adapter.getRemoteDevice(address)
                    socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                    adapter.cancelDiscovery()
                    socket.connect()
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    socket.outputStream.write(bytes)
                    socket.outputStream.flush()
                    Thread.sleep(300)
                    notifyResult(true, "انطبعت عبر البلوتوث" + if (attempt > 1) " (بعد إعادة محاولة)" else "")
                    return@Thread
                } catch (e: Exception) {
                    lastError = e.message ?: "خطأ غير معروف"
                    if (attempt < maxAttempts) Thread.sleep(1200)
                } finally {
                    try { socket?.close() } catch (_: Exception) {}
                }
            }
            notifyResult(false, "فشل البلوتوث بعد $maxAttempts محاولات: $lastError")
        }.start()
    }

    @JavascriptInterface
    fun printNetwork(ip: String, port: Int, base64Data: String) {
        Thread {
            // 🔁 إعادة محاولة تلقائية: طابعات الشبكة الرخيصة أحيانًا بترفض الاتصال الأول (مشغولة،
            // أو WiFi متلعثم لحظيًا) بس بتنجح بمحاولة تانية بعد ثانية أو ثنتين. هيك بدل ما نفشل
            // من أول محاولة (زي RawBT كان بيعمل)، منجرب لحد 3 مرات قبل ما نعلن فشل نهائي.
            val maxAttempts = 3
            var lastError: String? = null
            for (attempt in 1..maxAttempts) {
                var socket: Socket? = null
                try {
                    socket = Socket()
                    // ⏱️ مهلة أطول من 5 ثواني (كانت هاي مشكلة RawBT الأساسية) — 8 ثواني تعطي فرصة
                    // أكبر للشبكة المزدحمة وقت الذروة بدون ما تعلّق التطبيق لفترة طويلة كمان
                    socket.connect(java.net.InetSocketAddress(ip, port), 8000)
                    val bytes = Base64.decode(base64Data, Base64.DEFAULT)
                    socket.getOutputStream().write(bytes)
                    socket.getOutputStream().flush()
                    // 🔧 مهلة صغيرة قبل قفل الاتصال — بعض الطابعات الرخيصة بتقطع آخر بايتات
                    // لو قفلنا السوكيت فورًا بعد الكتابة مباشرة
                    Thread.sleep(300)
                    notifyResult(true, "انطبعت عبر الشبكة" + if (attempt > 1) " (بعد إعادة محاولة)" else "")
                    return@Thread
                } catch (e: Exception) {
                    lastError = e.message ?: "خطأ غير معروف"
                    if (attempt < maxAttempts) Thread.sleep(1200) // فسحة قبل إعادة المحاولة
                } finally {
                    try { socket?.close() } catch (_: Exception) {}
                }
            }
            notifyResult(false, "فشل الاتصال بالطابعة بعد $maxAttempts محاولات: $lastError")
        }.start()
    }

    // بيرجع الرد لصفحة الموقع دايمًا من الـUI thread (evaluateJavascript لازم يشتغل من فوق)
    private fun notifyResult(ok: Boolean, message: String) {
        webView.post {
            val safeMsg = message.replace("'", "\\'")
            webView.evaluateJavascript("window.__onNativePrintResult($ok, '$safeMsg')", null)
        }
    }
}
