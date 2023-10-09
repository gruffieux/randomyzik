package com.gbrfix.randomyzik;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteCursor;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Created by gab on 01.10.2017.
 */

@RunWith(AndroidJUnit4.class)
public class PlaylistTest {
    Context context;
    SharedPreferences.Editor editor;

    @Rule
    public ActivityScenarioRule<TestActivity> rule = new ActivityScenarioRule<>(TestActivity.class);
    
    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putBoolean("test", true);
        editor.putBoolean("amp", false);
        editor.commit();
    }

    @Test
    public void playAllTracks() throws Exception {
        MediaDAO dao = new MediaDAO(context, MediaDAOTest.TEST_DBNAME);
        dao.open();
        dao.updateFlagAll("unread");
        SQLiteCursor cursor = dao.getUnread();
        int total = cursor.getCount();
        dao.close();

        ActivityScenario<TestActivity> scenario = ActivityScenario.launchActivityForResult(TestActivity.class);
        scenario.onActivity(new ActivityScenario.ActivityAction<TestActivity>() {
            @Override
            public void perform(TestActivity activity) {
                activity.playAllTracks(total, MediaProvider.MODE_TRACK);
            }
        });
        int res = scenario.getResult().getResultCode();
    }

    @Test
    public void playAllAlbums() throws Exception {
        MediaDAO dao = new MediaDAO(context, MediaDAOTest.TEST_DBNAME);
        dao.open();
        dao.updateFlagAll("unread");
        SQLiteCursor cursor = dao.getFromFlagAlbumGrouped("unread");
        int total = cursor.getCount();
        dao.close();

        ActivityScenario<TestActivity> scenario = ActivityScenario.launchActivityForResult(TestActivity.class);
        scenario.onActivity(new ActivityScenario.ActivityAction<TestActivity>() {
            @Override
            public void perform(TestActivity activity) {
                activity.playAllTracks(total, MediaProvider.MODE_ALBUM);
            }
        });
        int res = scenario.getResult().getResultCode();
    }
/*
    @Test
    public void playLastTrack() throws Exception {
        activity.currentTest = TestActivity.TEST_PLAY_LAST_TRACK;

        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();

        MediaDAO dao = new MediaDAO(c, MediaDAOTest.TEST_DBNAME);
        dao.open();
        dao.updateFlagAll("read");
        SQLiteCursor cursor = dao.getAll();
        Random random = new Random();
        int total = cursor.getCount();

        if (total > 1) {
            int pos = random.nextInt(total);
            cursor.moveToPosition(pos);
        }
        else if (total > 0) {
            cursor.moveToFirst();
        }
        else {
            throw new Exception("List is empty");
        }

        dao.updateFlag(cursor.getInt(0), "unread");
        activity.trackTotal = 1;
        dao.close();

        activity.mediaBrowser.connect();

        while (activity.currentTest != 0) {
        }

        activity.mediaBrowser.disconnect();
    }
*/
    @Test
    public void playEndedList() throws Exception {
        ActivityScenario<TestActivity> scenario = ActivityScenario.launchActivityForResult(TestActivity.class);
        scenario.onActivity(new ActivityScenario.ActivityAction<TestActivity>() {
            @Override
            public void perform(TestActivity activity) {
                activity.playEndedList();
            }
        });
        int res = scenario.getResult().getResultCode();
    }
}
