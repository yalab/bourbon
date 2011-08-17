package org.yalab.bourbon

import _root_.android.app.Service
import _root_.android.content.{ContentValues, ContentResolver, Intent, SharedPreferences}
import _root_.android.os.{IBinder, Handler}
import _root_.android.preference.PreferenceManager
import _root_.android.widget.Toast

import java.io.IOException
import java.net.UnknownHostException

object CrawlService{
  val INVOKE = 1
  val TAG = "CrawlService"

  val NO_ERROR           = 0
  val UNKNOWN_HOST_ERROR = 1
  val IO_ERROR           = 2
  val EXCEPTION          = -1
}

class CrawlService extends Service{
  import CrawlService._

  var mPrefs: SharedPreferences = null
  var mResolver: ContentResolver = null

  override def onCreate{
    super.onCreate
    mResolver = getContentResolver
    mPrefs = PreferenceManager.getDefaultSharedPreferences(this)
  }

  val pipe = new ICrawlService.Stub{
    override def send(message: Int): Int = {
      message match{
        case INVOKE => crawl
        case _      => 0
      }
    }
  }

  override def onBind(intent: Intent): IBinder = {
    return pipe
  }

  def crawl: Int = {
    try{
      val rss = ArticleProvider.downloadRss
      val lastUpdate = ArticleProvider.DateFormatter.parse(mPrefs.getString("lastUpdate", "Thu, 01 Jan 1970 00:00:00 GMT"))
      val pubDate = rss.pubDate
      if(!lastUpdate.equals(pubDate)){
        rss.parse.filter(article => article(ArticleProvider.F_MP3) != null).foreach(article => {
          val values = new ContentValues
          article.filter(_._1 != ArticleProvider.F_TIME).foreach{case(k, v) => values.put(k, v.toString)}
          val c = mResolver.query(ArticleProvider.CONTENT_URI, Array(),
                                  ArticleProvider.F_GUID + ArticleProvider.EQUAL_PLACEHOLDER,
                                  Array(article(ArticleProvider.F_GUID).toString), null)
          if(c.getCount < 1){
            mResolver.insert(ArticleProvider.CONTENT_URI, values)
          }
        })
        val dateStr = ArticleProvider.DateFormatter.format(pubDate)
        val prefEdit = mPrefs.edit
        prefEdit.putString("lastUpdate", dateStr)
        prefEdit.commit
      }
      NO_ERROR
    }catch{
      case e: UnknownHostException => {
        ArticleProvider.writeErrorLog(TAG, e)
        UNKNOWN_HOST_ERROR
      }
      case e: IOException => {
        ArticleProvider.writeErrorLog(TAG, e)
        IO_ERROR
      }
      case e => {
        throw e
        EXCEPTION
      }
    }
  }
}
