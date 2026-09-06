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
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); webView=WebView(this); setContentView(webView); webView.settings.javaScriptEnabled=true; webView.settings.domStorageEnabled=true; webView.settings.allowFileAccess=true; webView.settings.allowContentAccess=true; webView.webViewClient=object:WebViewClient(){override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest)=false; override fun onPageFinished(view:WebView,url:String){super.onPageFinished(view,url);installLogoAspectRatioFix();installDefaultBrandLogos()}}; webView.webChromeClient=object:WebChromeClient(){override fun onShowFileChooser(w:WebView,cb:ValueCallback<Array<Uri>>,p:FileChooserParams):Boolean{filePathCallback?.onReceiveValue(null);filePathCallback=cb;val accepts=p.acceptTypes.flatMap{it.split(",")}.map{it.trim().lowercase()}.filter{it.isNotEmpty()};val image=accepts.isEmpty()||accepts.any{it.startsWith("image/")||it=="*/*"};if(p.isCaptureEnabled&&image){if(ContextCompat.checkSelfPermission(this@MainActivity,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)ActivityCompat.requestPermissions(this@MainActivity,arrayOf(Manifest.permission.CAMERA),cameraPermissionRequest) else launchCamera();return true};cameraUri=null;startActivityForResult(p.createIntent(),fileChooserRequest);return true}};webView.addJavascriptInterface(AndroidBridge(),"AndroidBridge");webView.loadUrl("file:///android_asset/index.html") }
    private fun launchCamera(){val dir=File(cacheDir,"shared");if(!dir.exists())dir.mkdirs();val f=File(dir,"capture_${System.currentTimeMillis()}.jpg");cameraUri=FileProvider.getUriForFile(this,"${BuildConfig.APPLICATION_ID}.fileprovider",f);val i=Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply{putExtra(MediaStore.EXTRA_OUTPUT,cameraUri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION);clipData=ClipData.newRawUri("output",cameraUri)};try{startActivityForResult(i,fileChooserRequest)}catch(e:Exception){filePathCallback?.onReceiveValue(null);filePathCallback=null;cameraUri=null}}
    override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(r,p,g);if(r==cameraPermissionRequest){if(g.isNotEmpty()&&g[0]==PackageManager.PERMISSION_GRANTED)launchCamera()else{filePathCallback?.onReceiveValue(null);filePathCallback=null}}}
    override fun onActivityResult(r:Int,result:Int,data:Intent?){super.onActivityResult(r,result,data);if(r!=fileChooserRequest)return;val cb=filePathCallback?:return;filePathCallback=null;if(result!=Activity.RESULT_OK){cb.onReceiveValue(null);cameraUri=null;return};val x=data?.clipData;val out: Array<Uri>?=when{cameraUri!=null->arrayOf(cameraUri!!);x!=null->Array(x.itemCount){i->x.getItemAt(i).uri};data?.data!=null->arrayOf(data.data!!);else->null};cb.onReceiveValue(out);cameraUri=null}
    private fun installLogoAspectRatioFix(){webView.evaluateJavascript("javascript:(function(){if(window.__trentLogoAspectFixInstalled)return;window.__trentLogoAspectFixInstalled=true;function i(){try{if(typeof PptxGenJS==='undefined')return;var o=PptxGenJS.prototype.addSlide;PptxGenJS.prototype.addSlide=function(){var s=o.apply(this,arguments),a=s.addImage;s.addImage=function(v){try{if(v&&window.__trentLogoData&&v.data===window.__trentLogoData&&window.__trentLogoRatio){var w=v.w||1,h=v.h||1;if(window.__trentLogoRatio>w/h)v.h=w/window.__trentLogoRatio;else v.w=h*window.__trentLogoRatio}}catch(e){}return a.call(this,v)};return s}}catch(e){}}i();document.addEventListener('DOMContentLoaded',i)})();",null)}
    private fun installDefaultBrandLogos(){webView.evaluateJavascript("javascript:(function(){if(window.__trentDefaultLogoSelectorInstalled)return;window.__trentDefaultLogoSelectorInstalled=true;var i=document.getElementById('logoInput');if(!i)return;var p=i.parentElement,s=document.createElement('select');s.id='trentDefaultLogo';s.innerHTML='<option value=\"\">Custom logo / none</option><option value=\"westside.svg\">Westside</option><option value=\"burnt-toast.svg\">Burnt Toast</option><option value=\"zudio.svg\">Zudio</option>';p.insertBefore(s,i);var n=document.createElement('div');n.textContent='Choose a default brand logo or use the custom upload below.';n.style.margin='6px 0';n.style.fontSize='12px';p.insertBefore(n,i);s.onchange=async function(){var v=this.value;if(!v){i.value='';window.__trentLogoData='';window.__trentLogoRatio=0;try{logo=''}catch(e){}return}try{var t=await(await fetch('branding/'+v)).text(),d='data:image/svg+xml;base64,'+btoa(unescape(encodeURIComponent(t)));window.__trentLogoData=d;window.__trentLogoRatio=1;try{logo=d}catch(e){}var b=new Blob([t],{type:'image/svg+xml'}),f=new File([b],v,{type:'image/svg+xml'}),dt=new DataTransfer();dt.items.add(f);i.files=dt.files;i.dispatchEvent(new Event('change',{bubbles:true}));try{logo=d}catch(e){}}catch(e){console.error(e)}}})();",null)}
    inner class AndroidBridge{@android.webkit.JavascriptInterface fun saveFile(n:String,m:String,b:String){val d=android.util.Base64.decode(b,android.util.Base64.DEFAULT);val v=android.content.ContentValues().apply{put(MediaStore.Downloads.DISPLAY_NAME,n);put(MediaStore.Downloads.MIME_TYPE,m);put(MediaStore.Downloads.IS_PENDING,1)};val u=contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v)?:return;contentResolver.openOutputStream(u)?.use{it.write(d)};v.clear();v.put(MediaStore.Downloads.IS_PENDING,0);contentResolver.update(u,v,null,null)};@android.webkit.JavascriptInterface fun shareFile(n:String,m:String,b:String){val d=File(cacheDir,"shared");if(!d.exists())d.mkdirs();val f=File(d,n);FileOutputStream(f).use{it.write(android.util.Base64.decode(b,android.util.Base64.DEFAULT))};val u=FileProvider.getUriForFile(this@MainActivity,"${BuildConfig.APPLICATION_ID}.fileprovider",f);val i=Intent(Intent.ACTION_SEND).apply{type=m;putExtra(Intent.EXTRA_STREAM,u);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)};startActivity(Intent.createChooser(i,"Share report"))}}
}
