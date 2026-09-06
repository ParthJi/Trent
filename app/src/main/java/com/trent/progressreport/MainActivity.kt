package com.trent.progressreport

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.webkit.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.Base64

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private var chooser: ValueCallback<Array<Uri>>? = null
    private val REQ = 77

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        web = WebView(this)
        setContentView(web)
        requestRuntimePermissions()
        WebView.setWebContentsDebuggingEnabled(false)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.allowFileAccess = true
        web.settings.allowContentAccess = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        web.addJavascriptInterface(AndroidBridge(), "AndroidBridge")
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(v: WebView?, cb: ValueCallback<Array<Uri>>?, p: FileChooserParams?): Boolean {
                chooser?.onReceiveValue(null)
                chooser = cb
                return try {
                    val params = p ?: throw IllegalArgumentException("Missing chooser parameters")
                    val intent = if (params.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = params.acceptTypes.firstOrNull { it.isNotBlank() } ?: "image/*"
                            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                        }
                    } else {
                        params.createIntent()
                    }
                    startActivityForResult(intent, REQ)
                    true
                } catch (_: Exception) {
                    chooser = null
                    false
                }
            }
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread { request.grant(request.resources) }
            }
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return !(request.url.scheme == "http" || request.url.scheme == "https")
            }
        }
        web.loadUrl("file:///android_asset/index.html")
    }

    private fun requestRuntimePermissions() {
        val list = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) {
            list += Manifest.permission.READ_MEDIA_IMAGES
            list += Manifest.permission.READ_MEDIA_VIDEO
        }
        if (Build.VERSION.SDK_INT >= 23 && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            list += Manifest.permission.CAMERA
        }
        if (list.isNotEmpty()) ActivityCompat.requestPermissions(this, list.toTypedArray(), 9)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ) return
        val uris = if (resultCode == RESULT_OK && data != null) {
            val list = ArrayList<Uri>()
            data.clipData?.let { clip ->
                for (i in 0 until clip.itemCount) list.add(clip.getItemAt(i).uri)
            }
            if (list.isEmpty()) data.data?.let { list.add(it) }
            if (list.isNotEmpty()) list.toTypedArray() else null
        } else null
        chooser?.onReceiveValue(uris)
        chooser = null
    }

    inner class AndroidBridge {
        @JavascriptInterface fun saveFile(name: String, mime: String, base64: String) {
            try {
                val bytes = Base64.getDecoder().decode(base64)
                val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                if (Build.VERSION.SDK_INT >= 29) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, safe)
                        put(MediaStore.Downloads.MIME_TYPE, mime)
                        put(MediaStore.Downloads.IS_PENDING, 1)
                    }
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw Exception("Downloads unavailable")
                    contentResolver.openOutputStream(uri)!!.use { it.write(bytes) }
                    values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                    runOnUiThread { Toast.makeText(this@MainActivity, "Saved to Downloads/$safe", Toast.LENGTH_LONG).show() }
                } else {
                    val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)!!
                    dir.mkdirs()
                    FileOutputStream(File(dir, safe)).use { it.write(bytes) }
                    runOnUiThread { Toast.makeText(this@MainActivity, "Saved to app Downloads/$safe", Toast.LENGTH_LONG).show() }
                }
                cacheForShare(safe, mime, bytes)
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }

        @JavascriptInterface fun shareFile(name: String, mime: String, base64: String) {
            try {
                val bytes = Base64.getDecoder().decode(base64)
                val file = cacheForShare(name, mime, bytes)
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
                val i = Intent(Intent.ACTION_SEND).apply {
                    type = mime
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(i, "Share report"))
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this@MainActivity, "Share failed: ${e.message}", Toast.LENGTH_LONG).show() }
            }
        }

        private fun cacheForShare(name: String, mime: String, bytes: ByteArray): File {
            val dir = File(cacheDir, "shared")
            dir.mkdirs()
            val f = File(dir, name.replace(Regex("[^A-Za-z0-9._-]"), "_"))
            FileOutputStream(f).use { it.write(bytes) }
            return f
        }
    }
}
