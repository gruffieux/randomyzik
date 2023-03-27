package com.gbrfix.randomyzik;

import android.os.Bundle;
import android.support.v4.media.session.MediaControllerCompat;

import org.xmlpull.v1.XmlPullParserException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import javax.net.ssl.HttpsURLConnection;

interface RepositoryCallback {
    void onProgress(int position, int total);
    void onComplete();
}

public class AmpRepository {
    private Executor executor;
    private AmpXmlParser parser;

    public AmpRepository(AmpXmlParser parser, Executor executor) {
        this.parser = parser;
        this.executor = executor;
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

    public void localplay_addAndPlay(final int mediaId, final int duration, final RepositoryCallback callback) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String authToken = handshake();
                    localplay_stop(authToken);
                    localplay_add(authToken, mediaId);
                    localplay_play(authToken);
                    int counter = 0;
                    while (counter < duration) {
                        Thread.sleep(1000);
                        counter += 1000;
                        callback.onProgress(counter, duration);
                    }
                    callback.onComplete();
                } catch (XmlPullParserException | IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void localplay_stopOrPause(final boolean stop) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String authToken = handshake();
                    if (stop) {
                        localplay_stop(authToken);
                    } else {
                        localplay_pause(authToken);
                    }
                } catch (XmlPullParserException | IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
