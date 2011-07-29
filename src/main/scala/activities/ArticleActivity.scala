package org.yalab.bourbon

import _root_.android.app.Activity
import _root_.android.os.Bundle
import _root_.android.widget.TextView

class ArticleActivity extends Activity {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.article)
    val fields = Array("title", "script", "mp3")
    val c = getContentResolver.query(getIntent.getData, fields, null, null, null)
    c.moveToFirst
    fields.foreach(f => {
      val id = getResources.getIdentifier(f, "id", "org.yalab.bourbon")
      findViewById(id).asInstanceOf[TextView].setText(c.getString(c.getColumnIndex(f)))
    })
  }
}
