package org.yalab.bourbon

import java.lang.Runnable
import _root_.android.app.{Activity, ListActivity, ProgressDialog}
import _root_.android.content.{ContentValues}
import _root_.android.os.{Bundle, Handler}
import _root_.android.widget.{TextView, ListView, SimpleCursorAdapter}
import _root_.android.view.{Menu, MenuItem}

object MainActivity {
  final val OPTION_DOWNLOAD = Menu.FIRST
}

class MainActivity extends ListActivity {
  import MainActivity._
  val handler = new Handler

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    render
  }

  def render{
    val c = getContentResolver.query(ArticleProvider.CONTENT_URI, Array("title"), null, null, null)

    val adapter = new SimpleCursorAdapter(MainActivity.this, R.layout.row, c,
                                          Array("title"), Array(_root_.android.R.id.title))
    setListAdapter(adapter)
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
            val resolver = getContentResolver
            ArticleProvider.download.foreach(article => {
              val values = new ContentValues
              article.foreach{case(k, v) => values.put(k, v.toString)}
              resolver.insert(ArticleProvider.CONTENT_URI, values)
            })
            dialog.dismiss
            handler.post(new Runnable() { def run { render } });
          }
        })).start
        true
      }
    }
  }
}
