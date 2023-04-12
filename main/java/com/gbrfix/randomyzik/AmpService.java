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

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class AmpService extends Service {
    public final static int NOTIFICATION_ID = 2;
    public final static String NOTIFICATION_CHANNEL = "Randomyzik amp channel";
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
    public void onCreate() {
        super.onCreate();

        bound = false;
        provider = new MediaProvider(this);
        amp = new AmpRepository(this);
        executor = Executors.newSingleThreadExecutor();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    addAndPlay();
                }
            }
        });

        return START_NOT_STICKY;
    }

    public void selectId(int id) {
        provider.setSelectId(id);
    }

    private boolean addAndPlay() {
        try {
            Media media = provider.selectTrack();
            //ampSignalListener.onSelect(media.getDuration(), media.getTitle(), media.getAlbum(), media.getArtist());
            showNotification(media.getTitle(), media.getAlbum(), media.getArtist());
            String auth = amp.handshake();
            amp.localplay_stop(auth);
            amp.localplay_add(auth, media.getMediaId());
            amp.localplay_play(auth);
            int counter = 0;
            while (counter < media.getDuration()) {
                Thread.sleep(1000);
                counter++;
                //ampSignalListener.onProgress(counter);
            }
            provider.updateState("read");
            boolean last = provider.getTotalRead() == provider.getTotal() - 1;
            //ampSignalListener.onComplete(last);
            if (last) {
                stopForeground(true);
                stopSelf();
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void showNotification(String title, String album, String artist) {
        // If the notification supports a direct reply action, use PendingIntent.FLAG_MUTABLE instead.
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Notifications compatibility
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL, "Control notification", NotificationManager.IMPORTANCE_LOW);
            channel.setVibrationPattern(null);
            channel.setShowBadge(false);
            NotificationManager notificationManager = (NotificationManager)this.getSystemService(MainActivity.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
        }

        String contentTitle = MediaProvider.getTrackLabel(title, "", "");
        String contentText = MediaProvider.getTrackLabel("", album, artist);

        Notification notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL)
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setSubText(provider.getSummary())
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.drawable.ic_stat_audio)
                .build();

        startForeground(NOTIFICATION_ID, notification);
    }
}
