package com.gbrfix.randomyzik;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteCursor;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.Observer;
import androidx.media.session.MediaButtonReceiver;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.ForegroundInfo;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.WorkContinuation;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AmpService extends Service implements Observer<WorkInfo> {
    public final static int NOTIFICATION_ID = 2;
    // Binder given to clients.
    private final IBinder binder = new LocalBinder();
    private boolean bound;
    private AmpSignal ampSignalListener;

    public class LocalBinder extends Binder {
        AmpService getService() {
            // Return this instance of AmpService so clients can call public methods.
            return AmpService.this;
        }
    }

    public boolean isBound() {
        return bound;
    }

    public void setBound(boolean bound) {
        this.bound = bound;
    }

    public void setAmpSignalListener(AmpSignal ampSignalListener) {
        this.ampSignalListener = ampSignalListener;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onChanged(WorkInfo workInfo) {
        if (workInfo != null) {
            //Data progress = workInfo.getProgress();
            //int position = progress.getInt("progress", 0);
            //ampSignalListener.onProgress(position);
            if (workInfo.getState().isFinished()) {
                WorkManager.getInstance(this).getWorkInfoByIdLiveData(workInfo.getId()).removeObserver(this);
                stopSelf();
                stopForeground(true);
                if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                }
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        bound = false;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            addAndPlay();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return START_NOT_STICKY;
    }

    private void addAndPlay() {
        WorkManager.getInstance(this).cancelAllWork();
        OneTimeWorkRequest playWork = OneTimeWorkRequest.from(PlayWorker.class);
        WorkManager.getInstance(this).enqueueUniqueWork(
                "play",
                ExistingWorkPolicy.KEEP,
                (OneTimeWorkRequest) playWork);
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(playWork.getId()).observeForever(this);
    }
}
