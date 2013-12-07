package jp.qee.miyabi.gdmp;

import java.io.File;
import java.io.InputStream;
import java.io.RandomAccessFile;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.os.AsyncTask;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;

public class MusicPlayerService extends Service	implements OnPreparedListener, OnCompletionListener {
	public static final String ACTION_STATE_CHANGED    = "jp.qee.miyabi.gdmp.ACTION_STATE_CHANGED";
	public static final String ACTION_PLAYPAUSE        = "jp.qee.miyabi.gdmp.ACTION_PLAYPAUSE";
	public static final String ACTION_PLAY             = "jp.qee.miyabi.gdmp.ACTION_PLAY";
	public static final String ACTION_PAUSE            = "jp.qee.miyabi.gdmp.ACTION_PAUSE";
	public static final String ACTION_SKIP             = "jp.qee.miyabi.gdmp.ACTION_SKIP";
	public static final String ACTION_REWIND           = "jp.qee.miyabi.gdmp.ACTION_REWIND";
	public static final String ACTION_CLEAR            = "jp.qee.miyabi.gdmp.ACTION_CLEAR";
	public static final String ACTION_REQUEST_STATE    = "jp.qee.miyabi.gdmp.ACTION_REQUEST_STATE";
	public static final String ACTION_BROADCAST        = "jp.qee.miyabi.gdmp.ACTION_BROADCAST";
	public static final String PARAM_TYPE      = "Type";
	public static final String PARAM_STATE     = "State";
	public static final String PARAM_POSITION  = "Position";
	public static final String PARAM_MESSAGE   = "Message";
//	public static final String PARAM_METADATA  = "MetaData";
	public static final String KEY_REPEAT = "RepeatMode";
	public static final String KEY_RANDOM = "RandomMode";
	private static final int TIME_FOR_STOP = 60 * 1 * 1000; // サービス停止までの待機時間(1分)
	// サービスの状態
	public static final int STATE_STOPPED = 0;		// 停止中
	public static final int STATE_PREPARING = 1;	// 再生準備中
	public static final int STATE_BUFFERING = 2;	// バッファリング中
	public static final int STATE_PLAYING = 3;		// 再生中
	public static final int STATE_PAUSED = 4;		// 一時停止中
	// リピートの状態
	public static final int REPEAT_NONE = 0;	// なし
	public static final int REPEAT_SINGLE = 1;	// 単曲
	public static final int REPEAT_ALL = 2;		// 全曲
	// ランダムの状態
	public static final int RANDOM_OFF = 0;
	public static final int RANDOM_ON = 1;

	private MediaPlayer mPlayer = null;
	private int mState = STATE_STOPPED;
	private long mReleaseTime = System.currentTimeMillis(); // MediaPlayerを解放した時間
	private int mPosition = -1;
	private boolean mStartPlayingAfterCancelBuffering = false;

	private boolean isIdleTimeOver() {
		return mReleaseTime + TIME_FOR_STOP < System.currentTimeMillis();
	}

	@Override
	public void onCreate() {
		Log.d("TEST", "MusicPlayerService::onCreate");

		// 不要になった時に自身を停止するための監視スレッドを作成する
		new Thread(new Runnable() {
			@Override
			public void run() {
				while (true) {
					try {
						if (mState == STATE_STOPPED || mState == STATE_PAUSED) {
							if (isIdleTimeOver()) {
								break;
							}
						}
						Thread.sleep(60 * 1000);
					} catch (InterruptedException e) {
//						e.printStackTrace();
					}
				}
				MusicPlayerService.this.stopSelf();
			}
		}).start();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d("TEST", "MusicPlayerService::onStartCommand");

		String action = intent.getAction();
		if (action.equals(ACTION_PLAYPAUSE)) {
			processPlayPauseRequest(intent);
		} else if (action.equals(ACTION_PLAY)) {
			processPlayRequest(intent);
		} else if (action.equals(ACTION_PAUSE)) {
			processPauseRequest(intent);
		} else if (action.equals(ACTION_SKIP)) {
			processSkipRequest(intent, false);
		} else if (action.equals(ACTION_REWIND)) {
			processRewindRequest(intent);
		} else if (action.equals(ACTION_CLEAR)) {
			processClearRequst(intent);
		} else if (action.equals(ACTION_REQUEST_STATE)) {
			sendStateChanged(mState);
		}

		return START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
		Log.d("TEST", "MusicPlayerService::onDestroy");

		releaseMediaPlayer();
		// 再生に使用したファイルを削除する
		File file = new File(getCacheDir(), "GDMP.mp3");
		file.delete();
		
		showToast("サービス終了。");
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}

