package com.gbrfix.randomizik;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.SQLException;
import android.database.sqlite.SQLiteCursor;
import android.graphics.Color;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ToggleButton;
import android.widget.CompoundButton;
import android.content.res.Configuration;
import android.util.Log;
import java.io.File;

/*
TODO
- BUG: Restore inoportun en mode paysage lorsque écran éteint
*/

public class MainActivity extends AppCompatActivity {
    public final int MY_PERSMISSIONS_REQUEST_STORAGE = 1;
    public MediaController controller = null;

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERSMISSIONS_REQUEST_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permission was granted, app can run
                    setContentView(R.layout.playlist);
                    init(this);
                }
                else {
                    // Permission denied, display info
                    TextView infoMsg = new TextView(this);
                    infoMsg.setText("Sorry, application needs read/write storage permissions to run.");
                    infoMsg.setTextColor(Color.RED);
                    setContentView(infoMsg);
                }
                return;
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

        init(context);
    }

    protected void init(final Context context) {
        // On récupère les fichiers musicaux
        File file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        final File[] files = file.listFiles();
        String[] flags = new String[files.length];
        final long[] ids = new long[files.length];
        final ListView listView = (ListView)findViewById(R.id.playlist);

        // Création de la liste de lecture sous forme de base de données SQLite avec une table medias contenant le chemin du fichier et un flag read/unread.
        // Si la liste n'existe pas, la créer en y ajoutant tous les fichiers du dossier Music.
        // Sinon vérifier que chaque fichier de la liste est toujours présent dans le dossier Music, le supprimer si ce n'est pas le cas, puis ajouter les fichiers pas encore présents dans la liste.
        try {
            MediaDAO dao = new MediaDAO(context);
            dao.open();
            SQLiteCursor cursor = dao.getAll();

            // Cursor adapter pour la listeView
            String[] fromColumns = {"track_nb", "title", "album", "artist"};
            int[] toViews = {R.id.track_nb, R.id.title, R.id.album, R.id.artist};
            TrackCursorAdapter adapter = new TrackCursorAdapter(context, R.layout.track, cursor, fromColumns, toViews);
            listView.setAdapter(adapter);

            if (!dao.getDb().isReadOnly()) {
                MediaFactory factory = new MediaFactory();
                if (cursor.getCount() == 0) {
                    for (int i = 0; i < files.length; i++) {
                        Media media = factory.createMedia(files[i].getPath());
                        ids[i] = dao.insert(media);
                        flags[i] = media.getFlag();
                    }
                } else {
                    while (cursor.moveToNext()) {
                        int id = cursor.getInt(0);
                        String path = cursor.getString(1);
                        String flag = cursor.getString(2);
                        int i = 0;
                        for (i = 0; i < files.length; i++) {
                            if (files[i].getPath().equals(path)) {
                                break;
                            }
                        }
                        if (i >= files.length) {
                            dao.remove(id);
                        }
                        else {
                            flags[i] = flag;
                            ids[i] = id;
                        }
                    }
                    for (int i = 0; i < files.length; i++) {
                        Media media = factory.createMedia(files[i].getPath());
                        if (dao.getFromPath(files[i].getPath()).getCount() == 0) {
                            ids[i] = dao.insert(media);
                            flags[i] = media.getFlag();
                        }
                    }
                }
            }
            dao.close();
        }
        catch (SQLException e) {
            Log.v("SQLException", e.getMessage());
        }
        catch (Exception e) {
            Log.v("Exception", e.getMessage());
        }

        // On instancie le contrôleur
        controller = new MediaController(context);

        // On récup les éléments de l'UI
        final ToggleButton playBtn = (ToggleButton)findViewById(R.id.play);
        final Button rewBtn = (Button)findViewById(R.id.rew);
        final Button fwdBtn = (Button)findViewById(R.id.fwd);
        LinearLayout controlLayout = (LinearLayout)findViewById(R.id.control);
        controlLayout.setHorizontalGravity(1);

        // On traite le changement d'état du bouton play
        playBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                controller.resume();
                rewBtn.setEnabled(isChecked);
                fwdBtn.setEnabled(isChecked);
                if (isChecked) {
                    new Thread(controller).start();
                }
            }
        });

        // On traite le changement d'état du bouton en arrière
        rewBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                controller.rewind();
            }
        });

        // On traite le changement d'état du bouton en avant
        fwdBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                controller.forward();
            }
        });

        // On met à jour l'UI
        controller.setUpdateSignalListener(new UpdateSignal() {

            @Override
            public void onTrackRead(boolean last) {
                try {
                    MediaDAO dao = new MediaDAO(context);
                    dao.open();
                    SQLiteCursor cursor = dao.getAll();
                    TrackCursorAdapter adapter = (TrackCursorAdapter) listView.getAdapter();
                    adapter.changeCursor(cursor);
                    dao.close();
                } catch (SQLException e) {
                    Log.v("SQLException", e.getMessage());
                }
            }

            @Override
            public void onTrackResume(boolean start) {
                playBtn.setChecked(start);
            }

            @Override
            public void onTrackSelect(int id, int duration) {
                ProgressBar progressBar = (ProgressBar)findViewById(R.id.progressBar);
                progressBar.setProgress(0);
                progressBar.setMax(duration);
                TextView textView = (TextView)findViewById(R.id.currentTrack);
                textView.setText(controller.getTrackLabel(id));
            }

            @Override
            public void onTrackProgress(int position) {
                ProgressBar progressBar = (ProgressBar)findViewById(R.id.progressBar);
                progressBar.setProgress(position);
            }
        });
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
        if (controller != null) {
            controller.savePlayer(bundle);
        }

        super.onSaveInstanceState(bundle);
    }

    @Override
    public void onRestoreInstanceState(Bundle bundle) {
        super.onRestoreInstanceState(bundle);

        boolean isPlaying = bundle.getBoolean("isPlaying");
        int currentId = bundle.getInt("currentId");
        int currentPosition = bundle.getInt("currentPosition");
        int duration = bundle.getInt("duration");

        ProgressBar progressBar = (ProgressBar)findViewById(R.id.progressBar);
        progressBar.setMax(duration);
        progressBar.setProgress(currentPosition);

        if (controller != null) {
            controller.restorePlayer(currentId, currentPosition);
            TextView currentTrack = (TextView)findViewById(R.id.currentTrack);
            currentTrack.setText(controller.getTrackLabel(currentId));
        }

        ToggleButton playBtn = (ToggleButton)findViewById(R.id.play);
        playBtn.setChecked(isPlaying);
    }

    protected void onPause() {
        super.onPause();
    }

    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (controller != null) {
            controller.destroy();
        }
    }
}
