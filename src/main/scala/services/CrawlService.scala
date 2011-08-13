package org.yalab.bourbon

import _root_.android.app.Service
import _root_.android.content.Intent
import _root_.android.os.{Binder, Handler}

class CrawlService extends Service{
  final val mBinder = new Binder
  final val mHandler = new Handler
  final val INTERVAL = 1000 * 3
  override def onCreate{
    super.onCreate
    //println("start service")
  }
  override def onBind(intent: Intent): Binder = {
    return mBinder
  }
}