	@Override
	public void onPrepared(MediaPlayer mp) {
		Log.d("TEST", "MusicPlayerService::onPrepared");
		if (mStartPlayingAfterCancelBuffering) {
			// バッファリング中に新たな再生要求を受けた場合は再生しない
		} else if (STATE_PREPARING == mState) {
			sendStateChanged(STATE_BUFFERING);;
			mp.start();
		} else {
			showToast("再生しません。現在の状態は" + Long.toString(mState));
		}
	}

	@Override
	public void onCompletion(MediaPlayer mp) {
		showToast("再生完了");
		releaseMediaPlayer();
		
	    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		if (prefs.getInt(KEY_REPEAT, 0) == REPEAT_SINGLE) {
			// 現在の曲を再指定して再生する
			Intent i = new Intent(ACTION_PLAY);
			i.putExtra(PARAM_POSITION, mPosition);
			processPlayRequest(i);
		} else {
			processSkipRequest(null, true);
		}
	}

	//
	// MediaPlayerのインスタンスを取得する
	//
	private MediaPlayer getMediaPlayer() {
		if (null == mPlayer) {
			mPlayer = new MediaPlayer();
			mPlayer.setOnPreparedListener(this);
			mPlayer.setOnCompletionListener(this);
			mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
		} else {
			mPlayer.reset();
		}
		return mPlayer;
	}
	
	//
	// MediaPlayerのインスタンスを解放する
	//
	private void releaseMediaPlayer() {
		if (mPlayer != null) {
			mPlayer.reset();
			mPlayer.release();
			mPlayer = null;
		}
		sendStateChanged(STATE_STOPPED);
		mReleaseTime = System.currentTimeMillis();
	}

	private void processPlayPauseRequest(Intent intent) {
		switch (mState) {
		case STATE_STOPPED:
		case STATE_PAUSED:
			processPlayRequest(intent);
			break;
		case STATE_BUFFERING:
		case STATE_PLAYING:
			processPauseRequest(intent);
			break;
		case STATE_PREPARING:
			showToast("すでに準備中なので続行します。");
			break;
		}
	}

	private void processPlayRequest(Intent intent) {
		Log.d("TEST", "processPlayRequest");
		
		DBAdapter dbAdapter = DBAdapter.getInstance(this);
		int new_position = mPosition;
		if (intent != null) {
			// インテントから再生トラック番号を取得する
			new_position = intent.getIntExtra(PARAM_POSITION, 0);
			if (new_position < 0) {
				new_position = 0;
			}
		} else {
			Log.d("TEST", "intent is null.");
		    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		    int repeat = prefs.getInt(MusicPlayerService.KEY_REPEAT, 0);
		    if (prefs.getInt(KEY_RANDOM, 0) == RANDOM_ON) {
		    	new_position = dbAdapter.getPositionAtRandom(REPEAT_ALL == repeat);
		    	if (new_position == -1) {
		    		// 次の再生リクエストのために、playedフラグをクリアしておく
		    		dbAdapter.clearPlayedFlag();
		    		releaseMediaPlayer();
		    		return;
		    	}
		    }
		}

		switch (mState) {
		case STATE_PREPARING:
		case STATE_BUFFERING:
			if (new_position != mPosition) {
				// 現在の再生を中止してから開始する
				// 再生トラック番号は↑で取得したものを使用する
				mPosition = new_position;
				mStartPlayingAfterCancelBuffering = true;
				// バッファリング処理で停止状態に移行し、再度 processPlayRequest が呼ばれる
				showToast("現在のバッファリングをキャンセルします。");
			} else {
				showToast("無視します。");
			}
			return;
		case STATE_PLAYING:
			if (new_position == mPosition) {
				showToast("再生してんでしょうが！");
				return;
			}
			break;
		case STATE_PAUSED:
			if (new_position == mPosition) {
				// 現在の停止位置から再開
				sendStateChanged(STATE_PLAYING);
				if (!mPlayer.isPlaying()) {
					mPlayer.start();
				}
				return;
			}
			break;
		}
		mPosition = new_position;
		
		// リソースを解放して停止状態に移行する
		releaseMediaPlayer();
	
		// 再生トラック番号に該当するアイテムを取得する
		PlayListItem item = dbAdapter.getItemByPosition(new_position);
		if (null == item) {
			showToast("再生トラック[" + Long.toString(new_position) + "]を取得できません。");
			return;
		}

		// 別スレッドでファイルのダウンロードを開始する
		mState = STATE_PREPARING;
		mStartPlayingAfterCancelBuffering = false;
		new DownloadAndPlayAsyncTask(this).execute(item);
	}
	
	private void processPauseRequest(Intent intent) {
		sendStateChanged(STATE_PAUSED);
		mReleaseTime = System.currentTimeMillis();
		if (mPlayer.isPlaying()) {
			mPlayer.pause();
		}
	}

