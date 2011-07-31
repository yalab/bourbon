package org.yalab.bourbon

import _root_.android.app.Activity
import _root_.android.os.{Bundle, Environment}
import _root_.android.view.{View, KeyEvent}
import _root_.android.view.View.OnLongClickListener
import _root_.android.widget.TextView
import _root_.android.webkit.WebView
import scala.io.Source
import java.io.{BufferedOutputStream, BufferedInputStream, File, FileOutputStream}
import java.net.URL

class ArticleActivity extends Activity {

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.article)
    val fields = Array("script", "mp3")
    val c = getContentResolver.query(getIntent.getData, fields, null, null, null)
    c.moveToFirst
    val script = c.getString(c.getColumnIndex("script"))
    val mp3    = c.getString(c.getColumnIndex("mp3"))
    val id     = c.getString(c.getColumnIndex("_id"))

    val dir =  new File(List(Environment.getExternalStorageDirectory, "Android", "data", "org.yalab.bourbon", "cache").mkString("/"))
    if(dir.exists == false){ dir.mkdirs }

    val path = new File(dir, id + ".mp3")
    if(path.exists == false){
      try{
        val stream = new BufferedInputStream((new URL(mp3)).openStream)
        val file = new BufferedOutputStream(new FileOutputStream(path))
        var binary:Int = 0
        while({binary = stream.read; binary != -1}){
          file.write(binary)
        }
        file.close
      }
    }

    val webview = findViewById(R.id.webview).asInstanceOf[WebView]
    webview.getSettings.setJavaScriptEnabled(true)
    webview.getSettings.setUseWideViewPort(true)

    webview.loadData(ArticleProvider.html_header + script + ArticleProvider.html_footer, "text/html", "utf-8")

    webview.setOnLongClickListener(new OnLongClickListener{
      override def onLongClick(v: View) = {
        val webview =  v.asInstanceOf[WebView]
        val hr = webview.getHitTestResult
        val url = "http://eow.alc.co.jp/" + hr.getExtra.replace(".", "") + "/UTF-8/"
        webview.loadUrl(url)
        true
      }
    })
  }

  override def onKeyDown(key_code: Int, event: KeyEvent ): Boolean = {
    val webview = findViewById(R.id.webview).asInstanceOf[WebView]
    if (key_code == KeyEvent.KEYCODE_BACK && webview.canGoBack) {
      webview.goBack
      true
    }else{
      super.onKeyDown(key_code, event);
    }
  }
}
