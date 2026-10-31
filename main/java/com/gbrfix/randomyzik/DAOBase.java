package com.gbrfix.randomyzik;

import android.database.sqlite.SQLiteDatabase;
import android.content.Context;

/**
 * Created by gab on 16.07.2017.
 */
public abstract class DAOBase implements AutoCloseable {
    final static String DEFAULT_NAME = "playlist.db";
    protected final static int VERSION = 11;
    protected SQLiteDatabase db;
    protected DatabaseHandler dbHandler;

    public DAOBase(Context context, String name) {
        dbHandler = new DatabaseHandler(context, name, null, VERSION);
        db = dbHandler.getWritableDatabase();
    }

    public SQLiteDatabase getDb() {
        return db;
    }
}
