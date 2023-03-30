package com.gbrfix.randomyzik;

import android.content.Context;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import org.xmlpull.v1.XmlPullParserException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import javax.net.ssl.HttpsURLConnection;

public class AmpRepository {
    private AmpXmlParser parser;
    private MediaProvider provider;
    private Context context;

    public AmpRepository(AmpXmlParser parser, Context context) {
        this.parser = parser;
        this.provider = new MediaProvider(context);
        this.context = context;
    }

    public String handshake() throws IOException, XmlPullParserException {
        URL url = new URL("https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=handshake&auth=a47b0979a17207e084f8b25f9b0a4440");
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        String authToken = parser.parseText(conn.getInputStream(), "auth");
        conn.disconnect();
        return authToken;
    }

    public ArrayList advanced_search(String auth) throws IOException, XmlPullParserException {
        URL url = new URL("https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=advanced_search&auth="+auth+"&operator=or&type=song&offset=0&limit=100&random=0&rule_1=Catalog&rule_1_operator=4&rule_1_input=gab");
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        ArrayList list = (ArrayList<Media>)parser.parseSongs(conn.getInputStream());
        conn.disconnect();
        return list;
    }

    public String localplay_add(String auth, int oid) throws IOException {
        URL url = new URL("https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=localplay&auth="+auth+"&command=add&type=song&oid="+oid+"&clear=1");
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        String res = conn.getResponseMessage();
        conn.disconnect();
        return res;
    }

    public String localplay_pause(String auth) throws IOException {
        URL url = new URL("https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=localplay&auth="+auth+"&command=pause");
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        String res = conn.getResponseMessage();
        conn.disconnect();
        return res;
    }

    public String localplay_play(String auth) throws IOException {
        URL url = new URL("https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=localplay&auth="+auth+"&command=play");
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        String res = conn.getResponseMessage();
        conn.disconnect();
        return res;
    }

    public String localplay_stop(String auth) throws IOException {
        URL url = new URL("https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=localplay&auth="+auth+"&command=stop");
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        String res = conn.getResponseMessage();
        conn.disconnect();
        return res;
    }

    public void localplay_addAndPlay() {
        try {
            Media media = provider.selectTrack();
            WorkRequest handshakeWork = new OneTimeWorkRequest.Builder(AmpWorker.class)
                    .setInputData(
                            new Data.Builder()
                                    .putString("URL_SPEC", "https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=handshake&auth=a47b0979a17207e084f8b25f9b0a4440")
                                    .putString("PARSE_AUTH", "auth")
                                    .build()
                    ).build();
            WorkRequest stopWork = new OneTimeWorkRequest.Builder(AmpWorker.class)
                    .setInputData(
                            new Data.Builder()
                                    .putString("URL_SPEC", "https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=localplay&command=stop")
                                    .build()
                    ).build();
            WorkRequest addWork = new OneTimeWorkRequest.Builder(AmpWorker.class)
                    .setInputData(
                            new Data.Builder()
                                    .putString("URL_SPEC", "https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=localplay&command=add&type=song&oid="+media.getMediaId()+"&clear=1")
                                    .build()
                    ).build();
            WorkRequest playWork = new OneTimeWorkRequest.Builder(AmpWorker.class)
                    .setInputData(
                            new Data.Builder()
                                    .putString("URL_SPEC", "https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=localplay&command=play")
                                    .putInt("duration", media.getDuration())
                                    .build()
                    ).build();
            WorkManager.getInstance(context)
                    .beginWith((OneTimeWorkRequest) handshakeWork)
                    .then((OneTimeWorkRequest) stopWork)
                    .then((OneTimeWorkRequest) addWork)
                    .then((OneTimeWorkRequest) playWork)
                    .enqueue();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
