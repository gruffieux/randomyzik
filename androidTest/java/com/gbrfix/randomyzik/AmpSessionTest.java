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
        editor.putString("server", "http://raspberrypi/ampache");
        editor.putString("amp_api_key", "7e5b37f14c08b28bdff73abe8f990c0b");
        editor.commit();
    }

    @Test
    public void handshakeServerEmpty() {
        String apiKey = prefs.getString("amp_api_key", "");
        try {
            AmpRepository.handshake("", apiKey);
            Assert.fail();
        }
        catch (Exception e) {
            assertTrue(true);
        }
    }

    @Test
    public void handhsakeServerNull() {
        String apiKey = prefs.getString("amp_api_key", "");
        try {
            AmpRepository.handshake(null, apiKey);
            Assert.fail();
        }
        catch (Exception e) {
            assertTrue(true);
        }
    }

    @Test
    public void handhsakeServerWrong() {
        String apiKey = prefs.getString("amp_api_key", "");
        try {
            AmpRepository.handshake("http://raspberrypi/abc", apiKey);
            Assert.fail();
        }
        catch (Exception e) {
            assertTrue(true);
        }
    }

    @Test
    public void handshakeApiKeyEmpty() {
        String server = prefs.getString("amp_server", "");
        try {
            Bundle data = AmpRepository.handshake(server, "");
            Assert.assertNull(data.getString("auth"));
        }
        catch (Exception e) {
            fail();
        }
    }

    @Test
    public void handshakeApiKeyNull() {
        String server = prefs.getString("amp_server", "");
        try {
            Bundle data = AmpRepository.handshake(server, null);
            Assert.assertNull(data.getString("auth"));
        }
        catch (Exception e) {
            fail();
        }
    }

    @Test
    public void handshakeApiKeyWrong() {
        String server = prefs.getString("amp_server", "abc");
        try {
            Bundle data = AmpRepository.handshake(server, "");
            Assert.assertNull(data.getString("auth"));
        }
        catch (Exception e) {
            fail();
        }
    }

    @Test
    public void handshakeLoginBadUser() {
        String server = prefs.getString("amp_server", "");
        try {
            Bundle data = AmpRepository.handshake(server, "aaa", "1234");
            Assert.assertNull(data.getString("auth"));
        }
        catch (Exception e) {
            fail();
        }
    }

    @Test
    public void handshakeLoginBadPwd() {
        String server = prefs.getString("amp_server", "");
        try {
            Bundle data = AmpRepository.handshake(server, "admin", "1234");
            Assert.assertNull(data.getString("auth"));
        }
        catch (Exception e) {
            fail();
        }
    }

    @Test
    public void pingInvalidToken() {
        String server = prefs.getString("server", "");
        try {
            Bundle data = AmpRepository.ping(server, "5bd8fda8a98db49473feb085d59d3a7e");
            Assert.assertNull(data.getString("auth"));
        }
        catch (Exception e) {
            fail();
        }
    }

    @Test
    public void pingExpiredToken() {
        String server = prefs.getString("server", "");
        try {
            Bundle data = AmpRepository.ping(server, "5bd8fda8a98db49473feb085d59d3a75");
            Assert.assertNotNull(data.getString("auth"));
        }
        catch (Exception e) {
            fail();
        }
    }
    
    @After
    public void destroy() {
    }
}
