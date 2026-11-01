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
import static org.junit.Assert.fail;

/**
 * Created by gab on 21.09.2017.
 */

@RunWith(AndroidJUnit4.class)
public class MediaDAOTest {
    private Context context;
    public static String TEST_DBNAME = "test-" + DAOBase.DEFAULT_NAME;
    private int mediaTotalExcepted;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mediaTotalExcepted = context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, new String[] {MediaStore.Audio.Media._ID},"is_music=1", null, null).getCount();
        if (mediaTotalExcepted > DbService.TEST_MAX_TRACKS) {
            mediaTotalExcepted = DbService.TEST_MAX_TRACKS;
        }
    }

    @Test
    public void selectAlbumEmpty() {
        try (MediaDAO dao = new MediaDAO(context, TEST_DBNAME)) {
            dao.getFromAlbum("", "unread");
            assertTrue(true);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void selectAlbumNull() {
        try (MediaDAO dao = new MediaDAO(context, TEST_DBNAME)) {
            dao.getFromAlbum(null, "");
            assertTrue(true);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void selectAlbumAllEmpty() {
        try (MediaDAO dao = new MediaDAO(context, TEST_DBNAME)) {
            dao.getFromAlbum("", "");
            assertTrue(true);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void selectAlbumAllNull() {
        try (MediaDAO dao = new MediaDAO(context, TEST_DBNAME)) {
            dao.getFromAlbum(null, "unread");
            assertTrue(true);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void selectAllMediaId() {
        try (MediaDAO dao = new MediaDAO(context, TEST_DBNAME)) {
            SQLiteCursor cursor = dao.getAll();
            int count = 0;
            while (cursor.moveToNext()) {
                if (cursor.getInt(cursor.getColumnIndex("media_id")) != 0) {
                    count++;
                }
            }
            assertEquals(mediaTotalExcepted, count);
        } catch (Exception e) {
            fail();
        }
    }

    @Test
    public void selectAllAlbumKey() {
        try (MediaDAO dao = new MediaDAO(context, TEST_DBNAME)) {
            SQLiteCursor cursor = dao.getAll();
            int count = 0;
            while (cursor.moveToNext()) {
                if (!cursor.getString(cursor.getColumnIndex("album_key")).isEmpty()) {
                    count++;
                }
            }
            assertEquals(mediaTotalExcepted, count);
        } catch (Exception e) {
            fail();
        }
    }

    @After
    public void destroy() {
    }
}