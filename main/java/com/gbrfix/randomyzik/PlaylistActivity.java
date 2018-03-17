package com.gbrfix.randomyzik;

import android.content.ComponentName;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v7.app.AppCompatActivity;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

/**
 * Created by gab on 16.03.2018.
 */

public class PlaylistActivity extends AppCompatActivity {
    final static int TEST_PLAY_ALL_TRACKS = 1;
    final static int TEST_PLAY_ALL_ALBUMS = 2;
    final static int TEST_PLAY_LAST_TRACK = 3;
    final static int TEST_PLAY_ENDED_LIST = 4;

    int currentTest, trackCount, trackTotal;
    MediaBrowserCompat mediaBrowser = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        currentTest = trackCount = trackTotal = 0;
        mediaBrowser = new MediaBrowserCompat(this, new ComponentName(this, MediaPlaybackService.class), browserConnection, null);
    }

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
                case "onError":
                    switch (currentTest) {
                        case TEST_PLAY_ENDED_LIST:
                            assertTrue(true);
                            break;
                        default:
                            assertTrue(false);
                    }
                    currentTest = 0;
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
                MediaControllerCompat mediaController = new MediaControllerCompat(PlaylistActivity.this, token);

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
            } catch (Exception e) {
                assertTrue(false);
            }
        }

        @Override
        public void onConnectionSuspended() {
            // The Service has crashed. Disable transport controls until it automatically reconnects
            assertTrue(false);
        }

        @Override
        public void onConnectionFailed() {
            // The Service has refused our connection
            assertTrue(false);
        }
    };
}
