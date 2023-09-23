package com.gbrfix.randomyzik;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.sqlite.SQLiteCursor;
import android.os.Build;
import android.os.Handler;
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

public class DbService implements Observer<WorkInfo> {
    public final static int NOTIFICATION_ID = 3;
    final static String NOTIFICATION_CHANNEL = "Database channel";
    private DbSignal dbSignalListener;
    private ContentResolver contentResolver;
    private ContentObserver mediaObserver;
    private Context context;
    private int total, counter;

    public DbService(Context context) {
        this.context = context;
    }

    public void setDbSignalListener(DbSignal listener) {
        dbSignalListener = listener;
    }

    // Création de la liste de lecture sous forme de base de données SQLite avec une table medias contenant un flag read/unread.
    // Si la liste n'existe pas, la créer en y ajoutant tous les médias du dossier audio.
    // Sinon vérifier que chaque média de la liste est toujours présent dans le dossier audio, le supprimer si ce n'est pas le cas, puis ajouter les médias pas encore présents dans la liste.
    public void scan(boolean onChange, String catalog) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean amp = prefs.getBoolean("amp", false);
        String server = prefs.getString("amp_server", "");

        WorkManager.getInstance(context).cancelAllWorkByTag("db");

        if (amp && !onChange) {
            AmpSession ampSession = AmpSession.getInstance(context);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Handler handler = new Handler(Looper.getMainLooper());
            executor.execute(() -> {
                Map<String, String> cats;
                try {
                    ampSession.connect();
                    cats = ampSession.catalogs();
                } catch (Exception e) {
                    cats = null;
                }
                final Map<String, String> catalogs = cats;
                if (catalogs != null) {
                    handler.post(() -> {
                        try {
                            total = 0;
                            WorkContinuation workContinuation = null;
                            for (Map.Entry<String, String> entry : catalogs.entrySet()) {
                                String key = entry.getKey();
                                String value = entry.getValue();
                                String dbName = AmpRepository.dbName(server, value);
                                if (!catalog.equals("0") && !catalog.equals(value)) {
                                    continue;
                                }
                                total++;
                                OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(DbWorker.class)
                                        .setInputData(
                                                new Data.Builder()
                                                        .putString("dbName", dbName)
                                                        .putInt("catalogId", Integer.valueOf(value))
                                                        .putString("catalogName", key)
                                                        .build()
                                        )
                                        .addTag("db")
                                        .build();
                                if (workContinuation == null) {
                                    workContinuation = WorkManager.getInstance(context).beginWith(workRequest);
                                } else {
                                    workContinuation = workContinuation.then(workRequest);
                                }
                                WorkManager.getInstance(context).getWorkInfoByIdLiveData(workRequest.getId()).observeForever(this);
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
            total = 1;
            String dbName = DAOBase.DEFAULT_NAME;
            WorkRequest workRequest = new OneTimeWorkRequest.Builder(DbWorker.class)
                    .setInputData(
                            new Data.Builder()
                                    .putBoolean("amp", false)
                                    .putString("dbName", dbName)
                                    .build()
                    )
                    .addTag("db")
                    .build();
            WorkManager.getInstance(context).enqueue(workRequest);
            WorkManager.getInstance(context).getWorkInfoByIdLiveData(workRequest.getId()).observeForever(this);
        }

        counter = 0;
        dbSignalListener.onScanStart();
    }

    @Override
    public void onChanged(WorkInfo workInfo) {
        if (workInfo != null) {
            Data progress = workInfo.progress();
            dbSignalListener.onScanProgress(progress.getInt("catalogId", 0), progress.getInt("total", 0));
            if (workInfo.getState().isFinished()) {
                WorkManager.getInstance(context).getWorkInfoByIdLiveData(workInfo.getId()).removeObserver(this);
                switch (workInfo.getState()) {
                    case SUCCEEDED:
                        Log.v("workInfo", "Work " + workInfo.getId() + " is succeeded");
                        int catId = workInfo.getOutputData().getInt("catId", 0);
                        boolean updated = workInfo.getOutputData().getBoolean("updated", false);
                        counter++;
                        dbSignalListener.onScanCompleted(catId, updated, counter >= total);
                        break;
                    case FAILED:
                        Log.v("workInfo", "Work " + workInfo.getId() + " is failed");
                        dbSignalListener.onError(workInfo.getOutputData().getString("msg"));
                        break;
                    case CANCELLED:
                        Log.v("workInfo", "Work " + workInfo.getId() + " is cancelled");
                        dbSignalListener.onScanCompleted(0, false, true);
                    default:
                }
            }
        }
    }
    public void register() {
        contentResolver = context.getContentResolver();

        mediaObserver = new ContentObserver(new Handler()) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                scan(true, "");
            }
        };

        contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, mediaObserver);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL, "Database notification", NotificationManager.IMPORTANCE_LOW);
            channel.setVibrationPattern(null);
            notificationManager.createNotificationChannel(channel);
        }
    }

    public void unregister() {
        contentResolver.unregisterContentObserver(mediaObserver);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager)context.getSystemService(context.NOTIFICATION_SERVICE);
            manager.deleteNotificationChannel(NOTIFICATION_CHANNEL);
        }
    }

    public void check() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean amp = prefs.getBoolean("amp", false);
        String server = prefs.getString("amp_server", "");
        String catalog = prefs.getString("amp_catalog", "0");

        if (amp && catalog.equals("0")) {
            scan(false, catalog);
        } else {
            try {
                String dbName = amp ? AmpRepository.dbName(server, catalog) : DAOBase.DEFAULT_NAME;
                MediaDAO dao = new MediaDAO(context, dbName);
                dao.open();
                SQLiteCursor cursor = dao.getAll();
                int total = cursor.getCount();
                dao.close();
                if (total == 0) {
                    scan(false, catalog);
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
