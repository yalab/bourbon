package org.yalab.bourbon

import android.content.{ContentProvider, ContentValues, ContentUris, Context, UriMatcher}
import android.database.{Cursor, SQLException}
import android.database.sqlite.{SQLiteDatabase, SQLiteOpenHelper}
import android.net.{Uri, ConnectivityManager}
import android.net.wifi.WifiManager
import android.text.format.Time
import android.util.Log
import android.os.Environment
import android.provider.BaseColumns
import scala.xml.{XML, Elem}
import scala.collection.mutable.ListBuffer
import java.io.{File, FileOutputStream}
import java.net.{URL, UnknownHostException}
import java.text.SimpleDateFormat
import java.util.{Locale}
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.client.methods.HttpGet

object ArticleProvider{
  final val DATABASE_NAME    = "bourbon.db"
  final val DATABASE_VERSION = 4
  final val AUTHORITY = "org.yalab.bourbon"
  final val TABLE_NAME = "articles"
  final val CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/" + TABLE_NAME)
  final val CONTENT_TYPE= "vnd.android.cursor.dir/org.yalab.bourbon"
  final val CONTENT_ITEM_TYPE = "vnd.android.cursor.item/org.yalab.bourbon"

  val EXPIRE_NUM = 30

  final val INDEX = 1
  final val SHOW  = 2

  final val F_TITLE     = "title"
  final val F_LINK      = "link"
  final val F_GUID      = "guid"
  final val F_SCRIPT    = "script"
  final val F_MP3       = "mp3"
  final val F_ENCLOSURE = "enclosure"
  final val F_SENTENCE  = "sentence"
  final val F_TIME      = "time"
  final val F_PUBDATE   = "pubDate"
  final val F_CURRENT_POSITION = "current_position"
  final val F_BOOKMARKED_AT    = "bookmarked_at"
  final val F_SCROLL_Y         = "scroll_y"
  final val F_DELETED_AT       = "deleted_at"
  final val F_SECTION          = "section"
  final val TAG = "ArticleProvider"

