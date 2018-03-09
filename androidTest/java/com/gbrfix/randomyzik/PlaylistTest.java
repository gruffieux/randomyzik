package com.gbrfix.randomyzik;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteCursor;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.rule.ServiceTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Created by gab on 01.10.2017.
 */

@RunWith(AndroidJUnit4.class)
public class PlaylistTest {
    final static int TEST_PLAY_ALL_TRACKS = 1;
    final static int TEST_PLAY_ALL_ALBUMS = 2;
    final static int TEST_PLAY_LAST_TRACK = 3;
    final static int TEST_PLAY_ENDED_LIST = 4;

    int currentTest, trackCount, trackTotal;
    MediaBrowserCompat mediaBrowser = null;

    private final MediaControllerCompat.Callback controllerCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onSessionEvent(String event, final Bundle extras) {
            switch (event) {
                case "onTrackSelect":
                    switch (currentTest) {
                        case TEST_PLAY_LAST_TRACK:
                            assertEquals(extras.getInt("total"), extras.getInt("totalRead"));
                            break;
                    }
                    break;
                case "onTrackProgress":
                    break;
                case "onTrackRead":
                    trackCount++;
                    if (extras.getBoolean("last")) {
                        switch (currentTest) {
                            case TEST_PLAY_ALL_TRACKS:
                            case TEST_PLAY_ALL_ALBUMS:
                            case TEST_PLAY_LAST_TRACK:
                            case TEST_PLAY_ENDED_LIST:
                                assertEquals(trackTotal, trackCount);
                                assertEquals(100, trackCount/trackTotal*100);
                                break;
                        }
                        currentTest = 0;
                    }
                    break;
            }
            super.onSessionEvent(event, extras);
        }
    };

    private final MediaBrowserCompat.ConnectionCallback browserConnection = new MediaBrowserCompat.ConnectionCallback() {
        @Override
        public void onConnected() {
            try {
                // Get the token for the MediaSession
                MediaSessionCompat.Token token = mediaBrowser.getSessionToken();

                // Create a MediaControllerCompat
                MediaControllerCompat mediaController = new MediaControllerCompat(InstrumentationRegistry.getContext(), token);

                mediaController.registerCallback(controllerCallback);

                Bundle args = new Bundle();
                switch (currentTest) {
                    case TEST_PLAY_ALL_TRACKS:
                        args.putInt("mode", MediaProvider.MODE_TRACK);
                        mediaBrowser.sendCustomAction("changeMode", args, null);
                        break;
                    case TEST_PLAY_ALL_ALBUMS:
                        args.putInt("mode", MediaProvider.MODE_ALBUM);
                        mediaBrowser.sendCustomAction("changeMode", args, null);
                        break;
                }
                switch (currentTest) {
                    case TEST_PLAY_ALL_TRACKS:
                    case TEST_PLAY_ALL_ALBUMS:
                    case TEST_PLAY_LAST_TRACK:
                    case TEST_PLAY_ENDED_LIST:
                        mediaBrowser.sendCustomAction("test", null, null);
                        mediaController.getTransportControls().play();
                        break;
                }
            } catch (RemoteException e) {
                Log.v("IllegalStateException", e.getMessage());
            } catch (IllegalStateException e) {
                Log.v("IllegalStateException", e.getMessage());
            }
        }

        @Override
        public void onConnectionSuspended() {
            // The Service has crashed. Disable transport controls until it automatically reconnects
        }

        @Override
        public void onConnectionFailed() {
            // The Service has refused our connection
        }
    };

    public PlaylistTest() {
        currentTest = trackCount = trackTotal = 0;
        DAOBase.NAME = "playlist-test.db";
    }

    @Test
    public void playAllTracks() {
        currentTest = TEST_PLAY_ALL_TRACKS;

        Context c = InstrumentationRegistry.getTargetContext();

        MediaDAO dao = new MediaDAO(c);
        dao.open();
        dao.updateFlagAll("unread");
        SQLiteCursor cursor = dao.getUnread();
        trackTotal = cursor.getCount();
        dao.close();

        while (currentTest != 0) {
        }

        Log.v("PlayAllTracks", "end");
    }

    @Test
    public void playAllAlbums() {
        currentTest = TEST_PLAY_ALL_ALBUMS;

        Context c = InstrumentationRegistry.getTargetContext();

        MediaDAO dao = new MediaDAO(c);
        dao.open();
        dao.updateFlagAll("unread");
        SQLiteCursor cursor = dao.getUnread();
        trackTotal = cursor.getCount();
        dao.close();

        while (currentTest != 0) {
        }

        Log.v("playAllAlbums", "end");
    }

    @Test
    public void playLastTrack() throws Exception {
        currentTest = TEST_PLAY_LAST_TRACK;

        Context c = InstrumentationRegistry.getTargetContext();

        MediaDAO dao = new MediaDAO(c);
        dao.open();
        dao.updateFlagAll("read");
        SQLiteCursor cursor = dao.getAll();
        Random random = new Random();
        int total = cursor.getCount();

        if (total > 1) {
            int pos = random.nextInt(total);
            cursor.moveToPosition(pos);
        }
        else if (total > 0) {
            cursor.moveToFirst();
        }
        else {
            throw new Exception("List is empty");
        }

        dao.updateFlag(cursor.getInt(0), "unread");
        trackTotal = 1;
        dao.close();

        mediaBrowser = new MediaBrowserCompat(c, new ComponentName(c, MediaPlaybackService.class), browserConnection, null);
        mediaBrowser.connect();

        while (currentTest != 0) {
        }

        Log.v("playLastTrack", "end");
    }

    @Test
    public void playEndedList() throws Exception {
        currentTest = TEST_PLAY_ENDED_LIST;

        Context c = InstrumentationRegistry.getTargetContext();

        MediaDAO dao = new MediaDAO(c);
        dao.open();
        dao.updateFlagAll("read");
        trackTotal = 0;
        dao.close();

        while (currentTest != 0) {
        }

        Log.v("playEndedList", "end");
    }
}
