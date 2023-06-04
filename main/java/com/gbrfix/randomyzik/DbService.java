package com.gbrfix.randomyzik;

import android.app.Service;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;

import androidx.lifecycle.Observer;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkContinuation;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by gab on 27.08.2017.
 */

public class DbService extends Service implements Observer<WorkInfo> {
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

    public void setDbSignalListener(DbSignal listener) {
        dbSignalListener = listener;
    }

    // Création de la liste de lecture sous forme de base de données SQLite avec une table medias contenant un flag read/unread.
    // Si la liste n'existe pas, la créer en y ajoutant tous les médias du dossier audio.
    // Sinon vérifier que chaque média de la liste est toujours présent dans le dossier audio, le supprimer si ce n'est pas le cas, puis ajouter les médias pas encore présents dans la liste.
    public void scan() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean amp = prefs.getBoolean("amp", false);
        String server = prefs.getString("amp_server", "");

        if (amp) {
            WorkManager.getInstance(this).cancelAllWork();
            AmpSession ampSession = AmpSession.getInstance();
                ExecutorService executor = Executors.newSingleThreadExecutor();
                Handler handler = new Handler(Looper.getMainLooper());
                executor.execute(() -> {
                    Map<String, String> cats;
                    try {
                        ampSession.connect(prefs);
                        cats = ampSession.catalogs();
                    } catch (Exception e) {
                        cats = null;
                    }
                    final Map<String, String> catalogs = cats;
                    if (catalogs != null) {
                        handler.post(() -> {
                            try {
                                WorkContinuation workContinuation = null;
                                for (Map.Entry<String, String> entry : catalogs.entrySet()) {
                                    String key = entry.getKey();
                                    String value = entry.getValue();
                                    String dbName = AmpRepository.dbName(server, value);
                                    if (key.equals("gab")) {
                                        continue;
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
                                    WorkManager.getInstance(this).getWorkInfoByIdLiveData(workRequest.getId()).observeForever(this);
                                }
                                workContinuation.enqueue();
                            } catch (Exception e) {
                                dbSignalListener.onError(e.getMessage());
                                return;
                            }
                        });
                    }
                });

        } else {
            WorkRequest workRequest = new OneTimeWorkRequest.Builder(DbWorker.class)
                    .setInputData(
                            new Data.Builder()
                                    .putBoolean("amp", false)
                                    .putString("dbName", "playlist.db")
                                    .build()
                    ).build();
            WorkManager.getInstance(this).enqueue(workRequest);
            WorkManager.getInstance(this).getWorkInfoByIdLiveData(workRequest.getId()).observeForever(this);
        }
    }

    @Override
    public void onChanged(WorkInfo workInfo) {
        if (workInfo != null) {
            if (workInfo.getState().isFinished()) {
                WorkManager.getInstance(this).getWorkInfoByIdLiveData(workInfo.getId()).removeObserver(this);
                switch (workInfo.getState()) {
                    case SUCCEEDED:
                        Log.v("workInfo", "Work " + workInfo.getId() + " is succeeded");
                        dbSignalListener.onScanCompleted(workInfo.getOutputData().getBoolean("updated", false));
                        break;
                    case FAILED:
                        Log.v("workInfo", "Work " + workInfo.getId() + " is failed");
                        dbSignalListener.onError(workInfo.getOutputData().getString("msg"));
                        break;
                    case CANCELLED:
                        Log.v("workInfo", "Work " + workInfo.getId() + " is cancelled");
                    default:
                }
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
    public void onCreate() {
        super.onCreate();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean amp = prefs.getBoolean("amp", false);

        if (!amp) {
            contentResolver = getContentResolver();
            mediaObserver = new ContentObserver(new Handler()) {
                @Override
                public void onChange(boolean selfChange) {
                    super.onChange(selfChange);
                    scan();
                }
            };
            contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver);
        } else {
            contentResolver = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (contentResolver != null) {
            contentResolver.unregisterContentObserver(mediaObserver);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return super.onStartCommand(intent, flags, startId);
    }
}
