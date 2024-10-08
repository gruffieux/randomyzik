package com.gbrfix.randomyzik;

import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.net.Uri;
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
        boolean test = prefs.getBoolean("test", false);
        String dbName = getInputData().getString("dbName");
        String catalogName = getInputData().getString("catalogName");
        int catalogId = getInputData().getInt("catalogId", 0);
        String contentTitle = context.getString(R.string.info_scanning);
        String contentText = "";
        String subText = "Playlist scan";
        MediaDAO dao = new MediaDAO(context, dbName);
        dao.open();
        ArrayList<Media> list = new ArrayList<Media>();
        SQLiteCursor cursor = dao.getAll();

        if (amp) {
            contentText = String.format(context.getString(R.string.info_cat_searching), catalogName);
            subText = "Ampache playlist";
            setForegroundAsync(createForegroundInfo(contentTitle, contentText, subText));
            AmpSession ampSession = AmpSession.getInstance(context);
            try {
                ampSession.connect();
                if (catalogId == 0) {
                    throw new Exception("No catalog found");
                }
                int offset = 0;
                int limit = test ? DbService.TEST_MAX_TRACKS : AmpRepository.MAX_ELEMENTS_PER_REQUEST;
                int total = 0;
                ArrayList<Media> elements = new ArrayList<Media>();
                do {
                    elements.clear();
                    elements = (ArrayList<Media>) ampSession.advanced_search(offset, limit, catalogId);
                    list.addAll(elements);
                    offset += AmpRepository.MAX_ELEMENTS_PER_REQUEST;
                    total = elements.size();
                } while (total >= AmpRepository.MAX_ELEMENTS_PER_REQUEST);
            } catch (Exception e) {
                Data output = new Data.Builder()
                        .putString("msg", e.getMessage())
                        .build();
                return Result.failure(output);
            }
        } else {
            Uri collection;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
            } else {
                collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            }
            contentText = String.format(context.getString(R.string.info_searching), collection.getPath());
            subText = "Playlist";
            setForegroundAsync(createForegroundInfo(contentTitle, contentText, subText));
            ContentResolver contentResolver = context.getContentResolver();
            String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
            Cursor c = contentResolver.query(collection, new String[]{
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TRACK,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.ARTIST,
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? MediaStore.Audio.Media.ALBUM_ID : MediaStore.Audio.Media.ALBUM_KEY // ALBUM_KEY depracated in api 30
            }, selection, null, null);
            int counter = 0;
            while (c.moveToNext()) {
                Media media = new Media();
                media.setMediaId(c.getInt(0));
                media.setFlag("unread");
                media.setTrackNb(c.getString(1));
                media.setTitle(c.getString(2));
                media.setAlbum(c.getString(3));
                media.setArtist(c.getString(4));
                media.setAlbumKey(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ? Integer.toString(c.getInt(5)) : c.getString(5)); // ALBUM_KEY depracated in api 30
                list.add(media);
                counter++;
                if (test && counter >= DbService.TEST_MAX_TRACKS) {
                    break;
                }
            }
        }

        if (cursor.getCount() == 0) {
            contentTitle = context.getString(R.string.info_inserting);
            contentText = list.size() + " elements";
            setForegroundAsync(createForegroundInfo(contentTitle, contentText, subText));
            for (int i = 0; i < list.size(); i++) {
                dao.insert(list.get(i));
                updated = true;
            }
        } else {
            contentTitle = context.getString(R.string.info_updating);
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
                contentTitle = context.getString(R.string.info_inserting);
                contentText = list.size() + " elements";
                setForegroundAsync(createForegroundInfo(contentTitle, contentText, subText));
                for (int i = 0; i < list.size(); i++) {
                    dao.insert(list.get(i));
                    updated = true;
                }
            }
        }

        dao.close();

        Data output = new Data.Builder()
                .putInt("catId", catalogId)
                .putInt("total", list.size())
                .putBoolean("updated", updated)
                .build();
        return Result.success(output);
    }

    @Override
    public void onStopped() {
        super.onStopped();
    }

    private ForegroundInfo createForegroundInfo(String contentTitle, String contentText, String subText) {
        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stopPendingIntent = WorkManager.getInstance(context).createCancelPendingIntent(getId());

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, DbService.NOTIFICATION_CHANNEL);

        builder
                .setContentTitle(contentTitle)
                .setContentText(contentText)
                .setSubText(subText)
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setSmallIcon(R.drawable.ic_action_rescan)
                .setOngoing(true)
                .addAction(R.drawable.ic_action_cancel, "Stop", stopPendingIntent);

        return new ForegroundInfo(DbService.NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
    }
}
