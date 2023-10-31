package com.gbrfix.randomyzik;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteCursor;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
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

    @After
    public void destroy() {
        Intent intent = new Intent(context, MediaPlaybackService.class);
        intent.setAction("close");
        context.startService(intent);
    }
}
