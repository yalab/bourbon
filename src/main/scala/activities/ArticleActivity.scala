package org.yalab.bourbon

import android.app.{Activity, ProgressDialog}
import android.content.{ContentResolver, ContentValues, ContentUris}
import android.media.{MediaPlayer, AudioManager}
import android.os.{Bundle, Handler}
import android.net.Uri
import android.provider.BaseColumns
import android.view.{View, KeyEvent, Window}
import android.view.View.OnLongClickListener
import android.widget.{SeekBar, ImageButton, Toast}
import android.widget.SeekBar.OnSeekBarChangeListener
import android.webkit.{WebView, WebViewClient, WebChromeClient}
import scala.io.Source
import java.io.IOException

class ArticleActivity extends Activity {
  var mPlayer: MediaPlayer = null
  var mPlayButton: ImageButton = null
  var mDuration: Int = 0
  var mSeeking: Boolean = false
  var mSeekBar: SeekBar = null
  var mProgressRefresher: Handler = null
  var mWebView: WebView = null
  var mResolver: ContentResolver = null
  var mUri: Uri = null
  val TAG = "ArticleActivity"
  val ONE_HOUR   = 1000 * 60 * 60
  val ONE_MINUTE = 1000 * 60

  val seekListener = new OnSeekBarChangeListener{
    def onStartTrackingTouch(bar: SeekBar){
      mSeeking = true
      stopSpeech
    }

    def onStopTrackingTouch(bar: SeekBar){
      mSeeking = false
      playSpeech
      mPlayButton.setImageResource(R.drawable.pause)
    }

    def onProgressChanged(bar: SeekBar, progress: Int, fromuser: Boolean){
      if(!fromuser){ return }
      mPlayer.seekTo(progress)
    }
  }

  val completionListener = new MediaPlayer.OnCompletionListener{
    def onCompletion(player :MediaPlayer){
      stopSpeech
      mSeekBar.setProgress(0)
      mPlayButton.setImageResource(R.drawable.play)
    }
  }

  class ProgressRefresher extends Runnable{
    def run{
      if(mPlayer != null && !mSeeking && mDuration != 0){
        mSeekBar.setProgress(mPlayer.getCurrentPosition)
      }
      mProgressRefresher.removeCallbacksAndMessages(null)
      mProgressRefresher.postDelayed(new ProgressRefresher, 200)
    }
  }

  override def onCreate(savedInstanceState: Bundle) {
    super.onCreate(savedInstanceState);
    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS)

    setContentView(R.layout.article)
    setVolumeControlStream(AudioManager.STREAM_MUSIC)
    val fields = Array(ArticleProvider.F_SCRIPT, ArticleProvider.F_MP3, ArticleProvider.F_TIME, ArticleProvider.F_CURRENT_POSITION, ArticleProvider.F_SCROLL_Y)
    mResolver = getContentResolver
    val c = mResolver.query(getIntent.getData, fields, null, null, null)
    val script   = c.getString(c.getColumnIndex(ArticleProvider.F_SCRIPT))
    val mp3      = c.getString(c.getColumnIndex(ArticleProvider.F_MP3))
    val time     = c.getString(c.getColumnIndex(ArticleProvider.F_TIME))
    val id       = c.getLong(c.getColumnIndex(BaseColumns._ID))
    val position = c.getInt(c.getColumnIndex(ArticleProvider.F_CURRENT_POSITION))
    val scrollY = c.getInt(c.getColumnIndex(ArticleProvider.F_SCROLL_Y))
    mUri = ContentUris.withAppendedId(ArticleProvider.CONTENT_URI, id)
    mPlayButton  = findViewById(R.id.play_button).asInstanceOf[ImageButton]

