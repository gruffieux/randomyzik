package com.gbrfix.randomyzik;

import android.database.sqlite.SQLiteDatabase;
import android.content.Context;

/**
 * Created by gab on 16.07.2017.
 */
public abstract class DAOBase {
    final static String DEFAULT_NAME = "playlist.db";
    protected final static int VERSION = 9;
    protected SQLiteDatabase db = null;
    protected DatabaseHandler dbHandler = null;

    public DAOBase(Context context, String name) {
        dbHandler = new DatabaseHandler(context, name, null, VERSION);
    }

    public SQLiteDatabase open() {
        db = dbHandler.getWritableDatabase();

        return db;
    }

    public void close() {
        db.close();
    }

    public SQLiteDatabase getDb() {
        return db;
    }
}
