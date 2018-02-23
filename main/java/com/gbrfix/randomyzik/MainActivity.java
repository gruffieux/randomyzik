package com.gbrfix.randomyzik;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.database.SQLException;
import android.database.sqlite.SQLiteCursor;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AbsListView;
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
    AudioService audioService = null;
    private MediaBrowserCompat mediaBrowser = null;
    SimpleDateFormat dateFormat = new SimpleDateFormat("mm:ss");
    int scrollY = 0;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            final ImageButton playBtn = (ImageButton)findViewById(R.id.play);
            final ImageButton rewBtn = (ImageButton)findViewById(R.id.rew);
            final ImageButton fwdBtn = (ImageButton)findViewById(R.id.fwd);

            // Service de scanning
            DbService.DbBinder binder = (DbService.DbBinder)iBinder;
            dbService = binder.getService();
            dbService.setDbSignalListener(new DbSignal() {
                @Override
                public void onScanCompleted(final boolean update) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            playBtn.setEnabled(true);
                            TextView infoMsg = (TextView)findViewById(R.id.infoMsg);
                            if (infoMsg.getText().equals(getText(R.string.info_scanning))) {
                                infoMsg.setText("");
                            }
                            if (update) {
                                try {
                                    MediaDAO dao = new MediaDAO(MainActivity.this);
                                    dao.open();
                                    SQLiteCursor cursor = dao.getAllOrdered();
                                    ListView listView = (ListView) findViewById(R.id.playlist);
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
            dbService.start();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            dbService.setBound(false);
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
            rewBtn.setEnabled(state.getState() == PlaybackStateCompat.STATE_PLAYING);
            rewBtn.setColorFilter(state.getState() == PlaybackStateCompat.STATE_PLAYING ? color : Color.GRAY);
            fwdBtn.setEnabled(state.getState() == PlaybackStateCompat.STATE_PLAYING);
            fwdBtn.setColorFilter(state.getState() == PlaybackStateCompat.STATE_PLAYING ? color : Color.GRAY);
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
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
            } catch (RemoteException e) {
                Log.v("IllegalStateException", e.getMessage());
            } catch (IllegalStateException e) {
                Log.v("IllegalStateException", e.getMessage());
            }

            // Finish building the UI
            buildTransportControls();
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

            int color = fetchColor(MainActivity.this, R.attr.colorAccent);
            playBtn.setEnabled(true);
            playBtn.setColorFilter(color);

            playBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int state = MediaControllerCompat.getMediaController(MainActivity.this).getPlaybackState().getState();
                    if (state == PlaybackStateCompat.STATE_PLAYING) {
                        MediaControllerCompat.getMediaController(MainActivity.this).getTransportControls().pause();
                    } else {
                        MediaControllerCompat.getMediaController(MainActivity.this).getTransportControls().play();
                    }
                    Log.v("state", String.valueOf(MediaControllerCompat.getMediaController(MainActivity.this).getPlaybackState().getState()));
                }
            });

            MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(MainActivity.this);

            mediaController.registerCallback(controllerCallback);
        }
    };

    private ServiceConnection audioConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            // On créé un intent pour les notifications
            final Intent notificationIntent = new Intent(MainActivity.this, MainActivity.class);
            final PendingIntent pendingIntent = PendingIntent.getActivity(MainActivity.this, 0, notificationIntent, 0);

            final ImageButton playBtn = (ImageButton)findViewById(R.id.play);
            final ImageButton rewBtn = (ImageButton)findViewById(R.id.rew);
            final ImageButton fwdBtn = (ImageButton)findViewById(R.id.fwd);

            final TextView positionLabel = (TextView)findViewById(R.id.position);
            final TextView durationLabel = (TextView)findViewById(R.id.duration);
            final ProgressBar progressBar = (ProgressBar)findViewById(R.id.progressBar);

            final AudioService.AudioBinder audioBinder = (AudioService.AudioBinder)iBinder;
            audioService = audioBinder.getService();
            audioService.setMediaSignalListener(new MediaSignal() {
                @Override
                public void onTrackRead(final boolean last) {
                    try {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                MediaDAO dao = new MediaDAO(MainActivity.this);
                                dao.open();
                                SQLiteCursor cursor = dao.getAllOrdered();
                                ListView listView = (ListView)findViewById(R.id.playlist);
                                TrackCursorAdapter adapter = (TrackCursorAdapter) listView.getAdapter();
                                adapter.changeCursor(cursor);
                                dao.close();
                                if (last) {
                                    playBtn.setImageResource(R.drawable.ic_action_play);
                                    rewBtn.setEnabled(false);
                                    rewBtn.setColorFilter(Color.GRAY);
                                    fwdBtn.setEnabled(false);
                                    fwdBtn.setColorFilter(Color.GRAY);
                                    positionLabel.setText("");
                                    durationLabel.setText("");
                                    progressBar.setProgress(0);
                                    progressBar.setMax(0);
                                    int color = fetchColor(MainActivity.this, R.attr.colorAccent);
                                    infoMsg(getString(R.string.info_play_end), color);
                                }
                            }
                        });
                    }
                    catch (Exception e) {
                        Log.v("Exception", e.getMessage());
                    }
                }

                @Override
                public void onTrackResume(boolean allowed, boolean changeFocus) {
                    if (allowed) {
                        clickPlayButton(changeFocus);
                    }
                }

                @Override
                public void onTrackSelect(int id, int duration, int total, int totalRead) {
                    // Libellé de position et durée
                    positionLabel.setText(dateFormat.format(new Date(0)));
                    durationLabel.setText(dateFormat.format(new Date(duration)));

                    // Barre de progression
                    progressBar.setProgress(0);
                    progressBar.setMax(duration);

                    // Titre en cours
                    String label = audioService.getTrackLabel(id);
                    int color = fetchColor(MainActivity.this, R.attr.colorPrimaryDark);
                    infoMsg(label, color);

                    // On notifie le premier-plan
                    Notification notification = new NotificationCompat.Builder(MainActivity.this)
                        .setContentTitle(label)
                        .setContentText(audioService.getSummary(total, totalRead))
                        .setSmallIcon(R.drawable.ic_stat_audio)
                        .setContentIntent(pendingIntent)
                        .build();
                    audioService.startForeground(AudioService.ONGOING_NOTIFICATION_ID, notification);
                }

                @Override
                public void onTrackProgress(final int position) {
                    try {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                positionLabel.setText(dateFormat.format(new Date(position)));
                                progressBar.setProgress(position);
                            }
                        });
                    }
                    catch (Exception e) {
                        Log.v("Exception", e.getMessage());
                    }
                }
            });
            audioService.setBound(true);

            if (audioService.playerIsActive()) {
                positionLabel.setText(dateFormat.format(new Date(audioService.playerPosition())));
                durationLabel.setText(dateFormat.format(new Date(audioService.playerDuration())));
                int color = fetchColor(MainActivity.this, R.attr.colorPrimaryDark);
                infoMsg(audioService.getCurrentTrackLabel(), color);
            }

            int color = fetchColor(MainActivity.this, R.attr.colorAccent);
            playBtn.setEnabled(true);
            playBtn.setColorFilter(color);
            rewBtn.setEnabled(audioService.playerIsPlaying());
            rewBtn.setColorFilter(audioService.playerIsPlaying() == false ? Color.GRAY : color);
            fwdBtn.setEnabled(audioService.playerIsPlaying());
            fwdBtn.setColorFilter(audioService.playerIsPlaying() == false ? Color.GRAY : color);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            audioService.setBound(false);
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
                    init(this, 1);
                }
                else {
                    // Permission denied, display info
                    setContentView(R.layout.playlist);
                    init(this, 0);
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
        init(this, 1);

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

        if (MediaControllerCompat.getMediaController(MainActivity.this) != null) {
            MediaControllerCompat.getMediaController(MainActivity.this).unregisterCallback(controllerCallback);
        }

        mediaBrowser.disconnect();
    }

    protected void clickPlayButton(boolean changeFocus) {
        ImageButton playBtn = (ImageButton)findViewById(R.id.play);
        ImageButton rewBtn = (ImageButton)findViewById(R.id.rew);
        ImageButton fwdBtn = (ImageButton)findViewById(R.id.fwd);

        try {
            boolean playing = false;
            if (audioService != null) {
                audioService.resume(changeFocus);
                playing = audioService.playerIsPlaying();
            }
            int color = fetchColor(this, R.attr.colorAccent);
            rewBtn.setEnabled(playing);
            rewBtn.setColorFilter(playing == true ? color :  Color.GRAY);
            fwdBtn.setEnabled(playing);
            fwdBtn.setColorFilter(playing == true ? color : Color.GRAY);
            playBtn.setImageResource(playing == true ? R.drawable.ic_action_pause : R.drawable.ic_action_play);
        }
        catch (Exception e) {
            playBtn.setEnabled(false);
            rewBtn.setEnabled(false);
            fwdBtn.setEnabled(false);
            infoMsg(e.getMessage(), Color.RED);
        }
    }

    protected void init(final Context context, int perms) {
        // On récup les éléments de l'UI
        final ListView listView = (ListView)findViewById(R.id.playlist);
        final ImageButton playBtn = (ImageButton)findViewById(R.id.play);
        final ImageButton rewBtn = (ImageButton)findViewById(R.id.rew);
        final ImageButton fwdBtn = (ImageButton)findViewById(R.id.fwd);
        Switch modeBtn = (Switch)findViewById(R.id.mode);

        LinearLayout controlLayout = (LinearLayout)findViewById(R.id.control);
        controlLayout.setHorizontalGravity(1);
        playBtn.setEnabled(false);
        playBtn.setColorFilter(Color.GRAY);
        rewBtn.setEnabled(false);
        rewBtn.setColorFilter(Color.GRAY);
        fwdBtn.setEnabled(false);
        fwdBtn.setColorFilter(Color.GRAY);

        try {
            if (perms == 1) {
                int color = fetchColor(this, R.attr.colorAccent);
                infoMsg(getString(R.string.info_scanning), color);

                Intent intent = new Intent(this, DbService.class);
                bindService(intent, connection, Context.BIND_AUTO_CREATE);

                MediaDAO dao = new MediaDAO(context);
                dao.open();
                SQLiteCursor cursor = dao.getAllOrdered();

                // Cursor adapter pour la listeView
                String[] fromColumns = {"track_nb", "title", "album", "artist"};
                int[] toViews = {R.id.track_nb, R.id.title, R.id.album, R.id.artist};
                TrackCursorAdapter adapter = new TrackCursorAdapter(context, R.layout.track, cursor, fromColumns, toViews);
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

        // On se connecte au service audio
        //final Intent intent = new Intent(this, AudioService.class);
        //bindService(intent, audioConnection, Context.BIND_AUTO_CREATE);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // On créé un intent pour les notifications
        final Intent notificationIntent = new Intent(MainActivity.this, MainActivity.class);
        final PendingIntent pendingIntent = PendingIntent.getActivity(MainActivity.this, 0, notificationIntent, 0);

        // On traite le changement d'état du bouton play
       /* playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clickPlayButton(true);
            }
        });*/

        // On traite le changement d'état du bouton en arrière
        rewBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    if (audioService != null) {
                        audioService.rewind();
                    }
                }
                catch (Exception e) {
                    playBtn.setEnabled(false);
                    rewBtn.setEnabled(false);
                    fwdBtn.setEnabled(false);
                    infoMsg(e.getMessage(), Color.RED);
                }
            }
        });

        // On traite le changement d'état du bouton en avant
        fwdBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    if (audioService != null) {
                        audioService.forward();
                    }
                }
                catch (Exception e) {
                    playBtn.setEnabled(false);
                    rewBtn.setEnabled(false);
                    fwdBtn.setEnabled(false);
                    infoMsg(e.getMessage(), Color.RED);
                }
            }
        });

        // Changement du mode aléatoire
        modeBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                if (audioService != null) {
                    audioService.setMode(b == true ? AudioService.MODE_ALBUM : AudioService.MODE_TRACK);
                }
            }
        });

        // On mémorise la position du scroll
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView absListView, int i) {
            }

            @Override
            public void onScroll(AbsListView absListView, int i, int i1, int i2) {
                scrollY = i;
            }
        });

        // Dialogue d'édition du flag pour une piste
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                SingleTrackDialogFragment dialog = new SingleTrackDialogFragment();
                dialog.setId((int)id);
                dialog.setLabel(audioService.getTrackLabel((int)id));
                dialog.show(getSupportFragmentManager(), "singleTrackFlagEditor");
            }
        });

        // Dialogue d'édition du flag de toutes les pistes
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                AllTracksDialogFragment dialog = new AllTracksDialogFragment();
                dialog.show(getSupportFragmentManager(), "allTrackFlagEditor");
                return true;
            }
        });

        // Sélection de la piste en cours
        TextView trackInfo = (TextView)findViewById(R.id.infoMsg);
        trackInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int currentId = audioService.getCurrentId();
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
        TextView infoMsg = (TextView)findViewById(R.id.infoMsg);
        infoMsg.setTextColor(color);
        infoMsg.setText(msg);
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        bundle.putBoolean("isPlaying", audioService != null ? audioService.playerIsPlaying() : false);
        bundle.putInt("currentPosition", audioService != null ? audioService.playerPosition() : 0);
        bundle.putInt("duration", audioService != null ? audioService.playerDuration() : 0);
        bundle.putInt("mode", audioService != null ? audioService.getMode() : AudioService.MODE_TRACK);
        bundle.putInt("scrollY", scrollY);

        super.onSaveInstanceState(bundle);
    }

    @Override
    public void onRestoreInstanceState(Bundle bundle) {
        boolean isPlaying = bundle.getBoolean("isPlaying");
        int currentPosition = bundle.getInt("currentPosition");
        int duration = bundle.getInt("duration");
        int mode = bundle.getInt("mode");
        scrollY = bundle.getInt("scrollY");

        super.onRestoreInstanceState(bundle);

        ListView listView = (ListView)findViewById(R.id.playlist);
        if (listView != null) {
            listView.setSelection(scrollY);
        }

        ProgressBar progressBar = (ProgressBar)findViewById(R.id.progressBar);
        if (progressBar != null) {
            progressBar.setMax(duration);
            progressBar.setProgress(currentPosition);
        }

        ImageButton playBtn = (ImageButton)findViewById(R.id.play);
        if (playBtn != null) {
            if (isPlaying) {
                playBtn.setImageResource(R.drawable.ic_action_pause);
            }
        }

        Switch modeBtn = (Switch)findViewById(R.id.mode);
        if (modeBtn != null) {
            modeBtn.setChecked(mode == AudioService.MODE_ALBUM);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (dbService != null && dbService.isBound()) {
            unbindService(connection);
        }
    }
}
