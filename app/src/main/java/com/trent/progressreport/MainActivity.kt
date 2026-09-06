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
    private var cameraUri: Uri? = null
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
                cameraUri = null
                return try {
                    val params = p ?: throw IllegalArgumentException("Missing chooser parameters")
                    val accepts = params.acceptTypes.flatMap { it.split(',') }.map { it.trim().lowercase() }.filter { it.isNotBlank() }
                    val wantsImage = accepts.any { it.startsWith("image/") } || accepts.isEmpty()

                    if (params.isCaptureEnabled && wantsImage && ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                        val dir = File(cacheDir, "shared")
                        dir.mkdirs()
                        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
                        cameraUri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", file)
                        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                            putExtra(MediaStore.EXTRA_OUTPUT, cameraUri)
                            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            clipData = android.content.ClipData.newRawUri("camera", cameraUri)
                        }
                        if (cameraIntent.resolveActivity(packageManager) == null) throw ActivityNotFoundException("No camera app found")
                        startActivityForResult(cameraIntent, REQ)
                    } else {
                        val intent = if (params.mode == FileChooserParams.MODE_OPEN_MULTIPLE) {
                            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                type = accepts.firstOrNull { it.isNotBlank() } ?: "image/*"
                                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                            }
                        } else params.createIntent()
                        startActivityForResult(intent, REQ)
                    }
                    true
                } catch (_: Exception) {
                    chooser = null
                    cameraUri = null
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
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                installLogoAspectRatioFix()
                installDefaultBrandLogos()
            }
        }
        web.loadUrl("file:///android_asset/index.html")
    }

    private fun installLogoAspectRatioFix() {
        val js = """
        (function(){
          function wrapSlide(slide){
            if(!slide || slide.__trentLogoWrapped) return slide;
            const originalAddImage=slide.addImage;
            if(typeof originalAddImage!==\"function\") return slide;
            slide.addImage=function(opts){
              try{
                if(window.__trentLogoData && opts && opts.data===window.__trentLogoData && window.__trentLogoRatio){
                  const bw=Number(opts.w)||1, bh=Number(opts.h)||1, boxRatio=bw/bh, r=window.__trentLogoRatio;
                  let w,h;
                  if(r>boxRatio){ w=bw; h=bw/r; } else { h=bh; w=bh*r; }
                  opts=Object.assign({},opts,{x:Number(opts.x||0)+(bw-w)/2,y:Number(opts.y||0)+(bh-h)/2,w:w,h:h});
                }
              }catch(e){}
              return originalAddImage.call(this,opts);
            };
            slide.__trentLogoWrapped=true;
            return slide;
          }
          if(window.PptxGenJS && !window.__trentPptPatched){
            const originalAddSlide=window.PptxGenJS.prototype.addSlide;
            window.PptxGenJS.prototype.addSlide=function(){ return wrapSlide(originalAddSlide.apply(this,arguments)); };
            window.__trentPptPatched=true;
          }
          const input=document.getElementById(\"logoInput\");
          if(input && !input.__trentLogoListener){
            input.addEventListener(\"change\",function(e){
              const f=e.target.files && e.target.files[0]; if(!f) return;
              const reader=new FileReader();
              reader.onload=function(){
                window.__trentLogoData=reader.result;
                const im=new Image();
                im.onload=function(){ if(im.naturalWidth && im.naturalHeight) window.__trentLogoRatio=im.naturalWidth/im.naturalHeight; };
                im.src=reader.result;
              };
              reader.readAsDataURL(f);
            });
            input.__trentLogoListener=true;
          }
        })();
        """.trimIndent()
        web.evaluateJavascript(js, null)
    }

    private fun installDefaultBrandLogos() {
        val js = """
        (function(){
          const input=document.getElementById('logoInput');
          if(!input || input.__trentBrandSelectorInstalled) return;
          input.__trentBrandSelectorInstalled=true;
          const wrap=input.parentElement;
          if(!wrap) return;
          const label=wrap.querySelector('label');
          const select=document.createElement('select');
          select.id='defaultBrandLogo';
          select.innerHTML='<option value="">Custom logo / none</option><option value="westside">Westside</option><option value="burnt-toast">Burnt Toast</option><option value="zudio">Zudio</option>';
          select.style.marginBottom='9px';
          if(label) wrap.insertBefore(select,label.nextSibling); else wrap.insertBefore(select,input);
          const note=document.createElement('div');
          note.textContent='Choose a default brand logo or use the custom upload below.';
          note.style.cssText='font-size:10px;color:#69727d;margin:-3px 0 8px;line-height:1.4';
          wrap.insertBefore(note,input);
          async function setLogo(path,name){
            try{
              const res=await fetch(path);
              const text=await res.text();
              const file=new File([text],name+'.svg',{type:'image/svg+xml'});
              const dt=new DataTransfer();
              dt.items.add(file);
              input.files=dt.files;
              input.dispatchEvent(new Event('change',{bubbles:true}));
            }catch(e){ console.warn('Brand logo load failed',e); }
          }
          select.addEventListener('change',function(){
            const v=select.value;
            if(!v){ input.value=''; window.__trentLogoData=''; window.__trentLogoRatio=0; return; }
            setLogo('file:///android_asset/branding/'+v+'.svg',v);
          });
          input.addEventListener('change',function(){
            if(input.files && input.files.length) {
              const f=input.files[0];
              if(f.name!=='westside.svg' && f.name!=='burnt-toast.svg' && f.name!=='zudio.svg') select.value='';
            }
          });
        })();
        """.trimIndent()
        web.evaluateJavascript(js, null)
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
        val uris = if (resultCode == RESULT_OK) {
            val list = ArrayList<Uri>()
            cameraUri?.let { uri ->
                if (File(uri.path ?: "").exists() || contentResolver.getType(uri)?.startsWith("image/") == true) list.add(uri)
            }
            if (list.isEmpty() && data != null) {
                data.clipData?.let { clip ->
                    for (i in 0 until clip.itemCount) list.add(clip.getItemAt(i).uri)
                }
                if (list.isEmpty()) data.data?.let { list.add(it) }
            }
            if (list.isNotEmpty()) list.toTypedArray() else null
        } else null
        chooser?.onReceiveValue(uris)
        chooser = null
        cameraUri = null
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
