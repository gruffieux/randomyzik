package com.gbrfix.randomyzik;

import android.content.Context;

import org.xmlpull.v1.XmlPullParserException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;

import javax.net.ssl.HttpsURLConnection;

public class AmpRepository {
    private Context context;

    public AmpRepository(Context context) {
        this.context = context;
    }

    public String handshake() throws IOException, XmlPullParserException {
        URL url = new URL("https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=handshake&auth=a47b0979a17207e084f8b25f9b0a4440");
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        AmpXmlParser parser = new AmpXmlParser();
        String authToken = parser.parseText(conn.getInputStream(), "auth");
        conn.disconnect();
        return authToken;
    }

    public ArrayList advanced_search(String auth) throws IOException, XmlPullParserException {
        URL url = new URL("https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=advanced_search&auth="+auth+"&operator=or&type=song&offset=0&limit=100&random=0&rule_1=Catalog&rule_1_operator=4&rule_1_input=gab");
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        AmpXmlParser parser = new AmpXmlParser();
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
}
