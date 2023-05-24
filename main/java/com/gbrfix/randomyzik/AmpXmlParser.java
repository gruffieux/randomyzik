package com.gbrfix.randomyzik;

import android.util.Pair;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
URL examples
Authentication:
https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=handshake&auth=7e5b37f14c08b28bdff73abe8f990c0b
Catalogs:
https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=catalogs&auth=a967f8fadc3053112edf87eac271ed76
Advanced search:
https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=advanced_search&auth=a967f8fadc3053112edf87eac271ed76&operator=or&type=song&offset=0&limit=10&random=0&rule_1=Catalog&rule_1_operator=4&rule_1_input=gab
Local play:
https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=localplay&auth=a967f8fadc3053112edf87eac271ed76&command=add&oid=12697&type=song&clear=1
https://gbrfix.internet-box.ch/ampache/server/xml.server.php?action=localplay&auth=a967f8fadc3053112edf87eac271ed76&command=status
*/

public class AmpXmlParser {
    // We don't use namespaces
    private static final String ns = null;

    private String readText(XmlPullParser parser, String tag) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, ns, "root");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            // Starts by looking for the entry tag
            if (name.equals(tag)) {
                return readTag(parser, tag);
            } else {
                skip(parser);
            }
        }
        return "";
    }

    private List readSongs(XmlPullParser parser) throws XmlPullParserException, IOException {
        List<Media> songs = new ArrayList();
        parser.require(XmlPullParser.START_TAG, ns, "root");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            // Starts by looking for the entry tag
            if (name.equals("song")) {
                songs.add(readSong(parser));
            } else {
                skip(parser);
            }
        }
        return songs;
    }

    // Parses the contents of an entry. If it encounters a song, hands them off
    // to their respective "read" methods for processing. Otherwise, skips the tag.
    private Media readSong(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, ns, "song");
        int id = Integer.valueOf(parser.getAttributeValue(0));
        int duration = 0;
        String title = null;
        String album = null;
        String artist = null;
        String trackNb = null;
        String albumKey = null;
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equals("title")) {
                title = readTag(parser, "title");
            }  else if (name.equals("artist")) {
                artist = readTag(parser, "artist");
            } else if (name.equals("album")) {
                albumKey = parser.getAttributeValue(0);
                album = readTag(parser, "album");
            } else if (name.equals("track")) {
                trackNb = readTag(parser, "track");
            } else if (name.equals("time")) {
                duration = Integer.valueOf(readTag(parser, "time"));
            } else {
                skip(parser);
            }
        }
        Media media = new Media();
        media.setMediaId(id);
        media.setDuration(duration);
        media.setFlag("unread");
        media.setTrackNb(trackNb);
        media.setTitle(title);
        media.setAlbum(album);
        media.setArtist(artist);
        media.setAlbumKey(albumKey);
        return media;
    }

    private Pair readCatalog(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, ns, "catalog");
        String value = parser.getAttributeValue(0);
        String key = null;
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equals("name")) {
                key = readTag(parser, "name");
            } else {
                skip(parser);
            }
        }
        return new Pair(key, value);
    }

    private Map readCatalogs(XmlPullParser parser) throws XmlPullParserException, IOException {
        Map catalogs = new HashMap<String, String>();
        parser.require(XmlPullParser.START_TAG, ns, "root");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            // Starts by looking for the entry tag
            if (name.equals("catalog")) {
                Pair pair = readCatalog(parser);
                catalogs.put(pair.first, pair.second);
            } else {
                skip(parser);
            }
        }
        return catalogs;
    }

    // Processes tags in the feed.
    private String readTag(XmlPullParser parser, String name) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, name);
        String tag = readText(parser);
        parser.require(XmlPullParser.END_TAG, ns, name);
        return tag;
    }

    // For the tags, extracts their text values.
    private String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        return result;
    }

    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }

    public String parseText(InputStream in, String name) throws XmlPullParserException, IOException {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(new BufferedInputStream(in), null);
            parser.nextTag();
            return readText(parser, name);
        } finally {
            in.close();
        }
    }

    public List parseSongs(InputStream in) throws XmlPullParserException, IOException {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(new BufferedInputStream(in), null);
            parser.nextTag();
            return readSongs(parser);
        } finally {
            in.close();
        }
    }

    public Map parseCatalogs(InputStream in) throws XmlPullParserException, IOException {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(new BufferedInputStream(in), null);
            parser.nextTag();
            return readCatalogs(parser);
        } finally {
            in.close();
        }
    }
}
