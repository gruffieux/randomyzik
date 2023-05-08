package com.gbrfix.randomyzik;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Executors;

/**
 * Created by gab on 27.08.2017.
 */

public class DbService extends IntentService {
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

    // Création de la liste de lecture sous forme de base de données SQLite avec une table medias contenant un flag read/unread.
    // Si la liste n'existe pas, la créer en y ajoutant tous les médias du dossier audio.
    // Sinon vérifier que chaque média de la liste est toujours présent dans le dossier audio, le supprimer si ce n'est pas le cas, puis ajouter les médias pas encore présents dans la liste.
    private void scan() {
        dbSignalListener.onScanStart();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean amp = prefs.getBoolean("amp", false);
        DAOBase.NAME = amp ? "playlist-amp.db" : "playlist.db";
        boolean updated = false;
        MediaDAO dao = new MediaDAO(this);
        dao.open();
        ArrayList<Media> list = new ArrayList<Media>();
        SQLiteCursor cursor = dao.getAll();

        if (amp) {
            try {
                String authToken = AmpRepository.handshake();
                list = (ArrayList<Media>)AmpRepository.advanced_search(authToken);
            } catch (IOException | XmlPullParserException e) {
                dbSignalListener.onError(e.getMessage());
                return;
            }
        } else {
            Cursor c = contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, new String[]{
                    MediaStore.Audio.Media.IS_MUSIC,
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TRACK,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM_KEY
            }, null, null, null);
            while (c.moveToNext()) {
                if (c.getInt(0) != 0) {
                    Media media = new Media();
                    media.setMediaId(c.getInt(1));
                    media.setFlag("unread");
                    media.setTrackNb(c.getString(2));
                    media.setTitle(c.getString(3));
                    media.setAlbum(c.getString(4));
                    media.setArtist(c.getString(5));
                    media.setAlbumKey(c.getString(6));
                    list.add(media);
                }
            }
        }

        if (cursor.getCount() == 0) {
            for (int i = 0; i < list.size(); i++) {
                dao.insert(list.get(i));
                updated = true;
            }
        } else {
            while (cursor.moveToNext()) {
                int id = cursor.getInt(cursor.getColumnIndex("id"));
                int media_id = cursor.getInt(cursor.getColumnIndex("media_id"));
                int i;
                for (i = 0; i < list.size(); i++) {
                    if (list.get(i).getMediaId() == media_id) {
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

        contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver);
    }

    public void rescan() {
        Executors.newSingleThreadExecutor().execute(new Runnable() {
            @Override
            public void run() {
                scan();
            }
        });

    }
}
