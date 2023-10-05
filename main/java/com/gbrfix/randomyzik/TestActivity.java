package com.gbrfix.randomyzik;

import android.content.ComponentName;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.os.Bundle;
import android.provider.MediaStore;
import androidx.annotation.Nullable;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.util.Log;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import org.junit.Assert;

import java.net.MalformedURLException;

/**
 * Created by gab on 16.03.2018.
 */

interface MediaSignal {
    public void sessionEvent(String event, Bundle extras);
};

public class TestActivity extends AppCompatActivity {
    final static int TEST_PLAY_ALL_TRACKS = 1;
    final static int TEST_PLAY_ALL_ALBUMS = 2;
    final static int TEST_PLAY_LAST_TRACK = 3;
    final static int TEST_PLAY_ENDED_LIST = 4;

    int currentTest, trackCount, trackTotal;
    MediaBrowserCompat mediaBrowser = null;
    MediaSignal mediaSignalListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        currentTest = trackCount = trackTotal = 0;
        mediaBrowser = new MediaBrowserCompat(this, new ComponentName(this, MediaPlaybackService.class), browserConnection, null);
    }

    @Override
    protected void onStart() {
        super.onStart();

        mediaBrowser.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();

        mediaBrowser.disconnect();
    }

    @Override
    protected void onDestroy() {
        if (MediaControllerCompat.getMediaController(this) != null) {
            MediaControllerCompat.getMediaController(this).unregisterCallback(controllerCallback);
        }

        super.onDestroy();
    }

    public void createAmpacheCatalogs() {
        DbService dbService = new DbService(this);

        dbService.setDbSignalListener(new DbSignal() {
            @Override
            public void onScanStart() {

            }

            @Override
            public void onScanCompleted(int catalogId, int total, boolean update, boolean all) {
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(TestActivity.this);
                String server = prefs.getString("amp_server", "");
                try {
                    String dbName = AmpRepository.dbName(server, String.valueOf(catalogId));
                    MediaDAO dao = new MediaDAO(TestActivity.this, "test-" + dbName);
                    dao.open();
                    SQLiteCursor cursor = dao.getAll();
                    int count = cursor.getCount();
                    dao.close();
                    if (count != total) {
                        Assert.assertFalse(true);
                        finish();
                    }
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
                if (all) {
                    Assert.assertTrue(true);
                    finish();
                }
            }

            @Override
            public void onError(String msg) {
                Assert.assertFalse(false);
            }
        });

        dbService.check();
    }

    public void createList() {
        DbService dbService = new DbService(this);
        
        dbService.setDbSignalListener(new DbSignal() {
            @Override
            public void onScanStart() {

            }

            @Override
            public void onScanCompleted(int catalogId, int total, boolean update, boolean all) {
                MediaDAO dao = new MediaDAO(TestActivity.this, "test-" + DAOBase.DEFAULT_NAME);
                dao.open();
                SQLiteCursor cursor = dao.getAll();
                Assert.assertEquals(total, cursor.getCount());
                dao.close();
                finish();
            }

            @Override
            public void onError(String msg) {
                Assert.assertFalse(false);
            }
        });

        dbService.check();
    }

    public void playAllTracks(int total) {
        mediaSignalListener = new mediaSignalListener() {
            int counter = 0;
            @Override
            public void sessionEvent(String event, Bundle extras) {
                switch (event) {
                    case "onTrackRead":
                        counter++;
                        if (extras.getBoolean("last")) {
                            assertEquals(total, counter);
                            finish();
                        }
                        break;
                }
            }
        });

        Bundle args = new Bundle();
        args.putInt("mode", MediaProvider.MODE_TRACK);
        mediaBrowser.sendCustomAction("changeMode", args, null);

        mediaController.getTransportControls().play();
    }
    
    private final MediaControllerCompat.Callback controllerCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            super.onPlaybackStateChanged(state);
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            super.onMetadataChanged(metadata);
        }

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
                            Cursor cursor = getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, new String[]{
                                    MediaStore.Audio.Media.DATA
                            }, "_id=?", new String[] {String.valueOf(extras.getInt("media_id"))}, null);
                            if (cursor.moveToFirst()) {
                                Log.i("trackPath", cursor.getString(0));
                            }
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
                MediaControllerCompat mediaController = new MediaControllerCompat(TestActivity.this, token);
                MediaControllerCompat.setMediaController(TestActivity.this, mediaController);
                mediaController.registerCallback(controllerCallback);
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
