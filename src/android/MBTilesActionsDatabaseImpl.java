package com.makina.offline.mbtiles;

import org.apache.cordova.CordovaResourceApi;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.database.Cursor;
import android.database.CursorWindow;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Environment;
import android.util.Base64;
import android.util.Log;

/**
 * {@link IMBTilesActions} SQLite implementation.
 * 
 * @author <a href="mailto:sebastien.grimault@makina-corpus.com">S. Grimault</a>
 */
public class MBTilesActionsDatabaseImpl extends MBTilesActionsGenImpl
{
	private SQLiteDatabase db = null;
	
	// declaration of static variable
	protected static final int FIELD_TYPE_BLOB = 4;
    protected static final int FIELD_TYPE_FLOAT = 2;
    protected static final int FIELD_TYPE_INTEGER = 1;
    protected static final int FIELD_TYPE_NULL = 0;
    protected static final int FIELD_TYPE_STRING = 3;

    public MBTilesActionsDatabaseImpl(Context context, CordovaResourceApi resourceApi, String typePath, String url) {
		super(context);
		
		if (typePath != null && typePath.equals(MBTilesPlugin.OPEN_TYPE_PATH_CDV)) {
			if (url == null || url.length() < 0) {
		 		url = "cdvfile://localhost/persistent/tiles/";
			}
			
			Uri fileURL = resourceApi.remapUri(Uri.parse(url));
			mDirectory = fileURL.getPath() + "/";
			 
		} else if (typePath == null || typePath.equals(MBTilesPlugin.OPEN_TYPE_PATH_FULL)) {
			if (url == null || url.length() < 0) {
				if (FileUtils.checkExternalStorageState()) {
					url = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Android/data/" +
						mContext.getPackageName() + "/databases/";
				} else {
					url = null;
				}
			}
			
			mDirectory = url;
		}
	}

