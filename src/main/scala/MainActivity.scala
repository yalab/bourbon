package org.yalab.bourbon

import _root_.android.app.{Activity, ListActivity, ProgressDialog}
import _root_.android.content.{ContentValues, Intent, ContentUris, Context, ContentResolver}
import _root_.android.database.Cursor
import _root_.android.os.{Bundle, Handler}
import _root_.android.preference.PreferenceManager
import _root_.android.widget.{TextView, ListView, SimpleCursorAdapter, Toast, ImageView}
import _root_.android.view.{Menu, MenuItem, View}
import java.io.IOException
import java.lang.Runnable
import java.net.UnknownHostException

object MainActivity {
  final val OPTION_DOWNLOAD = Menu.FIRST
  final val OPTION_SETTING  = Menu.FIRST + 1
  val COLUMNS = Array(R.id.title, R.id.paragraph, R.id.icon)
  val PARAGRAPH_COLUMN_INDEX = 2
  val ICON_COLUMN_INDEX = 3
}

class MainActivity extends ListActivity {
  import MainActivity._

  val DOWNLOAD_MESSAGE = "Downloading. Please wait..."
  val TAG = "MainActivity"
  var mResolver: ContentResolver = null
  var mHandler: Handler = null

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    val prefs = PreferenceManager.getDefaultSharedPreferences(this)
    //println(prefs.getAll)
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
    startManagingCursor(c)
    val adapter = new ArticleAdapter(MainActivity.this, R.layout.row, c,
                                     fields, COLUMNS)
    setListAdapter(adapter)
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    val result = super.onCreateOptionsMenu(menu)
    menu.add(0, OPTION_DOWNLOAD, 0, R.string.download)
    menu.add(0, OPTION_SETTING, 1,  R.string.setting)
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
      case OPTION_SETTING => {
        val intent = new Intent(MainActivity.this, Class.forName("org.yalab.bourbon.SettingsActivity"))
        startActivity(intent)
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
    def setViewValue(v: View, c: Cursor, columnIndex: Int): Boolean = {
      columnIndex match{
        case MainActivity.PARAGRAPH_COLUMN_INDEX => {
          val time = c.getString(c.getColumnIndex(ArticleProvider.F_TIME))
          val text = if(time == null){
            c.getInt(columnIndex).toString + SEPARATOR + ArticleProvider.F_PARAGRAPH
          }else{
            time
          }
          v.asInstanceOf[TextView].setText(text)
          true
        }
        case MainActivity.ICON_COLUMN_INDEX => {
          val time = c.getString(c.getColumnIndex(ArticleProvider.F_TIME))
          val icon = if(time != null){
            R.drawable.music
          }else{
            R.drawable.download
          }
          v.asInstanceOf[ImageView].setImageResource(icon)
          true
        }
        case _ => false
      }
    }
  }

  class ArticleAdapter(context: Context, id: Int, c: Cursor, fields: Array[String], nodes: Array[Int]) extends SimpleCursorAdapter(context, id, c, fields, nodes){
    setViewBinder(new ArticleViewBinder)
  }
}
