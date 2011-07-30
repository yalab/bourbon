package org.yalab.bourbon

import _root_.android.content.{ContentProvider, ContentValues, ContentUris, Context, UriMatcher}
import _root_.android.database.Cursor
import _root_.android.database.sqlite.{SQLiteDatabase, SQLiteOpenHelper}
import _root_.android.net.Uri
import _root_.android.provider.BaseColumns
import java.net.URL
import scala.io.Source
import scala.xml.XML

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

  val FIELDS = List("title", "link", "description", "guid", "category", "date", "enclosure", "mp3", "script")

  def download() = {
    val url = "http://www.voanews.com/templates/Articles.rss?sectionPath=/learningenglish/home"
    XML.load(new URL(url)) \ "channel" \ "item" map(item => {
      val encoded = XML.loadString("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>" + (item \ "encoded").head.text + "</root>")
      FIELDS.map(k => {
        val v = k match {
          case "enclosure" => item \ k \ "@url"
          case "mp3"       => {
            val flashvars = (encoded \\ "param" filter(node => (node \ "@name").toString == "flashvars")) \ "@value"
            flashvars.toString.split("&").map(str => str.split("=")).filter(a => a(0) == "file")(0)(1)
          }
          case "script"    => {
            (encoded \\ "p").map(node => {
              "<p>" + node.text.split(" ").map(word => "<a href=\"" + word + "\" onClick=\"return false\">" + word + "</a>").mkString(" ") + "</p>"
            }).mkString("\n\n")
          }
          case _           => (item \ k).head.text
        }
        (k, v)
      }).toMap
    })
  }

  val html_header = """
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
  </style>
</head>
<body>
<div id="content">
  """
  val html_footer = """
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
        db.query(TABLE_NAME, Array("_id") ++ fields, null, null, null, null, "date")
      }
      case SHOW  => {
        val id = uri.getPathSegments().get(1)
        db.query(TABLE_NAME, Array("_id") ++ fields, "_id = ?", Array(id), null, null, "date")
      }
      case _     => throw new IllegalArgumentException("Unknown URI " + uri)
    }

  }

  protected class Database(context: Context) extends SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    def onCreate(db: SQLiteDatabase) {
      val sql = "CREATE TABLE " + TABLE_NAME +
      "(" + BaseColumns._ID + " INTEGER PRIMARY KEY," + FIELDS.map(field => field + " varchar(255)" ).mkString(", ") + ");"
      db.execSQL(sql)
    }

    def onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    }
  }
}
