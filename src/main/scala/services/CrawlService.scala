package org.yalab.bourbon

import _root_.android.app.Service
import _root_.android.content.Intent
import _root_.android.os.{IBinder, Handler}

class CrawlService extends Service{
  override def onCreate{
    super.onCreate
  }

  val pipe = new ICrawlService.Stub{
    override def send(message: Int): Int = {
      println(message)
      0
    }
  }

  override def onBind(intent: Intent): IBinder = {
    return pipe
  }
}
