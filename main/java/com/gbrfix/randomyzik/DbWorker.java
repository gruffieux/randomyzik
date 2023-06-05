package com.gbrfix.randomyzik;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.os.Build;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.preference.PreferenceManager;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.ArrayList;

public class DbWorker extends Worker {
    private Context context;

    public DbWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);

        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        boolean updated = false;
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean amp = prefs.getBoolean("amp", false);
        String catalog = prefs.getString("amp_catalog", "");
        DAOBase.NAME = getInputData().getString("dbName");
        String catalogName = getInputData().getString("catalogName");
        int catalogId = getInputData().getInt("catalogId", 0);
        String contentTitle = "Scanning medias...";
        String contentText = "";
        String subText = "Get playlist";
        MediaDAO dao = new MediaDAO(context);
        dao.open();
        ArrayList<Media> list = new ArrayList<Media>();
        SQLiteCursor cursor = dao.getAll();

        if (amp) {
            contentText = "Searching in Ampache catalog: " + catalogName;
            setForegroundAsync(createForegroundInfo(contentTitle, contentText, subText));
            AmpSession ampSession = AmpSession.getInstance();
            try {
                ampSession.connect(prefs);
                if (catalogId == 0) {
                    throw new Exception("No catalog found");
                }
                int offset = 0;
                ArrayList<Media> elements = new ArrayList<Media>();
                do {
                    elements.clear();
                    elements = (ArrayList<Media>) ampSession.advanced_search(offset, catalogId);
                    list.addAll(elements);
                    offset += AmpRepository.MAX_ELEMENTS_PER_REQUEST;
                } while (elements.size() >= AmpRepository.MAX_ELEMENTS_PER_REQUEST);
            } catch (Exception e) {
                Data output = new Data.Builder()
                        .putString("msg", e.getMessage())
                        .build();
                return Result.failure(output);
            }
        } else {
            contentText = "Searching in local directory: " + MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.getPath();
            setForegroundAsync(createForegroundInfo(contentTitle, contentText, subText));
            ContentResolver contentResolver = context.getContentResolver();
            Cursor c = contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, new String[]{
                    MediaStore.Audio.Media.IS_MUSIC,
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TRACK,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM_KEY
            }, null, null, null);
            while (c.moveToNext()) {
                if (c.getInt(0) != 0) {
                    Media media = new Media();
                    media.setMediaId(c.getInt(1));
                    media.setFlag("unread");
                    media.setTrackNb(c.getString(2));
                    media.setTitle(c.getString(3));
                    media.setAlbum(c.getString(4));
                    media.setArtist(c.getString(5));
                    media.setAlbumKey(c.getString(6));
                    list.add(media);
                }
            }
        }

        if (cursor.getCount() == 0) {
            contentTitle = "Inserting medias...";
            contentText = list.size() + " elements";
            setForegroundAsync(createForegroundInfo(contentTitle, contentText, subText));
            for (int i = 0; i < list.size(); i++) {
                dao.insert(list.get(i));
                updated = true;
            }
        } else {
            contentTitle = "Updating medias...";
            contentText = list.size() + " elements";
            setForegroundAsync(createForegroundInfo(contentTitle, contentText, subText));
            while (cursor.moveToNext()) {
                int id = cursor.getInt(cursor.getColumnIndex("id"));
                int media_id = cursor.getInt(cursor.getColumnIndex("media_id"));
                int i;
                for (i = 0; i < list.size(); i++) {
                    Media media = list.get(i);
                    if (media.getMediaId() == media_id) {
                        dao.update(list.get(i), id);
                        updated = true;
                        break;
                    }
                }
                if (i >= list.size()) {
                    dao.remove(id);
                    updated = true;
                }
                else {
                    list.remove(i);
                }
            }
            if (list.size() > 0) {
                contentTitle = "Inserting new medias";
                contentText = list.size() + " elements";
                setForegroundAsync(createForegroundInfo(contentTitle, contentText, subText));
                for (int i = 0; i < list.size(); i++) {
                    dao.insert(list.get(i));
                    updated = true;
                }
            }
        }

        dao.close();

        boolean catUpdate = !amp ^ Integer.valueOf(catalog) == catalogId;
        Data output = new Data.Builder()
                .putBoolean("updated", updated && catUpdate)
                .build();
        return Result.success(output);
    }

    @Override
    public void onStopped() {
        super.onStopped();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager manager = (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
            manager.deleteNotificationChannel(DbService.NOTIFICATION_CHANNEL);
        }
    }

    private ForegroundInfo createForegroundInfo(String contentTitle, String contentText, String subText) {
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, 0);

        PendingIntent stopPendingIntent = WorkManager.getInstance(context)
                .createCancelPendingIntent(getId());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Notifications compatibility
            NotificationChannel channel = new NotificationChannel(DbService.NOTIFICATION_CHANNEL, "Database notification", NotificationManager.IMPORTANCE_LOW);
            channel.setVibrationPattern(null);
            channel.setShowBadge(false);
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, DbService.NOTIFICATION_CHANNEL);

        builder
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setSubText(subText)
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.drawable.ic_stat_audio)
                .addAction(android.R.drawable.ic_delete, "Stop", stopPendingIntent);

        return new ForegroundInfo(DbService.NOTIFICATION_ID, builder.build());
    }
}
