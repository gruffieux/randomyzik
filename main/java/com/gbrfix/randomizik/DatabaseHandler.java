package com.gbrfix.randomizik;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.content.Context;

/**
 * Created by gab on 16.07.2017.
 */
public class DatabaseHandler extends SQLiteOpenHelper {
    public static final String MEDIAS_TABLE_CREATE = "CREATE TABLE `medias` (" +
            "`id` INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "`path` TEXT, `flag` TEXT);";
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
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int newVersion, int oldVersion) {
    }
}
