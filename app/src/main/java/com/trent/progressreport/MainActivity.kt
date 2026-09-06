package com.trent.progressreport

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var cameraUri: Uri? = null
    private val fileChooserRequest = 1001
    private val cameraPermissionRequest = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = false
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                installLogoAspectRatioFix()
                installDefaultBrandLogos()
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                val accepts = params.acceptTypes.flatMap { it.split(",") }.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
                val imageIntent = accepts.isEmpty() || accepts.any { it.startsWith("image/") || it == "*/*" }
                if (params.isCaptureEnabled && imageIntent) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.CAMERA), cameraPermissionRequest)
                    } else launchCamera()
                    return true
                }
                cameraUri = null
                startActivityForResult(params.createIntent(), fileChooserRequest)
                return true
            }
        }
        webView.addJavascriptInterface(AndroidBridge(), "AndroidBridge")
        webView.loadUrl("file:///android_asset/index.html")
    }

    private fun launchCamera() {
        val dir = File(cacheDir, "shared")
        if (!dir.exists()) dir.mkdirs()
        val photoFile = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        cameraUri = FileProvider.getUriForFile(this, "${BuildConfig.APPLICATION_ID}.fileprovider", photoFile)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, cameraUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            clipData = ClipData.newRawUri("output", cameraUri)
        }
        try { startActivityForResult(intent, fileChooserRequest) } catch (e: Exception) {
            filePathCallback?.onReceiveValue(null); filePathCallback = null; cameraUri = null
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == cameraPermissionRequest) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) launchCamera()
            else { filePathCallback?.onReceiveValue(null); filePathCallback = null }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != fileChooserRequest) return
        val callback = filePathCallback ?: return
        filePathCallback = null
        if (resultCode != Activity.RESULT_OK) { callback.onReceiveValue(null); cameraUri = null; return }
        val clip = data?.clipData
        val result: Array<Uri>? = when {
            cameraUri != null -> arrayOf(cameraUri!!)
            clip != null -> Array(clip.itemCount) { index -> clip.getItemAt(index).uri }
            data?.data != null -> arrayOf(data.data!!)
            else -> null
        }
        callback.onReceiveValue(result)
        cameraUri = null
    }

    private fun installLogoAspectRatioFix() {
        val js = """
            (function(){
              if(window.__trentLogoAspectFixInstalled)return;
              window.__trentLogoAspectFixInstalled=true;
              function install(){
                try{
                  if(typeof PptxGenJS==='undefined')return;
                  var oldAddSlide=PptxGenJS.prototype.addSlide;
                  PptxGenJS.prototype.addSlide=function(){
                    var sl=oldAddSlide.apply(this,arguments);
                    var oldAddImage=sl.addImage;
                    sl.addImage=function(opts){
                      try{
                        if(opts&&window.__trentLogoData&&opts.data===window.__trentLogoData&&window.__trentLogoRatio){
                          var maxW=opts.w||1,maxH=opts.h||1;
                          if(window.__trentLogoRatio>maxW/maxH)opts.h=maxW/window.__trentLogoRatio;
                          else opts.w=maxH*window.__trentLogoRatio;
                        }
                      }catch(e){}
                      return oldAddImage.call(this,opts);
                    };
                    return sl;
                  };
                }catch(e){}
              }
              install();
              document.addEventListener('DOMContentLoaded',install);
            })();
        """.trimIndent()
        webView.evaluateJavascript("javascript:$js", null)
    }

    private fun installDefaultBrandLogos() {
        val js = """
            (function(){
              if(window.__trentDefaultLogoSelectorInstalled)return;
              window.__trentDefaultLogoSelectorInstalled=true;
              var input=document.getElementById('logoInput');
              if(!input)return;
              var parent=input.parentElement;
              var select=document.createElement('select');
              select.id='trentDefaultLogo';
              select.innerHTML='<option value="">Custom logo / none</option><option value="westside.svg">Westside</option><option value="burnt-toast.svg">Burnt Toast</option><option value="zudio.svg">Zudio</option>';
              parent.insertBefore(select,input);
              var note=document.createElement('div');
              note.textContent='Choose a default brand logo or use the custom upload below.';
              note.style.margin='6px 0';
              note.style.fontSize='12px';
              parent.insertBefore(note,input);
              select.onchange=async function(){
                var value=this.value;
                if(!value){
                  input.value='';
                  window.__trentLogoData='';
                  window.__trentLogoRatio=0;
                  try{logo='';}catch(e){}
                  return;
                }
                try{
                  var text=await(await fetch('branding/'+value)).text();
                  var dataUrl='data:image/svg+xml;base64,'+btoa(unescape(encodeURIComponent(text)));
                  window.__trentLogoData=dataUrl;
                  window.__trentLogoRatio=1;
                  try{logo=dataUrl;}catch(e){}
                  var blob=new Blob([text],{type:'image/svg+xml'});
                  var file=new File([blob],value,{type:'image/svg+xml'});
                  var dt=new DataTransfer();
                  dt.items.add(file);
                  input.files=dt.files;
                  input.dispatchEvent(new Event('change',{bubbles:true}));
                  try{logo=dataUrl;}catch(e){}
                }catch(e){console.error(e);}
              };
            })();
        """.trimIndent()
        webView.evaluateJavascript("javascript:$js", null)
    }

    inner class AndroidBridge {
        @android.webkit.JavascriptInterface
        fun saveFile(fileName: String, mimeType: String, base64Data: String) {
            val data = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
            val values = android.content.ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
            contentResolver.openOutputStream(uri)?.use { it.write(data) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
        }

        @android.webkit.JavascriptInterface
        fun shareFile(fileName: String, mimeType: String, base64Data: String) {
            val dir = File(cacheDir, "shared")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, fileName)
            FileOutputStream(file).use { it.write(android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)) }
            val uri = FileProvider.getUriForFile(this@MainActivity, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share report"))
        }
    }
}
