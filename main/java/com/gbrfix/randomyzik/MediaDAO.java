package com.gbrfix.randomyzik;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteCursor;

import java.util.ArrayList;

/**
 * Created by gab on 16.07.2017.
 */
public class MediaDAO extends DAOBase {
    public MediaDAO(Context context) {
        super(context);
    }

    public SQLiteCursor getAll() {
        String sql = "SELECT `id`, `path`, `media_id` FROM `medias`;";

        return (SQLiteCursor)this.db.rawQuery(sql, null);
    }

    public SQLiteCursor getAllOrdered() {
        String sql = "SELECT `id` AS `_id`, `path`, `flag`, PRINTF(\"%2d\", `track_nb`) AS `track_nb`, `title`, `album`, `artist` FROM `medias` ORDER BY `album`, `artist`, `track_nb`;";

        return (SQLiteCursor)this.db.rawQuery(sql, null);
    }

    public long insert(Media media) {
        ContentValues values = new ContentValues();

        values.put("media_id", media.getMediaId());
        values.put("path", media.getPath());
        values.put("flag", media.getFlag());
        values.put("track_nb", media.getTrackNb());
        values.put("title", media.getTitle());
        values.put("album", media.getAlbum());
        values.put("artist", media.getArtist());

        return this.db.insert("medias", null, values);
    }

    public long update(Media media, int id) {
        ContentValues values = new ContentValues();

        values.put("media_id", media.getMediaId());
        values.put("path", media.getPath());
        values.put("track_nb", media.getTrackNb());
        values.put("title", media.getTitle());
        values.put("album", media.getAlbum());
        values.put("artist", media.getArtist());

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

    public SQLiteCursor getFromPath(String path) {
        return (SQLiteCursor)this.db.rawQuery("SELECT * FROM `medias` WHERE `path`=?;", new String[] {path});
    }

    public SQLiteCursor getFromFlag(String flag) {
        return (SQLiteCursor)this.db.rawQuery("SELECT * FROM `medias` WHERE `flag`=?;", new String[] {flag});
    }

    public SQLiteCursor getUnread() {
        return (SQLiteCursor)this.db.rawQuery("SELECT * FROM `medias` WHERE `flag` != 'read';", null);
    }

    public SQLiteCursor getFromFlagAlbumGrouped(String flag) {
        return (SQLiteCursor)this.db.rawQuery("SELECT * FROM `medias` WHERE `flag`=? GROUP BY `album` ORDER BY `album`, `artist`, `track_nb`;", new String[] {flag});
    }

    public SQLiteCursor getFromAlbum(String album, String artist, String flag) {
        ArrayList<String> args = new ArrayList<String>();
        String query = "SELECT `id`, `path`, `flag`, PRINTF(\"%2d\", `track_nb`) AS `track_nb`, `title`, `album`, `artist` FROM `medias` WHERE (";

        if (album != null && !album.isEmpty()) {
            query += " `album`=?";
            args.add(album);
        }
        else {
            query += " `album` IS NULL";
        }

        if (artist != null && !artist.isEmpty()) {
            query += " AND `artist`=?";
            args.add(artist);
        }
        else {
            query += " OR `artist` IS NULL";
        }

        query += ")";

        if (flag != null && !flag.isEmpty()) {
            query += " AND `flag`=?";
            args.add(flag);
        }

        query += " ORDER BY `album`, `artist`, `track_nb`;";
        String[] arr = null;

        switch (args.size()) {
            case 1:
                arr = new String[] {args.get(0)};
                break;
            case 2:
                arr = new String[] {args.get(0), args.get(1)};
                break;
            case 3:
                arr = new String[] {args.get(0), args.get(1), args.get(2)};
                break;
        }

        return (SQLiteCursor) this.db.rawQuery(query, arr);
    }

    public void updateFlag(int id, String flag) {
        ContentValues values = new ContentValues();
        values.put("flag", flag);
        this.db.update("medias", values,  "`id`=?", new String[] {String.valueOf(id)});
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
