package com.gbrfix.randomyzik;

import android.database.sqlite.SQLiteCursor;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by gab on 21.09.2017.
 */

@RunWith(AndroidJUnit4.class)
public class MediaDAOTest extends MediaDAO {
    public MediaDAOTest() {
        super(InstrumentationRegistry.getTargetContext());
    }

    @Before
    public void init() {
        open();
    }

    @Test
    public void selectAlbumArtistEmpty() {
        String album = "Scorching Beauty";
        String artist = "";

        try {
            getFromAlbum(album, artist, "unread");
            assertTrue(true);
        }
        catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void selectAlbumArtistNull() {
        String album = "abc";

        try {
            getFromAlbum(album, null, "");
            assertTrue(true);
        }
        catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void selectAlbumEmpty() {
        String album = "";
        String artist = "Alpha Blondy";

        try {
            SQLiteCursor cursor = getFromAlbum(album, artist, "unread");
            //assertEquals(10, cursor.getCount());
            assertTrue(true);
        }
        catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void selectAlbumNull() {
        String artist = "Alpha Blondy";

        try {
            SQLiteCursor cursor = getFromAlbum(null, artist, "");
            //assertEquals(17, cursor.getCount());
            assertTrue(true);
        }
        catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void selectAlbumAllEmpty() {
        try {
            SQLiteCursor cursor = getFromAlbum("", "", "");
            //assertEquals(25, cursor.getCount());
            assertTrue(true);
        }
        catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void selectAlbumAllNull() {
        try {
            SQLiteCursor cursor = getFromAlbum(null, null, "unread");
            //assertEquals(17, cursor.getCount());
            assertTrue(true);
        }
        catch (Exception e) {
            assertTrue(false);
        }
    }

    @After
    public void destroy() {
        close();
    }
}