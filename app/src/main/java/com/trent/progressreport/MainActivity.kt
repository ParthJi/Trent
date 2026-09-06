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
        cameraUri = FileProvider.getUriForFile(this, "com.trent.progressreport.fileprovider", photoFile)
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, cameraUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            clipData = ClipData.newRawUri("output", cameraUri)
        }
        try { startActivityForResult(intent, fileChooserRequest) } catch (e: Exception) {
            filePathCallback?.onReceiveValue(null)
            filePathCallback = null
            cameraUri = null
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
        if (resultCode != Activity.RESULT_OK) {
            callback.onReceiveValue(null)
            cameraUri = null
            return
        }
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
              if(window.__trentBrandSelectorInstalled)return;
              window.__trentBrandSelectorInstalled=true;
              var input=document.getElementById('logoInput');
              if(!input)return;
              var brandingCard=input.closest('.card');
              if(brandingCard)brandingCard.remove();
              var exportCard=document.getElementById('ppt')?.closest('.card');
              if(!exportCard)return;

              var card=document.createElement('section');
              card.className='card';
              card.innerHTML='<div class="section-title"><strong>Selected brands</strong><span class="mono small">04</span></div>'+
                '<div class="grid">'+
                '<label style="display:flex;align-items:center;gap:8px;text-transform:none;letter-spacing:0"><input type="checkbox" data-brand="westside.jpg" style="width:auto"> Westside</label>'+
                '<label style="display:flex;align-items:center;gap:8px;text-transform:none;letter-spacing:0"><input type="checkbox" data-brand="zudio.png" style="width:auto"> Zudio</label>'+
                '<label style="display:flex;align-items:center;gap:8px;text-transform:none;letter-spacing:0"><input type="checkbox" data-brand="burnt-toast.png" style="width:auto"> Burnt Toast</label>'+
                '</div><div class="small" style="margin-top:8px">Selected logos are embedded directly into the PPTX/PDF output. The old default brand block is removed.</div>';
              exportCard.parentNode.insertBefore(card,exportCard);

              var checks=[...card.querySelectorAll('input[data-brand]')];
              function fileToData(blob){return new Promise(function(resolve,reject){var r=new FileReader();r.onload=()=>resolve(r.result);r.onerror=reject;r.readAsDataURL(blob);});}
              function loadImage(src){return new Promise(function(resolve,reject){var img=new Image();img.onload=()=>resolve(img);img.onerror=reject;img.src=src;});}
              async function rebuild(){
                try{
                  var selected=checks.filter(c=>c.checked).map(c=>c.getAttribute('data-brand'));
                  if(!selected.length){window.__trentLogoData='';window.__trentLogoRatio=0;try{logo='';}catch(e){}return;}
                  var imgs=[];
                  for(var i=0;i<selected.length;i++){
                    var response=await fetch('branding/'+selected[i]);
                    if(!response.ok)throw new Error('Brand image not found: '+selected[i]);
                    imgs.push(await loadImage(await fileToData(await response.blob())));
                  }
                  var h=220,gap=28,pad=24,total=pad*2+gap*(imgs.length-1);
                  imgs.forEach(function(img){total+=Math.max(1,Math.round(h*img.naturalWidth/img.naturalHeight));});
                  var c=document.createElement('canvas');c.width=total;c.height=h+pad*2;
                  var ctx=c.getContext('2d');ctx.fillStyle='#ffffff';ctx.fillRect(0,0,c.width,c.height);
                  var x=pad;
                  imgs.forEach(function(img){var w=Math.max(1,Math.round(h*img.naturalWidth/img.naturalHeight));var y=pad+(h-h)/2;ctx.drawImage(img,x,y,w,h);x+=w+gap;});
                  var data=c.toDataURL('image/png');
                  window.__trentLogoData=data;window.__trentLogoRatio=c.width/c.height;try{logo=data;}catch(e){}
                }catch(e){console.error('Selected brand logos failed',e);}
              }
              checks.forEach(c=>c.addEventListener('change',rebuild));
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
            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0); contentResolver.update(uri, values, null, null)
        }
        @android.webkit.JavascriptInterface
        fun shareFile(fileName: String, mimeType: String, base64Data: String) {
            val dir=File(cacheDir,"shared"); if(!dir.exists())dir.mkdirs(); val file=File(dir,fileName)
            FileOutputStream(file).use{it.write(android.util.Base64.decode(base64Data,android.util.Base64.DEFAULT))}
            val uri=FileProvider.getUriForFile(this@MainActivity,"com.trent.progressreport.fileprovider",file)
            val intent=Intent(Intent.ACTION_SEND).apply{type=mimeType;putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            startActivity(Intent.createChooser(intent,"Share report"))
        }
    }
}