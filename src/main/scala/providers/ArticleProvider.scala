package org.yalab.bourbon

import _root_.android.content.{ContentProvider, ContentValues, ContentUris, Context, UriMatcher}
import _root_.android.database.Cursor
import _root_.android.database.sqlite.{SQLiteDatabase, SQLiteOpenHelper}
import _root_.android.net.{Uri, ConnectivityManager}
import _root_.android.net.wifi.WifiManager
import _root_.android.util.Log
import _root_.android.os.Environment
import _root_.android.provider.BaseColumns
import _root_.android.util.Log
import java.net.URL
import scala.xml.{XML, Elem}
import java.io.{File, FileOutputStream}
import java.net.UnknownHostException
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.client.methods.HttpGet

object ArticleProvider{
  final val DATABASE_NAME    = "bourbon.db"
  final val DATABASE_VERSION = 1
  final val AUTHORITY = "org.yalab.bourbon"
  final val TABLE_NAME = "articles"
  final val CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + TABLE_NAME)
  final val CONTENT_TYPE= "vnd.android.cursor.dir/org.yalab.bourbon"
  final val CONTENT_ITEM_TYPE = "vnd.android.cursor.item/org.yalab.bourbon"

  final val INDEX = 1
  final val SHOW  = 2
  final val F_PARAGRAPH = "paragraph"
  final val F_TITLE     = "title"
  final val F_GUID      = "guid"
  final val F_SCRIPT    = "script"
  final val F_MP3       = "mp3"
  final val F_ENCLOSURE = "enclosure"
  final val F_TIME      = "time"
  final val TAG = "ArticleProvider"

  final val EQUAL_PLACEHOLDER = "= ?"
  final val MP3_DIR           = "/Android/data/%s/files/".format(AUTHORITY)
  final val RSS_URL           = "http://www.voanews.com/templates/Articles.rss?sectionPath=/learningenglish/home"

  val FIELDS = Map(BaseColumns._ID -> "INTEGER PRIMARY KEY",
                   F_TITLE         -> "TEXT",
                   "link"          -> "TEXT",
                   "description"   -> "TEXT",
                   F_GUID          -> "INTEGER",
                   "category"      -> "TEXT",
                   "date"          -> "TEXT",
                   F_ENCLOSURE     -> "TEXT",
                   F_MP3           -> "TEXT",
                   F_SCRIPT        -> "TEXT",
                   F_PARAGRAPH     -> "INTEGER",
                   F_TIME          -> "TEXT")

  def download() = {
    val client = new DefaultHttpClient
    var xml: Elem = null
    try{
      val response = client.execute(new HttpGet(RSS_URL))
      val entity = response.getEntity
      xml = XML.load(entity.getContent)
    }
    xml \ "channel" \ "item" map(item => {
      val encoded = XML.loadString("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>" + (item \ "encoded").head.text + "</root>")
      FIELDS.keys.filter{_ != BaseColumns._ID}.map(k => {
        val v = k match {
          case F_MP3       => {
            val flashvars = (encoded \\ "param" filter(node => (node \ "@name").toString == "flashvars")) \ "@value"
            val queryString = flashvars.toString.split("&").map(str => str.split("=")).filter(a => a(0) == "file")
            if(queryString.size > 0){
              queryString(0)(1).replaceAll("[ 　]", "")
            }else{
              null
            }
          }
          case F_SCRIPT    => {
            (encoded \\ "p").map(node => {
              "<p>" + node.text.split(" ").map(word => "<span onclick='search(this.innerHTML)'>%s</span>".format(word)).mkString(" ") + "</p>"
            }).mkString("\n\n")
          }
          case F_ENCLOSURE => item \ k \ "@url"
          case F_PARAGRAPH => (encoded \\ "p").length
          case F_TIME      => ""
          case _           => (item \ k).head.text
        }
        (k, v)
      }).toMap
    })
  }

  def mp3File(id: String, mp3:String) = {
    val dir =  new File(List(Environment.getExternalStorageDirectory, MP3_DIR).mkString("/"))
    if(dir.exists == false){ dir.mkdirs }
    new File(dir, id + ".mp3")
  }

  def fetchMp3(id: String, mp3: String): Option[File] = {
    val file = ArticleProvider.mp3File(id, mp3)
    if(file.exists == false){
      val output = new FileOutputStream(file)
      try{
        val response = (new DefaultHttpClient).execute(new HttpGet(mp3))
        response.getEntity.writeTo(output)
      }catch{
        case e =>{
          output.close
          file.delete
          ArticleProvider.writeErrorLog(TAG, e)
          return None
        }
      }finally{
        output.close
      }
    }
    Some(file)
  }

  def isDownloadable(c: Context): Boolean = {
    val wifiState = c.getSystemService(Context.WIFI_SERVICE).asInstanceOf[WifiManager].getWifiState
    val activeNetwork = c.getSystemService(Context.CONNECTIVITY_SERVICE).asInstanceOf[ConnectivityManager].getActiveNetworkInfo

    if(activeNetwork == null && wifiState != WifiManager.WIFI_STATE_ENABLED){
      false
    }else{
      true
    }
  }

  def writeErrorLog(tag: String, e:Throwable){
    Log.w(tag, e.getStackTrace.map(t => "at %s.%s(%s:%s)".format(t.getClassName, t.getMethodName, t.getFileName, t.getLineNumber)).mkString("\n"))
  }

  val htmlHeader = """
<!DOCTYPE html>
<html>
<head>
  <title>org.yalab.bourbon</title>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width; initial-scale=1.0; maximum-scale=1.0; user-scalable=0;">
  <style type="text/css">
a{
  text-decoration: none;
  color: #0F0F0F;
}
#content{
  padding-bottom: 30px;
}
  </style>
  <script type="text/javascript">
  function search(word){
    %s
    return false;
  }
  </script>
</head>
<body>
<div id="content">
  """
  val htmlFooter = """
</div>
</body>
</html>
  """
}

