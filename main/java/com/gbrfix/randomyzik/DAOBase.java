package com.gbrfix.randomyzik;

import android.database.sqlite.SQLiteDatabase;
import android.content.Context;

/**
 * Created by gab on 16.07.2017.
 */
public abstract class DAOBase {
    protected final static int VERSION = 6;
    public static String NAME = "playlist.db";
    protected SQLiteDatabase db = null;
    protected DatabaseHandler dbHandler = null;

    public DAOBase(Context context) {
        dbHandler = new DatabaseHandler(context, NAME, null, VERSION);
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
