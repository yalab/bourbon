package org.yalab.bourbon

import android.app.Service
import android.content.{ContentValues, ContentResolver, Intent, SharedPreferences}
import android.os.{IBinder, Handler}
import android.preference.PreferenceManager
import android.util.Log
import android.widget.Toast

import java.io.IOException
import java.net.UnknownHostException

object CrawlService{
  val INVOKE = 1
  val START  = 2
  val STOP   = 3
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
  val mHandler = new Handler
  val INTERVAL = 1000 * 3600 * 3

  override def onCreate{
    super.onCreate
    mResolver = getContentResolver
    mPrefs = PreferenceManager.getDefaultSharedPreferences(this)
    if(mPrefs.getBoolean("autodownload", false)){
      mHandler.postDelayed(new Crawler, 0)
    }
  }

  val pipe = new ICrawlService.Stub{
    override def send(message: Int): Int = {
      message match{
        case INVOKE => crawl
        case START  => {
          mHandler.removeCallbacksAndMessages(null)
          mHandler.postDelayed(new Crawler, 0)
          NO_ERROR
        }
        case STOP   => {
          mHandler.removeCallbacksAndMessages(null)
          NO_ERROR
        }
        case _      => NO_ERROR
      }
    }
  }

  override def onBind(intent: Intent): IBinder = {
    return pipe
  }

  def crawl: Int = {
    try{
      val rss = ArticleProvider.VOARss("Special English")
      val lastUpdate = ArticleProvider.RFC822DateTime.parse(mPrefs.getString("lastUpdate", "Thu, 01 Jan 1970 00:00:00 GMT"))
      val pubDate = rss.pubDate
      if(!lastUpdate.equals(pubDate)){
        rss.parse.filter(article => article.contains(ArticleProvider.F_MP3) && article(ArticleProvider.F_MP3) != null ).foreach(article => {
          val values = new ContentValues
          article.filter{case(k, v) => v != null}.foreach{case(k, v) => values.put(k, v.toString)}

          val c = mResolver.query(ArticleProvider.CONTENT_URI, Array(),
                                  ArticleProvider.F_GUID + ArticleProvider.EQUAL_PLACEHOLDER,
                                  Array(article(ArticleProvider.F_GUID).toString), null)
          if(c.getCount < 1){
            mResolver.insert(ArticleProvider.CONTENT_URI, values)
          }
        })
        val dateStr = ArticleProvider.RFC822DateTime.format(pubDate)
        val prefEdit = mPrefs.edit
        prefEdit.putString("lastUpdate", dateStr)
        prefEdit.commit
      }
      NO_ERROR
    }catch{
      case e: UnknownHostException => UNKNOWN_HOST_ERROR
      case e: IOException          => IO_ERROR
      case e => {
        ArticleProvider.writeErrorLog(TAG, e)
        throw e
        EXCEPTION
      }
    }
  }

  class Crawler extends Runnable{
    def run{
      (new Thread(new Runnable(){
        def run {
          Log.v(TAG, "start crawl")
          crawl
          Log.v(TAG, "stop crawl")
        }
      })).start
      mHandler.removeCallbacksAndMessages(null)
      mHandler.postDelayed(new Crawler, INTERVAL)
    }
  }
}
