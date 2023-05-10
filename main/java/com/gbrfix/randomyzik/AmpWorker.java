package com.gbrfix.randomyzik;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class AmpWorker extends Worker {
    private boolean playing;
    private String auth;
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

        playing = false;

        if (ampBroadcastReceiver != null) {
            context.unregisterReceiver(ampBroadcastReceiver);
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        MediaProvider provider = AmpService.getProvider();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String server = prefs.getString("amp_server", "");
        String apiKey = prefs.getString("amp_apiKey", "");
        AmpRepository repository = AmpRepository.getInstance();
        repository.init(server, apiKey);
        try {
            auth = repository.handshake();
            while (!isStopped()) {
                Media media = provider.selectTrack();
                repository.localplay_add(auth, media.getMediaId());
                repository.localplay_play(auth);
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
                while (counter < duration) {
                    Thread.sleep(1000);
                    if (playing) {
                        counter++;
                    }
                    Data data2 = new Data.Builder()
                            .putAll(data)
                            .putInt("state", playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED)
                            .putInt("position", counter)
                            .build();
                    setProgressAsync(data2);
                    if (isStopped()) {
                        repository.localplay_stop(auth);
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
                                    AmpRepository.getInstance().localplay_pause(auth);
                                } else {
                                    AmpRepository.getInstance().localplay_play(auth);
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

        IntentFilter resumeIntentFilter = new IntentFilter();
        resumeIntentFilter.addAction("resume");
        context.registerReceiver(ampBroadcastReceiver, resumeIntentFilter);

        return new ForegroundInfo(AmpService.NOTIFICATION_ID, notification);
    }
}
