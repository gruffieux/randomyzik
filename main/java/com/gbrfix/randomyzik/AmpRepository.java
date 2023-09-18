package com.gbrfix.randomyzik;

import android.os.Bundle;

import org.xmlpull.v1.XmlPullParserException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

abstract class AmpRepository {
    public final static int MAX_ELEMENTS_PER_REQUEST = 5000;

    private static String byteToHex(byte byteData[]) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < byteData.length; i++) {
            sb.append(Integer.toString((byteData[i] & 0xff) + 0x100, 16).substring(1));
        }
        return sb.toString();
    }

    public static Bundle handshake(String server, String apiKey) throws IOException, XmlPullParserException {
        URL url = new URL(server+"/server/xml.server.php?action=handshake&auth="+apiKey);
        URLConnection conn = url.openConnection();
        AmpXmlParser parser = new AmpXmlParser();
        Bundle user = parser.parseUser(conn.getInputStream());
        conn.disconnect();
        return user;
    }

    public Bundle handshake(String server, String user, String pwd) throws NoSuchAlgorithmException, IOException, XmlPullParserException {
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
        Bundle data = parser.parseUser(conn.getInputStream());
        conn.disconnect();
        return data;
    }

    public static Bundle ping(String server, String auth) throws IOException, XmlPullParserException {
        URL url = new URL(server+"/server/xml.server.php?action=ping&auth="+auth);
        URLConnection conn = url.openConnection();
        AmpXmlParser parser = new AmpXmlParser();
        Bundle user = parser.parseUser(conn.getInputStream());
        conn.disconnect();
        return user;
    }

    public static List advanced_search(String server, String auth, int offset, int catalogId) throws IOException, XmlPullParserException {
        URL url = new URL(server+"/server/xml.server.php?action=advanced_search&auth="+auth+"&operator=and&type=song&offset="+offset+"&limit="+MAX_ELEMENTS_PER_REQUEST+"&rule_1=catalog&rule_1_operator=0&rule_1_input="+catalogId);
        URLConnection conn = url.openConnection();
        AmpXmlParser parser = new AmpXmlParser();
        List list = parser.parseSongs(conn.getInputStream());
        conn.disconnect();
        return list;
    }

    public static Map catalogs(String server, String auth) throws IOException, XmlPullParserException {
        URL url = new URL(server+"/server/xml.server.php?action=catalogs&auth="+auth);
        URLConnection conn = url.openConnection();
        AmpXmlParser parser = new AmpXmlParser();
        Map<String, Integer> map = parser.parseCatalogs(conn.getInputStream());
        conn.disconnect();
        return map;
    }

    public static String localplay_add(String server, String auth, int oid) throws IOException {
        URL url = new URL(server+"/server/xml.server.php?action=localplay&auth="+auth+"&command=add&type=song&oid="+oid+"&clear=1");
        URLConnection conn = url.openConnection();
        String res = conn.getResponseMessage();
        conn.disconnect();
        return res;
    }

    public static String localplay_pause(String server, String auth) throws IOException {
        URL url = new URL(server+"/server/xml.server.php?action=localplay&auth="+auth+"&command=pause");
        URLConnection conn = url.openConnection();
        String res = conn.getResponseMessage();
        conn.disconnect();
        return res;
    }

    public static String localplay_play(String server, String auth) throws IOException {
        URL url = new URL(server+"/server/xml.server.php?action=localplay&auth="+auth+"&command=play");
        URLConnection conn = url.openConnection();
        String res = conn.getResponseMessage();
        conn.disconnect();
        return res;
    }

    public static Bundle localplay_status(String server, String auth) throws IOException, XmlPullParserException {
        URL url = new URL(server+"/server/xml.server.php?action=localplay&auth="+auth+"&command=status");
        URLConnection conn = url.openConnection();
        AmpXmlParser parser = new AmpXmlParser();
        Bundle status = parser.parseStatus(conn.getInputStream());
        conn.disconnect();
        return status;
    }

    public static String localplay_stop(String server, String auth) throws IOException {
        URL url = new URL(server+"/server/xml.server.php?action=localplay&auth="+auth+"&command=stop");
        URLConnection conn = url.openConnection();
        String res = conn.getResponseMessage();
        conn.disconnect();
        return res;
    }

    public static String streaming_url(String server, String auth, int oid, int offset) {
        return server+"/server/xml.server.php?action=stream&auth="+auth+"&id="+oid+"&type=song&offset="+offset;
    }

    public static String dbName(String server, String catalog) throws MalformedURLException {
        URL url = new URL(server);
        return "amp-" + url.hashCode() + "-" + catalog + ".db";
    }
}
