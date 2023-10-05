package com.gbrfix.randomyzik;

import android.content.ComponentName;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteCursor;
import android.os.Bundle;
import androidx.annotation.Nullable;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import org.junit.Assert;

import java.net.MalformedURLException;

/**
 * Created by gab on 16.03.2018.
 */

interface TestSignal {
    public void browserConnected();
};

public class TestActivity extends AppCompatActivity {
    int trackCount;
    MediaBrowserCompat mediaBrowser = null;
    TestSignal testSignalListener = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mediaBrowser = new MediaBrowserCompat(this, new ComponentName(this, MediaPlaybackService.class), browserConnection, null);
    }

    @Override
    protected void onDestroy() {
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
        trackCount = 0;
        mediaBrowser.connect();
        testSignalListener = new TestSignal() {
            @Override
            public void browserConnected() {
                Bundle args = new Bundle();
                args.putInt("mode", MediaProvider.MODE_TRACK);
                mediaBrowser.sendCustomAction("changeMode", args, null);
                MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(TestActivity.this);
                mediaController.registerCallback(new MediaControllerCompat.Callback() {
                    @Override
                    public void onSessionEvent(String event, Bundle extras) {
                        switch (event) {
                            case "onTrackRead":
                                trackCount++;
                                if (extras.getBoolean("last")) {
                                    assertEquals(total, trackCount);
                                    mediaBrowser.disconnect();
                                    MediaControllerCompat.getMediaController(TestActivity.this).unregisterCallback(this);
                                    finish();
                                }
                                break;
                        }
                        super.onSessionEvent(event, extras);
                    }
                });
                mediaController.getTransportControls().play();
            }
        };
    }

    private final MediaBrowserCompat.ConnectionCallback browserConnection = new MediaBrowserCompat.ConnectionCallback() {
        @Override
        public void onConnected() {
            try {
                MediaSessionCompat.Token token = mediaBrowser.getSessionToken();
                MediaControllerCompat mediaController = new MediaControllerCompat(TestActivity.this, token);
                MediaControllerCompat.setMediaController(TestActivity.this, mediaController);
                testSignalListener.browserConnected();
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
