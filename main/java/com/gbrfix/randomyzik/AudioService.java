package com.gbrfix.randomyzik;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.sqlite.SQLiteCursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import java.io.File;
import java.util.Random;

/**
 * Created by gab on 09.09.2017.
 */

public class AudioService extends IntentService implements MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, AudioManager.OnAudioFocusChangeListener {
    public final static int ONGOING_NOTIFICATION_ID = 1;
    public final static int MODE_TRACK = 0;
    public final static int MODE_ALBUM = 1;

    private MediaPlayer player;
    private AudioManager manager;
    private MediaDAO dao;
    private MediaSignal mediaSignalListener;
    private int currentId;
    private int mode;
    private boolean lastOfAlbum;
    private IntentFilter intentFilter;
    private AudioService.BecomingNoisyReceiver myNoisyAudioReceiver;
    private final IBinder binder = new AudioService.AudioBinder();
    private boolean bound;

    public boolean isBound() {
        return bound;
    }

    public void setBound(boolean bound) {
        this.bound = bound;
    }

    public class AudioBinder extends Binder {
        AudioService getService() {
            return AudioService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public MediaPlayer getPlayer() {
        return player;
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
        lastOfAlbum = false;
    }

    public AudioService() {
        super("MediaIntentService");

        player = null;
        currentId = 0;
        mode = MODE_TRACK;
        lastOfAlbum = false;
        intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        myNoisyAudioReceiver = new AudioService.BecomingNoisyReceiver();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        manager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        dao = new MediaDAO(this);
    }

    public void setMediaSignalListener(MediaSignal listener) {
        mediaSignalListener = listener;
    }

    public String getTrackLabel(int id) {
        try {
            dao.open();
            SQLiteCursor cursor = dao.getFromId(id);
            cursor.moveToFirst();
            String label = cursor.getString(4) + " - " + cursor.getString(5) + " - " + cursor.getString(6);
            dao.close();
            return label;
        }
        catch (Exception e) {
            return "";
        }
    }

    public String getCurrentTrackLabel() {
        return getTrackLabel(currentId);
    }

    public String getSummary(int total, int totalRead) {
        float percent = (float)totalRead / (float)total * 100;
        float f = percent * 10;
        int n = (int)f;
        percent = (float)n / 10;

        return String.format(getString(R.string.info_track_summary), totalRead, total, percent);
    }

    private void selectTrack() throws Exception {
        dao.open();

        SQLiteCursor cursor = dao.getAll();
        int total = cursor.getCount();

        SQLiteCursor cursorUnread = dao.getUnread();
        int totalUnread = cursorUnread.getCount();

        if (mode == MODE_ALBUM) {
            if (currentId == 0 || lastOfAlbum == true) {
                cursor = dao.getFromFlagAlbumGrouped("unread");
            }
            else {
                cursor = dao.getFromId(currentId);
            }
            int totalAlbum = cursor.getCount();
            if (totalAlbum > 1) {
                Random random = new Random();
                int pos = random.nextInt(totalAlbum);
                cursor.moveToPosition(pos);
            }
            else {
                cursor.moveToFirst();
            }
            String album = cursor.getString(5);
            String artist = cursor.getString(6);
            cursor = dao.getFromAlbum(album, artist);
            lastOfAlbum = cursor.getCount() <= 1;
        }
        else {
            cursor = cursorUnread;
        }

        dao.close();

        if (totalUnread == 0) {
            currentId = 0;
            throw new PlayEndException(getString(R.string.err_all_read));
        }

        if (mode == MODE_TRACK && totalUnread > 1) {
            Random random = new Random();
            int pos = random.nextInt(totalUnread);
            cursor.moveToPosition(pos);
        }
        else {
            cursor.moveToFirst();
        }

        currentId = cursor.getInt(0);
        String path = cursor.getString(1);

        if (player != null) {
            player.release();
        }

        int result = manager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            File file = new File(path);
            player = MediaPlayer.create(this, Uri.fromFile(file));
            //player.seekTo(player.getDuration() - 10000);
            player.start();
            player.setOnCompletionListener(this);
            player.setOnErrorListener(this);
            int totalRead = total - totalUnread + 1;
            mediaSignalListener.onTrackSelect(currentId, player.getDuration(), total, totalRead);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    int currentPosition = 0;
                    int total = player.getDuration();
                    while (currentPosition < total) {
                        try {
                            Thread.sleep(1000);
                            currentPosition = player.getCurrentPosition();
                        }
                        catch (Exception e) {
                            return;
                        }
                        mediaSignalListener.onTrackProgress(currentPosition);
                    }
                }
            }).start();
        }
    }

    public void rewind() throws Exception {
        if (player != null && player.isPlaying()) {
            player.stop();
            player.prepare();
            player.seekTo(0);
            player.start();
        }
    }

    public void forward() throws Exception {
        if (player != null && player.isPlaying()) {
            updateState("skip");
            player.stop();
            manager.abandonAudioFocus(this);
            selectTrack();
        }
    }

    public void resume() throws Exception {
        if (player == null) {
            selectTrack();
            registerReceiver(myNoisyAudioReceiver, intentFilter);
        }
        else {
            if (player.isPlaying()) {
                player.pause();
                manager.abandonAudioFocus(this);
                unregisterReceiver(myNoisyAudioReceiver);
            }
            else {
                int result = manager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    player.start();
                    registerReceiver(myNoisyAudioReceiver, intentFilter);
                }
            }
        }
    }

    private void updateState(String flag) {
        dao.open();

        if (!dao.getDb().isReadOnly()) {
            dao.updateFlag(currentId, flag);
        }

        dao.close();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        manager.abandonAudioFocus(this);

        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        updateState("read");
        manager.abandonAudioFocus(this);

        try {
            selectTrack();
            mediaSignalListener.onTrackRead(false);
        }
        catch (PlayEndException e) {
            unregisterReceiver(myNoisyAudioReceiver);
            mediaSignalListener.onTrackRead(true);
        }
        catch (Exception e) {
            Log.v("Exception", e.getMessage());
        }
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        if (player == null) {
            return;
        }

        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                // Your app has been granted audio focus again
                // Raise volume to normal, restart playback if necessary
                player.setVolume(1f, 1f);
                mediaSignalListener.onTrackResume(true);
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
                mediaSignalListener.onTrackResume(true);
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                player.setVolume(1f, 1f);
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                // Permanent loss of audio focus
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Pause playback
                mediaSignalListener.onTrackResume(false);
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Lower the volume, keep playing
                player.setVolume(0.5f, 0.5f);
                break;
        }
    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
        return false;
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {

    }

    private class BecomingNoisyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                mediaSignalListener.onTrackResume(false);
            }
        }
    }
}
