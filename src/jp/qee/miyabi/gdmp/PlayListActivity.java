package jp.qee.miyabi.gdmp;

import java.io.IOException;
import java.util.Random;

import com.google.android.gms.auth.GoogleAuthException;
import com.google.android.gms.auth.GoogleAuthUtil;
import com.google.android.gms.auth.UserRecoverableAuthException;
import com.google.android.gms.common.AccountPicker;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;

import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

public class PlayListActivity extends ListActivity implements OnClickListener {
	private static final int REQUEST_PLAY_SERVICES = 1001;
	private static final int REQUEST_PICK_ACCOUNT = 1002;
	private static final int REQUEST_AUTHORIZATION = 1003;
	private int mPositionClicked = -1;
	private ImageButton mBtnRewind;
	private ImageButton mBtnPlayPause;
	private ImageButton mBtnSkip;
	private Button mBtnRepeat;
	private ToggleButton mBtnRandom;
	private IntentFilter mFilter;
	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String type = intent.getStringExtra(MusicPlayerService.PARAM_TYPE);
			if (type.equals(MusicPlayerService.PARAM_MESSAGE)) {
				// サービスから受け取った文字列をトースト表示する
				String message = intent.getStringExtra(MusicPlayerService.PARAM_MESSAGE);
				Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
			} else if (type.equals(MusicPlayerService.PARAM_STATE)) {
				// トラックリストを更新する
				((PlaylistViewAdapter) getListAdapter()).notifyDataSetChanged();
				// 現在の再生トラックを選択する
				int position = intent.getIntExtra(MusicPlayerService.PARAM_POSITION, -1);
				if (position != -1) {
					getListView().setItemChecked(position, true);
					if (mPositionClicked != position) {
						getListView().setSelectionFromTop(position, 3);
					}
				}
				// 再生・停止ボタンの表示を更新する
				switch (intent.getIntExtra(MusicPlayerService.PARAM_STATE, -1)) {
				case MusicPlayerService.STATE_BUFFERING:
				case MusicPlayerService.STATE_PLAYING:
					mBtnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
					break;
				default:
					mBtnPlayPause.setImageResource(android.R.drawable.ic_media_play);
				}
				// その他のボタン表示を更新する
				invalidateButtons();
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d("TEST", "onCreate");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_palylist);
		
		mBtnRewind = (ImageButton) findViewById(R.id.btnRewind);
		mBtnPlayPause = (ImageButton) findViewById(R.id.btnPlayPause);
		mBtnSkip = (ImageButton) findViewById(R.id.btnSkip);
		mBtnRepeat = (Button) findViewById(R.id.btnRepeat);
		mBtnRandom = (ToggleButton) findViewById(R.id.btnRandom);

		mBtnRewind.setOnClickListener(this);
		mBtnPlayPause.setOnClickListener(this);
		mBtnSkip.setOnClickListener(this);
		mBtnRepeat.setOnClickListener(this);
		mBtnRandom.setOnClickListener(this);

