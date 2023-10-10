package com.gbrfix.randomyzik;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Created by gab on 10.10.2023.
 */

@RunWith(AndroidJUnit4.class)
public class AmpSessionTest {
    private Context context;
    private SharedPreferences prefs;

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean("test", true);
        editor.putBoolean("amp", true);
        editor.putString("server", "http://raspberrypi/ampache");
        editor.putString("amp_api_key", "7e5b37f14c08b28bdff73abe8f990c0b");
        editor.commit();
    }

    private void handshakeServer(String server) {
        String apiKey = prefs.getString("amp_api_key", "");
        try {
            AmpRepository.handshake(server, apiKey);
            Assert.fail();
        }
        catch (Exception e) {
            assertTrue(true);
        }
    }

    @Test
    public void handhsakeServerEmpty() {
        handshakeServer("");
    }

    @Test
    public void handhsakeServerNull() {
        handshakeServer(null);
    }

    @Test
    public void handhsakeServerWrong() {
        handshakeServer("http://raspberrypi/abc");
    }

    private void handshakeApiKey(String apiKey) {
        String server = prefs.getString("amp_server", "");
        try {
            Bundle data = AmpRepository.handshake(server, apiKey);
            Assert.assertEquals(null, data.getString("auth"));
        }
        catch (Exception e) {
            fail();
        }
    }

    @Test
    public void handshakeApiKeyEmpty() {
        handshakeApiKey("");
    }

    @Test
    public void handshakeApiKeyNull() {
        handshakeApiKey(null);
    }

    @Test
    public void handshakeApiKeyWrong() {
        handshakeApiKey("abc");
    }

    private void handshakeLogin(String user, String pwd) {
        String server = prefs.getString("amp_server", "");
        try {
            Bundle data = AmpRepository.handshake(server, user, pwd);
            Assert.assertEquals(null, data.getString("auth"));
        }
        catch (Exception e) {
            fail();
        }
    }

    @Test
    public void handshakeLoginBadUser() {
        handshakeLogin("aaa", "1234");
    }

    @Test
    public void handshakeLoginBadPwd() {
        handshakeLogin("admin", "1234");
    }

    @Test
    public void pingInvalidToken() {
        String server = prefs.getString("server", "");
        try {
            Bundle data = AmpRepository.ping(server, "abc");
            Assert.assertEquals(null, data.getString("auth"));
        }
        catch (Exception e) {
            fail();
        }
    }
    
    @After
    public void destroy() {
    }
}
