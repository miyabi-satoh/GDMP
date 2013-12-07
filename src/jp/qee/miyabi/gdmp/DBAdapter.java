package jp.qee.miyabi.gdmp;

import java.util.ArrayList;

import com.google.api.services.drive.model.File;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.media.MediaMetadataRetriever;
import android.util.Log;

public class DBAdapter {
	static final private String DB_NAME = "GDMP.db";
	static final private int DB_VERSION = 4;
//	static private PlaylistDBAdapter mSingleton = null;
	private DBHelper mDBHelper;
	private SQLiteDatabase mDB;
	
	private DBAdapter(Context ctx) {
		mDBHelper = new DBHelper(ctx);
		mDB = null;
	}

	public static DBAdapter getInstance(Context context) {
		/*
        if (mSingleton == null) {
            mSingleton = new PlaylistDBAdapter(context);
        }
        return mSingleton;
        */
		return new DBAdapter(context);
    }

	//
	// Adapter Methods
	//
	private DBAdapter open() {
		mDB = mDBHelper.getWritableDatabase();
		return this;
	}
	
	private void close() {
		mDBHelper.close();
	}
	
	private void execute(String sql) {
		mDB.beginTransaction();
		try {
			mDB.execSQL(sql);
			mDB.setTransactionSuccessful();
		} finally {
			mDB.endTransaction();
		}		
	}
	
	//
	// App Methods
	//
	public int getCount() {
		open();
		Cursor cs = mDB.rawQuery("SELECT COUNT(*) FROM playlist", null);
		cs.moveToFirst();
		int retVal = cs.getInt(0);
		close();
		
		return retVal;
	}

	public PlayListItem getItemById(String id) {
		open();
		Cursor cs = mDB.rawQuery("SELECT * FROM playlist WHERE id=?", new String[]{ id });
		PlayListItem retVal = makeItemFromCursor(cs);
		close();
		
		return retVal;
	}
	
	public PlayListItem getItemByPosition(int position) {
		open();
		Cursor cs = mDB.rawQuery("SELECT * FROM playlist WHERE position=?",
				new String[]{ Long.toString(position) });
		PlayListItem retVal = makeItemFromCursor(cs);
		close();
		
		return retVal;
	}
	
	public void insertItem(File item) {
		String sql;
		open();
		mDB.beginTransaction();
		try {
			sql = "SELECT COUNT(id) FROM playlist WHERE id=?";
			Cursor cs = mDB.rawQuery(sql, new String[]{ item.getId() });
			cs.moveToFirst();
			if (cs.getInt(0) == 0) {
				sql = "INSERT INTO playlist (id, position, file_title, file_size) SELECT"
						+ " " + DatabaseUtils.sqlEscapeString(item.getId()) + ","
						+ " CASE"
						+ "   WHEN MAX(position) IS NULL THEN 0"
						+ "   ELSE MAX(position) + 1"
						+ " END,"
						+ " " + DatabaseUtils.sqlEscapeString(item.getTitle()) + ","
						+ " " + Long.toString(item.getFileSize()) + ""
						+ " FROM playlist";
				mDB.execSQL(sql);
			} else {
				Log.d("TEST", item.getTitle() + "は登録済み。");
			}
			mDB.setTransactionSuccessful();
		} finally {
			mDB.endTransaction();
		}		
		close();
	}
	
	public void removeItem(File item) {
		String sql;
		open();
		mDB.beginTransaction();
		try {
			// 削除対象のpositionを取得する
			sql = "SELECT position FROM playlist WHERE id=?";
			Cursor cs = mDB.rawQuery(sql, new String[]{ item.getId() });
			if (cs.moveToFirst()) {
				int position = cs.getInt(0);

				// レコードを削除する
//				sql = "DELETE FROM playlist WHERE id='" + item.getId() + "'";
//				mDB.execSQL(sql);
				mDB.delete("playlist", "id=?", new String[]{ item.getId() });
				
				// positionを振り直す
				sql = "UPDATE playlist SET position = position - 1"
						+ " WHERE position >=" + position;
				mDB.execSQL(sql);
			}
			mDB.setTransactionSuccessful();
		} finally {
			mDB.endTransaction();
		}		
		close();
	}
	
	public void removeAllItems() {
		open();
		execute("DELETE FROM playlist");
		close();
	}

