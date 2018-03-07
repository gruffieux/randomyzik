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
public class MediaDAOTest {
    private  MediaDAO dao;

    public MediaDAOTest() {
        DAOBase.NAME = "playlist-test.db";
        dao = new MediaDAO(InstrumentationRegistry.getTargetContext());
    }

    @Before
    public void init() {
        dao.open();
    }

    @Test
    public void selectAlbumArtistEmpty() {
        String album = "Scorching Beauty";
        String artist = "";

        try {
            dao.getFromAlbum(album, artist, "unread");
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
            dao.getFromAlbum(album, null, "");
            assertTrue(true);
        }
        catch (Exception e) {
            assertTrue(false);
        }
    }

    @Test
    public void selectAlbumArtistVarious() {
        String album = "The Complete Stax-Volt Singles: 1959-1968 (Disc 1)";
        String artist = "Various";

        try {
            SQLiteCursor cursor = dao.getFromAlbum(album, artist, "");
            assertEquals(23, cursor.getCount());
            //assertTrue(true);
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
            SQLiteCursor cursor = dao.getFromAlbum(album, artist, "unread");
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
            SQLiteCursor cursor = dao.getFromAlbum(null, artist, "");
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
            SQLiteCursor cursor = dao.getFromAlbum("", "", "");
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
            SQLiteCursor cursor = dao.getFromAlbum(null, null, "unread");
            //assertEquals(17, cursor.getCount());
            assertTrue(true);
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