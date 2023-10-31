package com.gbrfix.randomyzik;

import android.content.ComponentName;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteCursor;
import android.os.Bundle;
import androidx.annotation.Nullable;

import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.fail;

import org.junit.Assert;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
                if (prefs.getString("amp_catalog", "0").equals("0")) {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putString("amp_catalog", String.valueOf(catalogId));
                    editor.commit();
                }
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
                int count = cursor.getCount();
                dao.close();
                Assert.assertEquals(total, count);
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
                                            fail(String.format("Expected %1$d tracks but was %2$d in album '%3$s'", trackTotal, trackCount, currentAlbum));
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
                                    mediaBrowser.disconnect();
                                    MediaControllerCompat.getMediaController(TestActivity.this).unregisterCallback(this);
                                    if (mode == MediaProvider.MODE_ALBUM) {
                                        assertEquals(total, albumCount);
                                    } else {
                                        assertEquals(total, trackCount);
                                    }
                                    finish();
                                }
                                break;
                            case "onError":
                                fail(extras.getString("message"));
                                break;
                        }
                        super.onSessionEvent(event, extras);
                    }
                });
                mediaController.getTransportControls().play();
            }
        };
    }

    public void localplayCanPlay(int id) {
        mediaBrowser.connect();
        testSignalListener = new TestSignal() {
            @Override
            public void browserConnected() {
                MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(TestActivity.this);
                mediaController.registerCallback(new MediaControllerCompat.Callback() {
                    @Override
                    public void onPlaybackStateChanged(PlaybackStateCompat state) {
                        if (state.getState() == PlaybackStateCompat.STATE_PLAYING) {
                            mediaBrowser.disconnect();
                            MediaControllerCompat.getMediaController(TestActivity.this).unregisterCallback(this);
                            Assert.assertTrue(true);
                            finish();
                        }
                        super.onPlaybackStateChanged(state);
                    }

                    @Override
                    public void onSessionEvent(String event, Bundle extras) {
                        switch (event) {
                            case "onTrackSelect":
                                if (id != extras.getInt("id")) {
                                    fail("Unexcepted media id");
                                }
                                break;
                            case "onError":
                                fail(extras.getString("message"));
                                break;
                        }
                        super.onSessionEvent(event, extras);
                    }
                });
                mediaController.getTransportControls().play();
            }
        };
    }

    public void localplayCannotPlay() {
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
                                mediaBrowser.disconnect();
                                MediaControllerCompat.getMediaController(TestActivity.this).unregisterCallback(this);
                                assertEquals(extras.getString("message"), getString(R.string.err_amp_excepted_state, "play", "stop"));
                                finish();
                                break;
                            default:
                                fail("Unexcepted session event");
                                break;
                        }
                        super.onSessionEvent(event, extras);
                    }
                });
                mediaController.getTransportControls().play();
            }
        };
    }

    public void localplayCanPause(int id) {
        MediaPlaybackService.TEST_DURATION = 45000;
        mediaBrowser.connect();
        testSignalListener = new TestSignal() {
            @Override
            public void browserConnected() {
                MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(TestActivity.this);
                mediaController.registerCallback(new MediaControllerCompat.Callback() {
                    @Override
                    public void onPlaybackStateChanged(PlaybackStateCompat state) {
                        if (state.getState() == PlaybackStateCompat.STATE_PAUSED) {
                            mediaBrowser.disconnect();
                            MediaControllerCompat.getMediaController(TestActivity.this).unregisterCallback(this);
                            Assert.assertTrue(true);
                            finish();
                        }
                        super.onPlaybackStateChanged(state);
                    }

                    @Override
                    public void onSessionEvent(String event, Bundle extras) {
                        switch (event) {
                            case "onTrackSelect":
                                if (id != extras.getInt("id")) {
                                    fail("Unexcepted media id");
                                }
                                break;
                            case "onTrackProgress":
                                if (extras.getInt("position") == 1000) {
                                    mediaController.getTransportControls().pause();
                                }
                                break;
                            case "onError":
                                fail(extras.getString("message"));
                                break;
                        }
                        super.onSessionEvent(event, extras);
                    }
                });
                mediaController.getTransportControls().play();
            }
        };
    }

    public void localplayCannotPause(int id) {
        MediaPlaybackService.TEST_DURATION = 45000;
        mediaBrowser.connect();
        testSignalListener = new TestSignal() {
            @Override
            public void browserConnected() {
                MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(TestActivity.this);
                mediaController.registerCallback(new MediaControllerCompat.Callback() {
                    @Override
                    public void onSessionEvent(String event, Bundle extras) {
                        switch (event) {
                            case "onTrackSelect":
                                if (id != extras.getInt("id")) {
                                    fail("Unexcepted media id");
                                }
                                break;
                            case "onTrackProgress":
                                if (extras.getInt("position") == 1000) {
                                    ExecutorService executor = Executors.newSingleThreadExecutor();
                                    Handler handler = new Handler(Looper.getMainLooper());
                                    AmpSession ampSession = AmpSession.getInstance(getApplicationContext());
                                    executor.execute(() -> {
                                        try {
                                            ampSession.localplay_pause();
                                        } catch (Exception e) {
                                            fail(e.getMessage());
                                        }
                                        handler.post(() -> {
                                            mediaController.getTransportControls().pause();
                                        });
                                    });
                                }
                                break;
                            case "onError":
                                mediaBrowser.disconnect();
                                MediaControllerCompat.getMediaController(TestActivity.this).unregisterCallback(this);
                                assertEquals(extras.getString("message"), getString(R.string.err_amp_excepted_state, "pause", "play"));
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

    public void localplayCanStop(int id) {
        MediaPlaybackService.TEST_DURATION = 45000;
        mediaBrowser.connect();
        testSignalListener = new TestSignal() {
            @Override
            public void browserConnected() {
                MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(TestActivity.this);
                mediaController.registerCallback(new MediaControllerCompat.Callback() {
                    @Override
                    public void onPlaybackStateChanged(PlaybackStateCompat state) {
                        if (state.getState() == PlaybackStateCompat.STATE_STOPPED) {
                            mediaBrowser.disconnect();
                            MediaControllerCompat.getMediaController(TestActivity.this).unregisterCallback(this);
                            Assert.assertTrue(true);
                            finish();
                        }
                        super.onPlaybackStateChanged(state);
                    }

                    @Override
                    public void onSessionEvent(String event, Bundle extras) {
                        switch (event) {
                            case "onTrackSelect":
                                if (id != extras.getInt("id")) {
                                    fail("Unexcepted media id");
                                }
                                break;
                            case "onTrackProgress":
                                if (extras.getInt("position") == 1000) {
                                    mediaController.getTransportControls().stop();
                                }
                                break;
                            case "onError":
                                fail(extras.getString("message"));
                                break;
                        }
                        super.onSessionEvent(event, extras);
                    }
                });
                mediaController.getTransportControls().play();
            }
        };
    }

    public void localplayCannotStop(int id) {
        MediaPlaybackService.TEST_DURATION = 45000;
        mediaBrowser.connect();
        testSignalListener = new TestSignal() {
            @Override
            public void browserConnected() {
                MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(TestActivity.this);
                mediaController.registerCallback(new MediaControllerCompat.Callback() {
                    @Override
                    public void onSessionEvent(String event, Bundle extras) {
                        switch (event) {
                            case "onTrackSelect":
                                if (id != extras.getInt("id")) {
                                    fail("Unexcepted media id");
                                }
                                break;
                            case "onTrackProgress":
                                if (extras.getInt("position") == 1000) {
                                    ExecutorService executor = Executors.newSingleThreadExecutor();
                                    Handler handler = new Handler(Looper.getMainLooper());
                                    AmpSession ampSession = AmpSession.getInstance(getApplicationContext());
                                    executor.execute(() -> {
                                        try {
                                            //ampSession.localplay_stop();
                                            String dbName = ampSession.dbName();
                                            MediaDAO dao = new MediaDAO(getApplicationContext(), dbName);
                                            dao.open();
                                            SQLiteCursor cursor = dao.getAll();
                                            int count = cursor.getCount();
                                            Random random = new Random();
                                            int pos = random.nextInt(count);
                                            cursor.moveToPosition(pos);
                                            int randomId = cursor.getInt(cursor.getColumnIndex("id"));
                                            if (randomId != id) {
                                                int oid = cursor.getInt(cursor.getColumnIndex("media_id"));
                                                ampSession.localplay_add(oid);
                                                ampSession.localplay_play();
                                            } else {
                                                fail("Random track is same as previous session");
                                            }
                                        } catch (Exception e) {
                                            fail(e.getMessage());
                                        }
                                        handler.post(() -> {
                                            mediaController.getTransportControls().stop();
                                        });
                                    });
                                }
                                break;
                            case "onError":
                                mediaBrowser.disconnect();
                                MediaControllerCompat.getMediaController(TestActivity.this).unregisterCallback(this);
                                assertEquals(extras.getString("message"), getString(R.string.err_amp_track_unexcepted));
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
                                mediaBrowser.disconnect();
                                MediaControllerCompat.getMediaController(TestActivity.this).unregisterCallback(this);
                                assertEquals(extras.getString("message"), TestActivity.this.getString(R.string.err_all_read));
                                finish();
                                break;
                            default:
                                fail("Unexcepted session event");
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
