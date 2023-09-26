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
    int mediaTotalExcepted;
    Context c;
    DbService dbService;
    
    @Rule
    public ActivityScenarioRule<MyActivity> rule = new ActivityScenarioRule<>(TestActivity.class);

    @Before
    public void setUp() throws Exception {
        c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        dbService = new DbService(c);
        String[] dbNames = c.databaseList();
        for (String dbName : dbNames) {
            if (dbName.startWith("test-")) {
                c.deleteDatabase(dbName);
            }
        }
    }

    @Test
    public void createAmpacheCatalog() {
        mediaTotalExcepted = 0;

        dbService.setDbSignalListener(new DbSignal() {
            @Override
            public void onScanStart() {

            }

            @Override
            public void onScanProgress(int catalogId, int total) {
                mediaTotalExcepted += total;
            }

            @Override
            public void onScanCompleted(int catalogId, boolean update, boolean all) {
                dao.open();
                cursor = dao.getAll();
                assertEquals(mediaTotalExcepted, cursor.getCount());
                dao.close();
            }

            @Override
            public void onError(String msg) {
            }
        });

        dbService.check();

        Log.v("createAmpacheCatalog", "end");
    }

    @Test
    public void createList() {
        mediaTotalExcepted = c.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, new String[] {MediaStore.Audio.Media._ID},"is_music=1", null, null).getCount();

        ActivityScenario<TestActivity> scenario = rule.getScenario();
        launchActivity<TestActivity>().use { scenario ->
            scenario.moveToState(State.CREATED); 
            scenario.moveToState(State.STARTED);
            scenario.onActivity { activity ->
                activity.createList();
            }
        }

        Log.v("createList", "end");
    }
}
