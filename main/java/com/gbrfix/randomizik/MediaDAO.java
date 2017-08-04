package com.gbrfix.randomizik;

import android.content.Context;
import android.database.sqlite.SQLiteCursor;

/**
 * Created by gab on 16.07.2017.
 */
public class MediaDAO extends DAOBase {
    public MediaDAO(Context context) {
        super(context);
    }

    public SQLiteCursor getAll() {
        return (SQLiteCursor)this.db.rawQuery("SELECT * FROM `medias`;", null);
    }

    public void add(String path, String flag) {
        this.db.execSQL("INSERT INTO `medias` (`path`, `flag`) VALUES (\"" + path + "\", \"" + flag + "\");");
    }

    public void remove(int id) {
        this.db.execSQL("DELETE FROM `medias` WHERE `id`=" + String.valueOf(id));
    }

    public SQLiteCursor getFromPath(String path) {
        return (SQLiteCursor)this.db.rawQuery("SELECT * FROM `medias` WHERE `path`=?;", new String[] {path});
    }

    public SQLiteCursor getFromFlag(String flag) {
        return (SQLiteCursor)this.db.rawQuery("SELECT * FROM `medias` WHERE `flag`=?;", new String[] {flag});
    }

    public void update(int id, String flag) {
        this.db.execSQL("UPDATE `medias` SET `flag`=\"" + flag + "\" WHERE `id`=" + String.valueOf(id));
    }
}