  final val EQUAL_PLACEHOLDER = "= ?"
  final val MP3_DIR           = "/Android/data/%s/files/".format(AUTHORITY)
  final val RFC822DateTime = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)

  val FIELDS = Map(BaseColumns._ID    -> "INTEGER PRIMARY KEY",
                   F_TITLE            -> "TEXT",
                   F_LINK             -> "TEXT",
                   "description"      -> "TEXT",
                   F_GUID             -> "INTEGER",
                   "category"         -> "TEXT",
                   F_PUBDATE          -> "TEXT",
                   F_ENCLOSURE        -> "TEXT",
                   F_MP3              -> "TEXT",
                   F_SCRIPT           -> "TEXT",
                   F_SENTENCE         -> "INTEGER",
                   F_TIME             -> "TEXT",
                   F_CURRENT_POSITION -> "INTEGER",
                   F_BOOKMARKED_AT    -> "TEXT",
                   F_SCROLL_Y         -> "INTEGER",
                   F_DELETED_AT       -> "TEXT",
                   F_SECTION          -> "TEXT")
  val INDICES = Map("articles_guid_ix"       -> F_GUID,
                    "articles_deleted_at_section_ix" -> (F_PUBDATE + "," + F_DELETED_AT+ "," + F_SECTION) )

  class VOARss(section: String){
    val SectionPath = Map("Special English" -> "/learningenglish/home",
                          "News"            -> "/english/news")
    val url = "http://www.voanews.com/templates/Articles.rss?sectionPath=" + SectionPath(section)
    val response = (new DefaultHttpClient).execute(new HttpGet(url))
    val mXml = XML.load(response.getEntity.getContent)

    def pubDate = {
      RFC822DateTime.parse((mXml \ "channel" \ F_PUBDATE).head.text.toString)
    }

    def parse = {
      mXml \ "channel" \ "item" map(item => {
        val encoded = XML.loadString("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>" + (item \ "encoded").head.text + "</root>")
        var script: String = null
        var sentence = 0
        FIELDS.keys.filter{_ != BaseColumns._ID}.map(k => {
          val v = k match {
            case F_MP3       => {
              val flashvars = (encoded \\ "param" filter(node => (node \ "@name").toString == "flashvars")) \ "@value"
              val queryString = flashvars.toString.split("&").map(str => str.split("=")).filter(a => a(0) == "file")
              if(queryString.isEmpty){
                null
              }else{
                queryString(0)(1).replaceAll("[ 　]", "")
              }
            }
            case F_SCRIPT    => {
              script = (encoded \\ "p").map(node => {
                val text = node.text
                sentence += "\\.".r.findAllIn(text).size
                "<p>" + text.split(" ").map(word => "<span onclick='search(this.innerHTML)'>%s</span>".format(word)).mkString(" ") + "</p>"
              }).mkString("\n\n")
              script
            }
            case F_PUBDATE   => {
              val time = new Time
              time.set(RFC822DateTime.parse((item \ k).head.text).getTime)
              time.format("%Y-%m-%d %H:%M:%S")
            }
            case F_ENCLOSURE => item \ k \ "@url"
            case F_SENTENCE  => sentence
            case _           => {
              try{
                (item \ k).head.text
              } catch {
                case _ => {
                  FIELDS(k).split(" ")(0) match{
                    case "TEXT"    => null
                    case "INTEGER" => 0
                  }
                }
              }
            }
          }
          (k, v)
        }).toMap
      })
    }
  }

  def mp3File(id: String) = {
    val dir =  new File(List(Environment.getExternalStorageDirectory, MP3_DIR).mkString("/"))
    if(dir.exists == false){ dir.mkdirs }
    new File(dir, id + ".mp3")
  }

  def fetchMp3(id: String, mp3: String): Option[File] = {
    val file = ArticleProvider.mp3File(id)
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
        getContext.getContentResolver.notifyChange(uri, null)
      }
    }
    1
  }

  def delete(uri: Uri, where: String, whereArgs: Array[String]): Int = {
    val db = connection.getWritableDatabase
    val target = new ListBuffer[String]

    matcher `match` uri match {
      case SHOW => {
        val id = uri.getPathSegments().get(1)
        target += id
        val path = ArticleProvider.mp3File(id.toString)
        if(path.exists == true){ path.delete }
      }
      case INDEX => {
        val c = query(uri, null, null, null, null)
        val offset = uri.getQueryParameter("offset")
        if(offset != null){ c.move(offset.toInt) }
        while(c.moveToNext()){
          val id = c.getInt(c.getColumnIndex("_id"))
          val path = ArticleProvider.mp3File(id.toString)
          if(path.exists == true){ path.delete }
          target += id.toString
        }
      }
    }
    val placeHolder = target.map(n => "?").mkString(", ")
    val values = new ContentValues
    for(k <- FIELDS.keys.filter(k => k != BaseColumns._ID && k != F_GUID && k != F_DELETED_AT)){
      values.putNull(k)
    }
    val deletedAt = new Time
    deletedAt.setToNow
    values.put(F_DELETED_AT, deletedAt.format("%Y-%m-%d %H:%M:%S"))
    val count = db.update(TABLE_NAME, values, BaseColumns._ID + " IN(" + placeHolder + ")", target.toArray[String])
    getContext.getContentResolver.notifyChange(uri, null)
    count
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
    val cursor = matcher `match` uri match {
      case INDEX => {
        val _fields = if(fields == null){ Array(BaseColumns._ID) } else { Array(BaseColumns._ID) ++ fields }
        val _where = if(where == null){ F_DELETED_AT + " is NULL" } else { where }
        db.query(TABLE_NAME, _fields, _where, whereArgs, null, null, F_PUBDATE + " DESC")
      }
      case SHOW  => {
        val id = uri.getPathSegments().get(1)
        db.query(TABLE_NAME, Array(BaseColumns._ID) ++ fields, BaseColumns._ID + EQUAL_PLACEHOLDER, Array(id), null, null, null)
      }
      case _     => throw new IllegalArgumentException("Unknown URI " + uri)
    }
    cursor.moveToFirst
    cursor.setNotificationUri(getContext.getContentResolver, uri);
    cursor
  }

  protected class Database(context: Context) extends SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    def onCreate(db: SQLiteDatabase) {
      val sql = "CREATE TABLE %s (%s);".format(TABLE_NAME, FIELDS.map{case(k, v) => k + " " + v }.mkString(", "))
      db.execSQL(sql)
      INDICES.foreach{case(name, fields) => db.execSQL("CREATE INDEX %s ON %s(%s)".format(name, TABLE_NAME, fields)) }
    }

    def partition (db: SQLiteDatabase, defined: Set[String], query: String) = {
      var existed: Set[String] = Set()
      val c = db.rawQuery(query, null)
      while(c.moveToNext){ existed += c.getString(c.getColumnIndex("name")) }
      val create = defined.diff(existed)
      val drop   = existed.diff(defined)
      (create, drop)
    }

    def onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
      db.beginTransaction
      try{
        partition(db, FIELDS.keys.toSet, "PRAGMA table_info('articles')") match{
          case(create, drop) => {
            for(field <- create){ db.execSQL("ALTER TABLE %s ADD COLUMN %s %s".format(TABLE_NAME, field, ArticleProvider.FIELDS(field))) }
          }
        }

        partition(db, INDICES.keys.toSet, "PRAGMA index_list('articles')") match{
          case(create, drop) => {
            for(index <- create){ db.execSQL("CREATE INDEX %s ON %s(%s)".format(index, TABLE_NAME, INDICES(index))) }
            for(index <- drop){ db.execSQL("DROP INDEX %s".format(index)) }
          }
        }
        if(oldVersion == 3 && newVersion == 4){
          db.execSQL("UPDATE articles SET %s = '%s'".format(F_SECTION, "Special English"))
        }
        db.setTransactionSuccessful
      }finally{
        db.endTransaction
      }
    }
  }
}
