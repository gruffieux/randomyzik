package com.gbrfix.randomizik;

import android.content.Context;
import android.database.SQLException;
import android.database.sqlite.SQLiteCursor;
import android.graphics.Color;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ToggleButton;
import android.widget.CompoundButton;
import android.content.res.Configuration;
import android.util.Log;
import java.io.File;

/*
TODO V1:
- Demande permission sotckage interactive

TODO V2
- ListView avec ListAdapter pour affichier les titres, artistes, albums
- Morceau en cours de lecture avec barre de progression
*/

public class MainActivity extends AppCompatActivity {
    public boolean configChanged = false;
    public MediaController controller = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Context context = this;

        // On récupère les fichiers musicaux
        File file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        final File[] files = file.listFiles();
        String[] flags = new String[files.length];
        final long[] ids = new long[files.length];

        // Création de la liste de lecture sous forme de base de données SQLite avec une table medias contenant le chemin du fichier et un flag read/unread.
        // Si la liste n'existe pas, la créer en y ajoutant tous les fichiers du dossier Music.
        // Sinon vérifier que chaque fichier de la liste est toujours présent dans le dossier Music, le supprimer si ce n'est pas le cas, puis ajouter les fichiers pas encore présents dans la liste.
        try {
            MediaDAO dao = new MediaDAO(context);
            dao.open();
            SQLiteCursor cursor = dao.getAll();
            if (!dao.getDb().isReadOnly()) {
                if (cursor.getCount() == 0) {
                    for (int i = 0; i < files.length; i++) {
                        ids[i] = dao.insert(files[i].getPath(), "unread");
                        flags[i] = "unread";
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
                        if (dao.getFromPath(files[i].getPath()).getCount() == 0) {
                            ids[i] = dao.insert(files[i].getPath(), "unread");
                            flags[i] = "unread";
                        }
                    }
                }
            }
            dao.close();
        }
        catch (SQLException e) {
            Log.v("SQLException", e.getMessage());
        }

        // Layout principale
        LinearLayout mainLayout = new LinearLayout(context);
        mainLayout.setOrientation(LinearLayout.VERTICAL);

        // Vue pour la liste des chansons
        ScrollView listView = new ScrollView(context);
        listView.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        // Layout pour la liste des chansons
        final LinearLayout listLayout = new LinearLayout(context);
        listLayout.setOrientation(LinearLayout.VERTICAL);

        // On créé une vue pour chaque fichier
        for (int i = 0; i < files.length; i++) {
            TextView songView = new TextView(context);
            songView.setText(files[i].getName());
            if (flags[i].equals("read")) {
                songView.setTextColor(Color.RED);
            }
            listLayout.addView(songView);
        }

        // On ajoute la vue au layout pour la liste de chansons
        listView.addView(listLayout);
        mainLayout.addView(listView);

        // On créé un layout avec les boutons de contrôle
        LinearLayout controlLayout = new LinearLayout(context);
        controlLayout.setOrientation(LinearLayout.HORIZONTAL);
        controlLayout.setHorizontalGravity(1);
        final ToggleButton playBtn = new ToggleButton(context);
        playBtn.setTextOff("Play");
        playBtn.setTextOn("Pause");
        playBtn.setChecked(false);
        Button rewBtn = new Button(context);
        rewBtn.setText("<");
        Button fwdBtn = new Button(context);
        fwdBtn.setText(">");
        controlLayout.addView(rewBtn);
        controlLayout.addView(playBtn);
        controlLayout.addView(fwdBtn);
        mainLayout.addView(controlLayout);

        // On instancie le contrôleur
        controller = new MediaController(context);

        // On traite le changement d'état du bouton play
        playBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                controller.resume();
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

        // On met à jour la liste
        controller.setUpdateSignalListener(new UpdateSignal() {

            @Override
            public void onTrackReaden(int id) {
                for (int i = 0; i < files.length; i++) {
                    if (ids[i] == id) {
                        TextView songView = (TextView)listLayout.getChildAt(i);
                        songView.setTextColor(Color.RED);
                        break;
                    }
                }
            }

            @Override
            public void onTrackResume(boolean start) {
                playBtn.setChecked(start);
            }
        });

        setContentView(mainLayout);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        Configuration config=getResources().getConfiguration();
        if(config.orientation == Configuration.ORIENTATION_PORTRAIT) {
            //setContentView(R.layout.activity_main);
        }
        else if(config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            //setContentView(R.layout.activity_main);
        }

        configChanged = true;
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (!configChanged) {
            controller.destroy();
        }

        configChanged = false;
    }
}
