package com.gbrfix.randomyzik;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.Observer;
import androidx.media.session.MediaButtonReceiver;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AmpService extends Service implements Observer<WorkInfo> {
    public final static int NOTIFICATION_ID = 2;
    // Binder given to clients.
    private final IBinder binder = new LocalBinder();
    private boolean bound;
    private AmpSignal ampSignalListener;
    private MediaProvider provider;
    private AmpRepository amp;
    private Executor executor;

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
            Data progress = workInfo.getProgress();
            int position = progress.getInt("progress", 0);
            ampSignalListener.onProgress(position);
            if (workInfo.getState().isFinished()) {
                WorkManager.getInstance(this).getWorkInfoByIdLiveData(workInfo.getId()).removeObserver(this);
                if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                    provider.updateState("read");
                    boolean last = provider.getTotalRead() == provider.getTotal() - 1;
                    ampSignalListener.onComplete(last);
                    if (!last) {
                        try {
                            addAndPlay();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        stopSelf();
                        stopForeground(true);
                    }
                }
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        bound = false;
        provider = new MediaProvider(this);
        amp = new AmpRepository(this);
        executor = Executors.newSingleThreadExecutor();
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

    public void selectId(int id) {
        provider.setSelectId(id);
    }

    private void addAndPlay() throws Exception {
        Media media = provider.selectTrack();
        String contentTitle = MediaProvider.getTrackLabel(media.getTitle(), "", "");
        String contentText = MediaProvider.getTrackLabel("", media.getAlbum(), media.getArtist());
        String subText = provider.getSummary();

        ampSignalListener.onSelect(media.getDuration(), media.getTitle(), media.getAlbum(), media.getArtist());

        WorkRequest playWork = new OneTimeWorkRequest.Builder(PlayWorker.class)
                .setInputData(
                        new Data.Builder()
                                .putInt("mediaId", media.getMediaId())
                                .putInt("duration", media.getDuration())
                                .putString("contentTitle", contentTitle)
                                .putString("contentText", contentText)
                                .putString("subText", subText)
                                .build()
                )
                .build();

        WorkManager.getInstance(this).enqueueUniqueWork(
                "play",
                ExistingWorkPolicy.KEEP,
                (OneTimeWorkRequest) playWork);

        WorkManager.getInstance(this).getWorkInfoByIdLiveData(playWork.getId()).observeForever(this);
    }

    private boolean addAndPlay_old() throws Exception {
        Media media = provider.selectTrack();
        ampSignalListener.onSelect(media.getDuration(), media.getTitle(), media.getAlbum(), media.getArtist());
        showNotification(media.getTitle(), media.getAlbum(), media.getArtist());
        String auth = amp.handshake();
        amp.localplay_stop(auth);
        amp.localplay_add(auth, media.getMediaId());
        amp.localplay_play(auth);

        int counter = 0;
        while (counter < media.getDuration()) {
            Thread.sleep(1000);
            counter++;
            ampSignalListener.onProgress(counter);
        }

        provider.updateState("read");
        boolean last = provider.getTotalRead() == provider.getTotal() - 1;
        ampSignalListener.onComplete(last);

        if (last) {
            stopForeground(true);
            stopSelf();
        }

        return true;
    }

    private void showNotification(String title, String album, String artist) {
        // If the notification supports a direct reply action, use PendingIntent.FLAG_MUTABLE instead.
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        // Create an action intent for stopping the service
        Intent stopIntent = new Intent(this, AmpService.class);
        stopIntent.setAction("STOP");
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, 0);

        // Create an action intent for resume
        Intent resumeIntent = new Intent(this, AmpService.class);
        resumeIntent.setAction("RESUME");
        PendingIntent resumePendingIntent = PendingIntent.getService(this, 0, resumeIntent, 0);

        String contentTitle = MediaProvider.getTrackLabel(title, "", "");
        String contentText = MediaProvider.getTrackLabel("", album, artist);

        Notification notification = new NotificationCompat.Builder(this, MainActivity.NOTIFICATION_CHANNEL)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setSubText(provider.getSummary())
                .setContentIntent(pendingIntent)
                .setDeleteIntent(stopPendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.drawable.ic_stat_audio)
                .addAction(new NotificationCompat.Action(
                        R.drawable.ic_action_pause, "Resume", resumePendingIntent))
                .addAction(new NotificationCompat.Action(
                        R.drawable.ic_action_cancel, "Stop", stopPendingIntent))
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1))
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }
}
