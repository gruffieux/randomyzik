package com.gbrfix.randomyzik;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteCursor;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.util.Log;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;

/**
 * Created by gab on 27.08.2017.
 */

public class DbService extends IntentService implements FilenameFilter {
    private ArrayList<File> mediaFiles;
    public DbService() {
        super("DbIntentService");

        mediaFiles = new ArrayList<File>();
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Context context = this;

        // Création de la liste de lecture sous forme de base de données SQLite avec une table medias contenant le chemin du fichier et un flag read/unread.
        // Si la liste n'existe pas, la créer en y ajoutant tous les fichiers du dossier Music.
        // Sinon vérifier que chaque fichier de la liste est toujours présent dans le dossier Music, le supprimer si ce n'est pas le cas, puis ajouter les fichiers pas encore présents dans la liste.

        File musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        scanMediaFiles(musicDir, true);
        //final File[] files = file.listFiles(this);
        String[] flags = new String[mediaFiles.size()];
        final long[] ids = new long[mediaFiles.size()];
        MediaDAO dao = new MediaDAO(context);
        MediaFactory factory = new MediaFactory();

        try {
            dao.open();
            SQLiteCursor cursor = dao.getAll();
            if (cursor.getCount() == 0) {
                for (int i = 0; i < mediaFiles.size(); i++) {
                    Media media = factory.createMedia(mediaFiles.get(i).getPath());
                    ids[i] = dao.insert(media);
                    flags[i] = media.getFlag();
                }
            } else {
                while (cursor.moveToNext()) {
                    int id = cursor.getInt(0);
                    String path = cursor.getString(1);
                    String flag = cursor.getString(2);
                    int i = 0;
                    for (i = 0; i < mediaFiles.size(); i++) {
                        if (mediaFiles.get(i).getPath().equals(path)) {
                            break;
                        }
                    }
                    if (i >= mediaFiles.size()) {
                        dao.remove(id);
                    }
                    else {
                        flags[i] = flag;
                        ids[i] = id;
                        //mediaFiles.remove(i); // Nouveau fichiers pas detectés
                    }
                }
                for (int i = 0; i < mediaFiles.size(); i++) {
                    Media media = factory.createMedia(mediaFiles.get(i).getPath());
                    if (dao.getFromPath(mediaFiles.get(i).getPath()).getCount() == 0) {
                        ids[i] = dao.insert(media);
                        flags[i] = media.getFlag();
                    }
                }
            }
            dao.close();
        }
        catch (Exception e) {
            Log.v("Exception", e.getMessage());
        }
    }

    @Override
    public boolean accept(File file, String s) {
        int lastIndex = s.lastIndexOf('.');

        if (lastIndex > 0) {
            String ext = s.substring(lastIndex);
            if (ext.equals(".mp3")) {
                return true;
            }
        }

        return false;
    }

    private void scanMediaFiles(File dir, boolean root) {
        File[] files = dir.listFiles(root ? null : this);

        for (int i = 0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                scanMediaFiles(files[i], false);
            }
            else {
                mediaFiles.add(files[i]);
            }
        }
    }
}