		setListAdapter(new PlaylistViewAdapter(this));
		getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Intent i = new Intent(MusicPlayerService.ACTION_PLAY);
				i.putExtra(MusicPlayerService.PARAM_POSITION, position);
				startService(i);
				mPositionClicked = position;
			}
		});
	}

	@Override
	protected void onResume() {
		Log.d("TEST", "onResume");
		super.onResume();

		mFilter = new IntentFilter();
		mFilter.addAction(MusicPlayerService.ACTION_BROADCAST);
		registerReceiver(mReceiver, mFilter);
		
		// Google Play ServiceとGoogleアカウントをチェックする
		if (checkGooglePlayService() && checkUserAccount()) {
			// アカウント認証を走らせる
			new AuthAsyncTask().execute(this);

//			mPlaylistView.invalidateViews();
			// サービスに現在の状態を問い合わせる
			Intent i = new Intent(MusicPlayerService.ACTION_REQUEST_STATE);
			startService(i);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		
		unregisterReceiver(mReceiver);
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.playlist, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent i;
		switch (item.getItemId()) {
		case R.id.action_browse:
			i = new Intent(this, TrackListActivity.class);
			startActivity(i);
			return true;
		case R.id.action_clear:
			i = new Intent(MusicPlayerService.ACTION_CLEAR);
			startService(i);
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onClick(View v) {
	    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

	    if (v == mBtnRewind) {
			if (showNoTracksMessage() == false) {
				startService(new Intent(MusicPlayerService.ACTION_REWIND));
			}
		} else if (v == mBtnPlayPause) {
			if (showNoTracksMessage() == false) {
				Intent i = new Intent(MusicPlayerService.ACTION_PLAYPAUSE);
				int position = getListView().getCheckedItemPosition();
				if (-1 == position) {
					if (prefs.getInt(MusicPlayerService.KEY_RANDOM, 0) == MusicPlayerService.RANDOM_ON) {
						position = new Random().nextInt(getListView().getCount());
					}
				}
				i.putExtra(MusicPlayerService.PARAM_POSITION, position);
				startService(i);
			}
		} else if (v == mBtnSkip) {
			if (showNoTracksMessage() == false) {
				startService(new Intent(MusicPlayerService.ACTION_SKIP));
			}
		} else if (v == mBtnRepeat) {
		    int repeat = prefs.getInt(MusicPlayerService.KEY_REPEAT, 0);
		    if (++repeat > MusicPlayerService.REPEAT_ALL) {
		    	repeat = MusicPlayerService.REPEAT_NONE;
		    }
		    Editor editor = prefs.edit();
		    editor.putInt(MusicPlayerService.KEY_REPEAT, repeat);
		    editor.apply();

		    // ボタンテキストを更新する
		    invalidateButtons();

		} else if (v == mBtnRandom) {
		    int random = prefs.getInt(MusicPlayerService.KEY_RANDOM, 0);
		    if (MusicPlayerService.RANDOM_OFF == random) {
		    	random = MusicPlayerService.RANDOM_ON;
		    } else {
		    	random = MusicPlayerService.RANDOM_OFF;
		    }
		    Editor editor = prefs.edit();
		    editor.putInt(MusicPlayerService.KEY_RANDOM, random);
		    editor.apply();
			
		    // ボタンテキストを更新する
		    invalidateButtons();
		}
	}

	private boolean showNoTracksMessage() {
		if (getListView().getCount() == 0) {
			Toast.makeText(this, getResources().getString(R.string.please_add_tracks), Toast.LENGTH_LONG).show();
			return true;
		}
		return false;
	}
	
	private void invalidateButtons() {
	    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
	    switch (prefs.getInt(MusicPlayerService.KEY_REPEAT, 0)) {
	    case MusicPlayerService.REPEAT_NONE:
	    	mBtnRepeat.setText(getResources().getString(R.string.repeat_none));
	    	break;
	    case MusicPlayerService.REPEAT_SINGLE:
	    	mBtnRepeat.setText(getResources().getString(R.string.repeat_single));
	    	break;
	    case MusicPlayerService.REPEAT_ALL:
	    	mBtnRepeat.setText(getResources().getString(R.string.repeat_all));
	    	break;
	    }
	    
	    if (prefs.getInt(MusicPlayerService.KEY_RANDOM, 0) == MusicPlayerService.RANDOM_OFF) {
	    	mBtnRandom.setChecked(false);
	    } else {
	    	mBtnRandom.setChecked(true);
	    }
	}

	private boolean checkGooglePlayService() {
		int retCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
		if (ConnectionResult.SUCCESS != retCode) {
			if (GooglePlayServicesUtil.isUserRecoverableError(retCode)) {
				GooglePlayServicesUtil.getErrorDialog(retCode, this, REQUEST_PLAY_SERVICES).show();
			} else {
				googlePlayServicesError();
			}
			return false;
		}
		return true;
	}

	private boolean checkUserAccount() {
		String accountName = AccountUtils.getAccountName(this);
		if (null == accountName) {
			showAccountPicker();
			return false;
		}

		Account account = AccountUtils.getGoogleAccountByName(this, accountName);
		if (null == account) {
			AccountUtils.removeAccount(this);
			showAccountPicker();
			return false;
		}

		return true;
	}

	private void showAccountPicker() {
		Intent i = AccountPicker.newChooseAccountIntent(null, null,
				new String[] { GoogleAuthUtil.GOOGLE_ACCOUNT_TYPE }, false,
				null, null, null, null);
		startActivityForResult(i, REQUEST_PICK_ACCOUNT);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.d("TEST", "onActivityResult");
		switch (requestCode) {
		case REQUEST_PLAY_SERVICES:
			if (RESULT_CANCELED == resultCode) {
				googlePlayServicesError();
			}
			return;
		case REQUEST_PICK_ACCOUNT:
			if (RESULT_OK == resultCode) {
				String accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
				AccountUtils.setAccountName(this, accountName);
			} else {
				accountError();
			}
			return;
		case REQUEST_AUTHORIZATION:
			if (RESULT_OK != resultCode) {
				accountError();
			}
			return;
		}
	}

	private void googlePlayServicesError() {
		new AlertDialog.Builder(this).setMessage(
				getResources().getString(R.string.please_install_playservices)).show();
		finish();
	}

	private void accountError() {
		new AlertDialog.Builder(this).setMessage(
				getResources().getString(R.string.please_set_account)).show();
		finish();
	}
	
	private class AuthAsyncTask extends AsyncTask<Activity, Integer, Void> {
		@Override
		protected Void doInBackground(Activity... params) {
			Activity activity = params[0];
			GoogleAccountCredential credential = GDriveUtils.getCredential(activity);
			try {
				credential.getToken();
			} catch (UserRecoverableAuthException e) {
				activity.startActivityForResult(e.getIntent(), REQUEST_AUTHORIZATION);
			} catch (IOException e) {
				e.printStackTrace();
			} catch (GoogleAuthException e) {
				e.printStackTrace();
			}
			return null;
		}
	}

	private class PlaylistViewAdapter extends ArrayAdapter<PlayListItem> {

		public PlaylistViewAdapter(Context context) {
			super(context, 0);
		}

		@Override
		public int getCount() {
			return DBAdapter.getInstance(getContext()).getCount();
		}

		@Override
		public PlayListItem getItem(int position) {
			return DBAdapter.getInstance(getContext()).getItemByPosition(position);
		}

		@Override
		public long getItemId(int position) {
			return position;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view;
			if (convertView == null) {
				view = LayoutInflater.from(getContext()).inflate(
						R.layout.playlist_item, parent, false);
			} else {
				view = convertView;
			}
			
			PlayListItem item = getItem(position);
			((TextView) view.findViewById(R.id.txtTitle)).setText(item.getTitle());
			((TextView) view.findViewById(R.id.txtArtist)).setText(item.getArtist());
			
		    if (getListView().getCheckedItemPosition() == position) {
		        view.setBackgroundColor(getContext().getResources().getColor(R.color.springgreen));
		    } else {
		        view.setBackgroundColor(getContext().getResources().getColor(R.color.white));
		    }
			return view;
		}
	}
}
