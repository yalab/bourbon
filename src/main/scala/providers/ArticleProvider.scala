package org.yalab.bourbon

import _root_.android.content.{ContentProvider, ContentValues, Context}
import _root_.android.database.Cursor
import _root_.android.database.sqlite.{SQLiteDatabase, SQLiteOpenHelper}
import _root_.android.net.Uri
import java.net.URL
import scala.io.Source
import scala.xml.XML

object ArticleProvider{
  final val DATABASE_NAME    = "bourbon.db"
  final val DATABASE_VERSION = 1

  def download() = {
    val url = "http://www.voanews.com/templates/Articles.rss?sectionPath=/learningenglish/home"
    val feed = XML.load(new URL(url))
    feed \ "channel" \ "item" map(item => {
      val fields = List("title", "link", "description", "guid", "category", "encoded", "date", "enclosure")
      fields.map(k => {
        val v = if (k == "enclosure") item \ k \ "@url" else (item \ k).head.text
        (k, v)
      }).toMap
    })
  }
}

class ArticleProvider extends ContentProvider {
  import ArticleProvider._

  override def onCreate: Boolean = {
    true
  }

  def insert(uri: Uri, values: ContentValues): Uri = {
    Uri.parse("http://example.com/")
  }

  def update(uri: Uri, values: ContentValues, where: String, whereArgs: Array[String]): Int = {
    1
  }

  def delete(uri: Uri, where: String, whereArgs: Array[String]): Int = {
    1
  }

  def getType(uri: Uri): String = {
    "example"
  }

  def query(uri: Uri, projection: Array[String], where: String, whereArgs: Array[String], sortOrder: String): Cursor = {
    val ary = new Array[java.lang.String](0)
    (new Database(getContext)).getReadableDatabase.rawQuery("", ary)
  }

  protected class Database(context: Context) extends SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    def onCreate(db: SQLiteDatabase) {
    }
    def onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    }
  }
}
