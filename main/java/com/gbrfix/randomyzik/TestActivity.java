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

import org.junit.Assert;

/**
 * Created by gab on 16.03.2018.
 */

interface TestSignal {
    public void browserConnected();
};

public class TestActivity extends AppCompatActivity {
    int trackCount, trackTotal, albumCount;
    String currentAlbum;
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
                        Assert.fail(String.format("Expected %2$d but was %1$d in catalog %3$d", count, total, catalogId));
                    }
                } catch (Exception e) {
                    Assert.fail(e.getMessage());
                }
                if (all) {
                    Assert.assertTrue(true);
                    finish();
                }
            }

            @Override
            public void onError(String msg) {
                Assert.fail(msg);
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

    public void playAllTracks(int total, int mode) {
        trackCount = albumCount = 0;
        currentAlbum = "";
        mediaBrowser.connect();
        testSignalListener = new TestSignal() {
            @Override
            public void browserConnected() {
                Bundle args = new Bundle();
                args.putInt("mode", mode);
                mediaBrowser.sendCustomAction("changeMode", args, null);
                MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(TestActivity.this);
                mediaController.registerCallback(new MediaControllerCompat.Callback() {
                    @Override
                    public void onSessionEvent(String event, Bundle extras) {
                        switch (event) {
                            case "onTrackSelect":
                                if (mode == MediaProvider.MODE_ALBUM) {
                                    String albumKey = extras.getString("albumKey");
                                    if (!albumKey.equals(currentAlbum) || currentAlbum.isEmpty()) {
                                        if (trackTotal > 0 && trackTotal != trackCount) {
                                            Assert.fail(String.format("Expected %1$d tracks but was %2$d in album '%3$s'", trackTotal, trackCount, currentAlbum));
                                        }
                                        currentAlbum = albumKey;
                                        MediaDAO dao = new MediaDAO(TestActivity.this, "test-" + DAOBase.DEFAULT_NAME);
                                        dao.open();
                                        SQLiteCursor cursor = dao.getFromAlbum(currentAlbum, "");
                                        trackTotal = cursor.getCount();
                                        dao.close();
                                        albumCount++;
                                        trackCount = 0;
                                    }
                                }
                                break;
                            case "onTrackRead":
                                trackCount++;
                                if (extras.getBoolean("last")) {
                                    if (mode == MediaProvider.MODE_ALBUM) {
                                        assertEquals(total, albumCount);
                                    } else {
                                        assertEquals(total, trackCount);
                                    }
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

    public void playEndedList() {
        MediaDAO dao = new MediaDAO(TestActivity.this, "test-" + DAOBase.DEFAULT_NAME);
        dao.open();
        dao.updateFlagAll("read");
        dao.close();
        mediaBrowser.connect();
        testSignalListener = new TestSignal() {
            @Override
            public void browserConnected() {
                MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(TestActivity.this);
                mediaController.registerCallback(new MediaControllerCompat.Callback() {
                    @Override
                    public void onSessionEvent(String event, Bundle extras) {
                        switch (event) {
                            case "onError":
                                assertEquals(extras.getString("message"), TestActivity.this.getString(R.string.err_all_read));
                                finish();
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
                Assert.fail(e.getMessage());
            }
        }

        @Override
        public void onConnectionSuspended() {
            Assert.fail("The Service has crashed. Disable transport controls until it automatically reconnects");
        }

        @Override
        public void onConnectionFailed() {
            Assert.fail("The Service has refused our connection");
        }
    };
}
