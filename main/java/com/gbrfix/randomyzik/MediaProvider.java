package com.gbrfix.randomyzik;

import android.content.Context;
import android.database.sqlite.SQLiteCursor;
import java.util.Random;

/**
 * Created by gab on 04.03.2018.
 */

public class MediaProvider {
    public final static int MODE_TRACK = 0;
    public final static int MODE_ALBUM = 1;
    private int currentId, selectId, position;
    private int total, totalRead;
    private int mode;
    private boolean lastOfAlbum;
    private Context context;

    private String dbName;

    public MediaProvider(Context context, String dbName) {
        currentId = selectId = position = 0;
        total = totalRead = 0;
        mode = MODE_TRACK;
        lastOfAlbum = false;
        this.context = context;
        this.dbName = dbName;
    }

    public int getPosition() { return position; }

    public void setPosition(int position) { this.position = position; }

    public int getSelectId() {
        return selectId;
    }

    public void setSelectId(int selectId) {
        this.selectId = selectId;
    }

    public int getCurrentId() {
        return currentId;
    }

    public int getTotal() {
        return total;
    }

    public int getTotalRead() {
        return totalRead;
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    public Media selectTrack() throws Exception {
        MediaDAO dao = new MediaDAO(context, dbName);
        dao.open();

        SQLiteCursor cursor = dao.getAll();
        total = cursor.getCount();

        SQLiteCursor cursorUnread = dao.getUnread();
        int totalUnread = cursorUnread.getCount();
        totalRead = total - totalUnread;

        SQLiteCursor cursorSel = null;
        if (selectId > 0) {
            cursorSel  = dao.getFromId(selectId);
            currentId = selectId;
            selectId = 0;
            lastOfAlbum = false;
        }

        if (mode == MODE_ALBUM) {
            if (currentId == 0 || lastOfAlbum == true) {
                dao.replaceFlag("skip", "unread");
                cursor = dao.getFromFlagAlbumGrouped("unread");
            }
            else {
                cursor = dao.getFromId(currentId);
            }
            int totalAlbum = cursor.getCount();
            if (totalAlbum == 0) {
                currentId = 0;
                throw new PlayEndException(context.getString(R.string.err_all_read));
            }
            if (totalAlbum > 1) {
                Random random = new Random();
                int pos = random.nextInt(totalAlbum);
                cursor.moveToPosition(pos);
            }
            else {
                cursor.moveToFirst();
            }
            String albumKey = cursor.getString(cursor.getColumnIndex("album_key"));
            cursor = dao.getFromAlbum(albumKey, "unread");
            lastOfAlbum = cursor.getCount() <= 1;
            if (cursorSel != null) {
                cursor = cursorSel;
            }
            cursor.moveToFirst();
        }
        else {
            if (cursorSel != null) {
                cursor = cursorSel;
                cursor.moveToFirst();
            }
            else {
                if (totalUnread == 0) {
                    currentId = 0;
                    throw new PlayEndException(context.getString(R.string.err_all_read));
                }
                cursor = cursorUnread;
                if (totalUnread > 1) {
                    Random random = new Random();
                    int pos = random.nextInt(totalUnread);
                    cursor.moveToPosition(pos);
                } else {
                    cursor.moveToFirst();
                }
            }
        }

        currentId = cursor.getInt(cursor.getColumnIndex("id"));

        Media media = new Media();
        media.setId(currentId);
        media.setAlbumKey(cursor.getString(cursor.getColumnIndex("album_key")));
        media.setTitle(cursor.getString(cursor.getColumnIndex("title")));
        media.setAlbum(cursor.getString(cursor.getColumnIndex("album")));
        media.setArtist(cursor.getString(cursor.getColumnIndex("artist")));
        media.setMediaId(cursor.getInt(cursor.getColumnIndex("media_id")));
        media.setDuration(cursor.getInt(cursor.getColumnIndex("duration")));

        dao.close();

        return media;
    }

    public static String getTrackLabel(String title, String album, String artist) {
        String label = title;

        if (album != null && !album.isEmpty()) {
            if (!label.isEmpty()) {
                label += " - ";
            }
            label += album;
        }

        if (artist != null && !artist.isEmpty()) {
            if (!label.isEmpty()) {
                label += " - ";
            }
            label += artist;
        }

        return label;
    }

    public String getSummary() {
        int current = totalRead + 1;
        float percent = (float)current / (float)total * 100;
        float f = percent * 10;
        int n = (int)f;
        percent = (float)n / 10;

        return String.format(context.getString(R.string.info_track_summary), current, total, percent);
    }

    public void updateState(String flag) {
        MediaDAO dao = new MediaDAO(context, dbName);
        dao.open();

        if (!dao.getDb().isReadOnly()) {
            dao.updateFlag(currentId, flag);
        }

        dao.close();
    }

    public boolean checkMediaId(int id) {
        if (id <= 0) {
            return false;
        }

        MediaDAO dao = new MediaDAO(context, dbName);
        dao.open();
        SQLiteCursor cursor = dao.getFromId(id);
        boolean res = cursor.moveToFirst();
        dao.close();

        return res;
    }
}
