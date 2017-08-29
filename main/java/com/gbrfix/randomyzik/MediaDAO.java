package com.gbrfix.randomyzik;

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
        String sql = "SELECT `id` AS `_id`, `path`, `flag`, `track_nb`, `title`, `album`, `artist` FROM `medias` ORDER BY `flag` DESC;";

        return (SQLiteCursor)this.db.rawQuery(sql, null);
    }

    public long insert(Media media) {
        ContentValues values = new ContentValues();

        values.put("path", media.getPath());
        values.put("flag", media.getFlag());
        values.put("track_nb", media.getTrackNb());
        values.put("title", media.getTitle());
        values.put("album", media.getAlbum());
        values.put("artist", media.getArtist());

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

    public SQLiteCursor getFromMeta(String trackNb, String title, String album, String artist) {
        return (SQLiteCursor)this.db.rawQuery("SELECT * FROM `medias` WHERE `track_nb`=? AND `title`=? AND `album`=? AND `artist`=?;", new String[] {trackNb, title, album, artist});
    }

    public void update(int id, String flag) {
        this.db.execSQL("UPDATE `medias` SET `flag`=\"" + flag + "\" WHERE `id`=" + String.valueOf(id));
    }
}
