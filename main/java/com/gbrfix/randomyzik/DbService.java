package com.gbrfix.randomyzik;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.database.SQLException;
import android.database.sqlite.SQLiteCursor;
import android.database.sqlite.SQLiteException;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.File;

/**
 * Created by gab on 27.08.2017.
 */

public class DbService extends IntentService {
    public DbService() {
        super("DbIntentService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Context context = this;

        // Création de la liste de lecture sous forme de base de données SQLite avec une table medias contenant le chemin du fichier et un flag read/unread.
        // Si la liste n'existe pas, la créer en y ajoutant tous les fichiers du dossier Music.
        // Sinon vérifier que chaque fichier de la liste est toujours présent dans le dossier Music, le supprimer si ce n'est pas le cas, puis ajouter les fichiers pas encore présents dans la liste.

        File file = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        final File[] files = file.listFiles();
        String[] flags = new String[files.length];
        final long[] ids = new long[files.length];
        MediaDAO dao = new MediaDAO(context);
        MediaFactory factory = new MediaFactory();

        try {
            dao.open();
            SQLiteCursor cursor = dao.getAll();
            if (cursor.getCount() == 0) {
                for (int i = 0; i < files.length; i++) {
                    if (files[i].isFile()) {
                        Media media = factory.createMedia(files[i].getPath());
                        ids[i] = dao.insert(media);
                        flags[i] = media.getFlag();
                    }
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
                    } else {
                        flags[i] = flag;
                        ids[i] = id;
                    }
                }
                for (int i = 0; i < files.length; i++) {
                    if (files[i].isFile()) {
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
        catch (Exception e) {
            Log.v("Exception", e.getMessage());
        }
    }
}
