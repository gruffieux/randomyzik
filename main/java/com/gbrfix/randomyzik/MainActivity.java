package com.gbrfix.randomyzik;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.database.SQLException;
import android.database.sqlite.SQLiteCursor;
import android.graphics.Color;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.CompoundButton;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    final int MY_PERSMISSIONS_REQUEST_STORAGE = 1;
    DbService dbService = null;
    AmpService ampService = null;
    MediaBrowserCompat mediaBrowser = null;
    SimpleDateFormat dateFormat = new SimpleDateFormat("mm:ss");
    int currentId = 0;
    AmpRepository amp = null; // Depracated

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            final ImageButton playBtn = findViewById(R.id.play);
            final ImageButton rewBtn = findViewById(R.id.rew);
            final ImageButton fwdBtn = findViewById(R.id.fwd);

            // Service de scanning
            DbService.DbBinder binder = (DbService.DbBinder)iBinder;
            dbService = binder.getService();
            dbService.setDbSignalListener(new DbSignal() {
                @Override
                public void onScanStart() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TextView infoMsg = findViewById(R.id.infoMsg);
                            if (infoMsg.getText().equals("")) {
                                int color = fetchColor(MainActivity.this, R.attr.colorAccent);
                                infoMsg(getString(R.string.info_scanning), color);
                            }
                        }
                    });
                }

                @Override
                public void onScanCompleted(final boolean update) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            playBtn.setEnabled(true);
                            TextView infoMsg = findViewById(R.id.infoMsg);
                            if (infoMsg.getText().equals(getText(R.string.info_scanning))) {
                                infoMsg.setText("");
                            }
                            if (update) {
                                try {
                                    MediaDAO dao = new MediaDAO(MainActivity.this);
                                    dao.open();
                                    SQLiteCursor cursor = dao.getAllOrdered();
                                    ListView listView = findViewById(R.id.playlist);
                                    TrackCursorAdapter adapter = (TrackCursorAdapter) listView.getAdapter();
                                    adapter.changeCursor(cursor);
                                    dao.close();
                                } catch (SQLException e) {
                                    Log.v("SQLException", e.getMessage());
                                }
                            }
                        }
                    });
                }

                @Override
                public void onError(String msg) {
                    infoMsg(msg, Color.RED);
                    playBtn.setEnabled(false);
                    rewBtn.setEnabled(false);
                    fwdBtn.setEnabled(false);
                }
            });

            dbService.setBound(true);
            Intent intent = new Intent(dbService, DbService.class);
            startService(intent);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            dbService.setBound(false);
        }
    };

    private ServiceConnection ampConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            AmpService.LocalBinder binder = (AmpService.LocalBinder)service;
            ampService = binder.getService();
            ampService.setBound(true);
            ampService.setAmpSignalListener(new AmpSignal() {
                @Override
                public void onSelect(int duration, String title, String album, String artist) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onTrackSelect(duration*1000, title, album, artist);
                        }
                    });
                }

                @Override
                public void onProgress(int position) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onTrackPorgress(position*1000);
                        }
                    });
                }

                @Override
                public void onComplete(boolean last) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            onTrackRead(last);
                        }
                    });
                }

                @Override
                public void onError(String msg) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            infoMsg(msg, Color.RED);
                        }
                    });
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            ampService.setBound(false);
        }
    };

    private final MediaControllerCompat.Callback controllerCallback = new MediaControllerCompat.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            ImageButton playBtn = findViewById(R.id.play);
            ImageButton rewBtn = findViewById(R.id.rew);
            ImageButton fwdBtn = findViewById(R.id.fwd);

            int color = fetchColor(MainActivity.this, R.attr.colorAccent);
            playBtn.setEnabled(true);
            playBtn.setColorFilter(color);
            playBtn.setImageResource(state.getState() == PlaybackStateCompat.STATE_PLAYING ? R.drawable.ic_action_pause : R.drawable.ic_action_play);

            rewBtn.setEnabled(state.getState() == PlaybackStateCompat.STATE_PLAYING);
            rewBtn.setColorFilter(state.getState() == PlaybackStateCompat.STATE_PLAYING ? color : Color.GRAY);

            fwdBtn.setEnabled(state.getState() == PlaybackStateCompat.STATE_PLAYING);
            fwdBtn.setColorFilter(state.getState() == PlaybackStateCompat.STATE_PLAYING ? color : Color.GRAY);

            if (state.getState() == PlaybackStateCompat.STATE_STOPPED) {
                TextView infoMsg = findViewById(R.id.infoMsg);
                TextView positionLabel = findViewById(R.id.position);
                TextView durationLabel = findViewById(R.id.duration);
                ProgressBar progressBar = findViewById(R.id.progressBar);

                if (!infoMsg.getText().equals(getText(R.string.info_play_end)) && !infoMsg.getText().equals(getText(R.string.err_all_read))) {
                    infoMsg.setText("");
                }

                positionLabel.setText("");
                durationLabel.setText("");
                progressBar.setProgress(0);
                progressBar.setMax(0);
            }
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
        }

        @Override
        public void onSessionEvent(String event, final Bundle extras) {
            SharedPreferences.Editor editor = getPreferences(Context.MODE_PRIVATE).edit();

            switch (event) {
                case "onTrackSelect":
                    currentId = extras.getInt("id");
                    int duration = extras.getInt("duration");
                    String title = extras.getString("title");
                    String album = extras.getString("album");
                    String artist = extras.getString("artist");

                    onTrackSelect(duration, title, album, artist);

                    // Sauvegarde piste en cours
                    editor.putInt("currentId", currentId);
                    editor.putInt("position", 0);
                    editor.commit();
                    break;
                case "onTrackSave":
                    // Sauvegarde piste en cours
                    editor.putInt("currentId", extras.getInt("id"));
                    editor.putInt("position", extras.getInt("position"));
                    editor.commit();
                    break;
                case "onTrackProgress":
                    int position = extras.getInt("position");
                    onTrackPorgress(position);
                    break;
                case "onTrackRead":
                    onTrackRead(extras.getBoolean("last"));
                    break;
                case "onError":
                    infoMsg(extras.getString("message"), Color.RED);
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
                MediaControllerCompat mediaController = new MediaControllerCompat(MainActivity.this, token);
                MediaControllerCompat.setMediaController(MainActivity.this, mediaController);

                mediaController.registerCallback(controllerCallback);
            } catch (RemoteException e) {
                Log.v("IllegalStateException", e.getMessage());
            } catch (IllegalStateException e) {
                Log.v("IllegalStateException", e.getMessage());
            }

            // Finish building the UI
            buildTransportControls();

            // Récup piste en cours
            if (currentId == 0) {
                SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
                int id = prefs.getInt("currentId", 0);
                int position = prefs.getInt("position", 0);
                if (id > 0) {
                    MediaDAO dao = new MediaDAO(MainActivity.this);
                    dao.open();
                    SQLiteCursor cursor = dao.getFromId(id);
                    if (cursor.moveToFirst()) {
                        Bundle args = new Bundle();
                        args.putInt("id", id);
                        args.putInt("position", position);
                        mediaBrowser.sendCustomAction("restoreTrack", args, null);
                    }
                    dao.close();
                }
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

        void buildTransportControls() {
            ImageButton playBtn = findViewById(R.id.play);
            ImageButton rewBtn = findViewById(R.id.rew);
            ImageButton fwdBtn = findViewById(R.id.fwd);
            Switch modeBtn = findViewById(R.id.mode);
            TextView positionLabel = findViewById(R.id.position);
            TextView durationLabel = findViewById(R.id.duration);
            ProgressBar progressBar = findViewById(R.id.progressBar);

            int state = MediaControllerCompat.getMediaController(MainActivity.this).getPlaybackState().getState();
            int color = fetchColor(MainActivity.this, R.attr.colorAccent);

            // Set media button state
            playBtn.setEnabled(true);
            playBtn.setColorFilter(color);
            playBtn.setImageResource(state == PlaybackStateCompat.STATE_PLAYING ? R.drawable.ic_action_pause : R.drawable.ic_action_play);
            rewBtn.setEnabled(state == PlaybackStateCompat.STATE_PLAYING);
            rewBtn.setColorFilter(state == PlaybackStateCompat.STATE_PLAYING ? color : Color.GRAY);
            fwdBtn.setEnabled(state == PlaybackStateCompat.STATE_PLAYING);
            fwdBtn.setColorFilter(state == PlaybackStateCompat.STATE_PLAYING ? color : Color.GRAY);

            // Set mode switcher state
            changeMode(modeBtn.isChecked());

            // Set controls info with current track
            if (state == PlaybackStateCompat.STATE_PLAYING || state == PlaybackStateCompat.STATE_PAUSED) {
                MediaMetadataCompat metaData = MediaControllerCompat.getMediaController(MainActivity.this).getMetadata();
                currentId = Integer.valueOf(metaData.getString(MediaMetadata.METADATA_KEY_MEDIA_ID));
                long duration = metaData.getLong(MediaMetadata.METADATA_KEY_DURATION);
                long position = MediaControllerCompat.getMediaController(MainActivity.this).getExtras().getInt("position");
                durationLabel.setText(dateFormat.format(new Date(duration)));
                positionLabel.setText(dateFormat.format(new Date(position)));
                progressBar.setMax((int)duration);
                progressBar.setProgress((int)position);
                color = fetchColor(MainActivity.this, R.attr.colorPrimaryDark);
                infoMsg(MediaProvider.getTrackLabel(metaData.getString(MediaMetadata.METADATA_KEY_TITLE), metaData.getString(MediaMetadata.METADATA_KEY_ALBUM), metaData.getString(MediaMetadata.METADATA_KEY_ARTIST)), color);
            }

            // Handle play button
            playBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (ampService.isBound()) {
                        if (ampService.isStarted()) {
                            Intent resumeIntent = new Intent();
                            //resumeIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            resumeIntent.setAction("resume");
                            sendBroadcast(resumeIntent);
                        } else {
                            Intent intent = new Intent(MainActivity.this, AmpService.class);
                            startService(intent);
                        }
                    } else {
                        int state = MediaControllerCompat.getMediaController(MainActivity.this).getPlaybackState().getState();
                        if (state == PlaybackStateCompat.STATE_PLAYING) {
                            MediaControllerCompat.getMediaController(MainActivity.this).getTransportControls().pause();
                        } else {
                            MediaControllerCompat.getMediaController(MainActivity.this).getTransportControls().play();
                        }
                    }

                }
            });

            // Handle rewind button
            rewBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    MediaControllerCompat.getMediaController(MainActivity.this).getTransportControls().rewind();
                }
            });

            // Handle forward button
            fwdBtn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    MediaControllerCompat.getMediaController(MainActivity.this).getTransportControls().skipToNext();
                }
            });

            // Handle mode switcher
            modeBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    changeMode(b);
                }
            });
        }

        void changeMode(boolean b) {
            int mode =  b == true ? MediaProvider.MODE_ALBUM : MediaProvider.MODE_TRACK;

            // Save mode as preferences
            SharedPreferences.Editor editor = getPreferences(Context.MODE_PRIVATE).edit();
            editor.putInt("mode", mode);
            editor.commit();

            if (ampService.isBound()) {
                ampService.changeMode(mode);
            } else {
                // Send action to browser service (Depracated)
                Bundle args = new Bundle();
                args.putInt("mode", mode);
                mediaBrowser.sendCustomAction("changeMode", args, null);
            }
        }
    };

    public int fetchColor( Context c, int id ) {
        int[] attrs = { id };
        TypedArray ta = c.obtainStyledAttributes(R.style.AppTheme, attrs );
        int color = ta.getColor( 0, Color.BLACK );
        return color;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            return super.onKeyDown(keyCode, event);
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MEDIA_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
            case KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
            case KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD:
                MediaControllerCompat.getMediaController(MainActivity.this).dispatchMediaButtonEvent(event);
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERSMISSIONS_REQUEST_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission was granted, app can run
                    setContentView(R.layout.playlist);
                    init(1);
                    if (mediaBrowser != null && !mediaBrowser.isConnected()) {
                        mediaBrowser.connect();
                    }
                }
                else {
                    // Permission denied, display info
                    setContentView(R.layout.playlist);
                    init(0);
                }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERSMISSIONS_REQUEST_STORAGE);
            return;
        }

        setContentView(R.layout.playlist);
        init(1);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (dbService != null && dbService.isBound()) {
            MediaDAO dao = new MediaDAO(this);
            dao.open();
            SQLiteCursor cursor = dao.getAllOrdered();
            ListView listView = findViewById(R.id.playlist);
            TrackCursorAdapter adapter = (TrackCursorAdapter) listView.getAdapter();
            adapter.changeCursor(cursor);
            dao.close();
        }

        if (mediaBrowser != null) {
            mediaBrowser.connect();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        // BUG: Cause un bug lors de la dernière piste lue car l'event onTrackRead n'est pas reçu par l'activité
        /*if (MediaControllerCompat.getMediaController(MainActivity.this) != null) {
            MediaControllerCompat.getMediaController(MainActivity.this).unregisterCallback(controllerCallback);
        }*/

        if (mediaBrowser != null) {
            mediaBrowser.disconnect();
        }
    }

    protected void onTrackSelect(int duration, String title, String album, String artist) {
        TextView positionLabel = findViewById(R.id.position);
        TextView durationLabel = findViewById(R.id.duration);
        ProgressBar progressBar = findViewById(R.id.progressBar);

        // Libellé de position et durée
        positionLabel.setText(dateFormat.format(new Date(0)));
        durationLabel.setText(dateFormat.format(new Date(duration)));

        // Barre de progression
        progressBar.setProgress(0);
        progressBar.setMax(duration);

        // Titre en cours
        String label = MediaProvider.getTrackLabel(title, album, artist);
        int color = fetchColor(MainActivity.this, R.attr.colorPrimaryDark);
        infoMsg(label, color);
    }

    protected void onTrackPorgress(int position) {
        TextView positionLabel = findViewById(R.id.position);
        ProgressBar progressBar = findViewById(R.id.progressBar);

        positionLabel.setText(dateFormat.format(new Date(position)));
        progressBar.setProgress(position);
    }

    protected  void onTrackRead(boolean last) {
        MediaDAO dao = new MediaDAO(MainActivity.this);
        dao.open();
        SQLiteCursor cursor = dao.getAllOrdered();
        ListView listView = findViewById(R.id.playlist);
        TrackCursorAdapter adapter = (TrackCursorAdapter) listView.getAdapter();
        adapter.changeCursor(cursor);
        dao.close();

        if (last) {
            int color = fetchColor(MainActivity.this, R.attr.colorAccent);
            infoMsg(getString(R.string.info_play_end), color);
        }
    }

    protected void init(int perms) {
        // On récup les éléments de l'UI
        final ListView listView = findViewById(R.id.playlist);
        ImageButton playBtn = findViewById(R.id.play);
        ImageButton rewBtn = findViewById(R.id.rew);
        ImageButton fwdBtn = findViewById(R.id.fwd);

        LinearLayout controlLayout = findViewById(R.id.control);
        controlLayout.setHorizontalGravity(1);
        playBtn.setEnabled(false);
        playBtn.setColorFilter(Color.GRAY);
        rewBtn.setEnabled(false);
        rewBtn.setColorFilter(Color.GRAY);
        fwdBtn.setEnabled(false);
        fwdBtn.setColorFilter(Color.GRAY);

        // Read preferences
        Switch modeBtn = findViewById(R.id.mode);
        SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
        int mode = prefs.getInt("mode", MediaProvider.MODE_TRACK);
        modeBtn.setChecked(mode == MediaProvider.MODE_ALBUM);
        int db = prefs.getInt("db", DAOBase.DB_TYPE);

        try {
            if (perms == 1) {
                mediaBrowser = new MediaBrowserCompat(this, new ComponentName(this, MediaPlaybackService.class), browserConnection, null);

                Intent intent = new Intent(this, DbService.class);
                bindService(intent, connection, Context.BIND_AUTO_CREATE);

                switch (db) {
                    case 2:
                        DAOBase.NAME = "playlist-amp.db";
                        Intent intent2 = new Intent(this, AmpService.class);
                        bindService(intent2, ampConnection, Context.BIND_AUTO_CREATE);
                        break;
                    case 1:
                        DAOBase.NAME = "playlist-test.db";
                        break;
                    default:
                        DAOBase.NAME = "playlist.db";
                }
                MediaDAO dao = new MediaDAO(this);
                dao.open();
                SQLiteCursor cursor = dao.getAllOrdered();

                // Cursor adapter pour la listeView
                String[] fromColumns = {"track_nb", "title", "album", "artist"};
                int[] toViews = {R.id.track_nb, R.id.title, R.id.album, R.id.artist};
                TrackCursorAdapter adapter = new TrackCursorAdapter(this, R.layout.track, cursor, fromColumns, toViews);
                listView.setAdapter(adapter);

                dao.close();
            }
            else {
                throw new Exception(getString(R.string.err_no_perms));
            }
        }
        catch (Exception e) {
            playBtn.setEnabled(false);
            rewBtn.setEnabled(false);
            fwdBtn.setEnabled(false);
            infoMsg(e.getMessage(), Color.RED);
        }

        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // Dialogue d'édition du flag pour une piste
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                SingleTrackDialogFragment dialog = new SingleTrackDialogFragment();
                dialog.setId((int)id);
                dialog.show(getSupportFragmentManager(), "singleTrackFlagEditor");
            }
        });

        // Dialogue d'édition du flag de toutes les pistes
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                AllTracksDialogFragment dialog = new AllTracksDialogFragment();
                dialog.setId((int)id);
                dialog.show(getSupportFragmentManager(), "allTrackFlagEditor");
                return true;
            }
        });

        // Sélection de la piste en cours
        TextView trackInfo = findViewById(R.id.infoMsg);
        trackInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentId > 0) {
                    TrackCursorAdapter adapter = (TrackCursorAdapter)listView.getAdapter();
                    int pos = adapter.findView(currentId);
                    if (pos != -1) {
                        listView.setSelection(pos);
                    }
                }
            }
        });
    }

    public void infoMsg(String msg, int color) {
        TextView infoMsg = findViewById(R.id.infoMsg);
        infoMsg.setTextColor(color);
        infoMsg.setText(msg);
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);
    }

    @Override
    public void onRestoreInstanceState(Bundle bundle) {
        super.onRestoreInstanceState(bundle);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (dbService != null && dbService.isBound()) {
            unbindService(connection);
        }

        if (ampService != null && ampService.isBound()) {
            unbindService(ampConnection);
        }
    }
}
