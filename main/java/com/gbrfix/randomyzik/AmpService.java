package com.gbrfix.randomyzik;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.Nullable;
import androidx.lifecycle.Observer;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

public class AmpService extends Service implements Observer<WorkInfo> {
    public final static int NOTIFICATION_ID = 2;
    final static String NOTIFICATION_CHANNEL = "Ampache channel";
    private final IBinder binder = new LocalBinder();
    private boolean bound, started;
    private Data metaData;
    private static MediaProvider provider = null;
    private AmpSignal ampSignalListener;

    private void addAndPlay() {
        WorkManager.getInstance(this).cancelUniqueWork("play");
        OneTimeWorkRequest playWork = OneTimeWorkRequest.from(AmpWorker.class);
        WorkManager.getInstance(this).enqueueUniqueWork(
                "play",
                ExistingWorkPolicy.KEEP,
                (OneTimeWorkRequest) playWork);
        WorkManager.getInstance(this).getWorkInfoByIdLiveData(playWork.getId()).observeForever(this);
    }

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

    public Data getMetadata() {
        return metaData;
    }

    public static MediaProvider getProvider() {
        return provider;
    }

    public void setBound(boolean bound) {
        this.bound = bound;
    }

    public void setAmpSignalListener(AmpSignal ampSignalListener) {
        this.ampSignalListener = ampSignalListener;
    }

    public void changeMode(int mode) {
        provider.setMode(mode);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onChanged(WorkInfo workInfo) {
        if (workInfo != null) {
            metaData = workInfo.getProgress();
            int state = metaData.getInt("state", 0);
            if (state == PlaybackStateCompat.STATE_PLAYING || state == PlaybackStateCompat.STATE_PAUSED) {
                int position = metaData.getInt("position", 0);
                int duration = metaData.getInt("duration", 0);
                if (position == 0) {
                    int id = metaData.getInt("id", 0);
                    String title = metaData.getString("title");
                    String album = metaData.getString("album");
                    String artist = metaData.getString("artist");
                    ampSignalListener.onSelect(id, duration, title, album, artist);
                } else if (position >= duration) {
                    boolean last = provider.getTotalRead() == provider.getTotal() - 1;
                    ampSignalListener.onComplete(last);
                } else {
                    ampSignalListener.onProgress(state, position);
                }
            }
            if (workInfo.getState().isFinished()) {
                WorkManager.getInstance(this).getWorkInfoByIdLiveData(workInfo.getId()).removeObserver(this);
                started = false;
                stopSelf();
                stopForeground(true);
                switch (workInfo.getState()) {
                    case SUCCEEDED:
                        ampSignalListener.onComplete(true);
                        break;
                    case FAILED:
                        ampSignalListener.onError(workInfo.getOutputData().getString("msg"));
                        break;
                    case CANCELLED:
                        ampSignalListener.onStop();
                    default:
                }
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        if (provider == null) {
            provider = new MediaProvider(this, DAOBase.DEFAULT_NAME);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL, "Ampache notification", NotificationManager.IMPORTANCE_LOW);
            channel.setVibrationPattern(null);
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            manager.deleteNotificationChannel(NOTIFICATION_CHANNEL);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction().equals("stop")) {
            WorkManager.getInstance(this).cancelUniqueWork("play");
            started = false;
        }

        if (intent.getAction().equals("start")) {
            try {
                addAndPlay();
                started = true;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    public void rewind() {
        provider.setSelectId(provider.getCurrentId());
        addAndPlay();
    }

    public void selectAndPlay(int id) {
        provider.setSelectId(id);
        addAndPlay();
    }

    public void skipToNext() {
        provider.updateState("skip");
        addAndPlay();
    }
}
