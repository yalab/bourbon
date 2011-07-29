package org.yalab.bourbon

import _root_.android.app.Activity
import _root_.android.os.Bundle

class ArticleActivity extends Activity {
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.article)
  }
}
