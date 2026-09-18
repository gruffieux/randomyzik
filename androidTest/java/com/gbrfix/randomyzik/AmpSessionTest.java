package com.gbrfix.randomyzik;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.PreferenceManager;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

/**
 * Created by gab on 10.10.2023.
 */

@RunWith(AndroidJUnit4.class)
public class AmpSessionTest {
    private Context context;
    public final static String TEST_SERVER = "http://raspberrypi/ampache";
    public final static String TEST_API_KEY = "6dc4d96e8470da10910d86747dd214f8";

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        prefs.edit().putString("amp_api_key", AmpSessionTest.TEST_API_KEY)
                .putString("amp_server", TEST_SERVER)
                .commit();
    }

    @Test
    public void handshakeServerEmpty() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String apiKey = prefs.getString("amp_api_key", "");
        try {
            AmpRepository.handshake("", apiKey);
            fail();
        }
        catch (Exception e) {
            assertTrue(true);
        }
    }

    @Test
    public void handhsakeServerNull() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
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
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String apiKey = prefs.getString("amp_api_key", "");
        try {
            AmpRepository.handshake("http://raspberrypi/abc", apiKey);
            fail();
        }
        catch (Exception e) {
            assertTrue(true);
        }
    }

    @Test
    public void handshakeApiKeyEmpty() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String server = prefs.getString("amp_server", "");
        try {
            Bundle data = AmpRepository.handshake(server, "");
            assertNull(data.getString("auth"));
        }
        catch (Exception e) {
            fail();
        }
    }

    @Test
    public void handshakeApiKeyNull() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String server = prefs.getString("amp_server", "");
        try {
            Bundle data = AmpRepository.handshake(server, null);
            assertNull(data.getString("auth"));
        }
        catch (Exception e) {
            fail();
        }
    }

    @Test
    public void handshakeApiKeyWrong() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String server = prefs.getString("amp_server", "abc");
        try {
            Bundle data = AmpRepository.handshake(server, "");
            assertNull(data.getString("auth"));
        }
        catch (Exception e) {
            fail();
        }
    }

    @Test
    public void handshakeLoginBadUser() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String server = prefs.getString("amp_server", "");
        try {
            Bundle data = AmpRepository.handshake(server, "aaa", "1234");
            assertNull(data.getString("auth"));
        }
        catch (Exception e) {
            fail();
        }
    }

    @Test
    public void handshakeLoginBadPwd() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String server = prefs.getString("amp_server", "");
        try {
            Bundle data = AmpRepository.handshake(server, "admin", "1234");
            assertNull(data.getString("auth"));
        }
        catch (Exception e) {
            fail();
        }
    }

    @Test
    public void pingInvalidToken() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String server = prefs.getString("amp_server", "");
        try {
            Bundle data = AmpRepository.ping(server, "5bd8fda8a98db49473feb085d59d3a7e");
            assertNull(data.getString("auth"));
        }
        catch (Exception e) {
            fail();
        }
    }

    @Test
    public void searchNoCatalog() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String apiKey = prefs.getString("amp_api_key", "");
        String server = prefs.getString("amp_server", "");
        try {
            Bundle data = AmpRepository.handshake(server, apiKey);
            String auth = data.getString("auth");
            List<Media> list = AmpRepository.advanced_search(server, auth, 0, DbService.TEST_MAX_TRACKS, 0);
            AmpRepository.goodbye(server, auth);
            assertTrue(list.isEmpty());
        }
        catch (Exception e) {
            fail();
        }
    }
}
