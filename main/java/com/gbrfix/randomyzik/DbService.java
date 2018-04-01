package com.gbrfix.randomyzik;

import android.app.IntentService;
import android.content.Intent;
import android.database.sqlite.SQLiteCursor;
import android.os.Binder;
import android.os.Environment;
import android.os.FileObserver;
import android.os.IBinder;
import android.support.annotation.Nullable;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;

/**
 * Created by gab on 27.08.2017.
 */

public class DbService extends IntentService implements FilenameFilter {
    private final IBinder binder = new DbBinder();
    private DbSignal dbSignalListener;
    private ArrayList<File> mediaFiles;
    private RecursiveFileObserver mediaObserver;
    File mediaDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
    private boolean bound;

    public boolean isBound() {
        return bound;
    }

    public void setBound(boolean bound) {
        this.bound = bound;
    }

    public DbService() {
        super("DbIntentService");

        mediaFiles = new ArrayList<File>();
    }

    public void start() {
        mediaObserver = new RecursiveFileObserver(mediaDir.getPath()) {
            @Override
            public void onEvent(int event, String path) {
                switch (event) {
                    //case FileObserver.ACCESS:           // 1: Data was read from a file
                    //case FileObserver.MODIFY:           // 2: Data was written to a file
                    //case FileObserver.ATTRIB:           // 4: Metadata (permissions, owner, timestamp) was changed explicitly
                    case FileObserver.CLOSE_WRITE:        // 8: Someone had a file or directory open for writing, and closed it
                    //case FileObserver.CLOSE_NOWRITE:    // 16: Someone had a file or directory open read-only, and closed it
                    //case FileObserver.OPEN:             // 32: A file or directory was opened
                    case FileObserver.MOVED_FROM:         // 64: A file or subdirectory was moved from the monitored directory
                    case FileObserver.MOVED_TO:           // 128: A file or subdirectory was moved to the monitored directory
                    //case FileObserver.CREATE:           // 256: A new file or subdirectory was created under the monitored directory
                    case FileObserver.DELETE:             // 512: A file was deleted from the monitored directory
                    case FileObserver.DELETE_SELF:        // 1024: The monitored file or directory was deleted; monitoring effectively stops
                    case FileObserver.MOVE_SELF:          // 2048: The monitored file or directory was moved; monitoring continues
                        scan();
                        break;
                    default:
                }
            }
        };

        scan();
        mediaObserver.startWatching();
    }

    public void setDbSignalListener(DbSignal listener) {
        dbSignalListener = listener;
    }

    // Création de la liste de lecture sous forme de base de données SQLite avec une table medias contenant le chemin du fichier et un flag read/unread.
    // Si la liste n'existe pas, la créer en y ajoutant tous les fichiers du dossier Music.
    // Sinon vérifier que chaque fichier de la liste est toujours présent dans le dossier Music, le supprimer si ce n'est pas le cas, puis ajouter les fichiers pas encore présents dans la liste.
    private void scan() {
        mediaObserver.stopWatching();
        scanMediaFiles(mediaDir);
        boolean updated = false;
        MediaFactory factory = new MediaFactory();
        MediaDAO dao = new MediaDAO(this);
        dao.open();
        SQLiteCursor cursor = dao.getAll();

        if (cursor.getCount() == 0) {
            for (int i = 0; i < mediaFiles.size(); i++) {
                Media media = factory.createMedia(mediaFiles.get(i).getPath());
                if (media != null) {
                    dao.insert(media);
                    updated = true;
                }
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
                if (media != null && dao.getFromPath(mediaFiles.get(i).getPath()).getCount() == 0) {
                    dao.insert(media);
                    updated = true;
                }
            }
        }

        dao.close();
        dbSignalListener.onScanCompleted(updated);
        mediaObserver.startWatching();
    }

    @Override
    public boolean accept(File file, String s) {
        int lastIndex = s.lastIndexOf('.');

        if (lastIndex > 0) {
            String ext = s.substring(lastIndex);
            if (ext.matches("\\.mp3|\\.flac|\\.ogg")) {
                return true;
            }
        }

        return false;
    }

    private void scanMediaFiles(File dir) {
        File[] files = dir.listFiles();

        if (files == null) {
            return;
        }

        for (int i = 0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                scanMediaFiles(files[i]);
            } else if (files[i].isFile() && accept(files[i], files[i].getName())) {
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
        start();
    }
}
