package org.yalab.bourbon

import java.lang.Runnable
import _root_.android.app.{Activity, ListActivity, ProgressDialog}
import _root_.android.content.{ContentValues, Intent, ContentUris, Context, ContentResolver}
import _root_.android.database.Cursor
import _root_.android.os.{Bundle, Handler}
import _root_.android.widget.{TextView, ListView, SimpleCursorAdapter}
import _root_.android.view.{Menu, MenuItem, View}

object MainActivity {
  final val OPTION_DOWNLOAD = Menu.FIRST
}

class MainActivity extends ListActivity {
  import MainActivity._
  val handler = new Handler
  val mDownloadMessage = "Downloading. Please wait..."
  var mResolver: ContentResolver = null

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    val intent = getIntent
    if(intent.getData == null){
      intent.setData(ArticleProvider.CONTENT_URI)
    }
    mResolver = getContentResolver
    render
  }

  def render{
    val fields = Array(ArticleProvider.F_TITLE, ArticleProvider.F_PARAGRAPH)
    val c = mResolver.query(ArticleProvider.CONTENT_URI, fields, null, null, null)
    val adapter = new ArticleAdapter(MainActivity.this, R.layout.row, c,
                                     fields, Array(R.id.title, R.id.paragraph))
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
        val dialog = ProgressDialog.show(MainActivity.this, null,
                                         mDownloadMessage, true, true)

        (new Thread(new Runnable(){
          def run() {
            ArticleProvider.download.foreach(article => {
              val values = new ContentValues
              article.foreach{case(k, v) => values.put(k, v.toString)}
              val c = mResolver.query(ArticleProvider.CONTENT_URI, Array(),
                                      ArticleProvider.F_GUID + ArticleProvider.mEqualPlaceHolder,
                                      Array(article(ArticleProvider.F_GUID).toString), null)
              if(c.getCount < 1){
                mResolver.insert(ArticleProvider.CONTENT_URI, values)
              }
            })
            dialog.dismiss
            handler.post(new Runnable() { def run { render } });
          }
        })).start
        true
      }
    }
  }

  override def onListItemClick(l: ListView, v: View, position: Int, id: Long) {
    val uri = ContentUris.withAppendedId(getIntent.getData, id)
    startActivity(new Intent(Intent.ACTION_VIEW, uri))
  }

  class ArticleViewBinder extends SimpleCursorAdapter.ViewBinder{
    val mSeparator = " "
    var mParagraphColumnIndex = -1
    def setViewValue(v: View, c: Cursor, columnIndex: Int): Boolean = {
      if(mParagraphColumnIndex == -1){
        mParagraphColumnIndex = c.getColumnIndex(ArticleProvider.F_PARAGRAPH)
      }

      if(mParagraphColumnIndex != columnIndex){ return false }

      val n = c.getInt(columnIndex)
      v.asInstanceOf[TextView].setText(n.toString + mSeparator + ArticleProvider.F_PARAGRAPH)
      true
    }
  }

  class ArticleAdapter(context: Context, id: Int, c: Cursor, fields: Array[String], nodes: Array[Int]) extends SimpleCursorAdapter(context, id, c, fields, nodes){
    setViewBinder(new ArticleViewBinder)
  }
}
