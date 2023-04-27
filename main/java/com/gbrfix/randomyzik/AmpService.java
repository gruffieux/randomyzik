package com.gbrfix.randomyzik;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.Observer;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

public class AmpService extends Service implements Observer<WorkInfo> {
    public final static int NOTIFICATION_ID = 2;
    // Binder given to clients.
    private final IBinder binder = new LocalBinder();
    private boolean bound;
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
            //Data progress = workInfo.getProgress();
            //int position = progress.getInt("progress", 0);
            //ampSignalListener.onProgress(position);
            if (workInfo.getState().isFinished()) {
                WorkManager.getInstance(this).getWorkInfoByIdLiveData(workInfo.getId()).removeObserver(this);
                stopSelf();
                stopForeground(true);
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

        bound = false;

        if (provider == null) {
            provider = new MediaProvider(this);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent.getAction() == "STOP") {
            stopSelf();
            stopForeground(true);
        }

        try {
            addAndPlay();
            //showNotification();
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

    public void changeMode(int mode) {
        provider.setMode(mode);
    }

    public void selectTrack(int id) {
        provider.setSelectId(id);
    }

    private void showNotification() {
        String contentTitle = "N/A";
        String contentText = "N/A";

        // Create an explicit intent for an Activity in your app
        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);

        // Create an action intent for stopping the service
        Intent stopIntent = new Intent(this, AmpService.class);
        stopIntent.setAction("STOP");
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, 0);

        // Build notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, MainActivity.NOTIFICATION_CHANNEL);

        builder
                // Add the metadata for the currently playing track
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setSubText(provider.getSummary())

                // Enable launching the app by clicking the notification
                .setContentIntent(pendingIntent)

                // Stop the service when the notification is swiped away
                //.setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP))
                .setDeleteIntent(stopPendingIntent)

                // Make the transport controls visible on the lockscreen
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(true)

                // Add an app icon and set its accent color
                // Be careful about the color
                .setSmallIcon(R.drawable.ic_stat_audio)

                // Add a pause button
                .addAction(new NotificationCompat.Action(
                        R.drawable.ic_action_pause, "Resume",
                        null))

                // Add a cancel button
                .addAction(new NotificationCompat.Action(
                        R.drawable.ic_action_cancel, "Stop", stopPendingIntent));

        // Display the notification and place the service in the foreground
        startForeground(NOTIFICATION_ID, builder.build());
    }
}
