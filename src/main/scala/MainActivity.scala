package org.yalab.bourbon

import android.app.{Activity, ListActivity, ProgressDialog, Dialog, AlertDialog}
import android.content.{ContentValues, Intent, ContentUris, Context, ContentResolver, SharedPreferences, ComponentName, ServiceConnection, DialogInterface}
import android.database.Cursor
import android.net.Uri
import android.os.{Bundle, Handler, IBinder}
import android.preference.PreferenceManager
import android.widget.{TextView, ListView, SimpleCursorAdapter, Toast, ImageView, AdapterView}
import android.view.{Menu, MenuItem, View, LayoutInflater, ContextMenu, Window}
import java.lang.Runnable

object MainActivity {
  final val OPTION_DOWNLOAD = Menu.FIRST
  final val OPTION_SETTING  = Menu.FIRST + 1
  val COLUMNS = Array(R.id.title, R.id.sentence, R.id.icon)
  val SENTENCE_COLUMN_INDEX = 2
  val ICON_COLUMN_INDEX = 3
}

class MainActivity extends Activity {
  import MainActivity._

  val DOWNLOAD_MESSAGE = "Downloading. Please wait..."
  val TAG = "MainActivity"
  var mResolver: ContentResolver   = null
  var mHandler:  Handler           = null
  var mPrefs:    SharedPreferences = null
  var mCursor:   Cursor            = null
  var mListView: ListView          = null
  var mAdapter:  ArticleAdapter    = null

  private var crawlService: ICrawlService = null
  val crawlServiceConnection = new ServiceConnection{
    override def onServiceConnected(name: ComponentName , service: IBinder ){
      crawlService = ICrawlService.Stub.asInterface(service)
    }
    override def onServiceDisconnected(name: ComponentName) {
      crawlService = null
    }
  }

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main)

    mPrefs = PreferenceManager.getDefaultSharedPreferences(this)
    val intent = getIntent
    if(intent.getData == null){
      intent.setData(ArticleProvider.CONTENT_URI)
    }
    mResolver = getContentResolver

    mListView = findViewById(R.id.list).asInstanceOf[ListView]

    mHandler  = new Handler
val fields = Array(ArticleProvider.F_TITLE, ArticleProvider.F_SENTENCE, ArticleProvider.F_TIME)
    mCursor = mResolver.query(ArticleProvider.CONTENT_URI, fields, null, null, null)
    if(mCursor.getCount < 1){
      val dialog = new Dialog(this)
      dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
      dialog.setContentView(R.layout.first_step)
      dialog.show
      dialog.findViewById(R.id.ok_button).setOnClickListener(new View.OnClickListener{
        def onClick(v: View){
          dialog.dismiss
        }
      })
    }
    startManagingCursor(mCursor)
    mAdapter = new ArticleAdapter(MainActivity.this, R.layout.row, mCursor,
                                     fields, COLUMNS)
    mListView.setAdapter(mAdapter)
    mListView.setOnItemClickListener(new AdapterView.OnItemClickListener{
      def onItemClick(l: AdapterView[_], v: View, position: Int, id: Long) {
        val uri = ContentUris.withAppendedId(getIntent.getData, id)
        startActivity(new Intent(Intent.ACTION_VIEW, uri))
      }
    })
    registerForContextMenu(mListView)
    val crawlerIntent = new Intent(MainActivity.this, classOf[CrawlService])
    bindService(crawlerIntent, crawlServiceConnection, Context.BIND_AUTO_CREATE)
  }

  override def onRestart{
    super.onRestart
    val message = if(mPrefs.getBoolean("autodownload", false)){
      CrawlService.START
    }else{
      CrawlService.STOP
    }
    crawlService.send(message)
  }

  override def onDestroy{
    super.onDestroy
    val removeOldArticle = mPrefs.getBoolean("remove_old_article", false)
    if(removeOldArticle && ArticleProvider.EXPIRE_NUM < mCursor.getCount){
      val uriBuilder = ArticleProvider.CONTENT_URI.buildUpon
      uriBuilder.appendQueryParameter("offset", ArticleProvider.EXPIRE_NUM.toString)
      mResolver.delete(uriBuilder.build, null, null)
    }
    unbindService(crawlServiceConnection)
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
            val result = crawlService.send(CrawlService.INVOKE)
            dialog.dismiss
            mHandler.post(new Runnable() { def run {
              val msg_id = result match{
                case CrawlService.UNKNOWN_HOST_ERROR => R.string.unknown_host_exeption_message
                case CrawlService.IO_ERROR           => R.string.io_exeption_message
                case _                               => 0
              }
              if(msg_id != 0){
                Toast.makeText(MainActivity.this, getString(msg_id), Toast.LENGTH_SHORT).show
              }
              mAdapter.notifyDataSetChanged
            } });
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

  override def onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo){
    super.onCreateContextMenu(menu, v, menuInfo)
    val info = menuInfo.asInstanceOf[AdapterView.AdapterContextMenuInfo]
    val c = mListView.getItemAtPosition(info.position).asInstanceOf[Cursor]
    val title = c.getString(c.getColumnIndex(ArticleProvider.F_TITLE))
    menu.setHeaderTitle(title)
    getMenuInflater.inflate(R.menu.main_context, menu)
  }

  override def onContextItemSelected(item: MenuItem): Boolean = {
    val info = item.getMenuInfo.asInstanceOf[AdapterView.AdapterContextMenuInfo]
    val uri = ContentUris.withAppendedId(getIntent.getData, info.id)
    item.getItemId match{
      case R.id.open => {
        startActivity(new Intent(Intent.ACTION_VIEW, uri))
      }
      case R.id.open_in_browser => {
        val itemView = info.targetView
        val c = mResolver.query(uri, Array(ArticleProvider.F_LINK), null, null, null)
        val link = c.getString(c.getColumnIndex(ArticleProvider.F_LINK))
        val intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link))
        startActivity(intent)
      }
      case R.id.destroy => {
        val dialog = new AlertDialog.Builder(this)
        dialog.setMessage(R.string.are_you_sure_destroy)
        dialog.setPositiveButton("Yes", new DialogInterface.OnClickListener{
          override def onClick(dialog: DialogInterface, which: Int){
            val c = mResolver.delete(uri, null, null)
            mAdapter.notifyDataSetChanged
          }
        })

        dialog.setNegativeButton("No", null)
        dialog.setCancelable(true)
        dialog.create.show
      }
    }
    true
  }

  class ArticleViewBinder extends SimpleCursorAdapter.ViewBinder{
    val SEPARATOR = " "
    def setViewValue(v: View, c: Cursor, columnIndex: Int): Boolean = {
      columnIndex match{
        case MainActivity.SENTENCE_COLUMN_INDEX => {
          val time = c.getString(c.getColumnIndex(ArticleProvider.F_TIME))
          val text = if(time == null){
            c.getInt(columnIndex).toString + SEPARATOR + ArticleProvider.F_SENTENCE
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