    if(ArticleProvider.mp3File(id.toString).exists == false &&
       ArticleProvider.isDownloadable(this) == false){
      mPlayButton.setImageResource(R.drawable.cross)
      Toast.makeText(this, getString(R.string.netword_is_down_so_cannot_donwload_mp3), Toast.LENGTH_LONG).show
      render(script)
      return
    }
    val handler = new Handler
    val dialog = ProgressDialog.show(ArticleActivity.this, null,
                                     getString(R.string.download_mp3), true, true)
    (new Thread(new Runnable(){
      def run {
        val path = ArticleProvider.fetchMp3(id.toString, mp3) match{
          case None    => null
          case Some(f) => f
        }
        handler.post(new Runnable() {
          def run {
            try{
              mPlayer = new MediaPlayer
              mPlayer.setOnCompletionListener(completionListener)
              if(path == null){
                throw new IOException
              }
              mPlayer.setDataSource(path.toString)
              mPlayer.prepare
            }catch{
              case e: IOException => {
                mPlayButton.setImageResource(R.drawable.cross)
                mPlayer = null
                if(path != null && path.exists){
                  path.delete
                }
                Toast.makeText(ArticleActivity.this, getString(R.string.mp3_is_wrong_please_retry), Toast.LENGTH_LONG).show
                dialog.dismiss
                ArticleProvider.writeErrorLog(TAG, e)
                return
              }
            }

            mSeekBar = findViewById(R.id.seekbar).asInstanceOf[SeekBar]
            mDuration = mPlayer.getDuration
            if(time == null){
              val uri = ContentUris.withAppendedId(ArticleProvider.CONTENT_URI, id)
              val minutes = ( mDuration % (ONE_HOUR) ) / (ONE_MINUTE);
              val seconds = ( ( mDuration % (ONE_HOUR) ) % (ONE_MINUTE) ) / 1000;

              val values = new ContentValues
              values.put(ArticleProvider.F_TIME, "%02d:%02d".format(minutes, seconds))
              mResolver.update(uri, values, null, null)
            }

            mSeekBar.setMax(mDuration)
            mSeekBar.setOnSeekBarChangeListener(seekListener)
            if(position > 0){
              mPlayer.seekTo(position)
              mSeekBar.setProgress(position)
            }
            mProgressRefresher = new Handler
            dialog.dismiss
          }
        });
      }
    })).start
    render(script)
    if(scrollY > 0){
      mWebView.loadUrl("javascript:window.onload=function(){window.scrollTo(0, %s);};".format(scrollY))
    }
  }

  def render(script:String) {
    mWebView = findViewById(R.id.webview).asInstanceOf[WebView]
    mWebView.getSettings.setJavaScriptEnabled(true)
    mWebView.getSettings.setUseWideViewPort(true)
    val activity = this;
    mWebView.setWebChromeClient(new WebChromeClient{
      var mLoading = false
      override def onProgressChanged(view: WebView, progress: Int){
        if(mLoading == false && 0 < progress){
          activity.setProgressBarIndeterminateVisibility(true)
          mLoading = true
        }

        if(mLoading == true && 99 < progress){
          activity.setProgressBarIndeterminateVisibility(false)
          mLoading = false
        }
      }
      override def onJsAlert(view: WebView, url: String, message: String, result: android.webkit.JsResult): Boolean = {
        val values = new ContentValues(2)
        values.put(ArticleProvider.F_SCROLL_Y, message.toInt.asInstanceOf[java.lang.Integer])
        values.put(ArticleProvider.F_CURRENT_POSITION, mPlayer.getCurrentPosition.asInstanceOf[java.lang.Integer])
        mResolver.update(mUri, values, null, null)
        result.confirm
        true
      }
    })


    mWebView.setWebViewClient(new WebViewClient)
    val js = """
    location.href = 'http://eow.alc.co.jp/' + word.replace(/[,.]/g, '') + '/UTF-8/';
    """
    mWebView.loadData(ArticleProvider.htmlHeader.format(js) + script + ArticleProvider.htmlFooter, "text/html; charset=UTF-8", null)
  }

  override def onKeyDown(keyCode: Int, event: KeyEvent): Boolean = {
    keyCode match{
      case KeyEvent.KEYCODE_BACK => {
        if(mWebView.canGoBack){
          val size = mWebView.copyBackForwardList.getSize
          mWebView.goBackOrForward(-(size - 1))
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
    if(mPlayer != null){
      mWebView.loadUrl("javascript:alert(document.body.scrollTop);")
      stopSpeech
      mPlayButton.setImageResource(R.drawable.play)
    }
  }

  def pressPlay(view: View) {
    if(mPlayer == null){
      return
    }
    if(mPlayer.isPlaying){
      stopSpeech
      mPlayButton.setImageResource(R.drawable.play)
    }else{
      playSpeech
      mPlayButton.setImageResource(R.drawable.pause)
    }
  }

  def playSpeech{
    if(!mPlayer.isPlaying){
      mPlayer.start
    }
    mProgressRefresher.postDelayed(new ProgressRefresher, 200)
  }

  def stopSpeech{
    if(mPlayer.isPlaying){
      mPlayer.pause
    }
    mProgressRefresher.removeCallbacksAndMessages(null)
  }
}
