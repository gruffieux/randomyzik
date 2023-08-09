package com.gbrfix.randomyzik;

import android.content.SharedPreferences;
import android.os.Bundle;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class AmpSession extends AmpRepository {
    private String server;
    private String auth;
    private String expire;
    private String user;
    private Bundle lastPlayActivity = null;
    private static AmpSession instance = null;

    public static AmpSession getInstance() {
        if (instance == null) {
            instance = new AmpSession();
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

    public boolean hasValidAuth() {
        if (auth == null) {
            return false;
        }
        return !auth.isEmpty();
    }

    public void connect(SharedPreferences prefs) throws Exception {
        boolean api = prefs.getBoolean("amp_api", false);
        server = prefs.getString("amp_server", "");
        Bundle data = null;

        if (hasValidAuth() && !hasExpired()) {
            data = ping(server, auth);
        } else {
            if (api) { // DEPRACATED
                String apiKey = prefs.getString("amp_api_key", "");
                data = handshake(server, apiKey);
            } else {
                user = prefs.getString("amp_user", "");
                String pwd = prefs.getString("amp_pwd", "");
                data = handshake(server, user, pwd);
            }
        }

        auth = data.getString("auth");
        expire = data.getString("session_expire");

        if (!hasValidAuth()) {
            throw new Exception("Invalid authentication");
        }
    }

    public boolean checkPlayActivity(int oid) throws XmlPullParserException, IOException {
        if (lastPlayActivity == null) {
            return false;
        }

        int date = lastPlayActivity.getInt("date");
        List<Bundle> activities = timeline(server, auth, user, 3, date);

        for (int i = 0; i < activities.size(); i++) {
            Bundle activity = activities.get(i);
            if (activity.getInt("oid") == oid && activity.getString("action").equals("play") && activity.getInt("date") == date) {
                return true;
            }
        }

        return false;
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

    public Bundle localplay_status() throws IOException, XmlPullParserException {
        return localplay_status(server, auth);
    }

    public String localplay_stop() throws IOException {
        return localplay_stop(server, auth);
    }

    public void setLastPlayActivity(Bundle activity) {
        lastPlayActivity = activity;
    }

    public Bundle lastPlayActivity(int oid) throws Exception {
        long now = Calendar.getInstance().getTimeInMillis() / 1000;

        List<Bundle> activities = timeline(server, auth, user, 1, now);

        if (activities.isEmpty()) {
            throw new Exception("No activity found");
        }

        Bundle activity = activities.get(0);

        if (activity.getInt("oid") != oid || !activity.getString("action").equals("play")) {
            throw new Exception("Activity mismatch");
        }

        return activity;
    }

    public String streaming_url(int oid, int offset) throws IOException {
        return streaming_url(server, auth, oid, offset);
    }
}
