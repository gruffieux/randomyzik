package com.gbrfix.randomyzik;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class PlayWorker extends Worker {
    public PlayWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        AmpRepository amp = new AmpRepository(getApplicationContext());
        MediaProvider provider = AmpService.getProvider();
        try {
            String auth = amp.handshake();
            while (!isStopped()) {
                Media media = provider.selectTrack();
                amp.localplay_stop(auth);
                amp.localplay_add(auth, media.getMediaId());
                amp.localplay_play(auth);
                String contentTitle = MediaProvider.getTrackLabel(media.getTitle(), "", "");
                String contentText = MediaProvider.getTrackLabel("", media.getAlbum(), media.getArtist());
                String subText = provider.getSummary();
                setForegroundAsync(createForegroundInfo(contentTitle, contentText, subText));
                int counter = 0;
                int duration = media.getDuration();
                //duration = 10; // Teste
                //setProgressAsync(new Data.Builder().putInt("progress", 0).build());
                while (counter < duration) {
                    Thread.sleep(1000);
                    counter++;
                    //setProgressAsync(new Data.Builder().putInt("progress", counter).build());
                    if (isStopped()) {
                        amp.localplay_stop(auth);
                        return Result.failure();
                    }
                }
                provider.updateState("read");
                boolean last = provider.getTotalRead() == provider.getTotal() - 1;
                if (last) {
                    return Result.success();
                }
            }
        } catch (Exception e) {
            Data output = new Data.Builder()
                    .putString("msg", e.getMessage())
                    .build();
            return Result.failure(output);
        }
        return Result.failure();
    }

    private ForegroundInfo createForegroundInfo(String contentTitle, String contentText, String subText) {
        Context context = getApplicationContext();

        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

        PendingIntent stopPendingIntent = WorkManager.getInstance(context)
                .createCancelPendingIntent(getId());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Notifications compatibility
            NotificationChannel channel = new NotificationChannel(AmpService.NOTIFICATION_CHANNEL, "Ampache notification", NotificationManager.IMPORTANCE_LOW);
            channel.setVibrationPattern(null);
            channel.setShowBadge(false);
            NotificationManager notificationManager = (NotificationManager)context.getSystemService(MainActivity.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(context, AmpService.NOTIFICATION_CHANNEL)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setSubText(subText)
                .setContentIntent(pendingIntent)
                //.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                //.setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.drawable.ic_stat_audio)
                .setOngoing(true)
                .addAction(new NotificationCompat.Action(R.drawable.ic_action_cancel, "Stop", stopPendingIntent))
                .build();

        return new ForegroundInfo(AmpService.NOTIFICATION_ID, notification);
    }
}
