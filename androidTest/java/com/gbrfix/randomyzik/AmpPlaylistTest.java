package com.gbrfix.randomyzik;

import static org.junit.Assert.fail;

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
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;

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
            fail();
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
    public void localplayCanPlay() throws Exception {
        editor.putBoolean("amp_streaming", false);
        editor.commit();

        // Read db
        AmpSession ampSession = AmpSession.getInstance(context);
        String dbName = ampSession.dbName();
        MediaDAO dao = new MediaDAO(context, dbName);
        dao.open();
        dao.updateFlagAll("read");
        SQLiteCursor cursor = dao.getAll();
        int count = cursor.getCount();
        Random random = new Random();
        int pos = random.nextInt(count);
        cursor.moveToPosition(pos);
        int id = cursor.getInt(cursor.getColumnIndex("id"));
        dao.updateFlag(id, "unread");
        dao.close();

        // Connect server
        ampSession.connect();
        ampSession.localplay_stop();

        // Lauch activity
        ActivityScenario<TestActivity> scenario = ActivityScenario.launchActivityForResult(TestActivity.class);
        scenario.onActivity(new ActivityScenario.ActivityAction<TestActivity>() {
            @Override
            public void perform(TestActivity activity) {
                activity.localplayCanPlay(id);
            }
        });
        int res = scenario.getResult().getResultCode();

        // Unconnect server
        ampSession.localplay_stop();
        ampSession.unconnect();
    }

    @Test
    public void localplayCannotPlay() throws Exception {
        editor.putBoolean("amp_streaming", false);
        editor.commit();

        // Read db
        AmpSession ampSession = AmpSession.getInstance(context);
        String dbName = ampSession.dbName();
        MediaDAO dao = new MediaDAO(context, dbName);
        dao.open();
        dao.updateFlagAll("unread");
        SQLiteCursor cursor = dao.getAll();
        int count = cursor.getCount();
        Random random = new Random();
        int pos = random.nextInt(count);
        cursor.moveToPosition(pos);
        int oid = cursor.getInt(cursor.getColumnIndex("media_id"));
        dao.close();

        // Connect server
        ampSession.connect();
        ampSession.localplay_add(oid);
        ampSession.localplay_play();

        // Lauch activity
        ActivityScenario<TestActivity> scenario = ActivityScenario.launchActivityForResult(TestActivity.class);
        scenario.onActivity(new ActivityScenario.ActivityAction<TestActivity>() {
            @Override
            public void perform(TestActivity activity) {
                activity.localplayCannotPlay();
            }
        });
        int res = scenario.getResult().getResultCode();

        // Unconnect server
        ampSession.localplay_stop();
        ampSession.unconnect();
    }

    @Test
    public void localplayCanPause() throws Exception {
        editor.putBoolean("amp_streaming", false);
        editor.commit();

        // Read db
        AmpSession ampSession = AmpSession.getInstance(context);
        String dbName = ampSession.dbName();
        MediaDAO dao = new MediaDAO(context, dbName);
        dao.open();
        dao.updateFlagAll("read");
        SQLiteCursor cursor = dao.getAll();
        int count = cursor.getCount();
        Random random = new Random();
        int pos = random.nextInt(count);
        cursor.moveToPosition(pos);
        int id = cursor.getInt(cursor.getColumnIndex("id"));
        dao.updateFlag(id, "unread");
        dao.close();

        // Connect server
        ampSession.connect();
        ampSession.localplay_stop();

        // Lauch activity
        ActivityScenario<TestActivity> scenario = ActivityScenario.launchActivityForResult(TestActivity.class);
        scenario.onActivity(new ActivityScenario.ActivityAction<TestActivity>() {
            @Override
            public void perform(TestActivity activity) {
                activity.localplayCanPause(id);
            }
        });
        int res = scenario.getResult().getResultCode();

        // Unconnect server
        ampSession.localplay_stop();
        ampSession.unconnect();
    }

    @Test
    public void localplayCannotPause() throws Exception {
        editor.putBoolean("amp_streaming", false);
        editor.commit();

        // Read db
        AmpSession ampSession = AmpSession.getInstance(context);
        String dbName = ampSession.dbName();
        MediaDAO dao = new MediaDAO(context, dbName);
        dao.open();
        dao.updateFlagAll("read");
        SQLiteCursor cursor = dao.getAll();
        int count = cursor.getCount();
        Random random = new Random();
        int pos = random.nextInt(count);
        cursor.moveToPosition(pos);
        int id = cursor.getInt(cursor.getColumnIndex("id"));
        dao.updateFlag(id, "unread");
        dao.close();

        // Connect server
        ampSession.connect();
        ampSession.localplay_stop();

        // Lauch activity
        ActivityScenario<TestActivity> scenario = ActivityScenario.launchActivityForResult(TestActivity.class);
        scenario.onActivity(new ActivityScenario.ActivityAction<TestActivity>() {
            @Override
            public void perform(TestActivity activity) {
                activity.localplayCannotPause(id);
            }
        });
        int res = scenario.getResult().getResultCode();

        // Unconnect server
        ampSession.localplay_stop();
        ampSession.unconnect();
    }

    @Test
    public void localplayCanStop() throws Exception {
        editor.putBoolean("amp_streaming", false);
        editor.commit();

        // Read db
        AmpSession ampSession = AmpSession.getInstance(context);
        String dbName = ampSession.dbName();
        MediaDAO dao = new MediaDAO(context, dbName);
        dao.open();
        dao.updateFlagAll("read");
        SQLiteCursor cursor = dao.getAll();
        int count = cursor.getCount();
        Random random = new Random();
        int pos = random.nextInt(count);
        cursor.moveToPosition(pos);
        int id = cursor.getInt(cursor.getColumnIndex("id"));
        dao.updateFlag(id, "unread");
        dao.close();

        // Connect server
        ampSession.connect();
        ampSession.localplay_stop();

        // Lauch activity
        ActivityScenario<TestActivity> scenario = ActivityScenario.launchActivityForResult(TestActivity.class);
        scenario.onActivity(new ActivityScenario.ActivityAction<TestActivity>() {
            @Override
            public void perform(TestActivity activity) {
                activity.localplayCanStop(id);
            }
        });
        int res = scenario.getResult().getResultCode();

        // Unconnect server
        ampSession.localplay_stop();
        ampSession.unconnect();
    }

    @Test
    public void localplayCannotStop() throws Exception {
        editor.putBoolean("amp_streaming", false);
        editor.commit();

        // Read db
        AmpSession ampSession = AmpSession.getInstance(context);
        String dbName = ampSession.dbName();
        MediaDAO dao = new MediaDAO(context, dbName);
        dao.open();
        dao.updateFlagAll("read");
        SQLiteCursor cursor = dao.getAll();
        int count = cursor.getCount();
        Random random = new Random();
        int pos = random.nextInt(count);
        cursor.moveToPosition(pos);
        int id = cursor.getInt(cursor.getColumnIndex("id"));
        dao.updateFlag(id, "unread");
        dao.close();

        // Connect server
        ampSession.connect();
        ampSession.localplay_stop();

        // Lauch activity
        ActivityScenario<TestActivity> scenario = ActivityScenario.launchActivityForResult(TestActivity.class);
        scenario.onActivity(new ActivityScenario.ActivityAction<TestActivity>() {
            @Override
            public void perform(TestActivity activity) {
                activity.localplayCannotStop(id);
            }
        });
        int res = scenario.getResult().getResultCode();

        // Unconnect server
        ampSession.localplay_stop();
        ampSession.unconnect();
    }

    @After
    public void destroy() {
        Intent intent = new Intent(context, MediaPlaybackService.class);
        intent.setAction("close");
        context.startService(intent);
    }
}
