package com.gbrfix.randomyzik;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.MediaStore;
import android.util.Log;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import androidx.lifecycle.Lifecycle;
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
    int mediaTotalExcepted;
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
    public void createAmpacheCatalog() {
        mediaTotalExcepted = 0;

        Log.v("createAmpacheCatalog", "end");
    }

    @Test
    public void createList() {
        editor.putBoolean("amp", false);
        editor.commit();

        mediaTotalExcepted = context.getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, new String[] {MediaStore.Audio.Media._ID},"is_music=1", null, null).getCount();

        Intent testIntent = new Intent(context, PlaylistActivity.class);
        ActivityScenario<PlaylistActivity> scenario = ActivityScenario::launchActivityForResult(testIntent);
        scenario.moveToState(Lifecycle.State.CREATED);
        scenario.moveToState(Lifecycle.State.STARTED);
        scenario.onActivity(new ActivityScenario.ActivityAction<PlaylistActivity>() {
            @Override
            public void perform(PlaylistActivity activity) {
                activity.createList(mediaTotalExcepted);
            }
        });

        Log.v("createList", "end");
    }
}
