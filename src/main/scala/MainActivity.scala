package org.yalab.bourbon

import java.lang.Runnable
import _root_.android.app.{Activity, ProgressDialog}
import _root_.android.os.Bundle
import _root_.android.widget.TextView
import _root_.android.view.{Menu, MenuItem}

object MainActivity {
  final val OPTION_DOWNLOAD = Menu.FIRST
}

class MainActivity extends Activity {
  import MainActivity._
  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    val result = super.onCreateOptionsMenu(menu)
    menu.add(0, OPTION_DOWNLOAD, 0, R.string.download)
    result
  }

  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    item.getItemId match {
      case OPTION_DOWNLOAD => {
        val dialog = ProgressDialog.show(MainActivity.this, "",
                                         "Downloading. Please wait...", true, true)
        (new Thread(new Runnable(){
          def run() {
            val articles = ArticleProvider.download
            println(articles)
            dialog.dismiss
          }
        })).start
        true
      }
    }
  }
}

