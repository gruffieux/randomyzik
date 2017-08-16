package com.gbrfix.randomizik;

import android.database.sqlite.SQLiteCursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.content.Context;
import android.util.Log;

import java.util.Random;

/**
 * Created by gab on 16.07.2017.
 */
public class MediaController implements MediaPlayer.OnCompletionListener, AudioManager.OnAudioFocusChangeListener {
    protected static MediaPlayer player = null;
    protected AudioManager manager;
    protected MediaDAO dao;
    private Context context;
    private UpdateSignal updateSignalListener;
    protected int currentId;

    public MediaController(Context context) {
        manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        dao = new MediaDAO(context);
        this.context = context;
        currentId = 0;
    }

    public int getCurrentId() {
        return currentId;
    }

    public int getCurrentPosition() {
        if (player == null) {
            return 0;
        }

        return player.getCurrentPosition();
    }

    public void restorePlayer(int id, int position) {
        try {
            dao.open();
            SQLiteCursor cursor = dao.getFromId(id);
            cursor.moveToFirst();
            currentId = id;
            String path = cursor.getString(1);
            player = MediaPlayer.create(context, Uri.parse(path));
            player.seekTo(position);
            player.setOnCompletionListener(this);
            dao.close();
        }
        catch (Exception e) {
            Log.v("Exception", e.getMessage());
        }
    }

    public void setUpdateSignalListener(UpdateSignal listener) {
        updateSignalListener = listener;
    }

    public boolean selectTrack() {
        dao.open();

        SQLiteCursor cursor = dao.getFromFlag("unread");
        int total = cursor.getCount();

        dao.close();

        if (total == 0) {
            return false;
        }

        if (total > 1) {
            Random random = new Random();
            int pos = random.nextInt(total - 1);
            cursor.moveToPosition(pos);
        }
        else {
            cursor.moveToFirst();
        }

        currentId = cursor.getInt(0);
        String path = cursor.getString(1);

        try {
            if (player != null) {
                player.release();
            }
            int result = manager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                player = MediaPlayer.create(context, Uri.parse(path));
                //player.seekTo(player.getDuration() - 10000);
                player.start();
                player.setOnCompletionListener(this);
            }

        }
        catch (Exception e) {
            Log.v("Exception", e.getMessage());
        }

        return true;
    }

    public void rewind() {
        if (player != null && player.isPlaying()) {
            try {
                player.stop();
                player.prepare();
                player.seekTo(0);
                player.start();
            } catch (Exception e) {
                Log.v("Exception", e.getMessage());
            }
        }
    }

    public void forward() {
        if (player != null && player.isPlaying()) {
            player.stop();
            manager.abandonAudioFocus(this);
            selectTrack();
        }
    }

    public void resume() {
        if (player == null) {
            selectTrack();
        }
        else {
            if (player.isPlaying()) {
                player.pause();
                manager.abandonAudioFocus(this);
            } else {
                int result = manager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    player.start();
                }
            }
        }
    }

    public boolean isPlaying() {
        return player != null && player.isPlaying();
    }

    public void updateState(String flag) {
        dao.open();

        if (!dao.getDb().isReadOnly()) {
            dao.update(currentId, flag);
        }

        dao.close();
    }

    public void destroy() {
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
        boolean res = selectTrack();
        updateSignalListener.onTrackReaden(!res);
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
                updateSignalListener.onTrackResume(true);
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
                updateSignalListener.onTrackResume(true);
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                player.setVolume(1f, 1f);
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                // Permanent loss of audio focus
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Pause playback
                updateSignalListener.onTrackResume(false);
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Lower the volume, keep playing
                player.setVolume(0.5f, 0.5f);
                break;
        }
    }
}
