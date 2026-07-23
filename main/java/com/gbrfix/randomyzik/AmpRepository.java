package com.gbrfix.randomyzik;

import android.os.Bundle;

import org.xmlpull.v1.XmlPullParserException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.List;
import java.util.Map;


abstract class AmpRepository {
    public final static int MAX_ELEMENTS_PER_REQUEST = 5000;

    private static String byteToHex(byte byteData[]) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < byteData.length; i++) {
            sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    public static Bundle handshake(String server, String apiKey) throws IOException, XmlPullParserException {
        URL url = new URL(server+"/server/xml.server.php?action=handshake&auth="+apiKey);
        URLConnection conn = url.openConnection();
        AmpXmlParser parser = new AmpXmlParser();
        return parser.parseUser(conn.getInputStream());
    }

    public static Bundle handshake(String server, String user, String pwd) throws NoSuchAlgorithmException, IOException, XmlPullParserException {
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
        URLConnection conn = url.openConnection();
        AmpXmlParser parser = new AmpXmlParser();
        return parser.parseUser(conn.getInputStream());
    }

    public static Bundle ping(String server, String auth) throws IOException, XmlPullParserException {
        URL url = new URL(server+"/server/xml.server.php?action=ping&auth="+auth);
        URLConnection conn = url.openConnection();
        AmpXmlParser parser = new AmpXmlParser();
        return parser.parseUser(conn.getInputStream());
    }

    public static void goodbye(String server, String auth) throws IOException {
        URL url = new URL(server+"/server/xml.server.php?action=goodbye&auth="+auth);
        URLConnection conn = url.openConnection();
        conn.getContent();
    }

    public static List<Media> advanced_search(String server, String auth, int offset, int limit, int catalogId) throws IOException, XmlPullParserException {
        URL url = new URL(server+"/server/xml.server.php?action=advanced_search&auth="+auth+"&operator=and&type=song&offset="+offset+"&limit="+limit+"&rule_1=catalog&rule_1_operator=0&rule_1_input="+catalogId);
        URLConnection conn = url.openConnection();
        AmpXmlParser parser = new AmpXmlParser();
        return parser.parseSongs(conn.getInputStream());
    }

    public static Map<String, String> catalogs(String server, String auth) throws IOException, XmlPullParserException {
        URL url = new URL(server+"/server/xml.server.php?action=catalogs&auth="+auth);
        URLConnection conn = url.openConnection();
        AmpXmlParser parser = new AmpXmlParser();
        return parser.parseCatalogs(conn.getInputStream());
    }

    public static void localplay_add(String server, String auth, int oid) throws IOException {
        URL url = new URL(server+"/server/xml.server.php?action=localplay&auth="+auth+"&command=add&type=song&oid="+oid+"&clear=1");
        URLConnection conn = url.openConnection();
        conn.getContent();
    }

    public static void localplay_pause(String server, String auth) throws IOException {
        URL url = new URL(server+"/server/xml.server.php?action=localplay&auth="+auth+"&command=pause");
        URLConnection conn = url.openConnection();
        conn.getContent();
    }

    public static void localplay_play(String server, String auth) throws IOException {
        URL url = new URL(server+"/server/xml.server.php?action=localplay&auth="+auth+"&command=play");
        URLConnection conn = url.openConnection();
        conn.getContent();
    }

    public static Bundle localplay_status(String server, String auth) throws IOException, XmlPullParserException {
        URL url = new URL(server+"/server/xml.server.php?action=localplay&auth="+auth+"&command=status");
        URLConnection conn = url.openConnection();
        AmpXmlParser parser = new AmpXmlParser();
        return parser.parseStatus(conn.getInputStream());
    }

    public static void localplay_stop(String server, String auth) throws IOException {
        URL url = new URL(server+"/server/xml.server.php?action=localplay&auth="+auth+"&command=stop");
        URLConnection conn = url.openConnection();
        conn.getContent();
    }

    public static String streaming_url(String server, String auth, int oid, int offset) {
        return server+"/server/xml.server.php?action=stream&auth="+auth+"&id="+oid+"&type=song&offset="+offset;
    }

    public static String get_art_url(String server, String auth, int oid) {
        return server+"/server/xml.server.php?action=get_art&auth="+auth+"&id="+oid+"&type=song";
    }

    public static String dbName(String server, String catalog) throws MalformedURLException {
        URL url = new URL(server);
        return "amp-" + url.hashCode() + "-" + catalog + ".db";
    }
}
