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

    private int currentId, selectId;
    private int mode;
    private boolean lastOfAlbum;
    private MediaDAO dao;
    private MediaSignal mediaSignalListener;
    private Context context;

    public MediaProvider(Context context) {
        currentId = selectId = 0;
        mode = MODE_TRACK;
        lastOfAlbum = false;
        dao = new MediaDAO(context);
        this.context = context;
    }

    public int getSelectId() {
        return selectId;
    }

    public void setMediaSignalListener(MediaSignal mediaSignalListener) {
        this.mediaSignalListener = mediaSignalListener;
    }

    public String selectTrack() throws Exception {
        dao.open();

        SQLiteCursor cursor = dao.getAll();
        int total = cursor.getCount();

        SQLiteCursor cursorUnread = dao.getUnread();
        int totalUnread = cursorUnread.getCount();

        SQLiteCursor cursorSel = null;
        if (selectId > 0) {
            cursorSel  = dao.getFromId(selectId);
            currentId = selectId;
            selectId = 0;
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
            String album = cursor.getString(5);
            String artist = cursor.getString(6);
            cursor = dao.getFromAlbum(album, artist, "unread");
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

        currentId = cursor.getInt(0);
        String path = cursor.getString(1);

        dao.close();

        return path;
    }
}
