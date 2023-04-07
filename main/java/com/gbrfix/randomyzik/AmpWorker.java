package com.gbrfix.randomyzik;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.media.session.MediaButtonReceiver;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class AmpWorker extends Worker {
    public final static int NOTIFICATION_ID = 2;
    public final static String NOTIFICATION_CHANNEL = "Randomyzik amp channel";
    private NotificationManager notificationManager;

    public AmpWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        notificationManager = (NotificationManager)context.getSystemService(MainActivity.NOTIFICATION_SERVICE);
    }

    @NonNull
    @Override
    public Result doWork() {
        String spec = getInputData().getString("URL_SPEC");
        String parseAuth = getInputData().getString("PARSE_AUTH");
        String auth = getInputData().getString("auth");
        int duration = getInputData().getInt("duration", 0);
        try {
            if (auth != null) {
                spec += "&auth=" + auth;
            }
            URL url = new URL(spec);
            HttpsURLConnection conn = (HttpsURLConnection)url.openConnection();
            if (parseAuth != null) {
                AmpXmlParser parser = new AmpXmlParser();
                auth = parser.parseText(conn.getInputStream(), parseAuth);
            }
            Data output = new Data.Builder()
                    .putString("auth", auth)
                    .putString("msg", conn.getResponseMessage())
                    .build();
            conn.disconnect();
            if (duration > 0) {
                int counter = 0;
                String progress = "Starting play";
                setForegroundAsync(createForegroundInfo(progress));
                //duration = 10; // Testes
                while (counter < duration) {
                    Thread.sleep(1000);
                    counter++;
                    setForegroundAsync(createForegroundInfo(String.valueOf(counter)));
                }
            }
            return Result.success(output);
        } catch (IOException | XmlPullParserException | InterruptedException e) {
            Data output = new Data.Builder()
                    .putString("msg", e.getMessage())
                    .build();
            return Result.failure(output);
        }
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
