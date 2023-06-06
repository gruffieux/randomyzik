package com.gbrfix.randomyzik;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteCursor;

import java.util.ArrayList;

/**
 * Created by gab on 16.07.2017.
 */
public class MediaDAO extends DAOBase {
    public MediaDAO(Context context, String name) {
        super(context, name);
    }

    public SQLiteCursor getAll() {
        String sql = "SELECT `id`, `media_id`, `album_key` FROM `medias`;";

        return (SQLiteCursor)this.db.rawQuery(sql, null);
    }

    public SQLiteCursor getAllOrdered() {
        String sql = "SELECT `id` AS `_id`, `flag`, LTRIM(SUBSTR(`track_nb`, -3, 3), 0) AS `track_nb`, PRINTF(\"%03d\", `track_nb`) AS `track_sort`, `title`, `album`, `artist` FROM `medias` ORDER BY `artist`, `album_key`, `track_sort`;";

        return (SQLiteCursor)this.db.rawQuery(sql, null);
    }

    public long insert(Media media) {
        ContentValues values = new ContentValues();


        values.put("media_id", media.getMediaId());
        values.put("album_key", media.getAlbumKey());
        values.put("flag", media.getFlag());
        values.put("track_nb", media.getTrackNb());
        values.put("title", media.getTitle());
        values.put("album", media.getAlbum());
        values.put("artist", media.getArtist());
        values.put("duration", media.getDuration());

        return this.db.insert("medias", null, values);
    }

    public long update(Media media, int id) {
        ContentValues values = new ContentValues();

        values.put("media_id", media.getMediaId());
        values.put("album_key", media.getAlbumKey());
        values.put("track_nb", media.getTrackNb());
        values.put("title", media.getTitle());
        values.put("album", media.getAlbum());
        values.put("artist", media.getArtist());
        values.put("duration", media.getDuration());

        return this.db.update("medias", values, "`id`=?", new String[] {String.valueOf(id)});
    }

    public void remove(int id) {
        this.db.delete("medias", "`id`=?", new String[] {String.valueOf(id)});
    }

    public SQLiteCursor getFromId(int id) {
        return (SQLiteCursor)this.db.rawQuery("SELECT * FROM `medias` WHERE `id`=?;", new String[] {String.valueOf(id)});
    }

    public SQLiteCursor getFromMediaId(int media_id) {
        return (SQLiteCursor)this.db.rawQuery("SELECT * FROM `medias` WHERE `media_id`=?;", new String[] {String.valueOf(media_id)});
    }

    public SQLiteCursor getFromFlag(String flag) {
        return (SQLiteCursor)this.db.rawQuery("SELECT * FROM `medias` WHERE `flag`=?;", new String[] {flag});
    }

    public SQLiteCursor getUnread() {
        return (SQLiteCursor)this.db.rawQuery("SELECT * FROM `medias` WHERE `flag` != 'read';", null);
    }

    public SQLiteCursor getFromFlagAlbumGrouped(String flag) {
        return (SQLiteCursor)this.db.rawQuery("SELECT * FROM `medias` WHERE `flag`=? GROUP BY `album` ORDER BY `album_key`, `track_nb`, `artist`;", new String[] {flag});
    }

    public SQLiteCursor getFromAlbum(String album_key, String flag) {
        ArrayList<String> args = new ArrayList<String>();
        String query = "SELECT `id`, `flag`, PRINTF(\"%03d\", `track_nb`) AS `track_nb`, `title`, `album`, `artist`, `media_id`, `album_key`, `duration` FROM `medias` WHERE";

        if (album_key != null && !album_key.isEmpty()) {
            query += " `album_key`=?";
            args.add(album_key);
        }
        else {
            query += " `album_key` IS NULL";
        }

        if (flag != null && !flag.isEmpty()) {
            query += " AND `flag`=?";
            args.add(flag);
        }

        query += " ORDER BY `album_key`, `track_nb`, `artist`;";
        String[] arr = null;

        switch (args.size()) {
            case 1:
                arr = new String[] {args.get(0)};
                break;
            case 2:
                arr = new String[] {args.get(0), args.get(1)};
                break;
        }

        return (SQLiteCursor) this.db.rawQuery(query, arr);
    }

    public void updateFlag(int id, String flag) {
        ContentValues values = new ContentValues();
        values.put("flag", flag);
        this.db.update("medias", values,  "`id`=?", new String[] {String.valueOf(id)});
    }

    public void updateFlagAlbum(String albumKey, String flag) {
        ContentValues values = new ContentValues();
        values.put("flag", flag);
        this.db.update("medias", values,  "`album_key`=?", new String[] {albumKey});
    }

    public void updateFlagAll(String flag) {
        ContentValues values = new ContentValues();
        values.put("flag", flag);
        this.db.update("medias", values, null, null);
    }

    public void replaceFlag(String oldFlag, String newFlag) {
        ContentValues values = new ContentValues();
        values.put("flag", newFlag);
        this.db.update("medias", values,  "`flag`=?", new String[] {oldFlag});
    }
}
