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
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ToggleButton;
import android.widget.CompoundButton;
import android.content.res.Configuration;
import android.util.Log;

public class MainActivity extends AppCompatActivity {
    final int MY_PERSMISSIONS_REQUEST_STORAGE = 1;
    DbService dbService = null;
    AudioService audioService = null;
    int scrollY = 0;
    int totalTrack = 0;
    int totalReadTrack = 0;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            // Service de scanning
            DbService.DbBinder binder = (DbService.DbBinder)iBinder;
            dbService = binder.getService();
            dbService.setDbSignalListener(new DbSignal() {
                @Override
                public void onUpdateEntries() {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                MediaDAO dao = new MediaDAO(dbService.getApplicationContext());
                                dao.open();
                                SQLiteCursor cursor = dao.getAllOrdered();
                                ListView listView = (ListView)findViewById(R.id.playlist);
                                TrackCursorAdapter adapter = (TrackCursorAdapter) listView.getAdapter();
                                adapter.changeCursor(cursor);
                                dao.close();
                            } catch (SQLException e) {
                                Log.v("SQLException", e.getMessage());
                            }
                        }
                    });
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
                                    ToggleButton playBtn = (ToggleButton)findViewById(R.id.play);
                                    Button rewBtn = (Button)findViewById(R.id.rew);
                                    Button fwdBtn = (Button)findViewById(R.id.fwd);
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
                    ToggleButton playBtn = (ToggleButton)findViewById(R.id.play);
                    playBtn.setChecked(start);
                }

                @Override
                public void onTrackSelect(int id, int duration, int total, int totalRead) {
                    ProgressBar progressBar = (ProgressBar)findViewById(R.id.progressBar);
                    progressBar.setProgress(0);
                    progressBar.setMax(duration);
                    String label = audioService.getTrackLabel(id);
                    infoMsg(label, Color.BLACK);

                    // On notifie le premier-plan
                    totalTrack = total;
                    totalReadTrack = totalRead;
                    Notification notification = new NotificationCompat.Builder(audioService.getApplicationContext())
                        .setContentTitle(label)
                        .setContentText(audioService.getSummary(totalTrack, totalReadTrack))
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

            TextView infoMsg = (TextView) findViewById(R.id.infoMsg);
            if (infoMsg != null && infoMsg.getText().length() == 0) {
                infoMsg.setText(audioService.getCurrentTrackLabel());
            }
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

    protected void init(final Context context, int perms) {
        // On récup les éléments de l'UI
        final ListView listView = (ListView)findViewById(R.id.playlist);
        final ToggleButton playBtn = (ToggleButton)findViewById(R.id.play);
        final Button rewBtn = (Button)findViewById(R.id.rew);
        final Button fwdBtn = (Button)findViewById(R.id.fwd);
        LinearLayout controlLayout = (LinearLayout)findViewById(R.id.control);
        controlLayout.setHorizontalGravity(1);

        // Création de la liste de lecture sous forme de base de données SQLite avec une table medias contenant le chemin du fichier et un flag read/unread.
        // Si la liste n'existe pas, la créer en y ajoutant tous les fichiers du dossier Music.
        // Sinon vérifier que chaque fichier de la liste est toujours présent dans le dossier Music, le supprimer si ce n'est pas le cas, puis ajouter les fichiers pas encore présents dans la liste.
        try {
            if (perms == 1) {
                Intent intent = new Intent(this, DbService.class);
                bindService(intent, connection, Context.BIND_AUTO_CREATE);

                MediaDAO dao = new MediaDAO(context);
                dao.open();
                SQLiteCursor cursor = dao.getAllOrdered();

                // Cursor adapter pour la listeView
                if (cursor.getCount() > 0) {
                    String[] fromColumns = {"track_nb", "title", "album", "artist"};
                    int[] toViews = {R.id.track_nb, R.id.title, R.id.album, R.id.artist};
                    TrackCursorAdapter adapter = new TrackCursorAdapter(context, R.layout.track, cursor, fromColumns, toViews);
                    listView.setAdapter(adapter);
                } else {
                    throw new Exception(getString(R.string.err_no_file));
                }

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
        playBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                try {
                    rewBtn.setEnabled(isChecked);
                    fwdBtn.setEnabled(isChecked);
                    if (audioService != null) {
                        audioService.resume();
                        if (isChecked) {
                            // On notifie le premier-plan
                            Notification notification = new NotificationCompat.Builder(audioService.getApplicationContext())
                                .setContentTitle(audioService.getCurrentTrackLabel())
                                .setContentText(audioService.getSummary(totalTrack, totalReadTrack))
                                .setSmallIcon(R.drawable.ic_stat_audio)
                                .setContentIntent(pendingIntent)
                                .build();
                            audioService.startForeground(AudioService.ONGOING_NOTIFICATION_ID, notification);
                        }
                        else {
                            audioService.stopForeground(true);
                        }
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
            setContentView(R.layout.playlist);
        }
        else if(config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setContentView(R.layout.playlist);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        if (audioService != null && audioService.getPlayer() != null){
            bundle.putBoolean("isPlaying", audioService.getPlayer().isPlaying());
            bundle.putInt("currentPosition", audioService.getPlayer().getCurrentPosition());
            bundle.putInt("duration", audioService.getPlayer().getDuration());
        }

        bundle.putInt("scrollY", scrollY);

        super.onSaveInstanceState(bundle);
    }

    @Override
    public void onRestoreInstanceState(Bundle bundle) {
        boolean isPlaying = bundle.getBoolean("isPlaying");
        int currentPosition = bundle.getInt("currentPosition");
        int duration = bundle.getInt("duration");
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

        ToggleButton playBtn = (ToggleButton)findViewById(R.id.play);
        if (playBtn != null) {
            playBtn.setChecked(isPlaying);
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
