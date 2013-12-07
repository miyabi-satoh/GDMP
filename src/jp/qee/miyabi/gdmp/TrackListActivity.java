package jp.qee.miyabi.gdmp;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Stack;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.Drive.Files;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.TextView;

public class TrackListActivity extends ListActivity {
	public static final String MIME_FOLDER = "application/vnd.google-apps.folder";
	public static final String MIME_MPEG = "audio/mpeg";
	private String mCurrentId;
	private Stack<String> mFolderStack;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_tracklist);

		mCurrentId = null;
		mFolderStack = new Stack<String>();

		getActionBar().setDisplayHomeAsUpEnabled(true);
		getActionBar().setTitle(getResources().getString(R.string.select_tracks));

		getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Log.d("TEST", "TrackList::onItemClick");

				ListView listView = (ListView) parent;
				File item = (File) listView.getItemAtPosition(position);
				if (item.getMimeType().equals(MIME_FOLDER)) {
					loadContents(item.getId());
				} else {
					((TrackListViewAdapter) getListAdapter()).notifyDataSetChanged();
				}
			}
		});
		
        loadContents("root");
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			if (mFolderStack.size() > 0) {
				mCurrentId = null;
				loadContents(mFolderStack.pop());
			}
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.tracklist, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
    	TrackListViewAdapter adapter = (TrackListViewAdapter) getListAdapter();
 
    	switch (item.getItemId()) {
        case android.R.id.home:
            Intent i = new Intent(this, PlayListActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
            return true;
        case R.id.action_select_all:
        	for (int position = 0; position < getListView().getCount(); position++) {
	    		if (getListView().isItemChecked(position) == false) {
	    			getListView().setItemChecked(position, true);
	    		}
        	}
        	adapter.notifyDataSetChanged();
        	return true;
        case R.id.action_invert:
        	for (int position = 0; position < getListView().getCount(); position++) {
	    		if (getListView().isItemChecked(position) == false) {
        			getListView().setItemChecked(position, true);
        		} else {
        			getListView().setItemChecked(position, false);
        		}
        	}
        	adapter.notifyDataSetChanged();
        	return true;
        default:
            return super.onOptionsItemSelected(item);
		}
	}

	private void loadContents(String id) {
		if (mCurrentId != null) {
			mFolderStack.push(mCurrentId);
		}
        mCurrentId = id;
        String query = "'" + mCurrentId + "' in parents and trashed=false";
        (new LoadContentsTask(this)).execute(query);
	}
	
	private class LoadContentsTask extends AsyncTask<String, Integer, ArrayList<File>> {
		private ProgressDialog mProgressDialog;
		private Context mContext;
		
		public LoadContentsTask(Context ctx) {
			mContext = ctx;
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();

			mProgressDialog = new ProgressDialog(mContext);
			mProgressDialog.setMessage(getResources().getString(R.string.loading));
			mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
			mProgressDialog.setCancelable(false);
			mProgressDialog.show();
		}

		@Override
		protected ArrayList<File> doInBackground(String... params) {
			ArrayList<File> retList = new ArrayList<File>();
			Files.List request = null;
			Drive service = GDriveUtils.getDriveService(mContext);
			try {
				request = service.files().list().setQ(params[0]);
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}
			
			do {
				try {
					FileList files = request.execute();
					
					retList.addAll(files.getItems());
					
					request.setPageToken(files.getNextPageToken());
				} catch (IOException e) {
					e.printStackTrace();
					if (request != null) {
						request.setPageToken(null);
					}
					return null;
				}
			} while (request.getPageToken() != null && request.getPageToken().length() > 0);
			return retList;
		}

		@Override
		protected void onPostExecute(ArrayList<File> result) {
			super.onPostExecute(result);

			mProgressDialog.dismiss();
			if (null == result) {
				return;
			}

			// フォルダと音楽ファイル以外は除去する
			for (int i = result.size() - 1; i >= 0; i--) {
				String mimeType = result.get(i).getMimeType();
				if (mimeType.equals(MIME_FOLDER) == false && mimeType.equals(MIME_MPEG) == false) {
					Log.d("TEST", mimeType);
					result.remove(i);
				}
			}
			// ソート
			Collections.sort(result, new Comparator<File>() {
				@Override
				public int compare(File lhs, File rhs) {
					if (lhs.getMimeType().equals(rhs.getMimeType())) {
						return lhs.getTitle().compareToIgnoreCase(rhs.getTitle());
					}
					return lhs.getMimeType().equals(MIME_FOLDER) ? -1 : 1;
				}
			});
			setListAdapter(new TrackListViewAdapter(TrackListActivity.this, result));
			for (int i = 0; i < result.size(); i++) {
				File item = result.get(i);
				// チェックボックスの初期状態をセットする
				if (DBAdapter.getInstance(TrackListActivity.this).getItemById(item.getId()) != null) {
					getListView().setItemChecked(i, true);
				} else {
					getListView().setItemChecked(i, false);
				}
			}
		}
	}

	private class TrackListViewAdapter extends ArrayAdapter<File> {

		public TrackListViewAdapter(Context context, List<File> objects) {
			super(context, 0, objects);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			View view;
			if (convertView == null) {
				view = LayoutInflater.from(getContext()).inflate(
						R.layout.tracklist_item, parent, false);
			} else {
				view = convertView;
			}
			
			final File item = getItem(position);
			((TextView) view.findViewById(R.id.txtTitle)).setText(item.getTitle());
			
			ListView listView = (ListView) parent;
			CheckBox checkBox = (CheckBox) view.findViewById(R.id.checkBox);
			if (item.getMimeType().equals(TrackListActivity.MIME_FOLDER)) {
				checkBox.setVisibility(View.INVISIBLE);
			} else {
				checkBox.setChecked(listView.isItemChecked(position));
				checkBox.setVisibility(View.VISIBLE);
				checkBox.setFocusable(false);
				checkBox.setFocusableInTouchMode(false);
				checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton buttonView,	boolean isChecked) {
						if (isChecked) {
							DBAdapter.getInstance(getContext()).insertItem(item);
						} else {
							DBAdapter.getInstance(getContext()).removeItem(item);
						}
					}
				});
			}
			
			return view;
		}
	}
}
