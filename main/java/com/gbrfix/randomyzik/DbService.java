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

/**
 * Created by gab on 27.08.2017.
 */

public class DbService extends IntentService {
    private final IBinder binder = new DbBinder();
    private DbSignal dbSignalListener;
    private ContentResolver contentResolver;
    private ContentObserver mediaObserver;
    Uri mediaUri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
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

    public void start() {
        contentResolver = getContentResolver();

        mediaObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                contentResolver.unregisterContentObserver(this);
                scan();
                contentResolver.registerContentObserver(mediaUri, true, this);
            }
        };


        scan();
        contentResolver.registerContentObserver(mediaUri, true, mediaObserver);
    }

    public void setDbSignalListener(DbSignal listener) {
        dbSignalListener = listener;
    }

    // Création de la liste de lecture sous forme de base de données SQLite avec une table medias contenant le chemin du fichier et un flag read/unread.
    // Si la liste n'existe pas, la créer en y ajoutant tous les fichiers du dossier Music.
    // Sinon vérifier que chaque fichier de la liste est toujours présent dans le dossier Music, le supprimer si ce n'est pas le cas, puis ajouter les fichiers pas encore présents dans la liste.
    private void scan() {
        boolean updated = false;
        MediaDAO dao = new MediaDAO(this);
        dao.open();
        SQLiteCursor cursor = dao.getAll();

        if (cursor.getCount() == 0) {
            Cursor c = contentResolver.query(mediaUri, null, null, null, null);
            if (c.moveToFirst()) {
                int idColumn = c.getColumnIndex(MediaStore.Audio.Media._ID);
                int pathColumn = c.getColumnIndex(MediaStore.Audio.Media.DATA);
                int trackColumn = c.getColumnIndex(MediaStore.Audio.Media.TRACK);
                int titleColumn = c.getColumnIndex(MediaStore.Audio.Media.TITLE);
                int albumColumn = c.getColumnIndex(MediaStore.Audio.Media.ALBUM);
                int artistColumn = c.getColumnIndex(MediaStore.Audio.Media.ARTIST);
                do {
                    Media media = new Media();
                    media.setMediaId(c.getInt(idColumn));
                    media.setPath(c.getString(pathColumn));
                    media.setFlag("unread");
                    media.setTrackNb(c.getString(trackColumn));
                    media.setTitle(c.getString(titleColumn));
                    media.setAlbum(c.getString(albumColumn));
                    media.setArtist(c.getString(artistColumn));
                    dao.insert(media);
                    updated = true;
                } while (c.moveToNext());
            }
        } else {
            while (cursor.moveToNext()) {
                int id = cursor.getInt(0);
                String path = cursor.getString(1);
                int media_id = cursor.getInt(2);
                Cursor c;
                if (media_id == 0) {
                    c = contentResolver.query(
                            mediaUri,
                            new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ARTIST},
                            "`_data`=?",
                            new String[]{path},
                            null
                    );
                }
                else {
                    c = contentResolver.query(
                            mediaUri,
                            new String[]{MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ARTIST},
                            "`_id`=?",
                            new String[]{String.valueOf(media_id)},
                            null
                    );
                }
                if (c == null) {
                    dao.remove(id);
                } else {
                    Media media = new Media();
                    c.moveToFirst();
                    media.setMediaId(c.getInt(0));
                    media.setPath(c.getString(1));
                    media.setTrackNb(c.getString(2));
                    media.setTitle(c.getString(3));
                    media.setAlbum(c.getString(4));
                    media.setArtist(c.getString(5));
                    dao.update(media, id);
                }
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
        start();
    }
}
