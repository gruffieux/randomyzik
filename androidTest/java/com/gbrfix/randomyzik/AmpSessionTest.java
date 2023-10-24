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
    private SharedPreferences prefs;
    //public final static String TEST_SERVER = "http://raspberrypi/ampache";
    public final static String TEST_SERVER = "https://gbrfix.internet-box.ch/ampache";
    public final static String TEST_API_KEY = "7e5b37f14c08b28bdff73abe8f990c0b";

    @Before
    public void setUp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("amp_server", TEST_SERVER);
        editor.putString("amp_api_key", TEST_API_KEY);
        editor.commit();
    }

    @Test
    public void handshakeServerEmpty() {
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
            fail();
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
            assertNull(data.getString("auth"));
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
            assertNull(data.getString("auth"));
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
            assertNull(data.getString("auth"));
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
            assertNull(data.getString("auth"));
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
            assertNull(data.getString("auth"));
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
            assertNull(data.getString("auth"));
        }
        catch (Exception e) {
            fail();
        }
    }

    @Test
    public void searchNoCatalog() {
        String server = prefs.getString("server", "");
        String apiKey = prefs.getString("amp_api_key", "");
        try {
            Bundle data = AmpRepository.handshake(server, apiKey);
            String auth = data.getString("auth");
            List list = AmpRepository.advanced_search(server, auth, 0, DbService.TEST_MAX_TRACKS, 0);
            AmpRepository.goodbye(server, auth);
            assertTrue(list.isEmpty());
        }
        catch (Exception e) {
            fail();
        }
    }
    
    @After
    public void destroy() {
    }
}
