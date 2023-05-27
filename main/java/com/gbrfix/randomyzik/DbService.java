package com.gbrfix.randomyzik;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import androidx.annotation.Nullable;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import java.net.URL;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Created by gab on 27.08.2017.
 */

public class DbService extends IntentService {
    public final static int NOTIFICATION_ID = 3;
    final static String NOTIFICATION_CHANNEL = "Database channel";
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
        String server = prefs.getString("amp_server", "");

        if (amp) {
            WorkManager.getInstance(this).cancelAllWork();
            AmpSession ampSession = AmpSession.getInstance();
            try {
                ampSession.connect(prefs);
                Map<String, String> catalogs;
                catalogs = ampSession.catalogs();
                WorkContinuation workContinuation = null;
                for (Map.Entry<String, String> entry : catalogs.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    String dbName = AmpRepository.dbName(server, value);
                    if (key.equals("gab")) {
                        //continue;
                    }
                    OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(DbWorker.class)
                            .setInputData(
                                    new Data.Builder()
                                            .putString("dbName", dbName)
                                            .putInt("catalogId", Integer.valueOf(value))
                                            .putString("catalogName", key)
                                            .build()
                            ).build();
                    if (workContinuation == null) {
                        workContinuation = WorkManager.getInstance(this).beginWith(workRequest);
                    } else {
                        workContinuation = workContinuation.then(workRequest);
                    }
                }
                workContinuation.enqueue();
            } catch (Exception e) {
                dbSignalListener.onError(e.getMessage());
                return;
            }
        } else {
            WorkRequest workRequest = new OneTimeWorkRequest.Builder(DbWorker.class)
                    .setInputData(
                            new Data.Builder()
                                    .putBoolean("amp", false)
                                    .putString("dbname", "playlist.db")
                                    .build()
                    ).build();
            WorkManager.getInstance(this).enqueue(workRequest);
        }

        //dbSignalListener.onScanCompleted(updated);
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
