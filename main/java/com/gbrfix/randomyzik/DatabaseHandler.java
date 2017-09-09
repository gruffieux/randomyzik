package com.gbrfix.randomyzik;

import android.content.ContentValues;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.content.Context;
import android.util.Log;

/**
 * Created by gab on 16.07.2017.
 */
public class DatabaseHandler extends SQLiteOpenHelper {
    public static final String MEDIAS_TABLE_CREATE = "CREATE TABLE `medias` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "`path` TEXT, `flag` TEXT, " +
            "`track_nb` TEXT, `title` TEXT, `album` TEXT, `artist` TEXT);";
    public static final String MEDIA_TABLE_DROP = "DROP TABLE `medias`;";

    public DatabaseHandler(Context context, String name, CursorFactory factory, int version) {
        super(context, name, factory, version);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(MEDIAS_TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion == 1 && newVersion == 2) {
            db.execSQL(MEDIA_TABLE_DROP);
            db.execSQL(MEDIAS_TABLE_CREATE);
        }
        if (oldVersion == 2 && newVersion >= 3) {
            try {
                db.execSQL("ALTER TABLE `medias` ADD `track_nb` TEXT;");
            }
            catch (SQLiteException e) {
                Log.v("SQLiteException", e.getMessage());
            }
        }
        if (oldVersion <= 3 && newVersion >= 4) {
            try {
                db.execSQL("ALTER TABLE `medias` ADD `title` TEXT;");
                db.execSQL("ALTER TABLE `medias` ADD `album` TEXT;");
                db.execSQL("ALTER TABLE `medias` ADD `artist` TEXT;");

                // On met à jour avec les meta tags
                MediaFactory factory = new MediaFactory();
                SQLiteCursor cursor = (SQLiteCursor) db.rawQuery("SELECT * FROM `medias`", null);
                while (cursor.moveToNext()) {
                    int id = cursor.getInt(0);
                    String path = cursor.getString(1);
                    Media media = factory.createMedia(path);
                    ContentValues values = new ContentValues();
                    values.put("track_nb", media.getTrackNb());
                    values.put("title", media.getTitle());
                    values.put("album", media.getAlbum());
                    values.put("artist", media.getArtist());
                    db.update("medias", values, "`id`=?", new String[] {String.valueOf(id)});
                }
            }
            catch (Exception e) {
                Log.v("Exception", e.getMessage());
            }
        }
        if (oldVersion <= 4 && newVersion >= 5) {
            try {
                // On corrige les titres
                MediaFactory factory = new MediaFactory();
                SQLiteCursor cursor = (SQLiteCursor) db.rawQuery("SELECT `id`, `path` FROM `medias`", null);
                while (cursor.moveToNext()) {
                    int id = cursor.getInt(0);
                    String path = cursor.getString(1);
                    Media media = factory.createMedia(path);
                    ContentValues values = new ContentValues();
                    values.put("title", media.getTitle());
                    db.update("medias", values, "`id`=?", new String[] {String.valueOf(id)});
                }
            }
            catch (Exception e) {
                Log.v("Exception", e.getMessage());
            }
        }
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int newVersion, int oldVersion) {
    }
}
