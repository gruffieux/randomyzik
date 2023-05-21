package com.gbrfix.randomyzik;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.io.IOException;

public class AmpWorker extends Worker {
    private boolean playing;
    private BroadcastReceiver ampBroadcastReceiver;
    private Context context;

    public AmpWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        playing = false;
        ampBroadcastReceiver = null;
        this.context = context;
    }

    @Override
    public void onStopped() {
        super.onStopped();

        try {
            AmpSession.getInstance().localplay_stop();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        playing = false;

        if (ampBroadcastReceiver != null) {
            context.unregisterReceiver(ampBroadcastReceiver);
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        MediaProvider provider = AmpService.getProvider();
        AmpSession session = AmpSession.getInstance();
        try {
            session.connect(prefs);
            while (!isStopped()) {
                Media media = provider.selectTrack();
                session.localplay_add(media.getMediaId());
                session.localplay_play();
                String contentTitle = MediaProvider.getTrackLabel(media.getTitle(), "", "");
                String contentText = MediaProvider.getTrackLabel("", media.getAlbum(), media.getArtist());
                String subText = provider.getSummary();
                setForegroundAsync(createForegroundInfo(contentTitle, contentText, subText));
                int counter = 0;
                int duration = media.getDuration();
                //duration = 10; // Teste
                Data data = new Data.Builder()
                        .putInt("state", PlaybackStateCompat.STATE_PLAYING)
                        .putInt("id", media.getId())
                        .putInt("position", 0)
                        .putInt("duration", duration)
                        .putString("title", media.getTitle())
                        .putString("album", media.getAlbum())
                        .putString("artist", media.getArtist())
                        .build();
                setProgressAsync(data);
                playing = true;
                boolean lastState = false;
                while (counter < duration) {
                    Thread.sleep(1000);
                    if (playing) {
                        if (!lastState) {
                            setForegroundAsync(createForegroundInfo(contentTitle, contentText, subText));
                            lastState = true;
                        }
                        counter++;
                    } else if (lastState) {
                        setForegroundAsync(createForegroundInfo(contentTitle, contentText, subText));
                        lastState = false;
                    }
                    Data data2 = new Data.Builder()
                            .putAll(data)
                            .putInt("state", playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED)
                            .putInt("position", counter)
                            .build();
                    setProgressAsync(data2);
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
        if (ampBroadcastReceiver != null) {
            context.unregisterReceiver(ampBroadcastReceiver);
        }

        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

        PendingIntent stopPendingIntent = WorkManager.getInstance(context)
                .createCancelPendingIntent(getId());

        ampBroadcastReceiver = new BroadcastReceiver() {
            private boolean locked = false;
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (!locked && action.equals("resume")) {
                    final PendingResult result = goAsync();
                    Thread thread = new Thread() {
                        @Override
                        public void run() {
                            try {
                                locked = true;
                                if (playing) {
                                    AmpSession.getInstance().localplay_pause();
                                } else {
                                    AmpSession.getInstance().localplay_play();
                                }
                                playing = !playing;
                                locked = false;
                                result.finish();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                    };
                    thread.start();
                }
            }
        };
        Intent resumeIntent = new Intent();
        resumeIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        resumeIntent.setAction("resume");
        PendingIntent resumePendingIntent = PendingIntent.getBroadcast(context, 1, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Notifications compatibility
            NotificationChannel channel = new NotificationChannel(AmpService.NOTIFICATION_CHANNEL, "Ampache notification", NotificationManager.IMPORTANCE_LOW);
            channel.setVibrationPattern(null);
            channel.setShowBadge(false);
            NotificationManager notificationManager = (NotificationManager)context.getSystemService(MainActivity.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, AmpService.NOTIFICATION_CHANNEL);

        builder
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setSubText(subText)
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.drawable.ic_stat_audio)
                .addAction(new NotificationCompat.Action(
                        playing ? R.drawable.ic_action_pause : R.drawable.ic_action_play, "Resume", resumePendingIntent))
                .addAction(new NotificationCompat.Action(
                        R.drawable.ic_action_cancel, "Stop", stopPendingIntent))
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setShowActionsInCompactView(0, 1));

        IntentFilter resumeIntentFilter = new IntentFilter();
        resumeIntentFilter.addAction("resume");
        context.registerReceiver(ampBroadcastReceiver, resumeIntentFilter);

        return new ForegroundInfo(AmpService.NOTIFICATION_ID, builder.build());
    }
}