	public int getPositionAtRandom(boolean loop) {
		int retVal = 0;
		open();
		while (true) {
			String sql = "SELECT position FROM playlist"
					+ " WHERE played=?"
					+ " ORDER BY RANDOM()"
					+ " LIMIT 1";
			Cursor cs = mDB.rawQuery(sql, new String[]{ "0" });
			if (cs.moveToFirst()) {
				retVal = cs.getInt(0);
				execute("UPDATE playlist SET played=1 WHERE position=" + retVal);
				break;
			} else if (loop) {
				execute("UPDATE playlist SET played=0");
			} else {
				break;
			}
		}
		close();
		return retVal;
	}

	public void clearPlayedFlag() {
		open();
		execute("UPDATE playlist SET played=0");
		close();
	}
		
	public void setMetaData(String id, MediaMetadataRetriever mmr) {
		open();
		mDB.beginTransaction();
		try {
			ContentValues values = new ContentValues();
			values.put("title", mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
			values.put("artist", mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
			values.put("image", mmr.getEmbeddedPicture());
			mDB.update("playlist", values, "id=?", new String[]{ id });
			mDB.setTransactionSuccessful();
		} finally {
			mDB.endTransaction();
		}
		close();
	}
	
	public void setDriveCache(String parent_id, ArrayList<File> files) {
		open();
		mDB.beginTransaction();
		try {
			mDB.delete("drivecache", "parent_id=?", new String[]{ parent_id });
			for (File file : files) {
				ContentValues values = new ContentValues();
				values.put("id", file.getId());
				values.put("parent_id", parent_id);
				values.put("title", file.getTitle());
				values.put("mimetype", file.getMimeType());
				mDB.insert("drivecache", null, values);
			}
			mDB.setTransactionSuccessful();
		} finally {
			mDB.endTransaction();
		}
		close();
	}

	public ArrayList<File> getDriveCache(String parent_id) {
		ArrayList<File> retVal = new ArrayList<File>();
		open();
		String sql = "SELECT * FROM drivecache WHERE parent_id=?";
		Cursor cs = mDB.rawQuery(sql, new String[]{ parent_id });
		while (cs.moveToNext()) {
			File file = new File();
			file.setId(cs.getString(cs.getColumnIndex("id")));
			file.setTitle(cs.getString(cs.getColumnIndex("title")));
			file.setMimeType(cs.getString(cs.getColumnIndex("mimetype")));
			retVal.add(file);
		}
		close();
		return retVal;
	}

	private PlayListItem makeItemFromCursor(Cursor cs) {
		PlayListItem retVal = null;
		if (cs.moveToFirst()) {
			retVal = new PlayListItem(
					cs.getString(cs.getColumnIndex("id")),
					cs.getInt(cs.getColumnIndex("position")),
					cs.getString(cs.getColumnIndex("file_title")),
					cs.getInt(cs.getColumnIndex("file_size")));
			retVal.setTitle(cs.getString(cs.getColumnIndex("title")));
			retVal.setArtist(cs.getString(cs.getColumnIndex("artist")));
			retVal.setCoverImage(cs.getBlob(cs.getColumnIndex("image")));
		} else {
//			Log.d("TEST", "makeItemFromCursor:該当なし");
		}
		return retVal;
	}
	
	//
	// SQLiteOpenHelper
	//
	private static class DBHelper extends SQLiteOpenHelper {
		static final String CREATE_PLAYLIST_TABLE =
				"CREATE TABLE playlist ("
				+ "	id TEXT NOT NULL PRIMARY KEY,"
				+ " position INTEGER NOT NULL,"
				+ " file_title TEXT NOT NULL,"
				+ " file_size INTEGER NOT NULL,"
				+ " played INTEGER DEFAULT 0,"
				+ " title TEXT,"
				+ " artist TEXT,"
				+ " image BLOB"
				+ ")";
		static final String CREATE_DRIVECACHE_TABLE =
				"CREATE TABLE drivecache ("
				+ "	id TEXT NOT NULL PRIMARY KEY,"
				+ " parent_id TEXT NOT NULL,"
				+ " title TEXT NOT NULL,"
				+ " mimetype TEXT NOT NULL,"
				+ " updated_at TIMESTAMP DEFAULT (DATETIME('now','localtime')) "
				+ ")";
		static final String DROP_PLAYLIST_TABLE = "DROP TABLE IF EXISTS playlist";
		static final String DROP_DRIVECACHE_TABLE = "DROP TABLE IF EXISTS drivecache";

		public DBHelper(Context ctx) {
			super(ctx, DB_NAME, null, DB_VERSION);
		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			db.execSQL(CREATE_PLAYLIST_TABLE);
			db.execSQL(CREATE_DRIVECACHE_TABLE);
		}
	
		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			db.execSQL(DROP_PLAYLIST_TABLE);
			db.execSQL(DROP_DRIVECACHE_TABLE);
			onCreate(db);
		}
	}
}
