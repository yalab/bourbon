package org.yalab.bourbon

import _root_.android.app.{Activity, ProgressDialog}
import _root_.android.media.MediaPlayer
import _root_.android.os.{Bundle, Handler}
import _root_.android.view.{View, KeyEvent}
import _root_.android.view.View.OnLongClickListener
import _root_.android.widget.{SeekBar, ImageButton}
import _root_.android.widget.SeekBar.OnSeekBarChangeListener
import _root_.android.webkit.WebView
import scala.io.Source

class ArticleActivity extends Activity {
  var mPlayer: MediaPlayer = null
  var mPlayButton: ImageButton = null
  var mDuration: Int = 0
  var mSeeking: Boolean = false
  var mSeekBar: SeekBar = null

  val seekListener = new OnSeekBarChangeListener{
    def onStopTrackingTouch(bar: SeekBar){
      mSeeking = false
    }
    def onStartTrackingTouch(bar: SeekBar){
      mSeeking = true
    }

    def onProgressChanged(bar: SeekBar, progress: Int, fromuser: Boolean){
      mPlayer.seekTo(progress);
    }
  }

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.article)
    val fields = Array("script", "mp3")
    val c = getContentResolver.query(getIntent.getData, fields, null, null, null)
    c.moveToFirst
    val script = c.getString(c.getColumnIndex("script"))
    val mp3    = c.getString(c.getColumnIndex("mp3"))
    val id     = c.getString(c.getColumnIndex("_id"))
    val handler = new Handler
    val dialog = ProgressDialog.show(ArticleActivity.this, "",
                                     "Downloading MP3 file.\nPlease wait a few minutes...", true, true)
    (new Thread(new Runnable(){
      def run() {
        val path = ArticleProvider.fetch_mp3(id, mp3)
        handler.post(new Runnable() {
          def run {
            mPlayer = new MediaPlayer
            mPlayer.setDataSource(path.toString)
            mPlayer.prepare
            mPlayButton = findViewById(R.id.play_button).asInstanceOf[ImageButton]
            mSeekBar = findViewById(R.id.seekbar).asInstanceOf[SeekBar]
            mDuration = mPlayer.getDuration

            mSeekBar.setMax(mDuration)
            mSeekBar.setOnSeekBarChangeListener(seekListener)
            dialog.dismiss
          }
        });
      }
    })).start

    val webview = findViewById(R.id.webview).asInstanceOf[WebView]
    webview.getSettings.setJavaScriptEnabled(true)
    webview.getSettings.setUseWideViewPort(true)

    webview.loadData(ArticleProvider.html_header + script + ArticleProvider.html_footer, "text/html", "utf-8")

    webview.setOnLongClickListener(new OnLongClickListener{
      override def onLongClick(v: View) = {
        val webview =  v.asInstanceOf[WebView]
        val hr = webview.getHitTestResult
        val url = "http://eow.alc.co.jp/" + hr.getExtra.replace(".", "") + "/UTF-8/"
        webview.loadUrl(url)
        true
      }
    })
  }

  override def onKeyDown(key_code: Int, event: KeyEvent ): Boolean = {
    val webview = findViewById(R.id.webview).asInstanceOf[WebView]
    if(key_code == KeyEvent.KEYCODE_BACK && webview.canGoBack){
      webview.goBack
      true
    }else{
      if(mPlayer != null){
        mPlayer.release
        mPlayer = null
      }
      super.onKeyDown(key_code, event);
    }
  }

  def pressPlay(view: View) {
    if(mPlayer.isPlaying){
      mPlayer.pause
      mPlayButton.setImageResource(R.drawable.play)
    }else{
      mPlayButton.setImageResource(R.drawable.pause)
      mPlayer.start
    }
  }
}
