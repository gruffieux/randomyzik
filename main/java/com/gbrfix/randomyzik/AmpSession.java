package com.gbrfix.randomyzik;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.PreferenceManager;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class AmpSession extends AmpRepository {
    private String auth;
    private String expire;
    private Context context;
    private SharedPreferences prefs;
    private static AmpSession instance = null;

    private AmpSession(Context context) {
        this.context = context;
        prefs = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public static AmpSession getInstance(Context context) {
        if (instance == null) {
            instance = new AmpSession(context);
        }
        return instance;
    }

    public boolean hasExpired() throws ParseException {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
        Date date = new Date();
        String dateTime = format.format(date);
        Date now = format.parse(dateTime);
        Date expired = format.parse(expire);

        return now.compareTo(expired) >= 0;
    }

    public boolean hasValidAuth() throws ParseException {
        if (auth == null) {
            return false;
        }
        return !auth.isEmpty() && !hasExpired();
    }

    public void checkAction(String reqState, Media media) throws Exception {
        String server = prefs.getString("amp_server", "");
        Bundle status = localplay_status(server, auth);
        
        String state = status.getString("state");
        String title = status.getString("title");
        String artist = status.getString("artist");
        String album = status.getString("album");

        if (reqState != null && !reqState.isEmpty()) {
            if (!state.equals(reqState)) {
                throw new Exception(context.getString(R.string.err_amp_excepted_state, state, reqState));
            }
        }

        if (media != null) {
            if (!title.equals(media.getTitle()) || !artist.equals(media.getArtist()) || !album.equals(media.getAlbum())) {
                throw new Exception(context.getString(R.string.err_amp_track_unexcepted));
            }
        }
    }

    public void connect() throws Exception {
        String server = prefs.getString("amp_server", "");
        boolean api = prefs.getBoolean("amp_api", false);
        Bundle data;

        if (hasValidAuth()) {
            data = ping(server, auth);
        } else {
            if (api) {
                String apiKey = prefs.getString("amp_api_key", "");
                data = handshake(server, apiKey);
            } else {
                String user = prefs.getString("amp_user", "");
                String pwd = prefs.getString("amp_pwd", "");
                data = handshake(server, user, pwd);
            }
        }

        auth = data.getString("auth");
        expire = data.getString("session_expire");

        if (!hasValidAuth()) {
            throw new Exception(context.getString(R.string.err_amp_invalid_auth));
        }
    }

    public void unconnect() throws Exception {
        goodbye(prefs.getString("amp_server", ""), auth);
        auth = null;
    }

    public List advanced_search(int offset, int limit, int catalogId) throws IOException, XmlPullParserException {
        return advanced_search(prefs.getString("amp_server", ""), auth, offset, limit, catalogId);
    }

    public Map catalogs() throws IOException, XmlPullParserException {
        return catalogs(prefs.getString("amp_server", ""), auth);
    }

    public void localplay_add(int oid) throws IOException {
        localplay_add(prefs.getString("amp_server", ""), auth, oid);
    }

    public void localplay_pause() throws IOException {
        localplay_pause(prefs.getString("amp_server", ""), auth);
    }

    public void localplay_play() throws IOException {
        localplay_play(prefs.getString("amp_server", ""), auth);
    }

    public void localplay_stop() throws IOException {
        localplay_stop(prefs.getString("amp_server", ""), auth);
    }

    public String streaming_url(int oid, int offset) {
        return streaming_url(prefs.getString("amp_server", ""), auth, oid, offset);
    }

    public String dbName() throws MalformedURLException {
        String server = prefs.getString("amp_server", "");
        String catalog = prefs.getString("amp_catalog", "0");
        boolean test = prefs.getBoolean("test", false);
        String dbName = dbName(server, catalog);
        if (test) {
            return "test-" + dbName;
        }
        return dbName;
    }
}
