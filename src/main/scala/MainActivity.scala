package org.yalab.bourbon

import _root_.android.app.{Activity, ListActivity, ProgressDialog}
import _root_.android.content.{ContentValues, Intent, ContentUris, Context, ContentResolver}
import _root_.android.database.Cursor
import _root_.android.os.{Bundle, Handler}
import _root_.android.widget.{TextView, ListView, SimpleCursorAdapter, Toast}
import _root_.android.view.{Menu, MenuItem, View}
import java.io.IOException
import java.lang.Runnable
import java.net.UnknownHostException

object MainActivity {
  final val OPTION_DOWNLOAD = Menu.FIRST
}

class MainActivity extends ListActivity {
  import MainActivity._

  val DOWNLOAD_MESSAGE = "Downloading. Please wait..."
  val TAG = "MainActivity"
  var mResolver: ContentResolver = null
  var mHandler: Handler = null

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    val intent = getIntent
    if(intent.getData == null){
      intent.setData(ArticleProvider.CONTENT_URI)
    }
    mResolver = getContentResolver
    mHandler  = new Handler
    render
  }

  def render{
    val fields = Array(ArticleProvider.F_TITLE, ArticleProvider.F_PARAGRAPH, ArticleProvider.F_TIME)
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
        if(ArticleProvider.isDownloadable(this) == false){
          Toast.makeText(MainActivity.this, getString(R.string.unknown_host_exeption_message), Toast.LENGTH_SHORT).show
          return true
        }

        (new Thread(new Runnable{
          val dialog = ProgressDialog.show(MainActivity.this, null,
                                           DOWNLOAD_MESSAGE, true, true)
          def run{
            try{
              ArticleProvider.download.filter(article => article(ArticleProvider.F_MP3) != null).foreach(article => {
                val values = new ContentValues
                article.filter(_._1 != ArticleProvider.F_TIME).foreach{case(k, v) => values.put(k, v.toString)}
                val c = mResolver.query(ArticleProvider.CONTENT_URI, Array(),
                                        ArticleProvider.F_GUID + ArticleProvider.EQUAL_PLACEHOLDER,
                                        Array(article(ArticleProvider.F_GUID).toString), null)
                if(c.getCount < 1){
                  mResolver.insert(ArticleProvider.CONTENT_URI, values)
                }
              })
            }catch{
              case e: UnknownHostException => {
                dialog.dismiss
                mHandler.post(new Runnable() { def run {
                  Toast.makeText(MainActivity.this, getString(R.string.unknown_host_exeption_message), Toast.LENGTH_SHORT).show
                } })
                ArticleProvider.writeErrorLog(TAG, e)
              }
              case e: IOException => {
                dialog.dismiss
                mHandler.post(new Runnable() { def run {
                  Toast.makeText(MainActivity.this, getString(R.string.io_exeption_message), Toast.LENGTH_SHORT).show
                } })
                ArticleProvider.writeErrorLog(TAG, e)
              }
              case e => {
                throw e
              }
            }
            dialog.dismiss
            mHandler.post(new Runnable() { def run { render } });
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
    val SEPARATOR = " "
    var mParagraphColumnIndex = -1
    def setViewValue(v: View, c: Cursor, columnIndex: Int): Boolean = {
      if(mParagraphColumnIndex == -1){
        mParagraphColumnIndex = c.getColumnIndex(ArticleProvider.F_PARAGRAPH)
      }

      if(mParagraphColumnIndex != columnIndex){ return false }

      val time = c.getString(c.getColumnIndex(ArticleProvider.F_TIME))
      val text = if(time == null){
        c.getInt(columnIndex).toString + SEPARATOR + ArticleProvider.F_PARAGRAPH
      }else{
        time
      }

      v.asInstanceOf[TextView].setText(text)
      true
    }
  }

  class ArticleAdapter(context: Context, id: Int, c: Cursor, fields: Array[String], nodes: Array[Int]) extends SimpleCursorAdapter(context, id, c, fields, nodes){
    setViewBinder(new ArticleViewBinder)
  }
}
