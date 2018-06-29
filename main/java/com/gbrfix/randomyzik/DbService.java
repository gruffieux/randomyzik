package com.gbrfix.randomyzik;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.annotation.Nullable;

import java.util.ArrayList;

/**
 * Created by gab on 27.08.2017.
 */

public class DbService extends IntentService {
    public final static Uri MEDIA_URI = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
    private final IBinder binder = new DbBinder();
    private DbSignal dbSignalListener;
    private ContentResolver contentResolver;
    private ContentObserver mediaObserver;
    private boolean bound;

    public boolean isBound() {
        return bound;
    }

    public void setBound(boolean bound) {
        this.bound = bound;
    }

    public DbService() {
        super("DbIntentService");
    }

    public void setDbSignalListener(DbSignal listener) {
        dbSignalListener = listener;
    }

    // Création de la liste de lecture sous forme de base de données SQLite avec une table medias contenant le chemin du fichier et un flag read/unread.
    // Si la liste n'existe pas, la créer en y ajoutant tous les fichiers du dossier Music.
    // Sinon vérifier que chaque fichier de la liste est toujours présent dans le dossier Music, le supprimer si ce n'est pas le cas, puis ajouter les fichiers pas encore présents dans la liste.
    private void scan() {
        dbSignalListener.onScanStart();

        boolean updated = false;
        MediaDAO dao = new MediaDAO(this);
        dao.open();
        SQLiteCursor cursor = dao.getAll();
        ArrayList<Media> list = new ArrayList<Media>();
        Cursor c = contentResolver.query(MEDIA_URI,
                new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.IS_MUSIC},
                null, null, null);

        if (c.moveToFirst()) {
            do {
                if (c.getInt(6) != 0) {
                    Media media = new Media();
                    media.setMediaId(c.getInt(0));
                    media.setPath(c.getString(1));
                    media.setFlag("unread");
                    media.setTrackNb(c.getString(2));
                    media.setTitle(c.getString(3));
                    media.setAlbum(c.getString(4));
                    media.setArtist(c.getString(5));
                    list.add(media);
                }
            } while (c.moveToNext());
        }

        if (cursor.getCount() == 0) {
            for (int i = 0; i < list.size(); i++) {
                dao.insert(list.get(i));
            }
        } else {
            while (cursor.moveToNext()) {
                int id = cursor.getInt(0);
                String path = cursor.getString(1);
                int media_id = cursor.getInt(2);
                int i;
                for (i = 0; i < list.size(); i++) {
                    boolean found = list.get(i).getMediaId() != 0 ? list.get(i).getMediaId() == media_id : list.get(i).getPath().equals(path);
                    if (found) {
                        dao.update(list.get(i), id);
                        updated = true;
                        break;
                    }
                }
                if (i >= list.size()) {
                    dao.remove(id);
                    updated = true;
                }
                else {
                    list.remove(i);
                }
            }
            for (int i = 0; i < list.size(); i++) {
                dao.insert(list.get(i));
                updated = true;
            }
        }

        dao.close();

        dbSignalListener.onScanCompleted(updated);
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
        contentResolver = getContentResolver();

        mediaObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
               super.onChange(selfChange);
               scan();
            }
        };

        scan();
        contentResolver.registerContentObserver(MEDIA_URI, true, mediaObserver);
    }
}
