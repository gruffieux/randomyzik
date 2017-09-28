package com.gbrfix.randomyzik;

import android.Manifest;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.SQLException;
import android.database.sqlite.SQLiteCursor;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.AbsListView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.CompoundButton;
import android.content.res.Configuration;
import android.util.Log;

public class MainActivity extends AppCompatActivity {
    final int MY_PERSMISSIONS_REQUEST_STORAGE = 1;
    DbService dbService = null;
    AudioService audioService = null;
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
                                    MediaDAO dao = new MediaDAO(dbService.getApplicationContext());
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

    private ServiceConnection audioConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            // On créé un intent pour les notifications
            final Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);
            final PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, 0);

            final AudioService.AudioBinder audioBinder = (AudioService.AudioBinder)iBinder;
            audioService = audioBinder.getService();
            audioService.setMediaSignalListener(new MediaSignal() {
                @Override
                public void onTrackRead(final boolean last) {
                    try {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                MediaDAO dao = new MediaDAO(audioService.getApplicationContext());
                                dao.open();
                                SQLiteCursor cursor = dao.getAllOrdered();
                                ListView listView = (ListView)findViewById(R.id.playlist);
                                TrackCursorAdapter adapter = (TrackCursorAdapter) listView.getAdapter();
                                adapter.changeCursor(cursor);
                                dao.close();
                                if (last) {
                                    ImageButton playBtn = (ImageButton)findViewById(R.id.play);
                                    ImageButton rewBtn = (ImageButton)findViewById(R.id.rew);
                                    ImageButton fwdBtn = (ImageButton)findViewById(R.id.fwd);
                                    playBtn.setEnabled(false);
                                    rewBtn.setEnabled(false);
                                    fwdBtn.setEnabled(false);
                                    infoMsg(getString(R.string.info_play_end), Color.GRAY);
                                }
                            }
                        });
                    }
                    catch (Exception e) {
                        Log.v("Exception", e.getMessage());
                    }
                }

                @Override
                public void onTrackResume(boolean start) {
                    clickPlayButton(!start);
                }

                @Override
                public void onTrackSelect(int id, int duration, int total, int totalRead) {
                    ProgressBar progressBar = (ProgressBar)findViewById(R.id.progressBar);
                    progressBar.setProgress(0);
                    progressBar.setMax(duration);
                    String label = audioService.getTrackLabel(id);
                    infoMsg(label, Color.BLACK);

                    // On notifie le premier-plan
                    Notification notification = new NotificationCompat.Builder(audioService.getApplicationContext())
                        .setContentTitle(label)
                        .setContentText(audioService.getSummary(total, totalRead))
                        .setSmallIcon(R.drawable.ic_stat_audio)
                        .setContentIntent(pendingIntent)
                        .build();
                    audioService.startForeground(AudioService.ONGOING_NOTIFICATION_ID, notification);
                }

                @Override
                public void onTrackProgress(int position) {
                    ProgressBar progressBar = (ProgressBar)findViewById(R.id.progressBar);
                    progressBar.setProgress(position);
                }
            });
            audioService.setBound(true);

            if (audioService.playerIsActive()) {
                infoMsg(audioService.getCurrentTrackLabel(), Color.BLACK);
            }

            ImageButton rewBtn = (ImageButton)findViewById(R.id.rew);
            rewBtn.setEnabled(audioService.playerIsPlaying());
            rewBtn.setColorFilter(audioService.playerIsPlaying() == false ? Color.GRAY : 0);
            ImageButton fwdBtn = (ImageButton)findViewById(R.id.fwd);
            fwdBtn.setEnabled(audioService.playerIsPlaying());
            fwdBtn.setColorFilter(audioService.playerIsPlaying() == false ? Color.GRAY : 0);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            audioService.setBound(false);
        }
    };

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

        Context context = this;

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, MY_PERSMISSIONS_REQUEST_STORAGE);
            return;
        }

        setContentView(R.layout.playlist);
        init(context, 1);
    }

    protected void clickPlayButton(boolean pause) {
        ImageButton playBtn = (ImageButton)findViewById(R.id.play);
        ImageButton rewBtn = (ImageButton)findViewById(R.id.rew);
        ImageButton fwdBtn = (ImageButton)findViewById(R.id.fwd);

        try {
            if (audioService != null) {
                audioService.resume();
            }
            rewBtn.setEnabled(!pause);
            rewBtn.setColorFilter(pause == true ? Color.GRAY : 0);
            fwdBtn.setEnabled(!pause);
            fwdBtn.setColorFilter(pause == true ? Color.GRAY : 0);
            playBtn.setImageResource(pause == true ? R.drawable.ic_action_play : R.drawable.ic_action_pause);
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

        try {
            if (perms == 1) {
                infoMsg(getString(R.string.info_scanning), Color.GRAY);

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
        final Intent intent = new Intent(this, AudioService.class);
        bindService(intent, audioConnection, Context.BIND_AUTO_CREATE);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);

        // On créé un intent pour les notifications
        final Intent notificationIntent = new Intent(getApplicationContext(), MainActivity.class);
        final PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, 0);

        // On traite le changement d'état du bouton play
        playBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                clickPlayButton(audioService.playerIsPlaying());
            }
        });

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
    }

    public void infoMsg(String msg, int color) {
        TextView infoMsg = (TextView)findViewById(R.id.infoMsg);
        infoMsg.setTextColor(color);
        infoMsg.setText(msg);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // Force l'UI à l'état initial
        Configuration config=getResources().getConfiguration();
        if(config.orientation == Configuration.ORIENTATION_PORTRAIT) {
            //setContentView(R.layout.playlist); // Provoque des plantées
        }
        else if(config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            //setContentView(R.layout.playlist); // Provoque des plantées
        }
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
