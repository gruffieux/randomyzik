package com.gbrfix.randomyzik;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteCursor;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;

/**
 * Created by gab on 27.08.2017.
 */

public class DbService extends IntentService implements FilenameFilter {
    private final IBinder binder = new DbBinder();
    private DbServiceSignal dbServiceListener;
    private ArrayList<File> mediaFiles;
    private boolean bound, locked;

    public boolean isBound() {
        return bound;
    }

    public void setBound(boolean bound) {
        this.bound = bound;
    }

    public boolean isLocked() {
        return locked;
    }

    public DbService() {
        super("DbIntentService");

        mediaFiles = new ArrayList<File>();
        bound = locked = false;

    }

    public void setDbServiceListener(DbServiceSignal listener) {
        dbServiceListener = listener;
    }

    public void scan() {
        locked = true;
        Context context = this;

        // Création de la liste de lecture sous forme de base de données SQLite avec une table medias contenant le chemin du fichier et un flag read/unread.
        // Si la liste n'existe pas, la créer en y ajoutant tous les fichiers du dossier Music.
        // Sinon vérifier que chaque fichier de la liste est toujours présent dans le dossier Music, le supprimer si ce n'est pas le cas, puis ajouter les fichiers pas encore présents dans la liste.

        File mediaDir = Environment.getExternalStorageDirectory();
        scanMediaFiles(mediaDir);
        MediaDAO dao = new MediaDAO(context);
        MediaFactory factory = new MediaFactory();

        try {
            boolean updated = false;
            dao.open();
            SQLiteCursor cursor = dao.getAll();
            if (cursor.getCount() == 0) {
                for (int i = 0; i < mediaFiles.size(); i++) {
                    Media media = factory.createMedia(mediaFiles.get(i).getPath());
                    dao.insert(media);
                    updated = true;
                }
            } else {
                while (cursor.moveToNext()) {
                    int id = cursor.getInt(0);
                    String path = cursor.getString(1);
                    int i = 0;
                    for (i = 0; i < mediaFiles.size(); i++) {
                        if (mediaFiles.get(i).getPath().equals(path)) {
                            break;
                        }
                    }
                    if (i >= mediaFiles.size()) {
                        dao.remove(id);
                        updated = true;
                    }
                    else {
                        mediaFiles.remove(i);
                    }
                }
                for (int i = 0; i < mediaFiles.size(); i++) {
                    Media media = factory.createMedia(mediaFiles.get(i).getPath());
                    if (dao.getFromPath(mediaFiles.get(i).getPath()).getCount() == 0) {
                        dao.insert(media);
                        updated = true;
                    }
                }
            }
            dao.close();
            if (updated) {
                dbServiceListener.onUpdateEntries();
            }
        }
        catch (Exception e) {
            Log.v("Exception", e.getMessage());
        }
        finally {
            locked = false;
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

    private void scanMediaFiles(File dir) {
        File[] files = dir.listFiles();

        for (int i = 0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                scanMediaFiles(files[i]);
            }
            else if (files[i].isFile() && accept(files[i], files[i].getName())) {
                mediaFiles.add(files[i]);
            }
        }
    }

    public class DbBinder extends Binder {
        DbService getService() {
            return DbService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        scan();
    }
}
