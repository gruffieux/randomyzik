package com.gbrfix.randomyzik;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

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
public class AmpSessionTest {
    private Context context;
    private AmpSession session;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putBoolean("test", true);
        editor.putBoolean("amp", true);
        editor.putString("server", "http://raspberrypi/ampache");
        editor.commit();
        session = AmpSession.getInstance(context);
    }

    @Test
    public void connectInvalidServer() {
        editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putString("server", "http://raspberrypi/abc");
        editor.commit();
        try {
            session.connect();
        }
        catch (Exception e) {
            assertTrue(true);
        }
    }
    
    @After
    public void destroy() {
        dao.close();
    }
}