class ArticleProvider extends ContentProvider {
  import ArticleProvider._
  var connection: Database = _

  val matcher = new UriMatcher(UriMatcher.NO_MATCH)
  matcher.addURI(AUTHORITY, TABLE_NAME, INDEX)
  matcher.addURI(AUTHORITY, TABLE_NAME + "/#", SHOW)

  override def onCreate: Boolean = {
    connection = new Database(getContext)
    true
  }

  def insert(uri: Uri, values: ContentValues): Uri = {
    val db = connection.getWritableDatabase
    val id = db.insertOrThrow(TABLE_NAME, null, values)
    val new_uri = ContentUris.withAppendedId(CONTENT_URI, id)
    getContext.getContentResolver.notifyChange(new_uri, null)
    new_uri
  }

  def update(uri: Uri, values: ContentValues, where: String, whereArgs: Array[String]): Int = {
    val db = connection.getWritableDatabase
    matcher `match` uri match {
      case SHOW => {
        val id = uri.getPathSegments().get(1)
        db.update(TABLE_NAME, values, BaseColumns._ID + EQUAL_PLACEHOLDER, Array(id))
      }
    }
    1
  }

  def delete(uri: Uri, where: String, whereArgs: Array[String]): Int = {
    1
  }

  def getType(uri: Uri): String = {
    matcher `match` uri match {
      case INDEX => CONTENT_TYPE
      case SHOW  => CONTENT_ITEM_TYPE
      case _     => throw new IllegalArgumentException("Unknown URI " + uri)
    }
  }

  def query(uri: Uri, fields: Array[String], where: String, whereArgs: Array[String], sortOrder: String): Cursor = {
    val db = connection.getReadableDatabase
    matcher `match` uri match {
      case INDEX => {
        db.query(TABLE_NAME, Array(BaseColumns._ID) ++ fields, where, whereArgs, null, null, "date DESC")
      }
      case SHOW  => {
        val id = uri.getPathSegments().get(1)
        db.query(TABLE_NAME, Array(BaseColumns._ID) ++ fields, BaseColumns._ID + EQUAL_PLACEHOLDER, Array(id), null, null, null)
      }
      case _     => throw new IllegalArgumentException("Unknown URI " + uri)
    }

  }

  protected class Database(context: Context) extends SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    def onCreate(db: SQLiteDatabase) {
      val sql = "CREATE TABLE %s (%s);".format(TABLE_NAME, FIELDS.map{case(k, v) => k + " " + v }.mkString(", "))
      db.execSQL(sql)
      val index_column = ArticleProvider.F_GUID
      db.execSQL("CREATE INDEX %s_%s_ix ON %s(%s)".format(TABLE_NAME, index_column, TABLE_NAME, index_column))
    }

    def onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    }
  }
}
