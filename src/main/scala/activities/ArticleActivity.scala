package org.yalab.bourbon

import _root_.android.app.Activity
import _root_.android.os.Bundle
import _root_.android.widget.TextView
import _root_.android.webkit.WebView

class ArticleActivity extends Activity {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.article)
    val fields = Array("script")
    val c = getContentResolver.query(getIntent.getData, fields, null, null, null)
    c.moveToFirst
    val script = c.getString(c.getColumnIndex("script"))
    val webview = findViewById(R.id.webview).asInstanceOf[WebView]
    webview.loadData(script, "text/html", "utf-8")
  }
}
