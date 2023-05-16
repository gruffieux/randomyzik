package com.gbrfix.randomyzik;

import org.xmlpull.v1.XmlPullParserException;
import java.io.IOException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public class AmpRepository {
    public final static int MAX_ELEMENTS_PER_REQUEST = 5000;
    private String server;
    private String auth;
    private static AmpRepository instance = null;

    private static String byteToHex(byte byteData[]) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < byteData.length; i++) {
            sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    public static AmpRepository getInstance() {
        if (instance == null) {
            instance = new AmpRepository();
        }
        return instance;
    }

    public void init(String server, String apiKey) {
        this.server = server;
        this.auth = apiKey;
    }

    public String handshake() throws IOException, XmlPullParserException {
        URL url = new URL(server+"/server/xml.server.php?action=handshake&auth="+auth);
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        AmpXmlParser parser = new AmpXmlParser();
        String authToken = parser.parseText(conn.getInputStream(), "auth");
        conn.disconnect();
        return authToken;
    }

    public String handshake(String user, String pwd) throws IOException, XmlPullParserException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(pwd.getBytes());
        byte[] md1 = md.digest();
        String key = byteToHex(md1);
        long time = Calendar.getInstance().getTimeInMillis() / 1000; // Convert in second to be PHP compatible
        String str = time + key;
        md.update(str.getBytes());
        byte[] md2 = md.digest();
        String pass = byteToHex(md2);
        URL url = new URL(server+"/server/xml.server.php?action=handshake&auth="+pass+"&timestamp="+time+"&user="+user);
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        AmpXmlParser parser = new AmpXmlParser();
        String authToken = parser.parseText(conn.getInputStream(), "auth");
        conn.disconnect();
        return authToken;
    }

    public List advanced_search(String auth, int offset, int catalogId) throws IOException, XmlPullParserException {
        URL url = new URL(server+"/server/xml.server.php?action=advanced_search&auth="+auth+"&operator=and&type=song&offset="+offset+"&limit="+MAX_ELEMENTS_PER_REQUEST+"&rule_1=catalog&rule_1_operator=0&rule_1_input="+catalogId);
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        AmpXmlParser parser = new AmpXmlParser();
        List list = parser.parseSongs(conn.getInputStream());
        conn.disconnect();
        return list;
    }

    public List songs(String auth, int offset) throws IOException, XmlPullParserException {
        URL url = new URL(server+"/server/xml.server.php?action=songs&auth="+auth+"&offset="+offset+"&limit="+MAX_ELEMENTS_PER_REQUEST);
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        AmpXmlParser parser = new AmpXmlParser();
        List list = parser.parseSongs(conn.getInputStream());
        conn.disconnect();
        return list;
    }

    public Map catalogs(String auth) throws IOException, XmlPullParserException {
        URL url = new URL(server+"/server/xml.server.php?action=catalogs&auth="+auth);
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        AmpXmlParser parser = new AmpXmlParser();
        Map<String, Integer> map = parser.parseCatalogs(conn.getInputStream());
        conn.disconnect();
        return map;
    }

    public String localplay_add(String auth, int oid) throws IOException {
        URL url = new URL(server+"/server/xml.server.php?action=localplay&auth="+auth+"&command=add&type=song&oid="+oid+"&clear=1");
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        String res = conn.getResponseMessage();
        conn.disconnect();
        return res;
    }

    public String localplay_pause(String auth) throws IOException {
        URL url = new URL(server+"/server/xml.server.php?action=localplay&auth="+auth+"&command=pause");
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        String res = conn.getResponseMessage();
        conn.disconnect();
        return res;
    }

    public String localplay_play(String auth) throws IOException {
        URL url = new URL(server+"/server/xml.server.php?action=localplay&auth="+auth+"&command=play");
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        String res = conn.getResponseMessage();
        conn.disconnect();
        return res;
    }

    public String localplay_stop(String auth) throws IOException {
        URL url = new URL(server+"/server/xml.server.php?action=localplay&auth="+auth+"&command=stop");
        HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
        String res = conn.getResponseMessage();
        conn.disconnect();
        return res;
    }

    public String streaming_url(String auth, int oid, int offset) {
        return server+"/server/xml.server.php?action=stream&auth="+auth+"&id="+oid+"&type=song&offset="+offset;
    }
}
