package org.yalab.bourbon

import _root_.android.app.{Activity, ProgressDialog}
import _root_.android.media.{MediaPlayer, AudioManager}
import _root_.android.os.{Bundle, Handler}
import _root_.android.provider.BaseColumns
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
  var mProgressRefresher: Handler = null
  var mWebView: WebView = null
  val mDownloadMessage = "Downloading MP3 file.\nPlease wait a few minutes..."

  val seekListener = new OnSeekBarChangeListener{
    def onStartTrackingTouch(bar: SeekBar){
      mSeeking = true
    }

    def onStopTrackingTouch(bar: SeekBar){
      mSeeking = false
    }

    def onProgressChanged(bar: SeekBar, progress: Int, fromuser: Boolean){
      if(!fromuser){ return }
      mPlayer.seekTo(progress)
    }
  }

  class ProgressRefresher extends Runnable{
    def run{
      if(mPlayer != null && !mSeeking && mDuration != 0){
        val progress = mPlayer.getCurrentPosition / mDuration
        mSeekBar.setProgress(mPlayer.getCurrentPosition)
      }
      mProgressRefresher.removeCallbacksAndMessages(null);
      mProgressRefresher.postDelayed(new ProgressRefresher, 200);
    }
  }

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.article)
    setVolumeControlStream(AudioManager.STREAM_MUSIC)
    val fields = Array(ArticleProvider.F_SCRIPT, ArticleProvider.F_MP3)
    val c = getContentResolver.query(getIntent.getData, fields, null, null, null)
    c.moveToFirst
    val script = c.getString(c.getColumnIndex(ArticleProvider.F_SCRIPT))
    val mp3    = c.getString(c.getColumnIndex(ArticleProvider.F_MP3))
    val id     = c.getString(c.getColumnIndex(BaseColumns._ID))
    val handler = new Handler
    val dialog = ProgressDialog.show(ArticleActivity.this, null,
                                     mDownloadMessage, true, true)
    (new Thread(new Runnable(){
      def run {
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

            mProgressRefresher = new Handler
            mProgressRefresher.postDelayed(new ProgressRefresher, 200)
            dialog.dismiss
          }
        });
      }
    })).start

    mWebView = findViewById(R.id.webview).asInstanceOf[WebView]
    mWebView.getSettings.setJavaScriptEnabled(true)
    mWebView.getSettings.setUseWideViewPort(true)

    mWebView.loadData(ArticleProvider.htmlHeader + script + ArticleProvider.htmlFooter, "text/html", "utf-8")

    mWebView.setOnLongClickListener(new OnLongClickListener{
      override def onLongClick(v: View) = {
        val hr = mWebView.getHitTestResult
        val url = "http://eow.alc.co.jp/" + hr.getExtra.replace(".", "") + "/UTF-8/"
        mWebView.loadUrl(url)
        true
      }
    })
  }

  override def onKeyDown(keyCode: Int, event: KeyEvent ): Boolean = {
    keyCode match{
      case KeyEvent.KEYCODE_BACK => {
        if(mWebView.canGoBack){
          mWebView.goBack
          true
        }else{
          super.onKeyDown(keyCode, event)
        }
      }
      case _ => super.onKeyDown(keyCode, event)
    }
  }

  override def onStop{
    super.onStop
    mPlayer.stop
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