	private void processSkipRequest(Intent intent, boolean auto) {
		// 単曲リピートで再生完了した場合は、onCompletion で同じ曲を再生するので、
		// このメソッドでは考慮不要。
	    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
	    if (prefs.getInt(KEY_RANDOM, 0) == RANDOM_ON) {
	    	// ランダムの場合は processPlayRequest で再生曲を決定する
	    	processPlayRequest(null);
	    } else {
			int new_position = mPosition + 1;
			if (new_position >= DBAdapter.getInstance(this).getCount()) {
			    if (auto && REPEAT_NONE == prefs.getInt(MusicPlayerService.KEY_REPEAT, 0)) {
			    	// 自動で次に進んだが、リピートなしで最後まで来たので終了する
			    	mPosition = 0;
			    	releaseMediaPlayer();
			    	return;
			    }
				new_position = 0;
			}
			Intent i = new Intent(ACTION_PLAY);
			i.putExtra(PARAM_POSITION, new_position);
			processPlayRequest(i);
	    }
	}
	
	private void processRewindRequest(Intent intent) {
		if (mPlayer != null && mPlayer.getCurrentPosition() > 1000) {
			mPlayer.seekTo(0);
		} else {
			int new_position = mPosition - 1;
			if (new_position < 0) {
				new_position = DBAdapter.getInstance(this).getCount() - 1;
			}
			Intent i = new Intent(ACTION_PLAY);
			i.putExtra(PARAM_POSITION, new_position);
			processPlayRequest(i);
		}
	}
	
	private void processClearRequst(Intent intent) {
		DBAdapter.getInstance(this).removeAllItems();
		releaseMediaPlayer();
	}

	private class DownloadAndPlayAsyncTask extends AsyncTask<PlayListItem, Long, Void> {
		private Context mContext;

		public DownloadAndPlayAsyncTask(Context context) {
			mContext = context;
		}
		
		@Override
		protected Void doInBackground(PlayListItem... params) {
			showToast("Downloading...");
			PlayListItem item = (PlayListItem) params[0];

			try {
				// 一時ファイルを作成する
				File file = new File(getCacheDir(), "GDMP.mp3");
				RandomAccessFile oStream = new RandomAccessFile(file, "rw");
				oStream.setLength(item.getFileSize());
				oStream.seek(0);

				// Google Driveからファイルを取得する
				InputStream iStream = GDriveUtils.getDownloadStream(mContext, item);
				try
				{
					byte[] buffer = new byte[1024];
					int read;
					int readed = 0;
					// 最初の320KBを読み込む(閾値は適当・・・)
					while ((read = iStream.read(buffer)) != -1) {
						if (mStartPlayingAfterCancelBuffering) {
							// バッファリングを中断して、次の曲を再生する
							oStream.close();
							releaseMediaPlayer();
							Intent i = new Intent(ACTION_PLAY);
							i.putExtra(PARAM_POSITION, mPosition);
							processPlayRequest(i);
							return null;
						}
						oStream.write(buffer, 0, read);
						oStream.getFD().sync();
						if (readed != -1) {
							readed += read;
							if (readed >= 320 * 1024) {
								MediaPlayer mp = getMediaPlayer();
								mp.setDataSource(file.getPath());
								mp.prepareAsync();
								readed = -1;
							}
						}
					}
					// 320KB未満・・・ありえる？
					if (null == mPlayer) {
						MediaPlayer mp = getMediaPlayer();
						mp.setDataSource(file.getPath());
						mp.prepare();
						mp.start();
					}
					
					MediaMetadataRetriever mmr = new MediaMetadataRetriever();
					mmr.setDataSource(file.getPath());
					DBAdapter.getInstance(getApplicationContext()).setMetaData(item.getId(), mmr);

					showToast("Download complete.");
					sendStateChanged(STATE_PLAYING);
				} catch (Exception e) {
					showToast("Download failed...orz");
					e.printStackTrace();
					releaseMediaPlayer();
				} finally {
					oStream.close();
				}
			} catch (Exception e) {
				showToast("Download incomplete...orz");
				e.printStackTrace();
				releaseMediaPlayer();
			}
			return null;
		}
	}

	private void sendStateChanged(int newState) {
		mState = newState;
		Intent intent = new Intent(ACTION_BROADCAST);
		intent.putExtra(PARAM_TYPE, PARAM_STATE);
		intent.putExtra(PARAM_POSITION, mPosition);
		intent.putExtra(PARAM_STATE, mState);

		sendBroadcast(intent);		
	}
	//
	// Activityでトーストを表示する
	//
	private void showToast(String str) {
		Intent intent = new Intent(ACTION_BROADCAST);
		intent.putExtra(PARAM_TYPE, PARAM_MESSAGE);
		intent.putExtra(PARAM_MESSAGE, str);

		sendBroadcast(intent);		
	}
}