package com.gbrfix.randomyzik;

import android.content.Context;
import android.database.sqlite.SQLiteCursor;
import android.provider.MediaStore;
import android.util.Log;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

import androidx.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

/**
 * Created by gab on 01.10.2017.
 */

@RunWith(AndroidJUnit4.class)
public class PlaylistDbTest {
    final static int TEST_CREATE_LIST = 1;

    int currentTest;
    int mediaTotalExcepted;
    DbService dbService;

    @Before
    public void setUp() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mediaTotalExcepted = c.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, new String[] {MediaStore.Audio.Media._ID},"is_music=1", null, null).getCount();
        dbService = new DbService(c);
    }

    @Test
    public void createList() {
        currentTest = TEST_CREATE_LIST;

        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();

        MediaDAO dao = new MediaDAO(c, MediaDAOTest.TEST_DBNAME);

        dao.open();
        dao.getDb().delete("medias", null, null);
        SQLiteCursor cursor = dao.getAll();
        assertEquals(0, cursor.getCount());
        dao.close();

        dbService.setDbSignalListener(new DbSignal() {
            @Override
            public void onScanStart() {

            }

            @Override
            public void onScanCompleted(int catalogId, boolean update, boolean all) {
                SQLiteCursor cursor;
                switch (currentTest) {
                    case TEST_CREATE_LIST:
                        dao.open();
                        cursor = dao.getAll();
                        assertEquals(mediaTotalExcepted, cursor.getCount());
                        dao.close();
                        currentTest = 0;
                        break;
                }

            }

            @Override
            public void onError(String msg) {
                switch (currentTest) {
                    case TEST_CREATE_LIST:
                }
                currentTest = 0;
            }
        });

        dbService.scan(false, "0");

        Log.v("createList", "end");
    }
}
