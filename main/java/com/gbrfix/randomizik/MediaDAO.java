package com.gbrfix.randomizik;

import android.content.ContentValues;
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
        return (SQLiteCursor)this.db.rawQuery("SELECT `id` AS `_id`, `path`, `flag` FROM `medias` ORDER BY `flag` DESC;", null);
    }

   /* public void add(String path, String flag) {
        this.db.execSQL("INSERT INTO `medias` (`path`, `flag`) VALUES (\"" + path + "\", \"" + flag + "\");");
    }*/

    public long insert(String path, String flag) {
        ContentValues values = new ContentValues();
        values.put("path", path);
        values.put("flag", flag);

        return this.db.insert("medias", null, values);
    }

    public void remove(int id) {
        this.db.execSQL("DELETE FROM `medias` WHERE `id`=" + String.valueOf(id));
    }

    public SQLiteCursor getFromId(int id) {
        return (SQLiteCursor)this.db.rawQuery("SELECT * FROM `medias` WHERE `id`='"+String.valueOf(id)+"'", null);
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
