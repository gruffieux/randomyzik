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
    public ActivityScenarioRule<TestActivity> rule = new ActivityScenarioRule<>(TestActivity.class);

    private void deleteDatabases(String prefix) {
        String[] dbNames = context.databaseList();
        for (String dbName : dbNames) {
            if (dbName.startsWith(prefix)) {
                context.deleteDatabase(dbName);
            }
        }

    }

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putBoolean("test", true);
        editor.commit();
    }

    @Test
    public void createAmpacheCatalogs() {
        editor.putBoolean("amp", true);
        editor.putString("amp_server", AmpSessionTest.TEST_SERVER);
        editor.putBoolean("amp_api", true);
        editor.putString("amp_api_key", AmpSessionTest.TEST_API_KEY);
        editor.putString("amp_catalog", "0");
        editor.commit();
        deleteDatabases("test-amp-");
        ActivityScenario<TestActivity> scenario = ActivityScenario.launchActivityForResult(TestActivity.class);
        scenario.onActivity(new ActivityScenario.ActivityAction<TestActivity>() {
            @Override
            public void perform(TestActivity activity) {
                activity.createAmpacheCatalogs();
            }
        });
        int res = scenario.getResult().getResultCode();
    }

    @Test
    public void createList() {
        editor.putBoolean("amp", false);
        editor.commit();
        deleteDatabases("test-"+DAOBase.DEFAULT_NAME);
        ActivityScenario<TestActivity> scenario = ActivityScenario.launchActivityForResult(TestActivity.class);
        scenario.onActivity(new ActivityScenario.ActivityAction<TestActivity>() {
            @Override
            public void perform(TestActivity activity) {
                activity.createList();
            }
        });
        int res = scenario.getResult().getResultCode();
    }
}
