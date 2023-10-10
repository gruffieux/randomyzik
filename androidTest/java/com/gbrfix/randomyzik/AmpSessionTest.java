package com.gbrfix.randomyzik;

import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

/**
 * Created by gab on 10.10.2023.
 */

@RunWith(AndroidJUnit4.class)
public class AmpSessionTest {
    private Context context;
    private AmpSession session;
    private SharedPreferences.Editor editor;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
        editor.putBoolean("test", true);
        editor.putBoolean("amp", true);
        editor.putString("server", "http://raspberrypi/ampache");
        editor.putBoolean("amp_api", true);
        editor.putString("amp_api_key", "7e5b37f14c08b28bdff73abe8f990c0b");
        editor.commit();
        session = AmpSession.getInstance(context);
    }

    @Test
    public void connectInvalidServer() {
        editor.putString("server", "http://raspberrypi/abc");
        editor.commit();
        try {
            session.connect();
            fail();
        }
        catch (Exception e) {
            assertTrue(true);
        }
    }

    @Test
    public void connectInvalidApiKey() {
        editor.putBoolean("amp_api", true);
        editor.putString("amp_api_key", "abc");
        editor.commit();
        try {
            session.connect();
            fail();
        }
        catch (Exception e) {
            assertTrue(true);
        }
    }

    @Test
    public void connectInvalidLogin() {
        editor.putBoolean("amp_api", false);
        editor.putString("amp_user", "aaa");
        editor.putString("amp_pwd", "bbb");
        editor.commit();
        try {
            session.connect();
            fail();
        }
        catch (Exception e) {
            assertTrue(true);
        }
    }
    
    @After
    public void destroy() {
    }
}
