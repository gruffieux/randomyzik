package com.gbrfix.randomyzik;

import android.content.Context;
import android.database.sqlite.SQLiteCursor;
import android.provider.MediaStore;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by gab on 21.09.2017.
 */

@RunWith(AndroidJUnit4.class)
public class MediaDAOTest {
    public static String TEST_DBNAME = "test-" + DAOBase.DEFAULT_NAME;
    private int mediaTotalExcepted;
    private MediaDAO dao;

    @Before
    public void setUp() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mediaTotalExcepted = c.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, new String[] {MediaStore.Audio.Media._ID},"is_music=1", null, null).getCount();
        if (mediaTotalExcepted > DbService.TEST_MAX_TRACKS) {
            mediaTotalExcepted = DbService.TEST_MAX_TRACKS;
        }
        dao = new MediaDAO(c, TEST_DBNAME);
        dao.open();
    }

    @Test
    public void selectAlbumEmpty() {
        try {
            SQLiteCursor cursor = dao.getFromAlbum("", "unread");
            assertTrue(true);
        }
        catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void selectAlbumNull() {
        try {
            SQLiteCursor cursor = dao.getFromAlbum(null, "");
            assertTrue(true);
        }
        catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void selectAlbumAllEmpty() {
        try {
            SQLiteCursor cursor = dao.getFromAlbum("", "");
            assertTrue(true);
        }
        catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void selectAlbumAllNull() {
        try {
            SQLiteCursor cursor = dao.getFromAlbum(null, "unread");
            assertTrue(true);
        }
        catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void selectAllMediaId() {
        try {
            SQLiteCursor cursor = dao.getAll();
            int count = 0;
            while (cursor.moveToNext()) {
                if (cursor.getInt(cursor.getColumnIndex("media_id")) != 0) {
                    count++;
                }
            }
            assertEquals(mediaTotalExcepted, count);
        }
        catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void selectAllAlbumKey() {
        try {
            SQLiteCursor cursor = dao.getAll();
            int count = 0;
            while (cursor.moveToNext()) {
                if (!cursor.getString(cursor.getColumnIndex("album_key")).isEmpty()) {
                    count++;
                }
            }
            assertEquals(mediaTotalExcepted, count);
        }
        catch (Exception e) {
            assertTrue(false);
        }
    }

    @After
    public void destroy() {
        dao.close();
    }
}