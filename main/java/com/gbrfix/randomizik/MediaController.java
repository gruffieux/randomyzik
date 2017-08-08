package com.gbrfix.randomizik;

import android.database.sqlite.SQLiteCursor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.content.Context;

import java.util.Random;

/**
 * Created by gab on 16.07.2017.
 */
public class MediaController implements AudioManager.OnAudioFocusChangeListener {
    protected static MediaPlayer player = null;
    protected AudioManager manager;
    protected SQLiteCursor cursor;
    protected MediaDAO dao;
    private Context context;

    public MediaController(Context context) {
        manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        cursor = null;
        dao = new MediaDAO(context);
        this.context = context;
    }

    public boolean selectTrack() {
        dao.open();

        cursor = dao.getFromFlag("unread");
        int total = cursor.getCount();

        dao.close();

        if (total == 0) {
            return false;
        }

        Random random = new Random();
        int pos = random.nextInt(total - 1);
        cursor.moveToPosition(pos);
        int id = cursor.getInt(0);
        final String path = cursor.getString(1);

        try {
            if (player != null) {
                player.release();
            }
            int result = manager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                player = MediaPlayer.create(context, Uri.parse(path));
                //player.seekTo(player.getDuration() - 10000);
                player.start();

                final AudioManager.OnAudioFocusChangeListener afListener = this;

                // On traite lorsque la lecture est complétée
                player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                    public void onCompletion(MediaPlayer mp) {
                        updateState("read");
                        manager.abandonAudioFocus(afListener);
                        selectTrack();
                    }
                });
            }

        }
        catch (Exception e) {

        }

        return true;
    }

    public void rewind() {
        try {
            player.stop();
            player.prepare();
            player.seekTo(0);
            player.start();
        }
        catch (Exception e) {

        }
    }

    public void forward() {
        manager.abandonAudioFocus(this);
        selectTrack();
    }

    public void resume() {
        if (player == null) {
            selectTrack();
        }
        else {
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.start();
            }
        }
    }

    public void updateState(String flag) {
        dao.open();

        if (!dao.getDb().isReadOnly()) {
            int id = cursor.getInt(0);
            dao.update(id, flag);
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

    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                // Your app has been granted audio focus again
                // Raise volume to normal, restart playback if necessary
                selectTrack();
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                player.start();
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE:
                player.start();
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                player.setVolume(1f, 1f);
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                // Permanent loss of audio focus
                if (player != null) {
                    player.stop();
                    player.release();
                    player = null;
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Pause playback
                player.pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Lower the volume, keep playing
                player.setVolume(0.5f, 0.5f);
                break;
            case AudioManager.AUDIOFOCUS_NONE:
                break;
        }
    }
}
