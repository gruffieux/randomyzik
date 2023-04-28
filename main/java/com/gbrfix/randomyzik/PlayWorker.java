package com.gbrfix.randomyzik;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class PlayWorker extends Worker {
    private boolean playing;
    private String auth;
    private AmpRepository amp;

    public PlayWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        playing = false;
        amp = new AmpRepository(context);
    }

    @NonNull
    @Override
    public Result doWork() {
        MediaProvider provider = AmpService.getProvider();
        try {
            auth = amp.handshake();
            while (!isStopped()) {
                Media media = provider.selectTrack();
                //amp.localplay_stop(auth);
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
                playing = true;
                while (counter < duration) {
                    Thread.sleep(1000);
                    if (playing) {
                        counter++;
                    }
                    //setProgressAsync(new Data.Builder().putInt("progress", counter).build());
                    if (isStopped()) {
                        amp.localplay_stop(auth);
                        playing = false;
                        return Result.failure();
                    }
                }
                playing = false;
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

        BroadcastReceiver ampBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals("resume")) {
                    Executor executor = Executors.newSingleThreadExecutor();
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (playing) {
                                    amp.localplay_pause(auth);
                                } else {
                                    amp.localplay_play(auth);
                                }
                                playing = !playing;
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    });
                }
            }
        };
        Intent resumeIntent = new Intent();
        //resumeIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        resumeIntent.setAction("resume");
        PendingIntent resumePendingIntent = PendingIntent.getBroadcast(context, 1, resumeIntent, PendingIntent.FLAG_IMMUTABLE);
        IntentFilter resumeIntentFilter = new IntentFilter();
        resumeIntentFilter.addAction("resume");
        context.registerReceiver(ampBroadcastReceiver, resumeIntentFilter);

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
                .addAction(new NotificationCompat.Action(
                        R.drawable.ic_action_pause, "Resume",
                        resumePendingIntent))
                .build();

        return new ForegroundInfo(AmpService.NOTIFICATION_ID, notification);
    }
}