    @Override
	public void open(String name)
	{
    	if (getDirectory() != null) {
			String path = getDirectory() + name;
			try {
				this.db = SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.NO_LOCALIZED_COLLATORS | SQLiteDatabase.OPEN_READONLY);
				Log.d(getClass().getName(), "openDatabase : " + this.db.getPath());
			} catch (SQLiteCantOpenDatabaseException e) {
				close();
				Log.e(getClass().getName(), "can't open database :" + e.getMessage());
			}
    	} else {
    		close();
    	}
	}

	@Override
	public boolean isOpen()
	{
		return (this.db != null) && this.db.isOpen();
	}

	@Override
	public void close()
	{
		if (isOpen())
		{
			Log.d(getClass().getName(), "close '" + db.getPath() + "'");
			
			this.db.close();
			this.db = null;
		}
	}

	@Override
	public JSONObject getMetadata()
	{
		Cursor cursor = db.query("metadata",
				new String[]{"name", "value"},
				null, null, null, null, null);
		
		JSONObject metadata = new JSONObject();
		
		try
		{
			if (cursor.moveToFirst())
			{
				while (!cursor.isAfterLast())
				{
					try
					{
						metadata.put(cursor.getString(cursor.getColumnIndex("name")), cursor.getString(cursor.getColumnIndex("value")));
					}
					catch (JSONException je)
					{
						Log.e(getClass().getName(), je.getMessage(), je);
					}
					
					cursor.moveToNext();
				}
			}
		}
		finally
		{
		cursor.close();
		}

		return metadata;
	}

	@Override
	public JSONObject getMinZoom()
	{
		Cursor cursor = db.rawQuery("SELECT MIN(zoom_level) AS min_zoom FROM tiles", null);
		
		JSONObject minZoom = new JSONObject();
		
		try
		{
			// we should have only one result
			if (cursor.moveToFirst())
			{
				try
				{
					minZoom.put(KEY_MIN_ZOOM, cursor.getInt(cursor.getColumnIndex("min_zoom")));
				}
				catch (JSONException je)
				{
					Log.e(getClass().getName(), je.getMessage(), je);
				}
			}
		}
		finally
		{
		cursor.close();
		}
		
		return minZoom;
	}

	@Override
	public JSONObject getMaxZoom()
	{
		Cursor cursor = db.rawQuery("SELECT MAX(zoom_level) AS max_zoom FROM tiles", null);
		
		JSONObject maxZoom = new JSONObject();
		
		try
		{
			// we should have only one result
			if (cursor.moveToFirst())
			{
				try
				{
					maxZoom.put(KEY_MAX_ZOOM, cursor.getInt(cursor.getColumnIndex("max_zoom")));
				}
				catch (JSONException je)
				{
					Log.e(getClass().getName(), je.getMessage(), je);
				}
			}
		}
		finally
		{
		cursor.close();
		}
		
		return maxZoom;
	}

	@Override
	public JSONObject getTile(int zoomLevel, int column, int row)
	{
		Log.d(getClass().getName(), "getTile [" + zoomLevel + ", " + column + ", " + row + "]");
		
		int currentZoomLevel = zoomLevel;
		
		// try to load the last zoom level if zoomLevel is too high
		try
		{
			int maxZoom = getMaxZoom().getInt(KEY_MAX_ZOOM);

			if (zoomLevel > maxZoom)
			{
				currentZoomLevel = maxZoom;
			}
		}
		catch (JSONException je)
		{
			Log.e(getClass().getName(), je.getMessage(), je);
		}
		
		Cursor cursor = db.query("tiles",
				new String[]{"tile_data"},
				"zoom_level = ? AND tile_column = ? AND tile_row = ?",
				new String[]{String.valueOf(currentZoomLevel), String.valueOf(column), String.valueOf(row)},
				null, null, null);
		
		JSONObject tileData = new JSONObject();
		
		try
		{
			// we should have only one result
			if (cursor.moveToFirst())
			{
				try
				{
					tileData.put(KEY_TILE_DATA, Base64.encodeToString(cursor.getBlob(cursor.getColumnIndex("tile_data")), Base64.DEFAULT));
				}
				catch (JSONException je)
				{
					Log.e(getClass().getName(), je.getMessage(), je);
				}
			}
		}
		finally
		{
		cursor.close();
		}
		
		return tileData;
	}
	
	/**
	 * get type of data in column
	 * @param cursor, the selected cursor
	 * @param index, the column to treat
	 * @return the type of data 
	 */
	private int getType(Cursor cursor, int index) {
		int type = FIELD_TYPE_NULL;
		if (cursor != null) {
			int currentapiVersion = android.os.Build.VERSION.SDK_INT;
			if (currentapiVersion >= android.os.Build.VERSION_CODES.HONEYCOMB){
			    type = cursor.getType(index);
			} else {
				SQLiteCursor sqLiteCursor = (SQLiteCursor) cursor;
		        CursorWindow cursorWindow = sqLiteCursor.getWindow();
		        int pos = cursor.getPosition();
		        if (cursorWindow.isNull(pos, index)) {
		            type = FIELD_TYPE_NULL;
		        } else if (cursorWindow.isLong(pos, index)) {
		            type = FIELD_TYPE_INTEGER;
		        } else if (cursorWindow.isFloat(pos, index)) {
		            type = FIELD_TYPE_FLOAT;
		        } else if (cursorWindow.isString(pos, index)) {
		            type = FIELD_TYPE_STRING;
		        } else if (cursorWindow.isBlob(pos, index)) {
		            type = FIELD_TYPE_BLOB;
		        }
			}
		}
		return type;
	}
	
	@Override
	public JSONObject executeStatement(String query, String... params) {
		JSONObject result = new JSONObject();
		JSONArray rows = new JSONArray();
		if (query != null && query.length() > 0) {
			// run the query
			Cursor cursor = db.rawQuery(query, params);
			if (cursor != null) {
				try {
					// loop the row
					while (cursor.moveToNext()) {
						JSONObject row = new JSONObject();
						// loop the column
						for (String name : cursor.getColumnNames()) {
							if (name != null ) {
								int columnIndex = cursor.getColumnIndex(name);
								if (columnIndex >= 0) {
									// get type of data in column
									int type = getType(cursor, columnIndex);
									Object value ;
									// treat the data
									switch (type) {
									case FIELD_TYPE_BLOB:
										value = Base64.encodeToString(cursor.getBlob(columnIndex),Base64.DEFAULT);
										break;
									case FIELD_TYPE_FLOAT:
										value = cursor.getDouble(columnIndex);
										break;
									case FIELD_TYPE_INTEGER:
										value = cursor.getInt(columnIndex);
										break;
									case FIELD_TYPE_STRING:
										value = cursor.getString(columnIndex);
										break;
									case FIELD_TYPE_NULL:
									default:
										value = null;
										break;
									}
									// put in JSONObject
									try {
										row.put(name, value);
									} catch (JSONException e) {
										Log.w(getClass().getName(), e.getMessage());
									}
								}
							}
						}
						// put in JSONArray
						rows.put(row);
					}
				}
				finally
				{
					cursor.close();
				}
			}
		}
		// put all rows in JSONObject
		try {
			result.put(KEY_EXECUTE_STATEMENT, rows);
		} catch (JSONException e) {
			Log.w(getClass().getName(), e.getMessage());
		}
		return result;
	}

	/**
	 * return the directory of working
	 * @return <code>JSONObject</code>
	 */
	@Override
	public JSONObject getDirectoryWorking() {
		JSONObject directoryWorking = new JSONObject();
		try {
			directoryWorking.put(KEY_DIRECTORY_WORKING, getDirectory());
		} catch (JSONException e) {
			Log.w(getClass().getName(), e.getMessage());
		}
		return directoryWorking;
	}

}
