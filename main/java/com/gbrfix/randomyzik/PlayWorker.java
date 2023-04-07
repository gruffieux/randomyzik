package com.gbrfix.randomyzik;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class PlayWorker extends Worker {
    public final static int NOTIFICATION_ID = 2;
    public final static String NOTIFICATION_CHANNEL = "Randomyzik amp channel";

    public PlayWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        setProgressAsync(new Data.Builder().putInt("progress", 0).build());
    }

    @NonNull
    @Override
    public Result doWork() {
        AmpRepository amp = new AmpRepository(new AmpXmlParser(), getApplicationContext(), null);
        int mediaId = getInputData().getInt("mediaId", 0);
        int duration = getInputData().getInt("duration", 0);
        try {
            String auth = amp.handshake();
            amp.localplay_stop(auth);
            amp.localplay_add(auth, mediaId);
            amp.localplay_play(auth);
            if (duration > 0) {
                int counter = 0;
                String progress = "Starting play";
                setForegroundAsync(createForegroundInfo(progress));
                while (counter < duration) {
                    Thread.sleep(1000);
                    counter++;
                    setForegroundAsync(createForegroundInfo(String.valueOf(counter)));
                    setProgressAsync(new Data.Builder().putInt("progress", counter).build());
                }
                return Result.success();
            }
        } catch (Exception e) {
            Data output = new Data.Builder()
                    .putString("msg", e.getMessage())
                    .build();
            return Result.failure(output);
        }
        return Result.failure();
    }

    private ForegroundInfo createForegroundInfo(String progress) {
        Context context = getApplicationContext();
        String title = "Teste";

        PendingIntent stopPendingIntent = WorkManager.getInstance(context)
                .createCancelPendingIntent(getId());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Notifications compatibility
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL, "Control notification", NotificationManager.IMPORTANCE_LOW);
            channel.setVibrationPattern(null);
            channel.setShowBadge(false);
            NotificationManager notificationManager = (NotificationManager)context.getSystemService(MainActivity.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
                .setContentTitle(title)
                .setContentText(progress)
                .setSmallIcon(R.drawable.ic_stat_audio)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent)
                .build();

        return new ForegroundInfo(NOTIFICATION_ID, notification);
    }
}
