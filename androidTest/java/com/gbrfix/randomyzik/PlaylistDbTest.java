package com.gbrfix.randomyzik;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

/**
 * Created by gab on 01.10.2017.
 */

@RunWith(AndroidJUnit4.class)
public class PlaylistDbTest {
    Context context;
    SharedPreferences.Editor editor;
    
    @Rule
    public ActivityScenarioRule<PlaylistActivity> rule = new ActivityScenarioRule<>(PlaylistActivity.class);

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putBoolean("test", true);
        editor.commit();
        String[] dbNames = context.databaseList();
        for (String dbName : dbNames) {
            if (dbName.startsWith("test-")) {
                context.deleteDatabase(dbName);
            }
        }
    }

    @Test
    public void createAmpacheCatalogs() {
        editor.putBoolean("amp", true);
        editor.putString("amp_server", "http://raspberrypi/ampache");
        editor.putBoolean("amp_api", true);
        editor.putString("amp_api_key", "7e5b37f14c08b28bdff73abe8f990c0b");
        editor.putString("amp_catalog", "0");
        editor.commit();
        ActivityScenario<PlaylistActivity> scenario = ActivityScenario.launchActivityForResult(PlaylistActivity.class);
        scenario.onActivity(new ActivityScenario.ActivityAction<PlaylistActivity>() {
            @Override
            public void perform(PlaylistActivity activity) {
                activity.createAmpacheCatalogs();
            }
        });
        int res = scenario.getResult().getResultCode();
    }

    @Test
    public void createList() {
        editor.putBoolean("amp", false);
        editor.commit();
        ActivityScenario<PlaylistActivity> scenario = ActivityScenario.launchActivityForResult(PlaylistActivity.class);
        //scenario.moveToState(Lifecycle.State.CREATED);
        //scenario.moveToState(Lifecycle.State.STARTED);
        scenario.onActivity(new ActivityScenario.ActivityAction<PlaylistActivity>() {
            @Override
            public void perform(PlaylistActivity activity) {
                activity.createList();
            }
        });
        int res = scenario.getResult().getResultCode();
    }
}
