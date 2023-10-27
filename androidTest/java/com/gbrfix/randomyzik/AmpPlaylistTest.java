package com.gbrfix.randomyzik;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteCursor;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Created by gab on 01.10.2017.
 */

@RunWith(AndroidJUnit4.class)
public class AmpPlaylistTest {
    private Context context;
    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;

    @Rule
    public ActivityScenarioRule<TestActivity> rule = new ActivityScenarioRule<>(TestActivity.class);

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        editor = prefs.edit();
        editor.putBoolean("test", true);
        editor.putBoolean("amp", true);
        editor.putBoolean("amp_api", true);
        editor.putString("amp_server", AmpSessionTest.TEST_SERVER);
        editor.putString("amp_api_key", AmpSessionTest.TEST_API_KEY);
        editor.commit();
    }

    @Test
    public void playStreaming() throws Exception {
        editor.putBoolean("amp_streaming", true);
        editor.commit();

        String dbName = AmpSession.getInstance(context).dbName();
        MediaDAO dao = new MediaDAO(context, dbName);
        dao.open();
        dao.updateFlagAll("read");
        SQLiteCursor cursor = dao.getAll();
        int count = cursor.getCount();
        final int total = DbService.TEST_MAX_TRACKS / 10;
        if (count >= total) {
            int pos = 0;
            while (pos < total) {
                cursor.moveToPosition(pos);
                int id = cursor.getInt(cursor.getColumnIndex("id"));
                dao.updateFlag(id, "unread");
                pos++;
            }
        }
        dao.close();

        if (count < total) {
            Assert.fail();
        }

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
    public void localPlayCleanPlay() throws Exception {
        editor.putBoolean("amp_streaming", false);
        editor.commit();

        String dbName = AmpSession.getInstance(context).dbName();
        MediaDAO dao = new MediaDAO(context, dbName);
        dao.open();
        dao.updateFlagAll("read");
        SQLiteCursor cursor = dao.getAll();
        int count = cursor.getCount();
        Random random = new Random();
        int pos = random.nextInt(count);
        cursor.moveToPosition(pos);
        int id = cursor.getInt(cursor.getColumnIndex("id"));
        int oid = cursor.getInt(cursor.getColumnIndex("media_id"));
        dao.updateFlag(id, "unread");
        dao.close();

        try {
            ampSession.connect();
            ampSession.localplay_stop();
            ampSession.localplay_add(oid);
            ampSession.localplay_play();
        } catch (Exception e) {
            fail(e.getMessage());
        }

        ActivityScenario<TestActivity> scenario = ActivityScenario.launchActivityForResult(TestActivity.class);
        scenario.onActivity(new ActivityScenario.ActivityAction<TestActivity>() {
            @Override
            public void perform(TestActivity activity) {
                activity.localPlayCleanPlay(id);
            }
        });
        int res = scenario.getResult().getResultCode();
    }
}
