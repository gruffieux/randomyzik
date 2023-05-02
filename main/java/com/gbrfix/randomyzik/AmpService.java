package com.gbrfix.randomyzik;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.Observer;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

public class AmpService extends Service implements Observer<WorkInfo> {
    public final static int NOTIFICATION_ID = 2;
    final static String NOTIFICATION_CHANNEL = "Ampache channel";
    // Binder given to clients.
    private final IBinder binder = new LocalBinder();
    private boolean bound, started;
    private int state;
    private static MediaProvider provider = null;
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

    public boolean isStarted() {
        return started;
    }

    public int getState() {
        return state;
    }

    public void setBound(boolean bound) {
        this.bound = bound;
    }

    public static MediaProvider getProvider() {
        return provider;
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
            Data progress = workInfo.getProgress();
            state = progress.getInt("state", 0);
            if (state == PlaybackStateCompat.STATE_PLAYING || state == PlaybackStateCompat.STATE_PAUSED) {
                int position = progress.getInt("position", 0);
                if (position == 0) {
                    int id = progress.getInt("id", 0);
                    int duration = progress.getInt("duration", 0);
                    String title = progress.getString("title");
                    String album = progress.getString("album");
                    String artist = progress.getString("artist");
                    ampSignalListener.onSelect(id, duration, title, album, artist);
                } else {
                    ampSignalListener.onProgress(state, position);
                }
            }
            if (workInfo.getState().isFinished()) {
                WorkManager.getInstance(this).getWorkInfoByIdLiveData(workInfo.getId()).removeObserver(this);
                stop();
                switch (workInfo.getState()) {
                    case SUCCEEDED:
                        ampSignalListener.onComplete(true);
                        break;
                    case FAILED:
                        ampSignalListener.onError(workInfo.getOutputData().getString("msg"));
                        break;
                    default:
                }
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (provider == null) {
            provider = new MediaProvider(this);
        }
    }

    @Override
    public void onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
            manager.deleteNotificationChannel(NOTIFICATION_CHANNEL);
        }

        super.onDestroy();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            addAndPlay();
            started = true;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return super.onStartCommand(intent, flags, startId);
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

    private void stop() {
        state = 0;
        started = false;
        stopSelf();
        stopForeground(true);
    }

    public void changeMode(int mode) {
        provider.setMode(mode);
    }

    public void selectTrack(int id) {
        provider.setSelectId(id);
    }
}
