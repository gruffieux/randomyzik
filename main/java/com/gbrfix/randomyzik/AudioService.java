package com.gbrfix.randomyzik;

import android.app.IntentService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.sqlite.SQLiteCursor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
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
    private AudioFocusRequest focusRequest;
    private MediaDAO dao;
    private MediaSignal mediaSignalListener;
    private int currentId, selectId;
    private int mode;
    private boolean lastOfAlbum;
    private IntentFilter intentFilter;
    private AudioService.BecomingNoisyReceiver myNoisyAudioReceiver;
    private final IBinder binder = new AudioService.AudioBinder();
    private boolean bound, test;

    public int getCurrentId() {
        return currentId;
    }

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

    public boolean playerIsActive() {
        return player != null;
    }

    public boolean playerIsPlaying() {
        return player != null && player.isPlaying();
    }

    public int playerPosition() {
        return player != null ? player.getCurrentPosition() : 0;
    }

    public int playerDuration() {
        return player != null ? player.getDuration() : 0;
    }

    public int getMode() {
        return mode;
    }

    public void setMode(int mode) {
        this.mode = mode;
        lastOfAlbum = false;
    }

    public void setSelectId(int selectId) {
        this.selectId = selectId;
    }

    public void setTest(boolean test) {
        this.test = test;
    }

    public AudioService() {
        super("MediaIntentService");

        player = null;
        currentId = selectId = 0;
        mode = MODE_TRACK;
        lastOfAlbum = test = false;
        intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        myNoisyAudioReceiver = new AudioService.BecomingNoisyReceiver();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        manager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
            focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes)
                .build();
        }

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

        SQLiteCursor cursorSel = null;
        if (selectId > 0) {
            cursorSel  = dao.getFromId(selectId);
            currentId = selectId;
            selectId = 0;
        }

        if (player != null) {
            player.release();
            player = null;
        }

        if (mode == MODE_ALBUM) {
            if (currentId == 0 || lastOfAlbum == true) {
                dao.replaceFlag("skip", "unread");
                cursor = dao.getFromFlagAlbumGrouped("unread");
            }
            else {
                cursor = dao.getFromId(currentId);
            }
            int totalAlbum = cursor.getCount();
            if (totalAlbum == 0) {
                currentId = 0;
                throw new PlayEndException(getString(R.string.err_all_read));
            }
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
            cursor = dao.getFromAlbum(album, artist, "unread");
            lastOfAlbum = cursor.getCount() <= 1;
            if (cursorSel != null) {
                cursor = cursorSel;
            }
            cursor.moveToFirst();
        }
        else {
            if (cursorSel != null) {
                cursor = cursorSel;
                cursor.moveToFirst();
            }
            else {
                if (totalUnread == 0) {
                    currentId = 0;
                    throw new PlayEndException(getString(R.string.err_all_read));
                }
                cursor = cursorUnread;
                if (totalUnread > 1) {
                    Random random = new Random();
                    int pos = random.nextInt(totalUnread);
                    cursor.moveToPosition(pos);
                } else {
                    cursor.moveToFirst();
                }
            }
        }

        currentId = cursor.getInt(0);
        String path = cursor.getString(1);

        dao.close();

        if (requestAudioFocus() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            File file = new File(path);
            player = MediaPlayer.create(this, Uri.fromFile(file));
            if (test) {
                player.seekTo(player.getDuration() - 10);
            }
            player.start();
            player.setOnCompletionListener(this);
            player.setOnErrorListener(this);
            int totalRead = total - totalUnread;
            mediaSignalListener.onTrackSelect(currentId, player.getDuration(), total, totalRead+1);

            new Thread(new Runnable() {
                @Override
                public void run() {
                    int currentPosition = 0;
                    int total = player.getDuration();
                    while (currentPosition < total) {
                        try {
                            Thread.sleep(1000);
                            currentPosition = player.getCurrentPosition();
                        } catch (Exception e) {
                            return;
                        }
                        mediaSignalListener.onTrackProgress(currentPosition);
                    }
                }
            }).start();
        }
    }

    private int requestAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return manager.requestAudioFocus(focusRequest);
        }

        return manager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    }

    private void abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.abandonAudioFocusRequest(focusRequest);
        }

        manager.abandonAudioFocus(this);
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

    public void resume(boolean changeFocus) throws Exception {
        if (player == null || selectId > 0) {
            selectTrack();
            registerReceiver(myNoisyAudioReceiver, intentFilter);
        }
        else {
            if (player.isPlaying()) {
                player.pause();
                if (changeFocus) {
                    abandonAudioFocus();
                }
                unregisterReceiver(myNoisyAudioReceiver);
            }
            else {
                int result = changeFocus ? requestAudioFocus() : AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
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
                mediaSignalListener.onTrackResume(!player.isPlaying(), true);
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
                mediaSignalListener.onTrackResume(!player.isPlaying(), false);
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                player.setVolume(1f, 1f);
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                // Permanent loss of audio focus
                mediaSignalListener.onTrackResume(player.isPlaying(), true);
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Pause playback
                mediaSignalListener.onTrackResume(player.isPlaying(), false);
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
                mediaSignalListener.onTrackResume(player.isPlaying(), true);
            }
        }
    }
}
