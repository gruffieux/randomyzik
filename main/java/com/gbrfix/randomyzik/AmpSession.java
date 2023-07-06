package com.gbrfix.randomyzik;

import android.content.SharedPreferences;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class AmpSession extends AmpRepository {
    private String server;
    private String auth;
    private static AmpSession instance = null;
    private boolean connected;

    public static AmpSession getInstance() {
        if (instance == null) {
            instance = new AmpSession();
        }
        return instance;
    }

    public void connect(SharedPreferences prefs) {
        boolean api = prefs.getBoolean("amp_api", false);
        server = prefs.getString("amp_server", "");
        connected = false;

        try {
            if (api) {
                String apiKey = prefs.getString("amp_api_key", "");
                auth = handshake(server, apiKey);
            } else {
                String user = prefs.getString("amp_user", "");
                String pwd = prefs.getString("amp_pwd", "");
                auth = handshake(server, user, pwd);
            }
            if (auth.isEmpty()) {
                throw new Exception("Invalid authentication");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        connected = true;
    }

    public boolean isConnected() {
        return connected;
    }

    public List advanced_search(int offset, int catalogId) throws IOException, XmlPullParserException {
        return advanced_search(server, auth, offset, catalogId);
    }

    public List songs(int offset) throws IOException, XmlPullParserException {
        return songs(server, auth, offset);
    }

    public Map catalogs() throws IOException, XmlPullParserException {
        return catalogs(server, auth);
    }

    public String localplay_add(int oid) throws IOException {
        return localplay_add(server, auth, oid);
    }

    public String localplay_pause() throws IOException {
        return localplay_pause(server, auth);
    }

    public String localplay_play() throws IOException {
        return localplay_play(server, auth);
    }

    public String localplay_stop() throws IOException {
        return localplay_stop(server, auth);
    }

    public String streaming_url(int oid, int offset) {
        return streaming_url(server, auth, oid, offset);
    }
}
